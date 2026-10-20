package com.spendly.backend.transaction;

import com.spendly.backend.category.Category;
import com.spendly.backend.category.CategoryRepository;
import com.spendly.backend.category.CategoryService;
import com.spendly.backend.common.ApiException;
import com.spendly.backend.domain.TransactionType;
import com.spendly.backend.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransactionCategoryLockTests {
    @Autowired private PlatformTransactionManager manager;
    @Autowired private DataSource dataSource;
    @Autowired private TransactionService service;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void holdsCategoryAgainstConcurrentArchiveUntilCreationCommits() throws Exception {
        TransactionTemplate template = new TransactionTemplate(manager);
        UUID[] fixture = template.execute(status -> {
            User user = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
            entityManager.persist(user);
            entityManager.flush();
            Category category = categories.saveAndFlush(new Category(user.getId(), "lock-test", TransactionType.EXPENSE));
            return new UUID[]{user.getId(), category.getId()};
        });
        assertNotNull(fixture);
        UUID owner = fixture[0];
        UUID categoryId = fixture[1];
        CreateTransactionRequest request = new CreateTransactionRequest(TransactionType.EXPENSE,
                new BigDecimal("12000"), categoryId, LocalDate.of(2026, 10, 21), "lock-test", null);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var creation = executor.submit(() -> template.execute(status -> {
                TransactionResponse result = service.create(owner, request);
                locked.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) { throw new IllegalStateException("Lock test release timed out"); }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Lock test interrupted");
                }
                return result;
            }));
            try {
                assertTrue(locked.await(10, TimeUnit.SECONDS));
                try (var connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    // An archive changes a non-key column; KEY SHARE from a FK alone would not protect it.
                    try (var statement = connection.prepareStatement("SELECT id FROM categories WHERE id = ? FOR NO KEY UPDATE NOWAIT")) {
                        statement.setObject(1, categoryId);
                        SQLException blocked = assertThrows(SQLException.class, statement::executeQuery);
                        assertEquals("55P03", blocked.getSQLState());
                    } finally {
                        connection.rollback();
                    }
                }
            } finally {
                release.countDown();
            }
            TransactionResponse created = creation.get(10, TimeUnit.SECONDS);
            categoryService.archive(owner, categoryId);
            assertEquals(created.id(), service.detail(owner, created.id()).id());
            assertEquals("CATEGORY_ARCHIVED", assertThrows(ApiException.class, () -> service.create(owner, request)).getCode());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            template.executeWithoutResult(status -> {
                entityManager.createQuery("delete from Transaction t where t.userId = :owner").setParameter("owner", owner).executeUpdate();
                entityManager.createQuery("delete from Category c where c.userId = :owner").setParameter("owner", owner).executeUpdate();
                entityManager.createQuery("delete from User u where u.id = :owner").setParameter("owner", owner).executeUpdate();
            });
        }
    }
}
