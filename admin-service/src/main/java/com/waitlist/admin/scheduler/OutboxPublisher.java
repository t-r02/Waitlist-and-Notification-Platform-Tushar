package com.waitlist.admin.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waitlist.admin.entity.OutboxEntry;
import com.waitlist.admin.repository.OutboxRepository;
import com.waitlist.events.StatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPending() {
        List<OutboxEntry> entries = outboxRepository.findUnpublishedForUpdate();
        for (OutboxEntry entry : entries) {
            try {
                dispatch(entry);
                entry.setPublishedAt(OffsetDateTime.now());
                outboxRepository.save(entry);
            } catch (Exception e) {
                log.error("Failed to publish outbox entry {}, will retry", entry.getId(), e);
            }
        }
    }

    private void dispatch(OutboxEntry entry) throws Exception {
        switch (entry.getEventType()) {
            case "StatusChangedEvent" -> {
                StatusChangedEvent event = objectMapper.readValue(entry.getPayload(), StatusChangedEvent.class);
                kafkaTemplate.send("waitlist.status-changed", entry.getAggregateId(), event).get();
            }
            default -> log.warn("Unknown event type in outbox entry {}: {}", entry.getId(), entry.getEventType());
        }
    }
}
