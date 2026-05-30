package com.waitlist.ingestion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable audit log for all referral fraud-related events.
 *
 * <p>Written by ingestion-service for: REFERRAL_CREATED, FLAGGED,
 * POINTS_AWARDED, POINTS_REJECTED.
 * Written by admin-service (cross-schema) for: BLACKLIST_ACTION, WHITELIST_ACTION.
 */
@Entity
@Getter
@Setter
@Table(name = "referral_fraud_audit")
public class ReferralFraudAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** REFERRAL_CREATED | FLAGGED | POINTS_AWARDED | POINTS_REJECTED | BLACKLIST_ACTION | WHITELIST_ACTION */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "referrer_email", nullable = false)
    private String referrerEmail;

    /** Populated for REFERRAL_CREATED. */
    @Column(name = "referee_email")
    private String refereeEmail;

    /** Hashed IP; populated for FLAGGED. */
    @Column(name = "ip_hash")
    private String ipHash;

    /** Signed delta (positive = award, negative = reversal). */
    private Integer delta;

    @Column(name = "total_points")
    private Integer totalPoints;

    /** Human-readable explanation; populated for admin actions and rejections. */
    private String reason;

    /** JWT subject of the admin who triggered the action; null for system events. */
    @Column(name = "admin_user")
    private String adminUser;

    /** UUID to prevent duplicate event writes. */
    @Column(name = "idempotency_key", unique = true)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
