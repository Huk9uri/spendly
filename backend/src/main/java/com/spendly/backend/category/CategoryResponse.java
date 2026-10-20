package com.spendly.backend.category;

import com.spendly.backend.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(UUID id, String name, TransactionType type, boolean archived, Instant createdAt) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType(),
                category.isArchived(), category.getCreatedAt());
    }
}
