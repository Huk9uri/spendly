package com.spendly.backend.transaction;

import com.spendly.backend.category.Category;
import com.spendly.backend.category.CategoryRepository;
import com.spendly.backend.domain.TransactionType;
import com.spendly.backend.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransactionConcurrencyTests {
    @Autowired private PlatformTransactionManager manager;
    @Autowired private TransactionService service;
    @Autowired private TransactionOperations operations;
    @Autowired private CategoryRepository categories;
    @PersistenceContext private EntityManager em;

    @Test
    void concurrentWriterPreventsLostUpdateNoOpAndStaleDelete() throws Exception {
        TransactionTemplate template = new TransactionTemplate(manager);
        for (String action : new String[]{"patch", "noop", "delete"}) {
            UUID[] fixture = template.execute(status -> {
                User owner = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
                em.persist(owner);
                em.flush();
                Category category = categories.saveAndFlush(new Category(owner.getId(), "concurrency", TransactionType.EXPENSE));
                var transaction = service.create(owner.getId(), new CreateTransactionRequest(TransactionType.EXPENSE,
                        BigDecimal.ONE, category.getId(), LocalDate.of(2026, 10, 21), "original", ""));
                return new UUID[]{owner.getId(), transaction.id()};
            });
            assertNotNull(fixture);
            var loaded = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var staleWriter = executor.submit(() -> template.execute(status -> {
                    em.find(Transaction.class, fixture[1]);
                    loaded.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Concurrent test timed out"); }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    if (action.equals("delete")) { operations.delete(fixture[0], fixture[1]); }
                    else {
                        operations.patch(fixture[0], fixture[1], PatchTransactionRequest.fromJson(
                                Map.of("version", 0, "description", action.equals("noop") ? "original" : "stale")));
                    }
                    return null;
                }));
                assertTrue(loaded.await(10, TimeUnit.SECONDS));
                operations.patch(fixture[0], fixture[1], PatchTransactionRequest.fromJson(Map.of("version", 0, "description", "winner")));
                release.countDown();
                var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> staleWriter.get(10, TimeUnit.SECONDS));
                Throwable cause = failure;
                boolean optimistic = false;
                while (cause != null) {
                    optimistic |= cause instanceof org.springframework.dao.OptimisticLockingFailureException
                            || cause instanceof jakarta.persistence.OptimisticLockException;
                    cause = cause.getCause();
                }
                assertTrue(optimistic, "Expected a real JPA optimistic conflict for " + action);
                assertEquals("winner", service.detail(fixture[0], fixture[1]).description());
                assertEquals(1, service.detail(fixture[0], fixture[1]).version());
            } finally {
                release.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
                template.executeWithoutResult(status -> {
                    em.createQuery("delete from Transaction t where t.userId = :owner").setParameter("owner", fixture[0]).executeUpdate();
                    em.createQuery("delete from Category c where c.userId = :owner").setParameter("owner", fixture[0]).executeUpdate();
                    em.createQuery("delete from User u where u.id = :owner").setParameter("owner", fixture[0]).executeUpdate();
                });
            }
        }
    }
}
