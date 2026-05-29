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
                    .map(entry -> {
                        if (entry.isVerified() && entry.getName() != null) {
                            String stored   = entry.getName().trim().toLowerCase();
                            String incoming = req.getName() != null ? req.getName().trim().toLowerCase() : "";
                            if (!stored.equals(incoming)) {
                                throw new IllegalArgumentException(
                                        "This email is already registered. " +
                                        "Please use the name you originally signed up with.");
                            }
                        }
                        return new SignupResponse(
                                entry.isVerified()
                                        ? "Already registered"
                                        : "Already registered — please verify your email to unlock your referral code",
                                entry.isVerified() ? entry.getReferralCode() : null,
                                true,
                                entry.isVerified());
                    })
                    .orElseThrow(() -> e);
        }
    }
}
