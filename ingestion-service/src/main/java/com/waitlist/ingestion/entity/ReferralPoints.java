package com.waitlist.ingestion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "referral_points")
public class ReferralPoints {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private int points = 0;

    private String badge;

    /** Set when the referrer triggers the 5-referrals-from-same-IP-in-24h heuristic. */
    @Column(nullable = false)
    private boolean flagged = false;

    /**
     * State machine: ACTIVE → FLAGGED → BLACKLISTED | WHITELISTED
     * BLACKLISTED: admin manually removed all points and barred future awards.
     * WHITELISTED: admin cleared a false-positive flag.
     */
    @Column(name = "referrer_status", nullable = false)
    private String referrerStatus = "ACTIVE";

    public void addPoints(int p) {
        points += p;
        updateBadge();
    }

    public void removeAllPoints() {
        points = 0;
        badge = null;
    }

    /** True when this referrer should be excluded from leaderboard and point awards. */
    public boolean isEffectivelyBlocked() {
        return flagged || "BLACKLISTED".equals(referrerStatus);
    }

    private void updateBadge() {
        if (points <= 0)       badge = null;
        else if (points >= 50) badge = "GOLD";
        else if (points >= 20) badge = "SILVER";
        else if (points >= 5)  badge = "BRONZE";
    }
}
