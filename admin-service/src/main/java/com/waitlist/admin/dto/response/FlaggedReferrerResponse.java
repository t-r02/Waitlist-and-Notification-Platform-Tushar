package com.waitlist.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FlaggedReferrerResponse {
    private String email;
    private String referrerStatus;
    private int    totalPoints;
    private long   referralCount;
    private long   uniqueIpCount;
    /** ISO-8601 timestamp of the first FLAGGED audit event, or null if not yet in audit log. */
    private String flaggedAt;
}
