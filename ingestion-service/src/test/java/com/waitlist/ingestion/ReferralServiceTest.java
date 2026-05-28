package com.waitlist.ingestion;

import com.waitlist.ingestion.entity.ReferralFingerprint;
import com.waitlist.ingestion.entity.ReferralPoints;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.repository.ReferralFingerprintRepository;
import com.waitlist.ingestion.repository.ReferralPointsRepository;
import com.waitlist.ingestion.repository.ReferralRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import com.waitlist.ingestion.service.LeaderboardService;
import com.waitlist.ingestion.service.ReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock WaitlistEntryRepository entryRepo;
    @Mock ReferralRepository referralRepo;
    @Mock ReferralPointsRepository pointsRepo;
    @Mock ReferralFingerprintRepository fingerprintRepo;
    @Mock LeaderboardService leaderboardService;

    ReferralService service;

    @BeforeEach
    void setUp() {
        service = new ReferralService(referralRepo, pointsRepo, entryRepo, fingerprintRepo,
                leaderboardService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WaitlistEntry entry(String email, String code) {
        var e = new WaitlistEntry();
        e.setEmail(email);
        e.setReferralCode(code);
        return e;
    }

    private void stubReferrer(WaitlistEntry referrer) {
        when(entryRepo.findByReferralCode(referrer.getReferralCode()))
                .thenReturn(Optional.of(referrer));
    }

    // ── self-referral ─────────────────────────────────────────────────────────

    @Test
    void selfReferral_isRejectedAndNothingIsPersisted() {
        // The referee existence check was removed (BUG #3 fix); only the referrer
        // lookup (by code) happens inside trackReferral now.
        var person = entry("alice@example.com", "aliccode");
        stubReferrer(person);

        service.trackReferral("aliccode", "alice@example.com");

        verify(referralRepo, never()).saveAndFlush(any());
        verify(pointsRepo, never()).save(any());
    }

    @Test
    void selfReferral_caseInsensitive_isRejected() {
        var person = entry("alice@example.com", "aliccode");
        stubReferrer(person);

        service.trackReferral("aliccode", "ALICE@EXAMPLE.COM");

        verify(referralRepo, never()).saveAndFlush(any());
    }

    // ── duplicate referee ─────────────────────────────────────────────────────

    @Test
    void duplicateReferee_throwsDataIntegrityViolation_andPointsAreNotAwarded() {
        var referrer = entry("bob@example.com", "bobscode");
        stubReferrer(referrer);

        when(referralRepo.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        // DataIntegrityViolationException propagates — the caller (SignupPersistenceService) catches it
        assertThatThrownBy(() -> service.trackReferral("bobscode", "carol@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(pointsRepo, never()).save(any());
        verify(fingerprintRepo, never()).save(any());
    }

    // ── legitimate referral ───────────────────────────────────────────────────

    @Test
    void legitimateReferral_createsReferralRowAndDoesNotAwardPoints() {
        // Points are awarded by StatusChangedConsumer on APPROVED, not here.
        var referrer = entry("dave@example.com", "davecode");
        stubReferrer(referrer);

        // No existing fingerprint (count will be 1 — below the flag threshold of 5)
        when(fingerprintRepo.findByReferrerEmailAndIpHash(eq("dave@example.com"), any()))
                .thenReturn(Optional.empty());

        service.trackReferral("davecode", "eve@example.com");

        // Referral row created with correct referrer and referee emails
        verify(referralRepo).saveAndFlush(argThat(r ->
                "dave@example.com".equals(r.getReferrerEmail()) &&
                "eve@example.com".equals(r.getRefereeEmail())));

        // Points repo must NOT be touched — points come later via APPROVED event
        verify(pointsRepo, never()).save(any());
        verify(pointsRepo, never()).findByEmail(any());
    }

    @Test
    void legitimateReferral_fingerprintRowIsCreated() {
        var referrer = entry("dave@example.com", "davecode");
        stubReferrer(referrer);

        when(fingerprintRepo.findByReferrerEmailAndIpHash(eq("dave@example.com"), any()))
                .thenReturn(Optional.empty());

        service.trackReferral("davecode", "eve@example.com");

        // A new fingerprint row is saved
        verify(fingerprintRepo).save(argThat(fp ->
                "dave@example.com".equals(fp.getReferrerEmail()) && fp.getCount() == 1));
    }

    /**
     * Regression test for BUG #3: the old code began with
     * {@code if (entryRepo.findByEmail(refereeEmail).isEmpty()) return;} which always
     * exited early because the REQUIRES_NEW inner transaction cannot see the
     * still-uncommitted referee row from the outer signup transaction.  After the fix,
     * trackReferral goes straight to the referrer lookup and inserts the referral row.
     */
    @Test
    void bug3Regression_trackReferral_doesNotQueryRefereeAndCreatesRow() {
        // Arrange: valid referrer, some referee email (not yet committed in outer tx)
        var referrer = entry("alice@example.com", "aliccode");
        stubReferrer(referrer);
        when(fingerprintRepo.findByReferrerEmailAndIpHash(eq("alice@example.com"), any()))
                .thenReturn(Optional.empty());

        // Act
        service.trackReferral("aliccode", "bob@example.com");

        // Assert: entryRepo.findByEmail was NEVER called (the removed guard)
        verify(entryRepo, never()).findByEmail(any());

        // And the referral row WAS created
        verify(referralRepo).saveAndFlush(argThat(r ->
                "alice@example.com".equals(r.getReferrerEmail()) &&
                "bob@example.com".equals(r.getRefereeEmail())));
    }

    // ── fingerprint / flagging ────────────────────────────────────────────────

    @Test
    void referrer_flaggedWhenFingerprintExceedsThreshold() {
        var referrer = entry("spammer@example.com", "spamcode");
        stubReferrer(referrer);

        var fp = new ReferralFingerprint();
        fp.setReferrerEmail("spammer@example.com");
        fp.setIpHash("unknown"); // test context — no real request
        fp.setCount(5);         // already at the limit; next referral tips it over
        fp.setWindowStart(java.time.OffsetDateTime.now().minusMinutes(10));

        when(fingerprintRepo.findByReferrerEmailAndIpHash("spammer@example.com", "unknown"))
                .thenReturn(Optional.of(fp));
        when(pointsRepo.findByEmail("spammer@example.com")).thenReturn(Optional.empty());

        service.trackReferral("spamcode", "victim@example.com");

        // Fingerprint count incremented to 6 and saved
        verify(fingerprintRepo).save(argThat(f -> f.getCount() == 6));

        // Referrer flagged
        verify(pointsRepo).save(argThat(rp ->
                "spammer@example.com".equals(rp.getEmail()) && rp.isFlagged()));
    }

    // ── hash helper ───────────────────────────────────────────────────────────

    @Test
    void hashIp_returnsDeterministic16CharHex() {
        String h1 = ReferralService.hashIp("192.168.1.1");
        String h2 = ReferralService.hashIp("192.168.1.1");
        String h3 = ReferralService.hashIp("10.0.0.1");

        assertThat(h1).hasSize(16).matches("[0-9a-f]+");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);
    }
}
