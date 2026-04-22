package com.banking_software.BankingSoftware.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> NOT_FOUND = Set.of(
            "ACCOUNT_NOT_FOUND", "USER_NOT_FOUND", "BANK_NOT_FOUND");

    private static final Set<String> CONFLICT = Set.of(
            "IDEMPOTENCY_KEY_CONFLICT", "IDEMPOTENCY_IN_PROGRESS", "IDEMPOTENCY_RACE",
            "ACCOUNT_NOT_ACTIVE");

    private static final Set<String> UNPROCESSABLE = Set.of(
            "INSUFFICIENT_FUNDS",
            "INTRA_PER_TXN_LIMIT", "INTRA_DAILY_LIMIT",
            "NEFT_PER_TXN_LIMIT", "NEFT_DAILY_LIMIT",
            "IMPS_PER_TXN_LIMIT", "IMPS_DAILY_LIMIT",
            "UPI_PER_TXN_LIMIT",  "UPI_DAILY_LIMIT",
            "RTGS_MIN", "RTGS_MAX");

    private static final Set<String> BAD_GATEWAY = Set.of(
            "RAIL_REJECTED", "RAIL_ERROR", "CLEARING_HOUSE_REJECTED");

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<Map<String, Object>> handleBanking(BankingException e) {
        return ResponseEntity.status(statusFor(e.getCode()))
                .body(body(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, Object> b = body("VALIDATION_ERROR", "Request validation failed");
        Map<String, String> fields = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
        b.put("fields", fields);
        return ResponseEntity.badRequest().body(b);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", e.getMessage()));
    }

    private static HttpStatus statusFor(String code) {
        if (code == null) return HttpStatus.BAD_REQUEST;
        if ("FORBIDDEN".equals(code)) return HttpStatus.FORBIDDEN;
        if (NOT_FOUND.contains(code)) return HttpStatus.NOT_FOUND;
        if (CONFLICT.contains(code)) return HttpStatus.CONFLICT;
        if (UNPROCESSABLE.contains(code)) return HttpStatus.UNPROCESSABLE_ENTITY;
        if (BAD_GATEWAY.contains(code)) return HttpStatus.BAD_GATEWAY;
        return HttpStatus.BAD_REQUEST;
    }

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("timestamp", LocalDateTime.now());
        m.put("code", code);
        m.put("message", message);
        return m;
    }
}
