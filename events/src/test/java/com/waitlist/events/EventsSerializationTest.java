package com.waitlist.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the shared event records (used by all three services) serialize
 * and deserialize correctly with Jackson.  A regression here would break the
 * Kafka message contract between producer and consumer services.
 */
class EventsSerializationTest {

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── SignupEvent ───────────────────────────────────────────────────────────

    @Test
    void signupEvent_roundTrip_preservesAllFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant now  = Instant.now();

        var original = new SignupEvent(
                eventId, now, 42L,
                "alice@example.com", "Alice", "Acme Corp", "ref12345", "ref00001");

        String json    = objectMapper.writeValueAsString(original);
        var    decoded = objectMapper.readValue(json, SignupEvent.class);

        assertThat(decoded.eventId()).isEqualTo(eventId);
        assertThat(decoded.occurredAt()).isEqualTo(now);
        assertThat(decoded.ingestionId()).isEqualTo(42L);
        assertThat(decoded.email()).isEqualTo("alice@example.com");
        assertThat(decoded.name()).isEqualTo("Alice");
        assertThat(decoded.company()).isEqualTo("Acme Corp");
        assertThat(decoded.referralCode()).isEqualTo("ref12345");
        assertThat(decoded.referredBy()).isEqualTo("ref00001");
    }

    @Test
    void signupEvent_nullOptionalFields_roundTripsCorrectly() throws Exception {
        var original = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 1L,
                "bob@example.com", null, null, "ref12345", null);

        String json    = objectMapper.writeValueAsString(original);
        var    decoded = objectMapper.readValue(json, SignupEvent.class);

        assertThat(decoded.name()).isNull();
        assertThat(decoded.company()).isNull();
        assertThat(decoded.referredBy()).isNull();
    }

    @Test
    void signupEvent_json_containsExpectedKeys() throws Exception {
        var event = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 1L,
                "carol@example.com", "Carol", null, "aabbccdd", null);

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("eventId", "occurredAt", "ingestionId",
                "email", "referralCode");
    }

    // ── StatusChangedEvent ────────────────────────────────────────────────────

    @Test
    void statusChangedEvent_roundTrip_preservesAllFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant now  = Instant.now();

        var original = new StatusChangedEvent(
                eventId, now, 99L,
                "dave@example.com", "PENDING", "APPROVED", "admin");

        String json    = objectMapper.writeValueAsString(original);
        var    decoded = objectMapper.readValue(json, StatusChangedEvent.class);

        assertThat(decoded.eventId()).isEqualTo(eventId);
        assertThat(decoded.occurredAt()).isEqualTo(now);
        assertThat(decoded.ingestionId()).isEqualTo(99L);
        assertThat(decoded.email()).isEqualTo("dave@example.com");
        assertThat(decoded.oldStatus()).isEqualTo("PENDING");
        assertThat(decoded.newStatus()).isEqualTo("APPROVED");
        assertThat(decoded.changedBy()).isEqualTo("admin");
    }

    @Test
    void statusChangedEvent_json_containsExpectedKeys() throws Exception {
        var event = new StatusChangedEvent(
                UUID.randomUUID(), Instant.now(), 1L,
                "eve@example.com", "APPROVED", "INVITED", "admin");

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("eventId", "occurredAt", "oldStatus", "newStatus", "changedBy");
    }

    // ── Cross-record distinctness ─────────────────────────────────────────────

    @Test
    void twoEventsWithSameData_areEqual_whenRecordFieldsMatch() {
        UUID id     = UUID.randomUUID();
        Instant now = Instant.now();

        var e1 = new SignupEvent(id, now, 1L, "f@f.com", "F", null, "ffffffff", null);
        var e2 = new SignupEvent(id, now, 1L, "f@f.com", "F", null, "ffffffff", null);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    void eventsWithDifferentIds_areNotEqual() {
        Instant now = Instant.now();

        var e1 = new SignupEvent(UUID.randomUUID(), now, 1L, "g@g.com", "G", null, "gggggggg", null);
        var e2 = new SignupEvent(UUID.randomUUID(), now, 1L, "g@g.com", "G", null, "gggggggg", null);

        assertThat(e1).isNotEqualTo(e2);
    }
}
