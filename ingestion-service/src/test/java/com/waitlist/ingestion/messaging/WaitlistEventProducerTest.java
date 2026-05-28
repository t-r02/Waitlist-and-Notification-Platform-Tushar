package com.waitlist.ingestion.messaging;

import com.waitlist.events.SignupEvent;
import com.waitlist.ingestion.messaging.producer.WaitlistEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaitlistEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    WaitlistEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new WaitlistEventProducer(kafkaTemplate);
    }

    @Test
    void publishSignup_sendsToCorrectTopicWithEmailAsKey() {
        var event = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 1L,
                "alice@example.com", "Alice", "Acme", "ref12345", null);

        producer.publishSignup(event);

        verify(kafkaTemplate).send("waitlist.signup", "alice@example.com", event);
    }

    @Test
    void publishSignup_differentEmails_useDifferentKeys() {
        var event1 = new SignupEvent(UUID.randomUUID(), Instant.now(), 1L,
                "bob@example.com", "Bob", null, "aaaaaaaa", null);
        var event2 = new SignupEvent(UUID.randomUUID(), Instant.now(), 2L,
                "carol@example.com", "Carol", null, "bbbbbbbb", null);

        producer.publishSignup(event1);
        producer.publishSignup(event2);

        verify(kafkaTemplate).send("waitlist.signup", "bob@example.com", event1);
        verify(kafkaTemplate).send("waitlist.signup", "carol@example.com", event2);
    }
}
