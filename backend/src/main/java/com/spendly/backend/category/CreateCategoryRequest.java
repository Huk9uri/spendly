package com.spendly.backend.category;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String type) {
    public CreateCategoryRequest { name = name == null ? null : name.strip(); }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateCategoryRequest fromJson(Map<String, Object> fields) {
        if (!Set.of("name", "type").containsAll(fields.keySet())) {
            throw new IllegalArgumentException("Unsupported category field");
        }
        return new CreateCategoryRequest(text(fields.get("name")), text(fields.get("type")));
    }

    private static String text(Object value) {
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Category field must be a string");
        }
        return (String) value;
    }
}
