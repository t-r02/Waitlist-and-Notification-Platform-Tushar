package com.waitlist.ingestion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Idempotency log for StatusChangedEvents processed by StatusChangedConsumer.
 * The UNIQUE constraint on event_id prevents a replayed Kafka message from
 * awarding or reversing points a second time.
 */
@Entity
@Getter
@Setter
@Table(name = "referral_event_log",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_referral_event_log_event_id",
               columnNames = "event_id"))
public class ReferralEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** AWARD, REVERSE, or NOOP */
    @Column(nullable = false)
    private String action;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt = OffsetDateTime.now();
}
