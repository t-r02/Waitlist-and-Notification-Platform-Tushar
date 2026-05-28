package com.waitlist.admin.dto.response;

import com.waitlist.admin.entity.Status;

import java.time.OffsetDateTime;

/**
 * Read-only projection of a waitlist entry returned by the admin API.
 * Using a record here keeps the DTO immutable and avoids leaking JPA
 * entity internals (version field, lazy-load proxies) to API consumers.
 */
public record WaitlistEntryResponse(
        Long id,
        String email,
        String name,
        String company,
        Status status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
