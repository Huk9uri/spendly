package com.spendly.backend;

import com.spendly.backend.category.Category;
import com.spendly.backend.domain.TransactionType;
import com.spendly.backend.transaction.Transaction;
import com.spendly.backend.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EntityMappingTests {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsEntitiesWithoutLosingMoneyOrDate() {
        Transaction transaction = persistFixture();
        UUID transactionId = transaction.getId();
        UUID userId = transaction.getUserId();
        UUID categoryId = transaction.getCategoryId();
        entityManager.clear();

        User user = entityManager.find(User.class, userId);
        Category category = entityManager.find(Category.class, categoryId);
        Transaction reloaded = entityManager.find(Transaction.class, transactionId);

        assertNotNull(user.getId());
        assertTrue(user.getEmail().endsWith("@example.invalid"));
        assertEquals("test-only-placeholder", user.getPasswordHash());
        assertNotNull(user.getCreatedAt());
        assertEquals(userId, category.getUserId());
        assertEquals("mapping-test", category.getName());
        assertEquals(TransactionType.EXPENSE, category.getType());
        assertFalse(category.isArchived());
        assertNotNull(category.getCreatedAt());
        assertEquals(userId, reloaded.getUserId());
        assertEquals(categoryId, reloaded.getCategoryId());
        assertEquals(TransactionType.EXPENSE, reloaded.getType());
        assertEquals(0, new BigDecimal("999999999999999").compareTo(reloaded.getAmount()));
        assertEquals(LocalDate.of(2026, 10, 20), reloaded.getOccurredOn());
        assertEquals("mapping verification", reloaded.getDescription());
        assertEquals("", reloaded.getMemo());
        assertEquals(0, reloaded.getVersion());
        assertNotNull(reloaded.getCreatedAt());
        assertEquals(reloaded.getCreatedAt(), reloaded.getUpdatedAt());
    }

    @Test
    void dirtyUpdateIncrementsVersionAndPreservesCreatedAt() {
        Transaction transaction = persistFixture();
        entityManager.refresh(transaction);
        Instant createdAt = transaction.getCreatedAt();

        // Mapping-only test: no public business update operation is introduced yet.
        ReflectionTestUtils.setField(transaction, "memo", "updated memo");
        entityManager.flush();
        UUID transactionId = transaction.getId();
        entityManager.clear();

        Transaction reloaded = entityManager.find(Transaction.class, transactionId);
        assertEquals("updated memo", reloaded.getMemo());
        assertEquals(1, reloaded.getVersion());
        assertEquals(createdAt, reloaded.getCreatedAt());
        assertTrue(reloaded.getUpdatedAt().isAfter(createdAt));
    }

    @Test
    void rejectsStaleVersionOnMerge() {
        Transaction stale = persistFixture();
        entityManager.detach(stale);
        Transaction current = entityManager.find(Transaction.class, stale.getId());
        ReflectionTestUtils.setField(current, "memo", "first update");
        entityManager.flush();
        assertEquals(1, current.getVersion());

        ReflectionTestUtils.setField(stale, "memo", "stale update");
        assertThrows(OptimisticLockException.class, () -> {
            entityManager.merge(stale);
            entityManager.flush();
        });
    }

    private Transaction persistFixture() {
        User user = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
        entityManager.persist(user);
        entityManager.flush();
        Category category = new Category(user.getId(), "mapping-test", TransactionType.EXPENSE);
        entityManager.persist(category);
        entityManager.flush();
        Transaction transaction = new Transaction(user.getId(), category.getId(),
                TransactionType.EXPENSE, new BigDecimal("999999999999999"),
                LocalDate.of(2026, 10, 20), "mapping verification", "");
        entityManager.persist(transaction);
        entityManager.flush();
        return transaction;
    }
}
