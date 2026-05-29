package com.waitlist.ingestion.service;

import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.mapper.SignupMapper;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Owns the @Transactional boundary for signup so that SignupService can catch
 * DataIntegrityViolationException *outside* a poisoned transaction.
 *
 * <p>The Kafka SignupEvent is intentionally NOT published here — it is deferred
 * until the user verifies their email (see {@link com.waitlist.ingestion.controller.VerificationController}).
 * The admin service should only ever see entries that have proven email ownership.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupPersistenceService {

    private final WaitlistEntryRepository repository;
    private final ReferralService         referralService;
    private final SignupMapper            signupMapper;
    private final EmailService            emailService;

    @Transactional
    public SignupResponse doInsert(SignupRequest req, String normalized) {
        var existing = repository.findByEmail(normalized);
        if (existing.isPresent()) {
            var e = existing.get();
            // For verified accounts the name must match (case-insensitive, trimmed).
            // A mismatch means someone else is trying to claim this email — reject with 400
            // so the frontend shows an error rather than any account information.
            if (e.isVerified() && e.getName() != null) {
                String stored   = e.getName().trim().toLowerCase();
                String incoming = req.getName() != null ? req.getName().trim().toLowerCase() : "";
                if (!stored.equals(incoming)) {
                    throw new IllegalArgumentException(
                            "This email is already registered. " +
                            "Please use the name you originally signed up with.");
                }
            }
            String code = e.isVerified() ? e.getReferralCode() : null;
            String msg  = e.isVerified()
                    ? "Already registered"
                    : "Already registered — please verify your email to unlock your referral code";
            return new SignupResponse(msg, code, true, e.isVerified());
        }

        var entry = signupMapper.toEntity(req);
        entry.setEmail(normalized);
        entry.setReferralCode(UUID.randomUUID().toString().substring(0, 8));

        // Unverified until the user clicks the link in their inbox
        String token = generateVerificationToken();
        entry.setVerified(false);
        entry.setVerificationToken(token);
        entry.setVerificationTokenExpiresAt(OffsetDateTime.now().plusHours(24));

        repository.save(entry);

        // Track referral NOW so the referral row exists before APPROVED events arrive.
        // Points will only be awarded if the referrer is verified (enforced in ReferralService).
        if (req.getReferralCode() != null) {
            try {
                referralService.trackReferral(req.getReferralCode(), normalized);
            } catch (DataIntegrityViolationException e) {
                log.debug("Duplicate referral skipped [refereeEmail={}]", normalized);
            }
        }

        // NOTE: SignupEvent outbox entry is written in VerificationController.verify()
        // so that the admin service only learns about verified users.

        try {
            emailService.sendVerificationEmail(normalized, token);
        } catch (Exception ex) {
            log.error("Verification email failed for [email={}] — user can request a resend", normalized, ex);
        }

        return new SignupResponse("Please verify your email to complete registration", null, false, false);
    }

    /** Generates a 64-character cryptographically-secure hex token. */
    static String generateVerificationToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
