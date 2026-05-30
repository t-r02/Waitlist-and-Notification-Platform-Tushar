package com.waitlist.ingestion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.repository.OutboxRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import com.waitlist.ingestion.service.EmailService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VerificationController.class)
@ActiveProfiles("test")
class VerificationControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean WaitlistEntryRepository entryRepo;
    @MockBean OutboxRepository        outboxRepo;
    @MockBean EmailService            emailService;

    // Rate-limit beans required by RateLimitInterceptor registered in WebMvcConfig
    @MockBean Bucket globalRateLimitBucket;
    @SuppressWarnings("rawtypes")
    @MockBean LoadingCache perIpBuckets;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void allowAllRequests() {
        ConsumptionProbe probe = org.mockito.Mockito.mock(ConsumptionProbe.class);
        org.mockito.Mockito.when(probe.isConsumed()).thenReturn(true);
        org.mockito.Mockito.when(globalRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        Bucket ipBucket = org.mockito.Mockito.mock(Bucket.class);
        ConsumptionProbe ipProbe = org.mockito.Mockito.mock(ConsumptionProbe.class);
        org.mockito.Mockito.when(ipProbe.isConsumed()).thenReturn(true);
        org.mockito.Mockito.when(ipBucket.tryConsumeAndReturnRemaining(1)).thenReturn(ipProbe);
        org.mockito.Mockito.when(perIpBuckets.get(org.mockito.ArgumentMatchers.any())).thenReturn(ipBucket);
    }

    private WaitlistEntry unverifiedEntry(String token) {
        var e = new WaitlistEntry();
        e.setId(1L);
        e.setEmail("alice@example.com");
        e.setName("Alice");
        e.setReferralCode("abc12345");
        e.setVerified(false);
        e.setVerificationToken(token);
        e.setVerificationTokenExpiresAt(OffsetDateTime.now().plusHours(24));
        return e;
    }

    // ── POST /verify — happy path ─────────────────────────────────────────────

    @Test
    void verify_validToken_returns200WithReferralCode() throws Exception {
        String token = "a".repeat(64);
        when(entryRepo.findByVerificationToken(token))
                .thenReturn(Optional.of(unverifiedEntry(token)));
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/public/verify").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referralCode").value("abc12345"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        verify(outboxRepo).save(any());
    }

    @Test
    void verify_validToken_marksEntryVerifiedAndClearsToken() throws Exception {
        String token = "b".repeat(64);
        var entry = unverifiedEntry(token);
        when(entryRepo.findByVerificationToken(token)).thenReturn(Optional.of(entry));
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/public/verify").param("token", token))
                .andExpect(status().isOk());

        // Token must be cleared so it cannot be replayed
        assert entry.isVerified();
        assert entry.getVerificationToken() == null;
    }

    // ── POST /verify — error paths ────────────────────────────────────────────

    @Test
    void verify_unknownToken_returns400() throws Exception {
        when(entryRepo.findByVerificationToken(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/public/verify").param("token", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(entryRepo, never()).save(any());
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void verify_expiredToken_returns400() throws Exception {
        String token = "c".repeat(64);
        var entry = unverifiedEntry(token);
        entry.setVerificationTokenExpiresAt(OffsetDateTime.now().minusHours(1));
        when(entryRepo.findByVerificationToken(token)).thenReturn(Optional.of(entry));

        mockMvc.perform(post("/api/public/verify").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("expired")));

        verify(entryRepo, never()).save(any());
    }

    @Test
    void verify_alreadyVerifiedEntry_returns200Idempotent() throws Exception {
        // Token is cleared after first verify, so this path is unreachable in practice,
        // but the idempotency guard is there for safety.
        String token = "d".repeat(64);
        var entry = unverifiedEntry(token);
        entry.setVerified(true);
        when(entryRepo.findByVerificationToken(token)).thenReturn(Optional.of(entry));

        mockMvc.perform(post("/api/public/verify").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referralCode").value("abc12345"));

        // No outbox write for an already-verified entry
        verify(outboxRepo, never()).save(any());
    }

    // ── POST /resend-verification ─────────────────────────────────────────────

    @Test
    void resendVerification_knownUnverifiedEmail_sends200AndNewToken() throws Exception {
        var entry = unverifiedEntry("old".repeat(21) + "x");
        when(entryRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(entry));
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/public/resend-verification")
                        .param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(emailService).sendVerificationEmail(eq("alice@example.com"), anyString());
    }

    @Test
    void resendVerification_unknownEmail_returns200WithoutRevealingAbsence() throws Exception {
        when(entryRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        // Returns 200 to prevent email enumeration
        mockMvc.perform(post("/api/public/resend-verification")
                        .param("email", "nobody@example.com"))
                .andExpect(status().isOk());

        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void resendVerification_alreadyVerifiedEmail_returns200WithoutSending() throws Exception {
        var entry = unverifiedEntry("tok");
        entry.setVerified(true);
        when(entryRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(entry));

        mockMvc.perform(post("/api/public/resend-verification")
                        .param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email is already verified"));

        verify(emailService, never()).sendVerificationEmail(any(), any());
    }
}
