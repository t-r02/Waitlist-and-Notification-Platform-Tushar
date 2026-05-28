package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.entity.ReferralEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReferralEventLogRepository extends JpaRepository<ReferralEventLog, Long> {
    boolean existsByEventId(UUID eventId);
}
