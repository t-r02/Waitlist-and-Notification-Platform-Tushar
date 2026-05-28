package com.waitlist.ingestion.service;

import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final WaitlistEntryRepository repository;
    private final SignupPersistenceService persistence;

    /**
     * Not @Transactional: the try/catch must sit outside the transaction boundary.
     * If two concurrent requests race past the findByEmail check and both attempt the
     * INSERT, the one that loses the unique-constraint race gets DataIntegrityViolationException.
     * Its transaction is rolled back by SignupPersistenceService, and we re-read here
     * to return the same idempotent response as a successful first call.
     */
    public SignupResponse signup(SignupRequest req) {
        String normalized = req.getEmail().toLowerCase().trim();
        try {
            return persistence.doInsert(req, normalized);
        } catch (DataIntegrityViolationException e) {
            return repository.findByEmail(normalized)
                    .map(entry -> new SignupResponse("Already registered", entry.getReferralCode(), true))
                    .orElseThrow(() -> e);
        }
    }
}
