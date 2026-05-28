package com.waitlist.notification.messaging.consumer;

import com.waitlist.events.StatusChangedEvent;
import com.waitlist.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatusChangedConsumer {

    private final EmailService emailService;

    @KafkaListener(topics = "waitlist.status-changed", groupId = "notification-service")
    public void handleStatusChange(StatusChangedEvent event) {
        emailService.sendStatusUpdate(event);
    }
}
