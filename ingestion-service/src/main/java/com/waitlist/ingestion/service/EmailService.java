package com.waitlist.ingestion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service for sending transactional emails.
 * Used for email verification tokens and notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.verification.base-url}")
    private String verificationBaseUrl;

    /**
     * Send email verification token to user.
     */
    public void sendVerificationEmail(String toEmail, String token) {
        try {
            String verificationLink = verificationBaseUrl + "?token=" + token;
            String subject = "Verify your Waitlist email";
            String text = "Click the link below to verify your email:\n\n" +
                    verificationLink + "\n\n" +
                    "This link expires in 24 hours.";

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Verification email sent to [email={}]", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to [email={}]", toEmail, e);
            throw new RuntimeException("Email send failed", e);
        }
    }

}
