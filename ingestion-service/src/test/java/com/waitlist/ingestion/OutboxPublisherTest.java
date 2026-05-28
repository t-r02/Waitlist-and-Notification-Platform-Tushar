package com.waitlist.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.waitlist.events.SignupEvent;
import com.waitlist.ingestion.entity.OutboxEntry;
import com.waitlist.ingestion.scheduler.OutboxPublisher;
import com.waitlist.ingestion.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    OutboxPublisher publisher;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new OutboxPublisher(outboxRepository, kafkaTemplate, objectMapper);
    }

    @Test
    void whenKafkaFails_rowRemainsUnpublished_andIsRetried() throws Exception {
        SignupEvent event = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 1L,
                "user@example.com", "User", "Acme", "ref123", null);

        OutboxEntry entry = new OutboxEntry();
        entry.setId(1L);
        entry.setAggregateType("WaitlistEntry");
        entry.setAggregateId("user@example.com");
        entry.setEventType("SignupEvent");
        entry.setPayload(objectMapper.writeValueAsString(event));
        entry.setCreatedAt(OffsetDateTime.now());

        when(outboxRepository.findUnpublishedForUpdate()).thenReturn(List.of(entry));
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new RuntimeException("Kafka broker unavailable"));

        publisher.publishPending();

        assertThat(entry.getPublishedAt()).isNull();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void whenKafkaSucceeds_publishedAtIsSet() throws Exception {
        SignupEvent event = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 2L,
                "other@example.com", "Other", "Corp", "ref456", null);

        OutboxEntry entry = new OutboxEntry();
        entry.setId(2L);
        entry.setAggregateType("WaitlistEntry");
        entry.setAggregateId("other@example.com");
        entry.setEventType("SignupEvent");
        entry.setPayload(objectMapper.writeValueAsString(event));
        entry.setCreatedAt(OffsetDateTime.now());

        when(outboxRepository.findUnpublishedForUpdate()).thenReturn(List.of(entry));

        var future = new java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>>();
        future.complete(null);
        when(kafkaTemplate.send(any(String.class), any(String.class), any())).thenReturn(future);

        publisher.publishPending();

        assertThat(entry.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(entry);
    }

    @Test
    void whenKafkaFailsOnFirstEntry_secondEntryIsStillAttempted() throws Exception {
        SignupEvent event1 = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 1L,
                "first@example.com", "First", "Corp", "r1", null);
        SignupEvent event2 = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 2L,
                "second@example.com", "Second", "Corp", "r2", null);

        OutboxEntry entry1 = new OutboxEntry();
        entry1.setId(1L);
        entry1.setAggregateId("first@example.com");
        entry1.setEventType("SignupEvent");
        entry1.setPayload(objectMapper.writeValueAsString(event1));
        entry1.setCreatedAt(OffsetDateTime.now());

        OutboxEntry entry2 = new OutboxEntry();
        entry2.setId(2L);
        entry2.setAggregateId("second@example.com");
        entry2.setEventType("SignupEvent");
        entry2.setPayload(objectMapper.writeValueAsString(event2));
        entry2.setCreatedAt(OffsetDateTime.now());

        when(outboxRepository.findUnpublishedForUpdate()).thenReturn(List.of(entry1, entry2));

        var successFuture = new java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>>();
        successFuture.complete(null);

        when(kafkaTemplate.send(any(String.class), eq("first@example.com"), any()))
                .thenThrow(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(any(String.class), eq("second@example.com"), any()))
                .thenReturn(successFuture);

        publisher.publishPending();

        assertThat(entry1.getPublishedAt()).isNull();
        assertThat(entry2.getPublishedAt()).isNotNull();
        verify(outboxRepository, times(1)).save(entry2);
        verify(outboxRepository, never()).save(entry1);
    }
}
