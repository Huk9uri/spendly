package com.spendly.backend.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface UserRepository extends Repository<User, UUID> {
    <S extends User> S saveAndFlush(S user);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
