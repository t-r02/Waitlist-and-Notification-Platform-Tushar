package com.waitlist.ingestion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "waitlist_entries")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;
    private String company;

    @Column(name = "referral_code", unique = true, nullable = false)
    private String referralCode;

    @Column(name = "referred_by")
    private String referredBy;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(name = "verification_token", length = 64, unique = true)
    private String verificationToken;

    @Column(name = "verification_token_expires_at")
    private OffsetDateTime verificationTokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private Long version;
}
