package com.waitlist.admin.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Configuration
public class KafkaErrorHandlerConfig {

    @Value("${app.kafka.error-handler.initial-interval-ms:1000}")
    private long initialIntervalMs;

    @Value("${app.kafka.error-handler.max-interval-ms:10000}")
    private long maxIntervalMs;

    @Value("${app.kafka.error-handler.max-retries:3}")
    private int maxRetries;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> kafkaConsumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, kafkaConsumerFactory);
        factory.setCommonErrorHandler(defaultErrorHandler(kafkaTemplate));
        return factory;
    }

    private DefaultErrorHandler defaultErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(record.topic() + ".dlt", -1));

        var backOff = new ExponentialBackOff(initialIntervalMs, 2.0);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxElapsedTime(maxElapsedTime(initialIntervalMs, 2.0, maxIntervalMs, maxRetries));

        var handler = new DefaultErrorHandler(recoverer, backOff);

        handler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class,
                MethodArgumentNotValidException.class
        );

        return handler;
    }

    private long maxElapsedTime(long initial, double multiplier, long max, int retries) {
        long total = 0;
        long interval = initial;
        for (int i = 0; i < retries; i++) {
            total += interval;
            interval = Math.min((long) (interval * multiplier), max);
        }
        return total + 1;
    }
}
