package com.waitlist.ingestion.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waitlist.events.SignupEvent;
import com.waitlist.ingestion.entity.OutboxEntry;
import com.waitlist.ingestion.repository.OutboxRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import com.waitlist.ingestion.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Handles email-address verification.
 *
 * <p>The Kafka {@link SignupEvent} is written to the outbox here — <em>after</em>
 * the user proves email ownership — so the admin service only ever sees verified
 * registrants.
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class VerificationController {

    private final WaitlistEntryRepository  entryRepo;
    private final OutboxRepository         outboxRepo;
    private final EmailService             emailService;
    private final ObjectMapper             objectMapper;

    /**
     * Confirms a user's email address and publishes the SignupEvent so the admin
     * service becomes aware of the verified registrant.
     * The token is single-use: cleared from the DB on success.
     */
    @PostMapping("/verify")
    @Transactional
    public ResponseEntity<?> verify(@RequestParam String token) {
        var entryOpt = entryRepo.findByVerificationToken(token);

        if (entryOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or already used verification token"));
        }

        var entry = entryOpt.get();

        if (entry.isVerified()) {
            // Idempotent: already verified (token was cleared, so this path is unreachable
            // in practice, but guard it anyway)
            return ResponseEntity.ok(Map.of(
                    "message", "Email already verified",
                    "email", entry.getEmail(),
                    "referralCode", entry.getReferralCode()));
        }

        if (entry.getVerificationTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error",
                            "Verification link has expired. Please sign up again to receive a new link."));
        }

        // Mark verified and invalidate the token
        entry.setVerified(true);
        entry.setVerificationToken(null);
        entry.setVerificationTokenExpiresAt(null);
        entry.setUpdatedAt(OffsetDateTime.now());
        entryRepo.save(entry);

        // Publish SignupEvent via outbox so the admin service creates the waitlist entry.
        // Doing this here (post-verification) ensures the admin dashboard only shows
        // users who have confirmed their email address.
        publishSignupEvent(entry.getId(), entry.getEmail(), entry.getName(),
                entry.getCompany(), entry.getReferralCode(), entry.getReferredBy());

        log.info("Email verified [email={}]", entry.getEmail());
        // Welcome email is sent by notification-service when it consumes the SignupEvent
        // from the outbox above — no second email from here.

        return ResponseEntity.ok(Map.of(
                "message", "Email verified successfully — welcome to the waitlist!",
                "email", entry.getEmail(),
                "referralCode", entry.getReferralCode()));
    }

    /**
     * Re-sends a verification email for users who missed or lost their original link.
     */
    @PostMapping("/resend-verification")
    @Transactional
    public ResponseEntity<?> resendVerification(@RequestParam String email) {
        var normalized = email.trim().toLowerCase();
        var entryOpt = entryRepo.findByEmail(normalized);

        if (entryOpt.isEmpty()) {
            // 200 to avoid email enumeration
            return ResponseEntity.ok(Map.of("message", "If that email is registered you will receive a new link"));
        }

        var entry = entryOpt.get();
        if (entry.isVerified()) {
            return ResponseEntity.ok(Map.of("message", "Email is already verified"));
        }

        String newToken = generateToken();
        entry.setVerificationToken(newToken);
        entry.setVerificationTokenExpiresAt(OffsetDateTime.now().plusHours(24));
        entryRepo.save(entry);

        try {
            emailService.sendVerificationEmail(normalized, newToken);
        } catch (Exception ex) {
            log.error("Resend verification email failed [email={}]", normalized, ex);
        }

        return ResponseEntity.ok(Map.of("message", "Verification email sent — please check your inbox"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void publishSignupEvent(Long ingestionId, String email, String name,
                                     String company, String referralCode, String referredBy) {
        var event = new SignupEvent(
                UUID.randomUUID(),
                Instant.now(),
                ingestionId,
                email,
                name,
                company,
                referralCode,
                referredBy);

        var outbox = new OutboxEntry();
        outbox.setAggregateType("WaitlistEntry");
        outbox.setAggregateId(email);
        outbox.setEventType("SignupEvent");
        outbox.setPayload(serialize(event));
        outbox.setCreatedAt(OffsetDateTime.now());
        outboxRepo.save(outbox);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
