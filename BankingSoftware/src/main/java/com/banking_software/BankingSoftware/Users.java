package com.banking_software.BankingSoftware;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name="DEMOTABLE")

public class Users{
    @Id
    @Column(name="ID")
    private int id;

    @Column(name="column1")
    private int columne1;

    @Column(name="names")
    private String names;
}