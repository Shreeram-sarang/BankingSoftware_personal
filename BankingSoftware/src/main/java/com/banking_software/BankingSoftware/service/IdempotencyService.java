package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.entity.IdempotencyKey;
import com.banking_software.BankingSoftware.exception.BankingException;
import com.banking_software.BankingSoftware.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repo;
    private final ObjectMapper objectMapper;

    /**
     * If (key, endpoint) was seen before, return the cached response (replay).
     * Otherwise run `work`, cache the response, and return it.
     */
    public <T> T execute(String key, String endpoint, Long userId, Class<T> type, Supplier<T> work) {
        if (key == null || key.isBlank()) {
            throw new BankingException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required");
        }
        return repo.findByIdemKeyAndEndpoint(key, endpoint)
                .map(existing -> replay(existing, userId, type))
                .orElseGet(() -> runAndStore(key, endpoint, userId, type, work));
    }

    private <T> T replay(IdempotencyKey existing, Long userId, Class<T> type) {
        if (!existing.getUserId().equals(userId)) {
            throw new BankingException("IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency-Key already used by a different user");
        }
        try {
            return objectMapper.readValue(existing.getResponseJson(), type);
        } catch (JsonProcessingException e) {
            throw new BankingException("IDEMPOTENCY_DESERIALIZE_FAILED", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected <T> T runAndStore(String key, String endpoint, Long userId,
                                Class<T> type, Supplier<T> work) {
        T result = work.get();
        IdempotencyKey row = new IdempotencyKey();
        row.setIdemKey(key);
        row.setEndpoint(endpoint);
        row.setUserId(userId);
        row.setStatus("COMPLETED");
        try {
            row.setResponseJson(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new BankingException("IDEMPOTENCY_SERIALIZE_FAILED", e.getMessage());
        }
        try {
            repo.save(row);
        } catch (DataIntegrityViolationException race) {
            // Concurrent first-time call with the same key — replay the stored one.
            IdempotencyKey existing = repo.findByIdemKeyAndEndpoint(key, endpoint)
                    .orElseThrow(() -> race);
            return replay(existing, userId, type);
        }
        return result;
    }
}
