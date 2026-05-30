package com.waitlist.admin.repository.ingestion;

import com.waitlist.admin.entity.ingestion.ReferralPointsAdminView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReferralPointsAdminRepository extends JpaRepository<ReferralPointsAdminView, Long> {

    Optional<ReferralPointsAdminView> findByEmail(String email);

    /** All referrers that require admin attention (FLAGGED or BLACKLISTED). */
    @Query("SELECT r FROM ReferralPointsAdminView r WHERE r.referrerStatus IN ('FLAGGED','BLACKLISTED') ORDER BY r.points DESC")
    List<ReferralPointsAdminView> findFlaggedOrBlacklisted();
}
