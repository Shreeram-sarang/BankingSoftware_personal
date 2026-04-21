package com.banking_software.BankingSoftware.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "banks",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "bankCode"),
                @UniqueConstraint(columnNames = "swiftCode")
        })
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String bankCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 15)
    private String swiftCode;

    @Column(nullable = false)
    private boolean isSelf = false;

    @Column(length = 50)
    private String rtgsIdentifier;

    @Column(length = 50)
    private String neftIdentifier;

    @Column(length = 50)
    private String upiHandle;

    @Column(length = 100)
    private String settlementPartner;

    private boolean active = true;
}
