package com.banking_software.BankingSoftware.exception;

public class BankingException extends RuntimeException {
    private final String code;

    public BankingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
