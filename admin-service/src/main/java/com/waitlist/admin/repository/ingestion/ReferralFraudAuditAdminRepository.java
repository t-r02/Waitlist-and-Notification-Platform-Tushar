package com.waitlist.admin.repository.ingestion;

import com.waitlist.admin.entity.ingestion.ReferralFraudAuditEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReferralFraudAuditAdminRepository extends JpaRepository<ReferralFraudAuditEntry, Long> {

    /** All entries ordered newest-first (used when no email filter is applied). */
    @Query("SELECT a FROM ReferralFraudAuditEntry a ORDER BY a.createdAt DESC")
    List<ReferralFraudAuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Entries for a specific referrer, newest-first. */
    @Query("SELECT a FROM ReferralFraudAuditEntry a WHERE a.referrerEmail = :email ORDER BY a.createdAt DESC")
    List<ReferralFraudAuditEntry> findByReferrerEmailOrderByCreatedAtDesc(@Param("email") String email, Pageable pageable);

    /** Most recent blacklist action — used to find original points for restore. */
    @Query("SELECT a FROM ReferralFraudAuditEntry a WHERE a.referrerEmail = :email " +
           "AND a.eventType = 'BLACKLIST_ACTION' ORDER BY a.createdAt DESC")
    List<ReferralFraudAuditEntry> findMostRecentBlacklist(@Param("email") String email, Pageable pageable);

    Optional<ReferralFraudAuditEntry> findFirstByReferrerEmailAndEventTypeOrderByCreatedAtDesc(
            String referrerEmail, String eventType);
}
