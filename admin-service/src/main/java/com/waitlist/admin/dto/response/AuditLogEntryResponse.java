package com.waitlist.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuditLogEntryResponse {
    private Long    id;
    private String  eventType;
    private String  referrerEmail;
    private String  refereeEmail;
    private String  ipHash;
    private Integer delta;
    private Integer totalPoints;
    private String  reason;
    private String  adminUser;
    private String  createdAt;
}
