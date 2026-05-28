package com.waitlist.admin.mapper;

import com.waitlist.admin.dto.response.WaitlistEntryResponse;
import com.waitlist.admin.entity.Status;
import com.waitlist.admin.entity.WaitlistEntry;
import com.waitlist.events.SignupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WaitlistEntryMapperTest {

    WaitlistEntryMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WaitlistEntryMapperImpl();
    }

    // ── fromEvent ─────────────────────────────────────────────────────────────

    @Test
    void fromEvent_mapsAllEventFields() {
        var event = new SignupEvent(
                UUID.randomUUID(), Instant.now(),
                42L, "alice@example.com", "Alice", "Acme", "ref12345", null);

        WaitlistEntry entry = mapper.fromEvent(event);

        assertThat(entry.getIngestionId()).isEqualTo(42L);
        assertThat(entry.getEmail()).isEqualTo("alice@example.com");
        assertThat(entry.getName()).isEqualTo("Alice");
        assertThat(entry.getCompany()).isEqualTo("Acme");
    }

    @Test
    void fromEvent_statusDefaultsToPending() {
        var event = new SignupEvent(
                UUID.randomUUID(), Instant.now(),
                1L, "bob@example.com", "Bob", null, "aabbccdd", null);

        WaitlistEntry entry = mapper.fromEvent(event);

        // Status defaults to PENDING in the entity field declaration, not in the mapping
        assertThat(entry.getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void fromEvent_idIsNotMapped() {
        var event = new SignupEvent(
                UUID.randomUUID(), Instant.now(),
                99L, "carol@example.com", "Carol", null, "xxyyzz11", null);

        WaitlistEntry entry = mapper.fromEvent(event);

        // id is excluded — DB assigns it on INSERT
        assertThat(entry.getId()).isNull();
    }

    // ── toResponse ────────────────────────────────────────────────────────────

    @Test
    void toResponse_mapsAllEntityFields() {
        var entry = new WaitlistEntry();
        entry.setId(7L);
        entry.setEmail("dave@example.com");
        entry.setName("Dave");
        entry.setCompany("Corp");
        entry.setStatus(Status.APPROVED);
        OffsetDateTime now = OffsetDateTime.now();
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        WaitlistEntryResponse resp = mapper.toResponse(entry);

        assertThat(resp.id()).isEqualTo(7L);
        assertThat(resp.email()).isEqualTo("dave@example.com");
        assertThat(resp.name()).isEqualTo("Dave");
        assertThat(resp.company()).isEqualTo("Corp");
        assertThat(resp.status()).isEqualTo(Status.APPROVED);
        assertThat(resp.createdAt()).isEqualTo(now);
    }

    @Test
    void toResponse_versionIsNotExposed() {
        // WaitlistEntryResponse record has no 'version' field
        var entry = new WaitlistEntry();
        entry.setId(1L);
        entry.setEmail("eve@example.com");
        entry.setStatus(Status.PENDING);

        WaitlistEntryResponse resp = mapper.toResponse(entry);

        // Verify the record type has no version component
        assertThat(resp.getClass().getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("version");
    }

    // ── toResponseList ────────────────────────────────────────────────────────

    @Test
    void toResponseList_convertsAllEntries() {
        var e1 = new WaitlistEntry();
        e1.setId(1L);
        e1.setEmail("f1@example.com");
        e1.setStatus(Status.PENDING);

        var e2 = new WaitlistEntry();
        e2.setId(2L);
        e2.setEmail("f2@example.com");
        e2.setStatus(Status.APPROVED);

        List<WaitlistEntryResponse> list = mapper.toResponseList(List.of(e1, e2));

        assertThat(list).hasSize(2);
        assertThat(list).extracting(WaitlistEntryResponse::email)
                .containsExactly("f1@example.com", "f2@example.com");
    }

    @Test
    void toResponseList_emptyInput_returnsEmptyList() {
        assertThat(mapper.toResponseList(List.of())).isEmpty();
    }
}
