package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.entity.IdempotencyKey;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Reserve-then-run idempotency. The reservation row is committed BEFORE
 * the work executes, so a concurrent request with the same key hits a
 * unique-constraint violation on insert and is deflected to replay —
 * never both running the work and double-charging the customer.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public <T> T execute(String key, String endpoint, Long userId, Class<T> type, Supplier<T> work) {
        if (key == null || key.isBlank()) {
            throw new BankingException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required");
        }

        try {
            store.reserve(key, endpoint, userId);
        } catch (DataIntegrityViolationException dup) {
            IdempotencyKey existing = store.find(key, endpoint)
                    .orElseThrow(() -> new BankingException("IDEMPOTENCY_RACE",
                            "Reservation conflict but no row found — retry"));
            return replay(existing, userId, type);
        }

        T result;
        try {
            result = work.get();
        } catch (RuntimeException e) {
            // Work failed — release the reservation so the caller can retry.
            store.release(key, endpoint);
            throw e;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            store.release(key, endpoint);
            throw new BankingException("IDEMPOTENCY_SERIALIZE_FAILED", e.getMessage());
        }
        store.complete(key, endpoint, json);
        return result;
    }

    private <T> T replay(IdempotencyKey existing, Long userId, Class<T> type) {
        if (!existing.getUserId().equals(userId)) {
            throw new BankingException("IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency-Key already used by a different user");
        }
        if (IdempotencyStore.STATUS_PENDING.equals(existing.getStatus())) {
            throw new BankingException("IDEMPOTENCY_IN_PROGRESS",
                    "Idempotency-Key is reserved by an in-flight request — retry later");
        }
        try {
            return objectMapper.readValue(existing.getResponseJson(), type);
        } catch (JsonProcessingException e) {
            throw new BankingException("IDEMPOTENCY_DESERIALIZE_FAILED", e.getMessage());
        }
    }
}
