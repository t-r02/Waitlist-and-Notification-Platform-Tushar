package com.waitlist.admin.entity.ingestion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read/write view of {@code ingestion.referral_fraud_audit} from admin-service.
 * Admin actions (BLACKLIST_ACTION, WHITELIST_ACTION) are written here by the admin service.
 */
@Entity
@Getter
@Setter
@Table(schema = "ingestion", name = "referral_fraud_audit")
public class ReferralFraudAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "referrer_email", nullable = false)
    private String referrerEmail;

    @Column(name = "referee_email")
    private String refereeEmail;

    @Column(name = "ip_hash")
    private String ipHash;

    private Integer delta;

    @Column(name = "total_points")
    private Integer totalPoints;

    private String reason;

    @Column(name = "admin_user")
    private String adminUser;

    @Column(name = "idempotency_key", unique = true)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
