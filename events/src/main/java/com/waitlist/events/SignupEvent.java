package com.waitlist.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by ingestion-service when a new waitlist entry is created.
 * eventId is the idempotency key for all downstream consumers.
 */
public record SignupEvent(
        UUID   eventId,
        Instant occurredAt,
        Long   ingestionId,
        String email,
        String name,
        String company,
        String referralCode,
        String referredBy
) {}
