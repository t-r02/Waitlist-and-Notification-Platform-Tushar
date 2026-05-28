package com.waitlist.ingestion.service;

import com.waitlist.ingestion.entity.Referral;
import com.waitlist.ingestion.entity.ReferralFingerprint;
import com.waitlist.ingestion.entity.ReferralPoints;
import com.waitlist.ingestion.filter.RateLimitInterceptor;
import com.waitlist.ingestion.repository.ReferralFingerprintRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final int MAX_REFERRALS_PER_IP_PER_24H = 5;

    private final ReferralRepository         referralRepo;
    private final ReferralPointsRepository   pointsRepo;
    private final WaitlistEntryRepository    entryRepo;
    private final ReferralFingerprintRepository fingerprintRepo;
    private final LeaderboardService         leaderboardService;

    /**
     * REQUIRES_NEW: independent of the caller's signup transaction so that a
     * duplicate-referee constraint fires inside this inner transaction and the
     * outer signup transaction can still commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackReferral(String referralCode, String refereeEmail) {
        // Note: we do NOT check entryRepo.findByEmail(refereeEmail) here.
        // trackReferral is called from within the outer signup transaction, after
        // repository.save(entry) but before that transaction commits.  The REQUIRES_NEW
        // inner transaction cannot see the uncommitted referee row, so that guard would
        // always return early and silently drop every referral.  The referee is, by
        // definition, the entry that was just created; we know it exists.
        var referrerOpt = entryRepo.findByReferralCode(referralCode);
        if (referrerOpt.isEmpty()) return;

        var referrer = referrerOpt.get();

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

        // Fingerprint fraud check — runs after a successful referral insert.
        // Points are awarded later by StatusChangedConsumer on APPROVED.
        String ipHash = currentIpHash();
        updateFingerprint(referrer.getEmail(), ipHash);
    }

    /**
     * Updates DB points and, after the enclosing transaction commits, pushes
     * the new score to the Redis leaderboard sorted sets.
     *
     * <p>{@code delta} is signed: positive for an award, negative for a reversal.
     */
    @Transactional
    public void awardPoints(String email, int delta) {
        var rp = pointsRepo.findByEmail(email).orElseGet(() -> {
            var newRp = new ReferralPoints();
            newRp.setEmail(email);
            return newRp;
        });
        rp.addPoints(delta);
        pointsRepo.save(rp);

        int newTotal = rp.getPoints();

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
            // Outside an active transaction (e.g. unit tests) — sync immediately.
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
            // count and windowStart default to 1 / now() in the entity
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
            pointsRepo.save(rp);
            log.warn("Referrer flagged for suspicious activity [referrerEmail={}, ipHash={}]",
                    referrerEmail, ipHash);
        }
    }

    /**
     * Reads the client IP from the current servlet request and returns a
     * truncated SHA-256 hex digest.  Returns {@code "unknown"} outside a web
     * context (async workers, tests).
     */
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
