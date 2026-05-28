package com.waitlist.notification.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "notification_log")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable UUID from the originating event — primary deduplication key. */
    @Column(name = "event_id", unique = true, nullable = false)
    private UUID eventId;

    /** Human-readable description kept for legacy queries (event_key is NOT NULL in DDL). */
    @Column(name = "event_key", unique = true, nullable = false)
    private String eventKey;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String type;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt = OffsetDateTime.now();
}
