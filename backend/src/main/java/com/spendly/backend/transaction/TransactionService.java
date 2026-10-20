package com.spendly.backend.transaction;

import com.spendly.backend.category.Category;
import com.spendly.backend.category.CategoryRepository;
import com.spendly.backend.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class TransactionService {
    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    @Transactional
    public TransactionResponse create(@NotNull UUID userId, @NotNull @Valid CreateTransactionRequest request) {
        Category category = categories.findForTransactionCreationByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
        if (category.getType() != request.type()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_TYPE_MISMATCH", "거래와 카테고리 유형이 일치해야 합니다.");
        }
        if (category.isArchived()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_ARCHIVED", "보관 처리된 카테고리는 선택할 수 없습니다.");
        }
        Transaction transaction = transactions.saveAndFlush(new Transaction(userId, category.getId(), request.type(),
                request.amount(), request.occurredOn(), request.description(), request.memo()));
        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse detail(@NotNull UUID userId, @NotNull UUID id) {
        return transactions.findByIdAndUserId(id, userId).map(TransactionResponse::from).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "거래를 찾을 수 없습니다."));
    }
}
