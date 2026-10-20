package com.spendly.backend.category;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;

public record RenameCategoryRequest(@NotBlank @Size(max = 50) String name) {
    public RenameCategoryRequest { name = name == null ? null : name.strip(); }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RenameCategoryRequest fromJson(Map<String, Object> fields) {
        if (!Set.of("name").containsAll(fields.keySet())) {
            throw new IllegalArgumentException("Unsupported category field");
        }
        Object name = fields.get("name");
        if (name != null && !(name instanceof String)) {
            throw new IllegalArgumentException("Category name must be a string");
        }
        return new RenameCategoryRequest((String) name);
    }
}
