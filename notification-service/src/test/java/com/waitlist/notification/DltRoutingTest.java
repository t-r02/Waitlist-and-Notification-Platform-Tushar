package com.waitlist.notification;

import com.waitlist.events.SignupEvent;
import com.waitlist.notification.service.EmailService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.TemplateEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "waitlist.signup",
        "waitlist.signup.dlt",
        "waitlist.status-changed",
        "waitlist.status-changed.dlt"
    }
)
class DltRoutingTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockBean private EmailService emailService;
    @MockBean private JavaMailSender javaMailSender;
    @MockBean private TemplateEngine templateEngine;

    @Test
    void whenEmailServiceAlwaysThrows_signupRecordLandsOnDlt() throws Exception {
        doThrow(new RuntimeException("simulated mail failure"))
                .when(emailService).sendConfirmation(any(SignupEvent.class));

        var event = new SignupEvent(
                UUID.randomUUID(), Instant.now(), 1L,
                "dlt-test@example.com", "DLT Tester", "ACME",
                "DLT123", null);

        kafkaTemplate.send("waitlist.signup", event.email(), event).get();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-verifier", "false", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, byte[]> dltConsumer =
                     new DefaultKafkaConsumerFactory<String, byte[]>(consumerProps).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, "waitlist.signup.dlt");

            ConsumerRecords<String, byte[]> dltRecords =
                    KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(15));

            assertThat(dltRecords.isEmpty())
                    .as("Expected at least one record in waitlist.signup.dlt")
                    .isFalse();
        }
    }

    /**
     * BUG #4 regression: a non-JSON (deserialization failure) record must be routed to
     * waitlist.signup.dlt by ErrorHandlingDeserializer + DefaultErrorHandler, without
     * crashing or stalling the consumer.
     *
     * <p>This is distinct from the sibling test above, which simulates a
     * <em>processing</em> failure (EmailService throws). Here the message is raw bytes
     * that cannot be parsed as JSON at all — the failure happens inside the deserializer,
     * before any listener code runs.
     */
    @Test
    void whenDeserializationFails_signupRecordRoutedToSignupDlt() throws Exception {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-deser-verifier", "false", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        try (Consumer<String, byte[]> dltConsumer =
                     new DefaultKafkaConsumerFactory<String, byte[]>(consumerProps).createConsumer()) {

            // Seek to the current end of the DLT so this test only observes records
            // produced DURING this method — avoids a false positive from the processing-
            // failure record that the previous test left in the DLT.
            var dltPartition = new TopicPartition("waitlist.signup.dlt", 0);
            dltConsumer.assign(List.of(dltPartition));
            dltConsumer.seekToEnd(List.of(dltPartition));
            dltConsumer.poll(Duration.ofMillis(200)); // materialise the seekToEnd

            // Produce raw bytes that cannot be deserialized as a SignupEvent JSON.
            // Bypasses KafkaTemplate's JsonSerializer entirely.
            Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
            try (var rawProducer = new KafkaProducer<String, byte[]>(producerProps)) {
                rawProducer.send(new ProducerRecord<>(
                        "waitlist.signup", null, "NOT_VALID_JSON_DESER_FAILURE".getBytes())).get();
            }

            // ErrorHandlingDeserializer catches the parse error and the DefaultErrorHandler
            // (with DeadLetterPublishingRecoverer) routes the record to the DLT after
            // exhausting retries.  Short intervals are configured in application-test.yaml
            // (100 ms initial, 3 retries → DLT within ~700 ms; 15 s timeout is ample).
            ConsumerRecords<String, byte[]> dltRecords =
                    KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(15));

            assertThat(dltRecords.isEmpty())
                    .as("Non-JSON record must be routed to waitlist.signup.dlt (BUG #4 regression)")
                    .isFalse();
        }
    }
}
