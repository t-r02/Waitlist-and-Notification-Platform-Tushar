package com.waitlist.admin.mapper;

import com.waitlist.admin.dto.response.WaitlistEntryResponse;
import com.waitlist.admin.entity.WaitlistEntry;
import com.waitlist.events.SignupEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Handles all WaitlistEntry conversions in the admin service.
 *
 * <p><b>fromEvent</b>: Converts an inbound {@link SignupEvent} Kafka message into a
 * {@link WaitlistEntry} entity. Fields not present in the event (status, timestamps,
 * optimistic-lock version) are excluded and take their entity-declared defaults.
 *
 * <p><b>toResponse</b>: Converts a {@link WaitlistEntry} entity to the API-safe
 * {@link WaitlistEntryResponse} record, which omits the JPA {@code version} field
 * and any internal implementation details.
 */
@Mapper(componentModel = "spring")
public interface WaitlistEntryMapper {

    /**
     * Maps a Kafka {@link SignupEvent} to a new {@link WaitlistEntry}.
     * <ul>
     *   <li>{@code id} — assigned by the DB on INSERT</li>
     *   <li>{@code status} — defaults to {@code PENDING} in the entity field declaration</li>
     *   <li>{@code createdAt / updatedAt} — default to {@code OffsetDateTime.now()}</li>
     *   <li>{@code version} — managed by JPA optimistic locking</li>
     * </ul>
     */
    @Mapping(target = "id",         ignore = true)
    @Mapping(target = "status",     ignore = true)
    @Mapping(target = "createdAt",  ignore = true)
    @Mapping(target = "updatedAt",  ignore = true)
    @Mapping(target = "version",    ignore = true)
    WaitlistEntry fromEvent(SignupEvent event);

    /** Converts a single entity to the API response record. */
    WaitlistEntryResponse toResponse(WaitlistEntry entry);

    /** Batch conversion used by the list endpoints. */
    List<WaitlistEntryResponse> toResponseList(List<WaitlistEntry> entries);
}
