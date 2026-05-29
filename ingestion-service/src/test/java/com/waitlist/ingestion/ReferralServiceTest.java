package com.waitlist.ingestion;

import com.waitlist.ingestion.entity.ReferralFingerprint;
import com.waitlist.ingestion.entity.ReferralPoints;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.repository.ReferralFingerprintRepository;
import com.waitlist.ingestion.repository.ReferralFraudAuditRepository;
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

    @Mock WaitlistEntryRepository        entryRepo;
    @Mock ReferralRepository             referralRepo;
    @Mock ReferralPointsRepository       pointsRepo;
    @Mock ReferralFingerprintRepository  fingerprintRepo;
    @Mock LeaderboardService             leaderboardService;
    @Mock ReferralFraudAuditRepository   fraudAuditRepo;

    ReferralService service;

    @BeforeEach
    void setUp() {
        service = new ReferralService(referralRepo, pointsRepo, entryRepo, fingerprintRepo,
                leaderboardService, fraudAuditRepo);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Creates a verified referrer — unverified users cannot generate referrals. */
    private WaitlistEntry entry(String email, String code) {
        var e = new WaitlistEntry();
        e.setEmail(email);
        e.setReferralCode(code);
        e.setVerified(true);
        return e;
    }

    private void stubReferrer(WaitlistEntry referrer) {
        when(entryRepo.findByReferralCode(referrer.getReferralCode()))
                .thenReturn(Optional.of(referrer));
    }

    // ── unverified referrer ───────────────────────────────────────────────────

    @Test
    void unverifiedReferrer_referralIsRejected() {
        var unverified = new WaitlistEntry();
        unverified.setEmail("unverified@example.com");
        unverified.setReferralCode("unvcode1");
        unverified.setVerified(false);
        when(entryRepo.findByReferralCode("unvcode1")).thenReturn(Optional.of(unverified));

        service.trackReferral("unvcode1", "someone@example.com");

        verify(referralRepo, never()).saveAndFlush(any());
    }

    // ── self-referral ─────────────────────────────────────────────────────────

    @Test
    void selfReferral_isRejectedAndNothingIsPersisted() {
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

        assertThatThrownBy(() -> service.trackReferral("bobscode", "carol@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(pointsRepo, never()).save(any());
        verify(fingerprintRepo, never()).save(any());
    }

    // ── legitimate referral ───────────────────────────────────────────────────

    @Test
    void legitimateReferral_createsReferralRowAndDoesNotAwardPoints() {
        var referrer = entry("dave@example.com", "davecode");
        stubReferrer(referrer);

        when(fingerprintRepo.findByReferrerEmailAndIpHash(eq("dave@example.com"), any()))
                .thenReturn(Optional.empty());

        service.trackReferral("davecode", "eve@example.com");

        verify(referralRepo).saveAndFlush(argThat(r ->
                "dave@example.com".equals(r.getReferrerEmail()) &&
                "eve@example.com".equals(r.getRefereeEmail())));

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

        verify(fingerprintRepo).save(argThat(fp ->
                "dave@example.com".equals(fp.getReferrerEmail()) && fp.getCount() == 1));
    }

    /**
     * Regression guard: trackReferral must NOT query the referee by email before
     * inserting the referral row. The method runs in REQUIRES_NEW which cannot see the
     * still-uncommitted referee row from the outer signup transaction, so any such
     * query would always return empty and silently skip every legitimate referral.
     */
    @Test
    void trackReferral_doesNotQueryRefereeBeforeInsert() {
        var referrer = entry("alice@example.com", "aliccode");
        stubReferrer(referrer);
        when(fingerprintRepo.findByReferrerEmailAndIpHash(eq("alice@example.com"), any()))
                .thenReturn(Optional.empty());

        service.trackReferral("aliccode", "bob@example.com");

        verify(entryRepo, never()).findByEmail(any());

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
        fp.setIpHash("unknown");
        fp.setCount(5);
        fp.setWindowStart(java.time.OffsetDateTime.now().minusMinutes(10));

        when(fingerprintRepo.findByReferrerEmailAndIpHash("spammer@example.com", "unknown"))
                .thenReturn(Optional.of(fp));
        when(pointsRepo.findByEmail("spammer@example.com")).thenReturn(Optional.empty());

        service.trackReferral("spamcode", "victim@example.com");

        verify(fingerprintRepo).save(argThat(f -> f.getCount() == 6));

        verify(pointsRepo).save(argThat(rp ->
                "spammer@example.com".equals(rp.getEmail()) && rp.isFlagged()));
    }

    // ── awardPoints — event type ──────────────────────────────────────────────

    @Test
    void awardPoints_positiveDelta_logsPOINTS_AWARDED() {
        when(pointsRepo.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        service.awardPoints("alice@example.com", 10);

        verify(fraudAuditRepo).save(argThat(a -> "POINTS_AWARDED".equals(a.getEventType())));
    }

    @Test
    void awardPoints_negativeDelta_logsPOINTS_DEDUCTED() {
        var rp = new ReferralPoints();
        rp.setEmail("alice@example.com");
        rp.addPoints(10);
        when(pointsRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(rp));

        service.awardPoints("alice@example.com", -10);

        verify(fraudAuditRepo).save(argThat(a -> "POINTS_DEDUCTED".equals(a.getEventType())));
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
