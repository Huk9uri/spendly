package com.spendly.backend.transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface TransactionRepository extends Repository<Transaction, UUID> {

    <S extends Transaction> S save(S transaction);

    <S extends Transaction> S saveAndFlush(S transaction);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    List<Transaction> findAllByUserId(UUID userId);
}
