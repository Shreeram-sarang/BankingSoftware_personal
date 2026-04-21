package com.banking_software.BankingSoftware.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = {"idemKey", "endpoint"}))
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String idemKey;

    @Column(nullable = false, length = 80)
    private String endpoint;

    @Column(nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String responseJson;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
