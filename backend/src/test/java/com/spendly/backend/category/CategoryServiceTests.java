package com.spendly.backend.category;

import com.spendly.backend.common.ApiException;
import com.spendly.backend.domain.TransactionType;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CategoryServiceTests {
    @Test
    void translatesConcurrentDuplicateCreateWithoutMaskingOtherConstraints() {
        CategoryRepository categories = mock(CategoryRepository.class);
        CategoryService service = new CategoryService(categories);
        UUID owner = UUID.randomUUID();
        when(categories.saveAndFlush(any())).thenThrow(violation("uk_categories_user_type_name"));
        ApiException duplicate = assertThrows(ApiException.class,
                () -> service.create(owner, new CreateCategoryRequest("food", "EXPENSE")));
        assertEquals("CATEGORY_ALREADY_EXISTS", duplicate.getCode());
        assertEquals(409, duplicate.getStatus().value());
        DataIntegrityViolationException unrelated = violation("fk_categories_user");
        doThrow(unrelated).when(categories).saveAndFlush(any());
        assertSame(unrelated, assertThrows(DataIntegrityViolationException.class,
                () -> service.create(owner, new CreateCategoryRequest("food", "EXPENSE"))));
    }

    @Test
    void translatesConcurrentDuplicateRename() {
        CategoryRepository categories = mock(CategoryRepository.class);
        CategoryService service = new CategoryService(categories);
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Category category = new Category(owner, "food", TransactionType.EXPENSE);
        ReflectionTestUtils.setField(category, "id", id);
        when(categories.findByIdAndUserId(id, owner)).thenReturn(Optional.of(category));
        when(categories.saveAndFlush(any())).thenThrow(violation("uk_categories_user_type_name"));
        assertEquals("CATEGORY_ALREADY_EXISTS", assertThrows(ApiException.class,
                () -> service.rename(owner, id, new RenameCategoryRequest("other"))).getCode());
    }

    private DataIntegrityViolationException violation(String name) {
        return new DataIntegrityViolationException("redacted",
                new ConstraintViolationException("redacted", new SQLException("redacted", "23505"), name));
    }
}
