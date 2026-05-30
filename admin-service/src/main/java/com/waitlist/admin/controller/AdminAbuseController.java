package com.waitlist.admin.controller;

import com.waitlist.admin.dto.request.AbuseActionRequest;
import com.waitlist.admin.dto.response.AuditLogEntryResponse;
import com.waitlist.admin.dto.response.FlaggedReferrerResponse;
import com.waitlist.admin.service.AbuseManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for referral fraud monitoring and enforcement.
 * All routes require a valid JWT (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAbuseController {

    private final AbuseManagementService abuseService;

    /**
     * Returns all referrers whose status is FLAGGED or BLACKLISTED,
     * along with aggregate stats useful for the admin review UI.
     */
    @GetMapping("/flagged-referrers")
    public ResponseEntity<List<FlaggedReferrerResponse>> getFlaggedReferrers() {
        return ResponseEntity.ok(abuseService.getFlaggedReferrers());
    }

    /**
     * Full audit trail filtered by optional criteria.
     *
     * @param email     filter by referrer email (optional)
     * @param eventType filter by event type, e.g. FLAGGED, POINTS_REJECTED (optional)
     * @param startDate ISO-8601 start date-time (optional)
     * @param endDate   ISO-8601 end date-time (optional)
     */
    @GetMapping("/audit-log")
    public ResponseEntity<List<AuditLogEntryResponse>> getAuditLog(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ResponseEntity.ok(abuseService.getAuditLog(email, eventType, startDate, endDate));
    }

    /**
     * Blacklists a referrer: sets status=BLACKLISTED, zeros out all points,
     * and blocks future point awards.  Logs to audit trail.
     */
    @PostMapping("/referrers/{email}/blacklist")
    public ResponseEntity<Void> blacklist(@PathVariable String email,
                                          @Valid @RequestBody AbuseActionRequest req,
                                          Authentication auth) {
        abuseService.blacklist(email, req.getReason(), auth.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Whitelists a referrer: clears the blocked status, restores original points
     * (if available in audit log), and logs to audit trail.
     */
    @PostMapping("/referrers/{email}/whitelist")
    public ResponseEntity<Void> whitelist(@PathVariable String email,
                                          @Valid @RequestBody AbuseActionRequest req,
                                          Authentication auth) {
        abuseService.whitelist(email, req.getReason(), auth.getName());
        return ResponseEntity.ok().build();
    }
}
