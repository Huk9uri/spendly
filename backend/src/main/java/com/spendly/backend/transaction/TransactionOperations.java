package com.spendly.backend.transaction;

import com.spendly.backend.category.Category;
import com.spendly.backend.category.CategoryRepository;
import com.spendly.backend.common.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionOperations {
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final EntityManager em;
    private final Validator validator;

    public TransactionOperations(TransactionRepository transactions, CategoryRepository categories, EntityManager em, Validator validator) {
        this.transactions = transactions;
        this.categories = categories;
        this.em = em;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public TransactionPage list(UUID userId, TransactionListQuery query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        var data = cb.createQuery(Transaction.class);
        Root<Transaction> root = data.from(Transaction.class);
        data.where(predicates(cb, root, userId, query));
        data.orderBy(query.ascending() ? cb.asc(root.get(query.sort())) : cb.desc(root.get(query.sort())),
                query.ascending() ? cb.asc(root.get("id")) : cb.desc(root.get("id")));
        var content = em.createQuery(data).setFirstResult(query.page() * query.size()).setMaxResults(query.size())
                .getResultList().stream().map(TransactionResponse::from).toList();
        var count = cb.createQuery(Long.class);
        Root<Transaction> counted = count.from(Transaction.class);
        count.select(cb.count(counted)).where(predicates(cb, counted, userId, query));
        long total = em.createQuery(count).getSingleResult();
        return new TransactionPage(content, query.page(), query.size(), total, (total + query.size() - 1) / query.size());
    }

    private Predicate[] predicates(CriteriaBuilder cb, Root<Transaction> root, UUID userId, TransactionListQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("userId"), java.util.Objects.requireNonNull(userId)));
        if (query.month() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredOn"), query.month().atDay(1)));
            predicates.add(cb.lessThan(root.get("occurredOn"), query.month().plusMonths(1).atDay(1)));
        }
        if (query.type() != null) { predicates.add(cb.equal(root.get("type"), query.type())); }
        if (query.categoryId() != null) { predicates.add(cb.equal(root.get("categoryId"), query.categoryId())); }
        if (!query.q().isEmpty()) {
            String pattern = "%" + query.q().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            predicates.add(cb.or(cb.like(cb.lower(root.get("description")), pattern, '\\'), cb.like(cb.lower(root.get("memo")), pattern, '\\')));
        }
        return predicates.toArray(Predicate[]::new);
    }

    @Transactional
    public TransactionResponse patch(UUID userId, UUID id, PatchTransactionRequest request) {
        Transaction transaction = owned(userId, id);
        if (transaction.getVersion() != request.version()) { throw conflict(); }
        // Also verify the version at commit for a no-op request.
        em.lock(transaction, LockModeType.OPTIMISTIC);
        CreateTransactionRequest values;
        try { values = request.merge(transaction); }
        catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "수정할 입력값을 확인해 주세요.");
        }
        var violations = validator.validate(values);
        if (!violations.isEmpty()) { throw new ConstraintViolationException(violations); }
        if (!transaction.getCategoryId().equals(values.categoryId()) || transaction.getType() != values.type()) {
            Category category = categories.findForTransactionCreationByIdAndUserId(values.categoryId(), userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
            if (category.getType() != values.type()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_TYPE_MISMATCH", "거래와 카테고리 유형이 일치해야 합니다.");
            }
            if (category.isArchived()) { throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_ARCHIVED", "보관 처리된 카테고리는 선택할 수 없습니다."); }
        }
        transaction.edit(values);
        em.flush();
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        em.remove(owned(userId, id));
        em.flush();
    }

    private Transaction owned(UUID userId, UUID id) {
        return transactions.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "거래를 찾을 수 없습니다."));
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "TRANSACTION_VERSION_CONFLICT", "거래가 변경되었습니다. 다시 조회해 주세요.");
    }
}
