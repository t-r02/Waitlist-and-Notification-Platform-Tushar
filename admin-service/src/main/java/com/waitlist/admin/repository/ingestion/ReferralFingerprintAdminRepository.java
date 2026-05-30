package com.waitlist.admin.repository.ingestion;

import com.waitlist.admin.entity.ingestion.ReferralFingerprintAdminView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferralFingerprintAdminRepository extends JpaRepository<ReferralFingerprintAdminView, Long> {

    /** Number of distinct IPs seen for this referrer across all windows. */
    @Query("SELECT COUNT(DISTINCT f.ipHash) FROM ReferralFingerprintAdminView f WHERE f.referrerEmail = :email")
    long countDistinctIpsByReferrerEmail(@Param("email") String email);
}
