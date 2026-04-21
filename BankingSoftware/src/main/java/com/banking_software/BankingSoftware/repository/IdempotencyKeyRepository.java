package com.banking_software.BankingSoftware.repository;

import com.banking_software.BankingSoftware.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByIdemKeyAndEndpoint(String idemKey, String endpoint);
}
