package com.waitlist.admin.entity.ingestion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Read-only view of {@code ingestion.referrals_fingerprint} used for fraud stats. */
@Entity
@Getter
@Setter
@Table(schema = "ingestion", name = "referrals_fingerprint",
       uniqueConstraints = @UniqueConstraint(columnNames = {"referrer_email", "ip_hash"}))
public class ReferralFingerprintAdminView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referrer_email", nullable = false)
    private String referrerEmail;

    @Column(name = "ip_hash", nullable = false)
    private String ipHash;

    @Column(nullable = false)
    private int count = 1;

    @Column(name = "window_start", nullable = false)
    private OffsetDateTime windowStart;
}
