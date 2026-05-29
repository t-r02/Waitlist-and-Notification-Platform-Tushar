package com.waitlist.ingestion.service;

import com.waitlist.ingestion.entity.Referral;
import com.waitlist.ingestion.entity.ReferralFingerprint;
import com.waitlist.ingestion.entity.ReferralFraudAudit;
import com.waitlist.ingestion.entity.ReferralPoints;
import com.waitlist.ingestion.filter.RateLimitInterceptor;
import com.waitlist.ingestion.repository.ReferralFingerprintRepository;
import com.waitlist.ingestion.repository.ReferralFraudAuditRepository;
import com.waitlist.ingestion.repository.ReferralPointsRepository;
import com.waitlist.ingestion.repository.ReferralRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final int MAX_REFERRALS_PER_IP_PER_24H = 5;

    private final ReferralRepository              referralRepo;
    private final ReferralPointsRepository        pointsRepo;
    private final WaitlistEntryRepository         entryRepo;
    private final ReferralFingerprintRepository   fingerprintRepo;
    private final LeaderboardService              leaderboardService;
    private final ReferralFraudAuditRepository    fraudAuditRepo;

    /**
     * REQUIRES_NEW: independent of the caller's signup transaction so that a
     * duplicate-referee constraint fires inside this inner transaction and the
     * outer signup transaction can still commit.
     *
     * <p>Referrals are only tracked when the <em>referrer</em> has verified their
     * email address — unverified users cannot generate referrals.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackReferral(String referralCode, String refereeEmail) {
        var referrerOpt = entryRepo.findByReferralCode(referralCode);
        if (referrerOpt.isEmpty()) return;

        var referrer = referrerOpt.get();

        // Only verified users may generate referrals
        if (!referrer.isVerified()) {
            log.info("Referral rejected — referrer not verified [code={}, referee={}]",
                    referralCode, refereeEmail);
            return;
        }

        if (referrer.getEmail().equalsIgnoreCase(refereeEmail)) {
            log.warn("Self-referral rejected [email={}]", refereeEmail);
            return;
        }

        var ref = new Referral();
        ref.setReferrerEmail(referrer.getEmail());
        ref.setRefereeEmail(refereeEmail);
        // saveAndFlush surfaces DataIntegrityViolationException inside this
        // REQUIRES_NEW transaction, not at outer-transaction commit time.
        referralRepo.saveAndFlush(ref);

        // Audit: record the referral creation
        logAuditEvent("REFERRAL_CREATED", referrer.getEmail(), refereeEmail,
                null, null, null, null, null, UUID.randomUUID());

        // Fingerprint fraud check — runs after a successful referral insert.
        String ipHash = currentIpHash();
        updateFingerprint(referrer.getEmail(), ipHash);
    }

    /**
     * Updates DB points and, after the enclosing transaction commits, pushes
     * the new score to the Redis leaderboard sorted sets.
     *
     * <p>{@code delta} is signed: positive for an award, negative for a reversal.
     * If the referrer is flagged or blacklisted the award is silently rejected
     * and logged to the fraud audit table.
     */
    @Transactional
    public void awardPoints(String email, int delta) {
        var rp = pointsRepo.findByEmail(email).orElseGet(() -> {
            var newRp = new ReferralPoints();
            newRp.setEmail(email);
            return newRp;
        });

        // Enforce state machine: blocked referrers do not earn points
        if (rp.isEffectivelyBlocked()) {
            log.warn("Points award rejected — referrer blocked [email={}, status={}]",
                    email, rp.getReferrerStatus());
            logAuditEvent("POINTS_REJECTED", email, null, null, delta, rp.getPoints(),
                    "Referrer status: " + rp.getReferrerStatus(), null, UUID.randomUUID());
            return;
        }

        rp.addPoints(delta);
        pointsRepo.save(rp);

        int newTotal = rp.getPoints();
        String eventType = delta >= 0 ? "POINTS_AWARDED" : "POINTS_DEDUCTED";
        logAuditEvent(eventType, email, null, null, delta, newTotal,
                null, null, UUID.randomUUID());

        // Sync Redis only after the DB transaction commits so a rollback
        // cannot leave Redis ahead of the DB.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    leaderboardService.syncPoints(email, newTotal, delta);
                }
            });
        } else {
            leaderboardService.syncPoints(email, newTotal, delta);
        }
    }

    // ── Fingerprint helpers ──────────────────────────────────────────────────

    private void updateFingerprint(String referrerEmail, String ipHash) {
        var existing = fingerprintRepo.findByReferrerEmailAndIpHash(referrerEmail, ipHash);

        ReferralFingerprint fp;
        if (existing.isPresent()) {
            fp = existing.get();
            if (fp.getWindowStart().isBefore(OffsetDateTime.now().minusHours(24))) {
                fp.setCount(1);
                fp.setWindowStart(OffsetDateTime.now());
            } else {
                fp.setCount(fp.getCount() + 1);
            }
        } else {
            fp = new ReferralFingerprint();
            fp.setReferrerEmail(referrerEmail);
            fp.setIpHash(ipHash);
        }
        fingerprintRepo.save(fp);

        if (fp.getCount() > MAX_REFERRALS_PER_IP_PER_24H) {
            flagReferrer(referrerEmail, ipHash);
        }
    }

    private void flagReferrer(String referrerEmail, String ipHash) {
        var rp = pointsRepo.findByEmail(referrerEmail).orElseGet(() -> {
            var newRp = new ReferralPoints();
            newRp.setEmail(referrerEmail);
            return newRp;
        });
        if (!rp.isFlagged()) {
            rp.setFlagged(true);
            rp.setReferrerStatus("FLAGGED");
            pointsRepo.save(rp);
            logAuditEvent("FLAGGED", referrerEmail, null, ipHash, null, rp.getPoints(),
                    null, null, UUID.randomUUID());
            log.warn("Referrer flagged for suspicious activity [referrerEmail={}, ipHash={}]",
                    referrerEmail, ipHash);
        }
    }

    // ── Audit logging ────────────────────────────────────────────────────────

    private void logAuditEvent(String eventType, String referrerEmail, String refereeEmail,
                               String ipHash, Integer delta, Integer totalPoints,
                               String reason, String adminUser, UUID idempotencyKey) {
        var audit = new ReferralFraudAudit();
        audit.setEventType(eventType);
        audit.setReferrerEmail(referrerEmail);
        audit.setRefereeEmail(refereeEmail);
        audit.setIpHash(ipHash);
        audit.setDelta(delta);
        audit.setTotalPoints(totalPoints);
        audit.setReason(reason);
        audit.setAdminUser(adminUser);
        audit.setIdempotencyKey(idempotencyKey);
        fraudAuditRepo.save(audit);
    }

    // ── IP hash helpers ──────────────────────────────────────────────────────

    static String currentIpHash() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            String ip = RateLimitInterceptor.extractClientIp(attrs.getRequest());
            return hashIp(ip);
        } catch (IllegalStateException e) {
            return "unknown";
        }
    }

    public static String hashIp(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ip.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
