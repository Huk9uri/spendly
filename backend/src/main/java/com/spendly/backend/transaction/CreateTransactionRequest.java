package com.spendly.backend.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.spendly.backend.domain.TransactionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull TransactionType type,
        @NotNull @DecimalMin("1") @DecimalMax("999999999999999") @Digits(integer = 15, fraction = 0) BigDecimal amount,
        @NotNull UUID categoryId,
        @NotNull LocalDate occurredOn,
        @NotBlank @Size(max = 100) String description,
        @Size(max = 1000) String memo) {

    public CreateTransactionRequest {
        description = description == null ? null : description.strip();
        memo = memo == null ? "" : memo;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateTransactionRequest fromJson(Map<String, Object> fields) {
        if (!Set.of("type", "amount", "categoryId", "occurredOn", "description", "memo").containsAll(fields.keySet())) {
            throw new IllegalArgumentException("Unsupported transaction field");
        }
        String type = text(fields.get("type"));
        String category = text(fields.get("categoryId"));
        String date = text(fields.get("occurredOn"));
        if (category != null && !category.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Invalid category UUID");
        }
        if (date != null && (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || date.startsWith("0000-"))) {
            throw new IllegalArgumentException("Invalid transaction date");
        }
        return new CreateTransactionRequest(type == null ? null : TransactionType.valueOf(type),
                integerAmount(fields.get("amount")), category == null ? null : UUID.fromString(category),
                date == null ? null : LocalDate.parse(date), text(fields.get("description")), text(fields.get("memo")));
    }

    private static String text(Object value) {
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Transaction field must be a string");
        }
        return (String) value;
    }

    private static BigDecimal integerAmount(Object value) {
        if (value == null) { return null; }
        if (!(value instanceof Integer || value instanceof Long || value instanceof BigInteger)) {
            throw new IllegalArgumentException("Amount must be a JSON integer");
        }
        return new BigDecimal(value.toString());
    }

    @Override
    public String toString() { return "CreateTransactionRequest[REDACTED]"; }
}
