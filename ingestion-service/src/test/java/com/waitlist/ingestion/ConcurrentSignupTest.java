package com.waitlist.ingestion;

import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import com.waitlist.ingestion.service.SignupPersistenceService;
import com.waitlist.ingestion.service.SignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcurrentSignupTest {

    @Mock WaitlistEntryRepository    repository;
    @Mock SignupPersistenceService   persistence;

    SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(repository, persistence);
    }

    private WaitlistEntry verifiedEntry(String email, String referralCode) {
        var e = new WaitlistEntry();
        e.setEmail(email);
        e.setName("Test User");
        e.setReferralCode(referralCode);
        e.setVerified(true);
        return e;
    }

    @Test
    void concurrentSignup_sameEmail_bothReturn200WithSameReferralCode() throws Exception {
        String email        = "double-click@example.com";
        String referralCode = "deadbeef";

        // Winner thread: persistence says "new user registered (unverified)"
        SignupResponse successResponse =
                new SignupResponse("Please verify your email to complete registration", null, false, false);

        // After the constraint fires, SignupService re-reads the committed row.
        // The row is verified so the referral code is visible.
        WaitlistEntry committedEntry = verifiedEntry(email, referralCode);

        CountDownLatch bothStarted = new CountDownLatch(2);

        when(persistence.doInsert(any(), eq(email)))
                .thenAnswer(inv -> {
                    bothStarted.countDown();
                    bothStarted.await();
                    return successResponse;
                })
                .thenAnswer(inv -> {
                    bothStarted.countDown();
                    bothStarted.await();
                    throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
                });

        when(repository.findByEmail(email)).thenReturn(Optional.of(committedEntry));

        SignupRequest req = new SignupRequest();
        req.setEmail(email);
        req.setName("Test User");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<SignupResponse> f1 = pool.submit(() -> signupService.signup(req));
            Future<SignupResponse> f2 = pool.submit(() -> signupService.signup(req));

            List<SignupResponse> results = List.of(f1.get(), f2.get());

            assertThat(results).hasSize(2);

            // Exactly one is the winning "new signup" response
            assertThat(results).filteredOn(r -> !r.isDuplicate()).hasSize(1);

            // The loser gets the duplicate path — committed entry is verified, so code is returned
            assertThat(results).filteredOn(SignupResponse::isDuplicate).hasSize(1);
            assertThat(results).filteredOn(SignupResponse::isDuplicate)
                    .extracting(SignupResponse::getReferralCode)
                    .containsOnly(referralCode);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void signup_existingVerifiedEmail_returnsIdempotentResponseWithCode() {
        String email = "existing@example.com";
        // persistence returns the duplicate response directly (no constraint race)
        SignupResponse idempotent = new SignupResponse("Already registered", "ref0001", true, true);
        when(persistence.doInsert(any(), eq(email))).thenReturn(idempotent);

        SignupRequest req = new SignupRequest();
        req.setEmail("EXISTING@example.com");   // mixed-case must be normalised
        req.setName("Existing User");

        SignupResponse resp = signupService.signup(req);

        assertThat(resp.isDuplicate()).isTrue();
        assertThat(resp.isVerified()).isTrue();
        assertThat(resp.getReferralCode()).isEqualTo("ref0001");
    }

    @Test
    void signup_dataIntegrityViolation_readsCommittedVerifiedRow() {
        String email        = "race@example.com";
        String referralCode = "winner1";

        WaitlistEntry winner = verifiedEntry(email, referralCode);

        when(persistence.doInsert(any(), eq(email)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repository.findByEmail(email)).thenReturn(Optional.of(winner));

        SignupRequest req = new SignupRequest();
        req.setEmail(email);
        req.setName("Test User");

        SignupResponse resp = signupService.signup(req);

        assertThat(resp.isDuplicate()).isTrue();
        assertThat(resp.isVerified()).isTrue();
        assertThat(resp.getReferralCode()).isEqualTo(referralCode);
    }

    @Test
    void signup_dataIntegrityViolation_unverifiedCommittedRow_hidesCode() {
        String email = "race-unverified@example.com";

        var unverified = new WaitlistEntry();
        unverified.setEmail(email);
        unverified.setName("Test User");
        unverified.setReferralCode("hidden");
        unverified.setVerified(false);

        when(persistence.doInsert(any(), eq(email)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repository.findByEmail(email)).thenReturn(Optional.of(unverified));

        SignupRequest req = new SignupRequest();
        req.setEmail(email);
        req.setName("Test User");

        SignupResponse resp = signupService.signup(req);

        assertThat(resp.isDuplicate()).isTrue();
        assertThat(resp.isVerified()).isFalse();
        assertThat(resp.getReferralCode()).isNull();  // hidden until verified
    }
}
