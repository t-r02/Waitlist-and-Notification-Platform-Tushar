package com.waitlist.admin.service;

import com.waitlist.admin.dto.response.AuditLogEntryResponse;
import com.waitlist.admin.dto.response.FlaggedReferrerResponse;
import com.waitlist.admin.entity.ingestion.ReferralFraudAuditEntry;
import com.waitlist.admin.entity.ingestion.ReferralPointsAdminView;
import com.waitlist.admin.repository.ingestion.ReferralAdminRepository;
import com.waitlist.admin.repository.ingestion.ReferralFingerprintAdminRepository;
import com.waitlist.admin.repository.ingestion.ReferralFraudAuditAdminRepository;
import com.waitlist.admin.repository.ingestion.ReferralPointsAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AbuseManagementService {

    private final ReferralPointsAdminRepository    pointsRepo;
    private final ReferralFraudAuditAdminRepository auditRepo;
    private final ReferralFingerprintAdminRepository fpRepo;
    private final ReferralAdminRepository          referralRepo;
    private final RestTemplate                     restTemplate;

    @Value("${app.ingestion.base-url:http://ingestion-service:8081}")
    private String ingestionBaseUrl;

    // ── Flagged referrers list ────────────────────────────────────────────────

    public List<FlaggedReferrerResponse> getFlaggedReferrers() {
        return pointsRepo.findFlaggedOrBlacklisted().stream()
                .map(rp -> {
                    long referralCount = referralRepo.countByReferrerEmail(rp.getEmail());
                    long uniqueIpCount  = fpRepo.countDistinctIpsByReferrerEmail(rp.getEmail());
                    String flaggedAt = auditRepo
                            .findFirstByReferrerEmailAndEventTypeOrderByCreatedAtDesc(
                                    rp.getEmail(), "FLAGGED")
                            .map(a -> a.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                            .orElse(null);
                    return new FlaggedReferrerResponse(
                            rp.getEmail(),
                            rp.getReferrerStatus(),
                            rp.getPoints(),
                            referralCount,
                            uniqueIpCount,
                            flaggedAt);
                })
                .toList();
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    public List<AuditLogEntryResponse> getAuditLog(String email,
                                                    String eventType,
                                                    String startDateStr,
                                                    String endDateStr) {
        OffsetDateTime startDate = startDateStr != null ? OffsetDateTime.parse(startDateStr) : null;
        OffsetDateTime endDate   = endDateStr   != null ? OffsetDateTime.parse(endDateStr)   : null;

        // Fetch a broad slice then apply optional filters in Java to avoid
        // PostgreSQL parameter-type-inference issues with nullable TIMESTAMPTZ params.
        List<ReferralFraudAuditEntry> rows = (email != null && !email.isBlank())
                ? auditRepo.findByReferrerEmailOrderByCreatedAtDesc(email, PageRequest.of(0, 500))
                : auditRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 500));

        return rows.stream()
                .filter(a -> eventType == null || eventType.isBlank() || eventType.equals(a.getEventType()))
                .filter(a -> startDate == null || !a.getCreatedAt().isBefore(startDate))
                .filter(a -> endDate   == null || !a.getCreatedAt().isAfter(endDate))
                .map(this::toResponse)
                .toList();
    }

    // ── Blacklist ─────────────────────────────────────────────────────────────

    @Transactional
    public void blacklist(String email, String reason, String adminUser) {
        var rp = requireReferralPoints(email);

        if ("BLACKLISTED".equals(rp.getReferrerStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Referrer " + email + " is already blacklisted");
        }

        int originalPoints = rp.getPoints();

        rp.setFlagged(true);
        rp.setReferrerStatus("BLACKLISTED");
        rp.setPoints(0);
        rp.setBadge(null);
        pointsRepo.save(rp);

        var audit = new ReferralFraudAuditEntry();
        audit.setEventType("BLACKLIST_ACTION");
        audit.setReferrerEmail(email);
        audit.setDelta(-originalPoints);          // stored for potential restore
        audit.setTotalPoints(0);
        audit.setReason(reason);
        audit.setAdminUser(adminUser);
        audit.setIdempotencyKey(UUID.randomUUID());
        auditRepo.save(audit);

        log.info("Referrer blacklisted [email={}, originalPts={}, admin={}]",
                email, originalPoints, adminUser);

        syncRedis("/api/public/internal/leaderboard/remove", email);
    }

    // ── Whitelist ─────────────────────────────────────────────────────────────

    @Transactional
    public void whitelist(String email, String reason, String adminUser) {
        var rp = requireReferralPoints(email);

        if ("WHITELISTED".equals(rp.getReferrerStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Referrer " + email + " is already whitelisted");
        }

        // Attempt to restore points from the most recent BLACKLIST_ACTION audit entry
        int restoredPoints = auditRepo
                .findMostRecentBlacklist(email, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(a -> a.getDelta() != null ? Math.abs(a.getDelta()) : 0)
                .orElse(0);

        rp.setFlagged(false);
        rp.setReferrerStatus("WHITELISTED");
        if (restoredPoints > 0) {
            rp.setPoints(restoredPoints);
            // Recalculate badge
            if      (restoredPoints >= 50) rp.setBadge("GOLD");
            else if (restoredPoints >= 20) rp.setBadge("SILVER");
            else if (restoredPoints >= 5)  rp.setBadge("BRONZE");
            else                           rp.setBadge(null);
        }
        pointsRepo.save(rp);

        var audit = new ReferralFraudAuditEntry();
        audit.setEventType("WHITELIST_ACTION");
        audit.setReferrerEmail(email);
        audit.setDelta(restoredPoints);
        audit.setTotalPoints(rp.getPoints());
        audit.setReason(reason);
        audit.setAdminUser(adminUser);
        audit.setIdempotencyKey(UUID.randomUUID());
        auditRepo.save(audit);

        log.info("Referrer whitelisted [email={}, restoredPts={}, admin={}]",
                email, restoredPoints, adminUser);

        syncRedis("/api/public/internal/leaderboard/restore", email);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ReferralPointsAdminView requireReferralPoints(String email) {
        return pointsRepo.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No referral record found for " + email));
    }

    private AuditLogEntryResponse toResponse(ReferralFraudAuditEntry a) {
        return new AuditLogEntryResponse(
                a.getId(),
                a.getEventType(),
                a.getReferrerEmail(),
                a.getRefereeEmail(),
                a.getIpHash(),
                a.getDelta(),
                a.getTotalPoints(),
                a.getReason(),
                a.getAdminUser(),
                a.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    /** Best-effort Redis sync via ingestion-service internal endpoint. */
    private void syncRedis(String path, String email) {
        try {
            String url = ingestionBaseUrl + path + "?email=" +
                    URLEncoder.encode(email, StandardCharsets.UTF_8);
            restTemplate.postForEntity(url, null, Void.class);
        } catch (RestClientException ex) {
            log.warn("Redis sync call failed for [email={}] path={} — leaderboard will reconcile on restart",
                    email, path, ex);
        }
    }
}
