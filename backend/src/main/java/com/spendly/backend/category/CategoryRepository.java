package com.spendly.backend.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.spendly.backend.domain.TransactionType;
import org.springframework.data.repository.Repository;

public interface CategoryRepository extends Repository<Category, UUID> {

    <S extends Category> S save(S category);

    <S extends Category> S saveAndFlush(S category);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    List<Category> findAllByUserId(UUID userId);

    List<Category> findAllByUserIdOrderByNameAscIdAsc(UUID userId);

    List<Category> findAllByUserIdAndArchivedFalseOrderByNameAscIdAsc(UUID userId);

    boolean existsByUserIdAndTypeAndName(UUID userId, TransactionType type, String name);

    boolean existsByUserIdAndTypeAndNameAndIdNot(UUID userId, TransactionType type, String name, UUID id);
}
