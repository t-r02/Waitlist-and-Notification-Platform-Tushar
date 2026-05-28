package com.waitlist.notification.service;

import com.waitlist.events.SignupEvent;
import com.waitlist.events.StatusChangedEvent;
import com.waitlist.notification.entity.NotificationLog;
import com.waitlist.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationLogRepository logRepo;

    @Transactional
    public void sendConfirmation(SignupEvent event) {
        if (logRepo.existsByEventId(event.eventId())) return;

        var ctx = new Context();
        ctx.setVariable("name", event.name() != null ? event.name() : "User");
        ctx.setVariable("referralCode", event.referralCode());
        String html = templateEngine.process("confirmation", ctx);

        send(event.email(), "Welcome to the Waitlist", html);

        var log = new NotificationLog();
        log.setEventId(event.eventId());
        log.setEventKey(event.eventId().toString());
        log.setEmail(event.email());
        log.setType("CONFIRMATION");
        logRepo.save(log);
    }

    @Transactional
    public void sendStatusUpdate(StatusChangedEvent event) {
        if (logRepo.existsByEventId(event.eventId())) return;

        var ctx = new Context();
        ctx.setVariable("status", event.newStatus());
        String html = templateEngine.process("invitation", ctx);

        send(event.email(), "Status Update: " + event.newStatus(), html);

        var log = new NotificationLog();
        log.setEventId(event.eventId());
        log.setEventKey(event.eventId().toString());
        log.setEmail(event.email());
        log.setType("STATUS_UPDATE");
        logRepo.save(log);
    }

    private void send(String to, String subject, String html) {
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            throw new RuntimeException("Email failed", e);
        }
    }
}
