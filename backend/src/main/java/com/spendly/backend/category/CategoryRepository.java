package com.spendly.backend.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CategoryRepository extends Repository<Category, UUID> {

    <S extends Category> S save(S category);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    List<Category> findAllByUserId(UUID userId);
}
