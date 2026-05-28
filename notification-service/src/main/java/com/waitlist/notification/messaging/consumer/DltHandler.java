package com.waitlist.notification.messaging.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class DltHandler {

    @KafkaListener(topics = "waitlist.signup.dlt", groupId = "notification-dlt-service")
    public void handleSignupDlt(ConsumerRecord<String, Object> record) {
        logDlt("signup", record);
    }

    @KafkaListener(topics = "waitlist.status-changed.dlt", groupId = "notification-dlt-service")
    public void handleStatusDlt(ConsumerRecord<String, Object> record) {
        logDlt("status-changed", record);
    }

    private void logDlt(String eventType, ConsumerRecord<String, Object> record) {
        String originalTopic    = headerValue(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        String exceptionMessage = headerValue(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        String stacktrace       = headerValue(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE);

        log.error(
            "DLT [{}] originalTopic={} offset={} partition={} | error: {} | payload: {}",
            eventType,
            originalTopic,
            record.offset(),
            record.partition(),
            exceptionMessage,
            record.value()
        );

        if (stacktrace != null) {
            log.debug("DLT [{}] stacktrace:\n{}", eventType, stacktrace);
        }
    }

    private String headerValue(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
