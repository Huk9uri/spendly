package com.spendly.backend;

import com.spendly.backend.category.Category;
import com.spendly.backend.category.CategoryRepository;
import com.spendly.backend.domain.TransactionType;
import com.spendly.backend.transaction.Transaction;
import com.spendly.backend.transaction.TransactionRepository;
import com.spendly.backend.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RepositoryTests {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private TransactionRepository transactions;

    private UUID ownerId;
    private UUID otherOwnerId;
    private UUID categoryId;
    private UUID otherCategoryId;
    private UUID transactionId;
    private UUID otherTransactionId;

    @BeforeEach
    void persistTwoOwnersAndTheirResources() {
        User owner = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
        User other = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
        entityManager.persist(owner);
        entityManager.persist(other);
        entityManager.flush();
        ownerId = owner.getId();
        otherOwnerId = other.getId();

        Category category = categories.save(new Category(ownerId, "repository-test", TransactionType.EXPENSE));
        Category otherCategory = categories.save(new Category(otherOwnerId, "repository-test", TransactionType.EXPENSE));
        entityManager.flush();
        categoryId = category.getId();
        otherCategoryId = otherCategory.getId();

        Transaction transaction = transactions.save(new Transaction(ownerId, categoryId,
                TransactionType.EXPENSE, new BigDecimal("12000"),
                LocalDate.of(2026, 10, 20), "repository verification", ""));
        Transaction otherTransaction = transactions.save(new Transaction(otherOwnerId, otherCategoryId,
                TransactionType.EXPENSE, new BigDecimal("24000"),
                LocalDate.of(2026, 10, 20), "repository verification", ""));
        entityManager.flush();
        transactionId = transaction.getId();
        otherTransactionId = otherTransaction.getId();
        entityManager.clear();
    }

    @Test
    void categoryDetailRequiresMatchingOwner() {
        assertEquals(categoryId, categories.findByIdAndUserId(categoryId, ownerId).orElseThrow().getId());
        assertEquals(otherCategoryId, categories.findByIdAndUserId(otherCategoryId, otherOwnerId).orElseThrow().getId());
        assertTrue(categories.findByIdAndUserId(categoryId, otherOwnerId).isEmpty());
        assertTrue(categories.findByIdAndUserId(otherCategoryId, ownerId).isEmpty());
        assertTrue(categories.findByIdAndUserId(UUID.randomUUID(), ownerId).isEmpty());
    }

    @Test
    void categoryListsContainOnlyTheRequestedOwnersResources() {
        assertEquals(List.of(categoryId), categories.findAllByUserId(ownerId).stream().map(Category::getId).toList());
        assertEquals(List.of(otherCategoryId), categories.findAllByUserId(otherOwnerId).stream().map(Category::getId).toList());
        assertTrue(categories.findAllByUserId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void transactionDetailRequiresMatchingOwner() {
        Transaction found = transactions.findByIdAndUserId(transactionId, ownerId).orElseThrow();
        assertEquals(transactionId, found.getId());
        assertEquals(0, new BigDecimal("12000").compareTo(found.getAmount()));
        assertEquals(otherTransactionId, transactions.findByIdAndUserId(otherTransactionId, otherOwnerId).orElseThrow().getId());
        assertTrue(transactions.findByIdAndUserId(transactionId, otherOwnerId).isEmpty());
        assertTrue(transactions.findByIdAndUserId(otherTransactionId, ownerId).isEmpty());
        assertTrue(transactions.findByIdAndUserId(UUID.randomUUID(), ownerId).isEmpty());
    }

    @Test
    void transactionListsContainOnlyTheRequestedOwnersResources() {
        assertEquals(List.of(transactionId), transactions.findAllByUserId(ownerId).stream().map(Transaction::getId).toList());
        assertEquals(List.of(otherTransactionId), transactions.findAllByUserId(otherOwnerId).stream().map(Transaction::getId).toList());
        assertTrue(transactions.findAllByUserId(UUID.randomUUID()).isEmpty());
    }
}
