package com.waitlist.ingestion.messaging.producer;

import com.waitlist.events.SignupEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WaitlistEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishSignup(SignupEvent event) {
        kafkaTemplate.send("waitlist.signup", event.email(), event);
    }
}
