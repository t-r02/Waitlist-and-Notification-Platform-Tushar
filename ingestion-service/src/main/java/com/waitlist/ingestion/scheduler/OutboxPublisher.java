package com.waitlist.ingestion.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waitlist.events.SignupEvent;
import com.waitlist.ingestion.entity.OutboxEntry;
import com.waitlist.ingestion.repository.OutboxRepository;
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
            case "SignupEvent" -> {
                SignupEvent event = objectMapper.readValue(entry.getPayload(), SignupEvent.class);
                kafkaTemplate.send("waitlist.signup", entry.getAggregateId(), event).get();
            }
            default -> log.warn("Unknown event type in outbox entry {}: {}", entry.getId(), entry.getEventType());
        }
    }
}
