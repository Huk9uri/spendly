package com.spendly.backend.category;

import com.spendly.backend.common.ApiException;
import com.spendly.backend.domain.TransactionType;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) { this.categories = categories; }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, boolean includeArchived) {
        List<Category> result = includeArchived
                ? categories.findAllByUserIdOrderByNameAscIdAsc(userId)
                : categories.findAllByUserIdAndArchivedFalseOrderByNameAscIdAsc(userId);
        return result.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CreateCategoryRequest request) {
        TransactionType type = TransactionType.valueOf(request.type());
        if (categories.existsByUserIdAndTypeAndName(userId, type, request.name())) { throw duplicateName(); }
        return CategoryResponse.from(saveAndFlush(new Category(userId, request.name(), type)));
    }

    @Transactional
    public CategoryResponse rename(UUID userId, UUID id, RenameCategoryRequest request) {
        Category category = owned(userId, id);
        if (categories.existsByUserIdAndTypeAndNameAndIdNot(userId, category.getType(), request.name(), id)) {
            throw duplicateName();
        }
        category.rename(request.name());
        return CategoryResponse.from(saveAndFlush(category));
    }

    @Transactional
    public void archive(UUID userId, UUID id) {
        Category category = owned(userId, id);
        category.archive();
        saveAndFlush(category);
    }

    private Category owned(UUID userId, UUID id) {
        return categories.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
    }

    private Category saveAndFlush(Category category) {
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "uk_categories_user_type_name".equals(constraint.getConstraintName())) {
                    throw duplicateName();
                }
            }
            throw exception;
        }
    }

    private ApiException duplicateName() {
        return new ApiException(HttpStatus.CONFLICT, "CATEGORY_ALREADY_EXISTS", "이미 사용 중인 이름입니다.");
    }
}
