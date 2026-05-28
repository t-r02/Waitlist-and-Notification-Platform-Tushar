package com.waitlist.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the error handler configuration is loaded correctly in the
 * notification-service application context.
 */
@SpringBootTest
@ActiveProfiles("test")
class KafkaErrorHandlerConfigTest {

    @Autowired ConcurrentKafkaListenerContainerFactory<?, ?> factory;

    // KafkaTemplate is needed by KafkaErrorHandlerConfig but unused in this test
    @MockBean(name = "kafkaTemplate")
    @SuppressWarnings("rawtypes")
    KafkaTemplate kafkaTemplate;

    // JavaMailSender health indicator is disabled in test profile; mock it to be safe
    @MockBean JavaMailSender mailSender;

    @Test
    void factoryIsCreatedSuccessfully() {
        assertThat(factory).isNotNull();
    }

    // ── maxElapsedTime utility (package-private via reflection) ──────────────

    /**
     * Tests the {@code maxElapsedTime} calculation used for the ExponentialBackOff cap.
     * For initial=100, multiplier=2, max=500, retries=3:
     * iteration 0: 100ms, iteration 1: 200ms, iteration 2: 400ms → total=700ms + 1
     */
    @Test
    void maxElapsedTime_3retries_sumsCorrectly() throws Exception {
        var config = new KafkaErrorHandlerConfig();
        var method = KafkaErrorHandlerConfig.class
                .getDeclaredMethod("maxElapsedTime", long.class, double.class, long.class, int.class);
        method.setAccessible(true);

        long result = (long) method.invoke(config, 100L, 2.0, 500L, 3);

        // 100 + 200 + 400 = 700, +1 = 701
        assertThat(result).isEqualTo(701L);
    }

    @Test
    void maxElapsedTime_capRespected_doesNotExceedMax() throws Exception {
        var config = new KafkaErrorHandlerConfig();
        var method = KafkaErrorHandlerConfig.class
                .getDeclaredMethod("maxElapsedTime", long.class, double.class, long.class, int.class);
        method.setAccessible(true);

        // initial=100, max=150, retries=5 → intervals: 100, 150, 150, 150, 150 = 700+1
        long result = (long) method.invoke(config, 100L, 2.0, 150L, 5);

        assertThat(result).isEqualTo(701L);
    }
}
