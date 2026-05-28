package com.waitlist.admin.messaging.producer;

import com.waitlist.events.StatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatusChangedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishStatusChange(StatusChangedEvent event) {
        kafkaTemplate.send("waitlist.status-changed", event.email(), event);
    }
}
