package com.waitlist.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by admin-service whenever a waitlist entry's status is updated.
 * eventId is the idempotency key for all downstream consumers.
 */
public record StatusChangedEvent(
        UUID   eventId,
        Instant occurredAt,
        Long   ingestionId,
        String email,
        String oldStatus,
        String newStatus,
        String changedBy
) {}
