package com.banking_software.BankingSoftware.service;

import com.banking_software.BankingSoftware.entity.IdempotencyKey;
import com.banking_software.BankingSoftware.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Backing store for idempotency reservations. Each method runs in its
 * own transaction so the reservation row is visible to other requests
 * immediately — this is the whole point of splitting it out of
 * IdempotencyService: self-invocation inside one bean bypasses Spring's
 * @Transactional proxy.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_COMPLETED = "COMPLETED";

    private final IdempotencyKeyRepository repo;

    /**
     * Insert a PENDING reservation. Returns normally on success.
     * Throws DataIntegrityViolationException (wrapped by Spring) if another
     * request already owns the (idemKey, endpoint) pair — caller should
     * then look it up and replay or reject per status.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(String idemKey, String endpoint, Long userId) {
        IdempotencyKey row = new IdempotencyKey();
        row.setIdemKey(idemKey);
        row.setEndpoint(endpoint);
        row.setUserId(userId);
        row.setStatus(STATUS_PENDING);
        row.setResponseJson("");
        repo.saveAndFlush(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKey> find(String idemKey, String endpoint) {
        return repo.findByIdemKeyAndEndpoint(idemKey, endpoint);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String idemKey, String endpoint, String responseJson) {
        IdempotencyKey row = repo.findByIdemKeyAndEndpoint(idemKey, endpoint)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency row vanished mid-flight: " + idemKey));
        row.setStatus(STATUS_COMPLETED);
        row.setResponseJson(responseJson);
        repo.save(row);
    }

    /** Release a reservation when the work failed, so the caller can retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String idemKey, String endpoint) {
        repo.findByIdemKeyAndEndpoint(idemKey, endpoint).ifPresent(repo::delete);
    }
}
