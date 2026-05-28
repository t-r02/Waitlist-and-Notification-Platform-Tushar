package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.entity.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    @Query(value = """
            SELECT * FROM outbox
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntry> findUnpublishedForUpdate();
}
