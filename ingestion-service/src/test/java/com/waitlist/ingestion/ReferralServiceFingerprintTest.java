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

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Exercises the fingerprint / fraud-detection paths of {@link ReferralService}
 * that are not covered by {@link ReferralServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class ReferralServiceFingerprintTest {

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

    /** Creates a verified referrer — unverified users cannot generate referrals. */
    private WaitlistEntry entry(String email, String code) {
        var e = new WaitlistEntry();
        e.setEmail(email);
        e.setReferralCode(code);
        e.setVerified(true);
        return e;
    }

    // ── count below threshold — no flagging ──────────────────────────────────

    @Test
    void fingerprintBelowThreshold_countIncrementedButNotFlagged() {
        var referrer = entry("referrer@example.com", "ref00001");
        when(entryRepo.findByReferralCode("ref00001")).thenReturn(Optional.of(referrer));

        var fp = new ReferralFingerprint();
        fp.setReferrerEmail("referrer@example.com");
        fp.setIpHash("unknown");
        fp.setCount(3);
        fp.setWindowStart(OffsetDateTime.now().minusMinutes(30));
        when(fingerprintRepo.findByReferrerEmailAndIpHash("referrer@example.com", "unknown"))
                .thenReturn(Optional.of(fp));

        service.trackReferral("ref00001", "referee@example.com");

        verify(fingerprintRepo).save(argThat(f -> f.getCount() == 4));
        verify(pointsRepo, never()).save(any());
    }

    // ── expired window — count resets ────────────────────────────────────────

    @Test
    void expiredWindow_countResetsToOneAndReferrerIsNotFlagged() {
        var referrer = entry("oldspam@example.com", "spamcode");
        when(entryRepo.findByReferralCode("spamcode")).thenReturn(Optional.of(referrer));

        var fp = new ReferralFingerprint();
        fp.setReferrerEmail("oldspam@example.com");
        fp.setIpHash("unknown");
        fp.setCount(5);
        fp.setWindowStart(OffsetDateTime.now().minusHours(25));
        when(fingerprintRepo.findByReferrerEmailAndIpHash("oldspam@example.com", "unknown"))
                .thenReturn(Optional.of(fp));

        service.trackReferral("spamcode", "victim@example.com");

        verify(fingerprintRepo).save(argThat(f -> f.getCount() == 1));
        verify(pointsRepo, never()).save(any());
    }

    // ── already-flagged referrer — idempotent ────────────────────────────────

    @Test
    void alreadyFlaggedReferrer_isNotSavedAgain() {
        var referrer = entry("flagged@example.com", "flagcode");
        when(entryRepo.findByReferralCode("flagcode")).thenReturn(Optional.of(referrer));

        var fp = new ReferralFingerprint();
        fp.setReferrerEmail("flagged@example.com");
        fp.setIpHash("unknown");
        fp.setCount(6);
        fp.setWindowStart(OffsetDateTime.now().minusMinutes(5));
        when(fingerprintRepo.findByReferrerEmailAndIpHash("flagged@example.com", "unknown"))
                .thenReturn(Optional.of(fp));

        var rp = new ReferralPoints();
        rp.setEmail("flagged@example.com");
        rp.setFlagged(true);
        when(pointsRepo.findByEmail("flagged@example.com")).thenReturn(Optional.of(rp));

        service.trackReferral("flagcode", "victim2@example.com");

        // flagReferrer is idempotent: since already flagged, pointsRepo.save is NOT called
        verify(pointsRepo, never()).save(any());
    }

    // ── awardPoints — syncs leaderboard outside transaction ──────────────────

    @Test
    void awardPoints_newReferrer_createsPointsRowAndSyncsLeaderboard() {
        when(pointsRepo.findByEmail("newbie@example.com")).thenReturn(Optional.empty());

        service.awardPoints("newbie@example.com", 10);

        verify(pointsRepo).save(argThat(rp ->
                "newbie@example.com".equals(rp.getEmail()) && rp.getPoints() == 10));
        verify(leaderboardService).syncPoints("newbie@example.com", 10, 10);
    }

    @Test
    void awardPoints_negativeDeltaReverts_syncsWithNegativeDelta() {
        var rp = new ReferralPoints();
        rp.setEmail("reverter@example.com");
        rp.addPoints(10);
        when(pointsRepo.findByEmail("reverter@example.com")).thenReturn(Optional.of(rp));

        service.awardPoints("reverter@example.com", -10);

        verify(pointsRepo).save(argThat(p -> p.getPoints() == 0));
        verify(leaderboardService).syncPoints("reverter@example.com", 0, -10);
    }

    @Test
    void awardPoints_flaggedReferrer_pointsRejected() {
        var rp = new ReferralPoints();
        rp.setEmail("flagged@example.com");
        rp.setFlagged(true);
        rp.setReferrerStatus("FLAGGED");
        rp.addPoints(20);
        when(pointsRepo.findByEmail("flagged@example.com")).thenReturn(Optional.of(rp));

        service.awardPoints("flagged@example.com", 10);

        // Points must NOT be saved or synced
        verify(pointsRepo, never()).save(any());
        verify(leaderboardService, never()).syncPoints(any(), anyInt(), anyInt());
        // Audit log must be written
        verify(fraudAuditRepo).save(argThat(a -> "POINTS_REJECTED".equals(a.getEventType())));
    }
}
