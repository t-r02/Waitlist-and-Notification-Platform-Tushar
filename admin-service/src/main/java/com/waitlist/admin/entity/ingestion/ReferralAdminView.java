package com.waitlist.admin.entity.ingestion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Read-only view of {@code ingestion.referrals} used for referral count stats. */
@Entity
@Getter
@Setter
@Table(schema = "ingestion", name = "referrals")
public class ReferralAdminView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referrer_email", nullable = false)
    private String referrerEmail;

    @Column(name = "referee_email", nullable = false, unique = true)
    private String refereeEmail;

    @Column(nullable = false)
    private boolean converted = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
