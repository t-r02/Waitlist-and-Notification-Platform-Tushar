package com.waitlist.ingestion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "referrals")
public class Referral {
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
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
