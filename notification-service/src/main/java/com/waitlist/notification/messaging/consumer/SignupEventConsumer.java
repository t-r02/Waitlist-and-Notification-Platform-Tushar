package com.waitlist.notification.messaging.consumer;

import com.waitlist.events.SignupEvent;
import com.waitlist.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SignupEventConsumer {

    private final EmailService emailService;

    @KafkaListener(topics = "waitlist.signup", groupId = "notification-service")
    public void handleSignup(SignupEvent event) {
        emailService.sendConfirmation(event);
    }
}
