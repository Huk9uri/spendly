package com.spendly.backend.transaction;

import com.spendly.backend.category.Category;
import com.spendly.backend.category.CategoryRepository;
import com.spendly.backend.common.ApiException;
import com.spendly.backend.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransactionServiceTests {
    @Test
    void createsWithScopedCategoryAndNormalizedMemo() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        UUID owner = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category category = new Category(owner, "food", TransactionType.EXPENSE);
        ReflectionTestUtils.setField(category, "id", categoryId);
        when(categories.findForTransactionCreationByIdAndUserId(categoryId, owner)).thenReturn(Optional.of(category));
        when(transactions.saveAndFlush(any())).thenAnswer(call -> {
            Transaction saved = call.getArgument(0);
            assertEquals(owner, saved.getUserId());
            assertEquals("", saved.getMemo());
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        TransactionResponse created = new TransactionService(transactions, categories).create(owner, request(categoryId));
        assertEquals(categoryId, created.categoryId());
        assertEquals("", created.memo());
        assertEquals(0, created.version());
        verify(categories).findForTransactionCreationByIdAndUserId(categoryId, owner);
    }

    @Test
    void rejectsMissingMismatchedOrArchivedCategoryBeforeSaving() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        TransactionService service = new TransactionService(transactions, categories);
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(categories.findForTransactionCreationByIdAndUserId(id, owner)).thenReturn(Optional.empty());
        assertEquals("CATEGORY_NOT_FOUND", assertThrows(ApiException.class, () -> service.create(owner, request(id))).getCode());
        Category mismatched = new Category(owner, "income", TransactionType.INCOME);
        when(categories.findForTransactionCreationByIdAndUserId(id, owner)).thenReturn(Optional.of(mismatched));
        assertEquals("CATEGORY_TYPE_MISMATCH", assertThrows(ApiException.class, () -> service.create(owner, request(id))).getCode());
        Category archived = new Category(owner, "food", TransactionType.EXPENSE);
        archived.archive();
        when(categories.findForTransactionCreationByIdAndUserId(id, owner)).thenReturn(Optional.of(archived));
        assertEquals("CATEGORY_ARCHIVED", assertThrows(ApiException.class, () -> service.create(owner, request(id))).getCode());
        verifyNoInteractions(transactions);
    }

    @Test
    void missingDetailIsScopedAndDoesNotConsultCategory() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(transactions.findByIdAndUserId(id, owner)).thenReturn(Optional.empty());
        ApiException failure = assertThrows(ApiException.class, () -> new TransactionService(transactions, categories).detail(owner, id));
        assertEquals(404, failure.getStatus().value());
        assertEquals("TRANSACTION_NOT_FOUND", failure.getCode());
        verify(transactions).findByIdAndUserId(id, owner);
        verifyNoInteractions(categories);
    }

    private CreateTransactionRequest request(UUID category) {
        return new CreateTransactionRequest(TransactionType.EXPENSE, new BigDecimal("12000"), category,
                LocalDate.of(2026, 10, 21), "lunch", null);
    }
}
