package com.spendly.backend.transaction;

import com.spendly.backend.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(UUID id, TransactionType type, BigDecimal amount, UUID categoryId,
        LocalDate occurredOn, String description, String memo, long version) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getType(), transaction.getAmount(),
                transaction.getCategoryId(), transaction.getOccurredOn(), transaction.getDescription(),
                transaction.getMemo(), transaction.getVersion());
    }
}
