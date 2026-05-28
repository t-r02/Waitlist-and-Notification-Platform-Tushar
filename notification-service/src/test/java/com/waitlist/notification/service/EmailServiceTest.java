package com.waitlist.notification.service;

import com.waitlist.events.SignupEvent;
import com.waitlist.events.StatusChangedEvent;
import com.waitlist.notification.entity.NotificationLog;
import com.waitlist.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender            mailSender;
    @Mock TemplateEngine            templateEngine;
    @Mock NotificationLogRepository logRepo;

    EmailService emailService;

    @BeforeEach
    void setUp() {
        // Provide a real MimeMessage (no actual SMTP connection needed) so that
        // MimeMessageHelper.setText / setTo / setSubject work correctly in tests.
        // lenient: idempotency tests return early before calling these, which is expected.
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        lenient().when(mailSender.createMimeMessage()).thenReturn(impl.createMimeMessage());
        lenient().when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>email</html>");

        emailService = new EmailService(mailSender, templateEngine, logRepo);
    }

    private SignupEvent signupEvent(UUID id, String email) {
        return new SignupEvent(id, Instant.now(), 1L, email, "Alice", "Acme", "ref12345", null);
    }

    private StatusChangedEvent statusEvent(UUID id, String email) {
        return new StatusChangedEvent(id, Instant.now(), 1L, email, "PENDING", "APPROVED", "admin");
    }

    // ── sendConfirmation ──────────────────────────────────────────────────────

    @Test
    void sendConfirmation_newEvent_sendsEmailAndSavesLog() {
        UUID eventId = UUID.randomUUID();
        when(logRepo.existsByEventId(eventId)).thenReturn(false);

        emailService.sendConfirmation(signupEvent(eventId, "alice@example.com"));

        verify(mailSender).send(any(jakarta.mail.internet.MimeMessage.class));
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(captor.getValue().getType()).isEqualTo("CONFIRMATION");
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void sendConfirmation_duplicateEventId_isIdempotentAndDoesNotSendEmail() {
        UUID eventId = UUID.randomUUID();
        when(logRepo.existsByEventId(eventId)).thenReturn(true);

        emailService.sendConfirmation(signupEvent(eventId, "dup@example.com"));

        verify(mailSender, never()).send(any(jakarta.mail.internet.MimeMessage.class));
        verify(logRepo, never()).save(any());
    }

    @Test
    void sendConfirmation_usesMemberNameInTemplate() {
        UUID eventId = UUID.randomUUID();
        when(logRepo.existsByEventId(eventId)).thenReturn(false);

        emailService.sendConfirmation(signupEvent(eventId, "bob@example.com"));

        verify(templateEngine).process(eq("confirmation"), argThat((Context ctx) ->
                "Alice".equals(ctx.getVariable("name"))));
    }

    @Test
    void sendConfirmation_nullName_usesDefaultUserLabel() {
        UUID eventId = UUID.randomUUID();
        when(logRepo.existsByEventId(eventId)).thenReturn(false);
        var event = new SignupEvent(eventId, Instant.now(), 1L,
                "noname@example.com", null, null, "ref12345", null);

        emailService.sendConfirmation(event);

        verify(templateEngine).process(eq("confirmation"), argThat((Context ctx) ->
                "User".equals(ctx.getVariable("name"))));
    }

    @Test
    void sendConfirmation_mailSenderFails_throwsRuntimeException() {
        UUID eventId = UUID.randomUUID();
        when(logRepo.existsByEventId(eventId)).thenReturn(false);
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(jakarta.mail.internet.MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendConfirmation(signupEvent(eventId, "fail@example.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email failed");
    }

    // ── sendStatusUpdate ──────────────────────────────────────────────────────

    @Test
    void sendStatusUpdate_newEvent_sendsEmailAndSavesLog() {
        UUID eventId = UUID.randomUUID();
        when(logRepo.existsByEventId(eventId)).thenReturn(false);

        emailService.sendStatusUpdate(statusEvent(eventId, "carol@example.com"));

        verify(mailSender).send(any(jakarta.mail.internet.MimeMessage.class));
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("STATUS_UPDATE");
    }

    @Test
    void sendStatusUpdate_duplicateEventId_isIdempotent() {
        UUID eventId = UUID.randomUUID();
        when(logRepo.existsByEventId(eventId)).thenReturn(true);

        emailService.sendStatusUpdate(statusEvent(eventId, "dave@example.com"));

        verify(mailSender, never()).send(any(jakarta.mail.internet.MimeMessage.class));
        verify(logRepo, never()).save(any());
    }

    @Test
    void sendStatusUpdate_usesNewStatusInTemplate() {
        UUID eventId = UUID.randomUUID();
        when(logRepo.existsByEventId(eventId)).thenReturn(false);

        emailService.sendStatusUpdate(statusEvent(eventId, "eve@example.com"));

        verify(templateEngine).process(eq("invitation"), argThat((Context ctx) ->
                "APPROVED".equals(ctx.getVariable("status"))));
    }
}
