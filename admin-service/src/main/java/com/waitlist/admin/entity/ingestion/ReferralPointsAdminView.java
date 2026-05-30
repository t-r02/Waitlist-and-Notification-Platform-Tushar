package com.waitlist.admin.entity.ingestion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Read/write view of {@code ingestion.referral_points} from the admin service.
 * Both services share the same PostgreSQL instance; the schema qualifier forces
 * JPA to use the ingestion schema regardless of the admin service default.
 */
@Entity
@Getter
@Setter
@Table(schema = "ingestion", name = "referral_points")
public class ReferralPointsAdminView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private int points = 0;

    private String badge;

    @Column(nullable = false)
    private boolean flagged = false;

    @Column(name = "referrer_status", nullable = false)
    private String referrerStatus = "ACTIVE";
}
