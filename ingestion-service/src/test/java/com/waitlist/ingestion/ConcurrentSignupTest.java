package com.waitlist.ingestion;

import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.SignupResponse;
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

    @Mock
    WaitlistEntryRepository repository;

    @Mock
    SignupPersistenceService persistence;

    SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(repository, persistence);
    }

    @Test
    void concurrentSignup_sameEmail_bothReturn200WithSameReferralCode() throws Exception {
        String email = "double-click@example.com";
        String referralCode = "deadbeef";

        // Thread that wins the race: normal successful insert
        SignupResponse successResponse = new SignupResponse("Successfully registered", referralCode, false);

        // Thread that loses the race: unique constraint fires
        WaitlistEntry committedEntry = new WaitlistEntry();
        committedEntry.setEmail(email);
        committedEntry.setReferralCode(referralCode);

        CountDownLatch bothStarted = new CountDownLatch(2);

        when(persistence.doInsert(any(), eq(email)))
                .thenAnswer(inv -> {
                    bothStarted.countDown();
                    bothStarted.await();   // hold until both threads are in-flight
                    // first call returns success; second call simulates constraint violation
                    return successResponse;
                })
                .thenAnswer(inv -> {
                    bothStarted.countDown();
                    bothStarted.await();
                    throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
                });

        // After the constraint violation, SignupService re-reads the committed row
        when(repository.findByEmail(email)).thenReturn(Optional.of(committedEntry));

        SignupRequest req = new SignupRequest();
        req.setEmail(email);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<SignupResponse> f1 = pool.submit(() -> signupService.signup(req));
            Future<SignupResponse> f2 = pool.submit(() -> signupService.signup(req));

            List<SignupResponse> results = List.of(f1.get(), f2.get());

            // Both requests must complete without throwing
            assertThat(results).hasSize(2);

            // Both must carry the same referralCode
            assertThat(results).extracting(SignupResponse::getReferralCode)
                    .containsOnly(referralCode);

            // Exactly one should be a duplicate
            assertThat(results).filteredOn(SignupResponse::isDuplicate).hasSize(1);
            assertThat(results).filteredOn(r -> !r.isDuplicate()).hasSize(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void signup_existingEmail_returnsIdempotentResponseWithoutException() {
        String email = "existing@example.com";
        SignupResponse idempotent = new SignupResponse("Already registered", "ref0001", true);

        when(persistence.doInsert(any(), eq(email))).thenReturn(idempotent);

        SignupRequest req = new SignupRequest();
        req.setEmail("EXISTING@example.com");   // mixed-case — must be normalised

        SignupResponse resp = signupService.signup(req);

        assertThat(resp.isDuplicate()).isTrue();
        assertThat(resp.getReferralCode()).isEqualTo("ref0001");
    }

    @Test
    void signup_dataIntegrityViolation_readsCommittedRowAndReturnsDuplicate() {
        String email = "race@example.com";
        String referralCode = "winner1";

        WaitlistEntry winner = new WaitlistEntry();
        winner.setEmail(email);
        winner.setReferralCode(referralCode);

        when(persistence.doInsert(any(), eq(email)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(repository.findByEmail(email)).thenReturn(Optional.of(winner));

        SignupRequest req = new SignupRequest();
        req.setEmail(email);

        SignupResponse resp = signupService.signup(req);

        assertThat(resp.isDuplicate()).isTrue();
        assertThat(resp.getReferralCode()).isEqualTo(referralCode);
    }
}
