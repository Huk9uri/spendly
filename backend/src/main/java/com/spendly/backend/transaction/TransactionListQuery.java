package com.spendly.backend.transaction;

import com.spendly.backend.common.ApiException;
import com.spendly.backend.domain.TransactionType;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record TransactionListQuery(YearMonth month, TransactionType type, UUID categoryId,
        String q, int page, int size, String sort, boolean ascending) {
    public static TransactionListQuery parse(String month, String type, String categoryId,
            String q, int page, int size, String sort) {
        try {
            if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) { throw new IllegalArgumentException(); }
            if (month != null && (!month.matches("[0-9]{4}-[0-9]{2}") || month.startsWith("0000-"))) { throw new IllegalArgumentException(); }
            UUID category = categoryId == null ? null : UUID.fromString(categoryId);
            if (category != null && !category.toString().equalsIgnoreCase(categoryId)) { throw new IllegalArgumentException(); }
            String search = q == null ? "" : q.strip();
            if (search.length() > 100) { throw new IllegalArgumentException(); }
            String[] order = sort.split(",", -1);
            if (order.length != 2 || !Set.of("occurredOn", "amount", "createdAt").contains(order[0])
                    || !Set.of("asc", "desc").contains(order[1])) { throw new IllegalArgumentException(); }
            return new TransactionListQuery(month == null ? null : YearMonth.parse(month),
                    type == null ? null : TransactionType.valueOf(type), category, search, page, size, order[0], order[1].equals("asc"));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "목록 조회 조건을 확인해 주세요.");
        }
    }
}
