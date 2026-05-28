package com.waitlist.notification.messaging;

import com.waitlist.events.SignupEvent;
import com.waitlist.events.StatusChangedEvent;
import com.waitlist.notification.messaging.consumer.SignupEventConsumer;
import com.waitlist.notification.messaging.consumer.StatusChangedConsumer;
import com.waitlist.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Tests both notification Kafka consumers: {@link SignupEventConsumer} and
 * {@link StatusChangedConsumer}. Both are thin wrappers that delegate to
 * {@link EmailService}, so the focus here is the delegation contract.
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock EmailService emailService;

    SignupEventConsumer   signupConsumer;
    StatusChangedConsumer statusConsumer;

    @BeforeEach
    void setUp() {
        signupConsumer = new SignupEventConsumer(emailService);
        statusConsumer = new StatusChangedConsumer(emailService);
    }

    private SignupEvent signup(String email) {
        return new SignupEvent(UUID.randomUUID(), Instant.now(),
                1L, email, "User", "Acme", "ref12345", null);
    }

    private StatusChangedEvent statusChanged(String email) {
        return new StatusChangedEvent(UUID.randomUUID(), Instant.now(),
                1L, email, "PENDING", "APPROVED", "admin");
    }

    // ── SignupEventConsumer ───────────────────────────────────────────────────

    @Test
    void handleSignup_delegatesToEmailService() {
        var event = signup("alice@example.com");

        signupConsumer.handleSignup(event);

        verify(emailService).sendConfirmation(event);
    }

    @Test
    void handleSignup_emailServiceThrows_propagatesException() {
        var event = signup("fail@example.com");
        doThrow(new RuntimeException("SMTP down")).when(emailService).sendConfirmation(event);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> signupConsumer.handleSignup(event))
                .isInstanceOf(RuntimeException.class);
    }

    // ── StatusChangedConsumer ─────────────────────────────────────────────────

    @Test
    void handleStatusChange_delegatesToEmailService() {
        var event = statusChanged("bob@example.com");

        statusConsumer.handleStatusChange(event);

        verify(emailService).sendStatusUpdate(event);
    }

    @Test
    void handleStatusChange_emailServiceThrows_propagatesException() {
        var event = statusChanged("fail@example.com");
        doThrow(new RuntimeException("Template error")).when(emailService).sendStatusUpdate(event);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> statusConsumer.handleStatusChange(event))
                .isInstanceOf(RuntimeException.class);
    }
}
