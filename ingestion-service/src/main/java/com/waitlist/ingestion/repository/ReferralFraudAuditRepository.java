package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.entity.ReferralFraudAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReferralFraudAuditRepository extends JpaRepository<ReferralFraudAudit, Long> {

    boolean existsByIdempotencyKey(UUID idempotencyKey);

    @Query("SELECT a FROM ReferralFraudAudit a WHERE a.referrerEmail = :email " +
           "AND a.eventType = 'BLACKLIST_ACTION' ORDER BY a.createdAt DESC")
    List<ReferralFraudAudit> findMostRecentBlacklist(@Param("email") String email, Pageable pageable);

    @Query("SELECT a FROM ReferralFraudAudit a WHERE " +
           "(:email IS NULL OR a.referrerEmail = :email) AND " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    List<ReferralFraudAudit> findWithFilters(@Param("email") String email,
                                              @Param("eventType") String eventType,
                                              @Param("startDate") OffsetDateTime startDate,
                                              @Param("endDate") OffsetDateTime endDate,
                                              Pageable pageable);
}
