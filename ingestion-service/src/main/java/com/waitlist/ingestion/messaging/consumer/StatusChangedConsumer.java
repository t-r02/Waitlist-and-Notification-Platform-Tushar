package com.waitlist.ingestion.messaging.consumer;

import com.waitlist.events.StatusChangedEvent;
import com.waitlist.ingestion.entity.ReferralEventLog;
import com.waitlist.ingestion.repository.ReferralEventLogRepository;
import com.waitlist.ingestion.repository.ReferralRepository;
import com.waitlist.ingestion.service.ReferralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Listens to {@code waitlist.status-changed} and awards (or reverses) referral
 * points when a waitlist entry is approved (or later rejected).
 *
 * <p>Idempotency: every processed event writes its {@code eventId} to
 * {@code referral_event_log} under a UNIQUE constraint.  A replayed message
 * with the same {@code eventId} is detected by {@link ReferralEventLogRepository#existsByEventId}
 * and silently dropped before any mutation occurs.
 *
 * <p>The method is {@code @Transactional} so that the DB writes (referral flag,
 * points, event-log row) and the subsequent Kafka offset commit are atomic from
 * the JPA perspective.  If the transaction rolls back, the Kafka listener
 * container keeps the offset un-committed and retries the message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusChangedConsumer {

    private final ReferralRepository     referralRepo;
    private final ReferralEventLogRepository eventLogRepo;
    private final ReferralService        referralService;

    @KafkaListener(topics = "waitlist.status-changed", groupId = "ingestion-referral")
    @Transactional
    public void onStatusChanged(StatusChangedEvent event) {
        log.info("StatusChanged received [eventId={}, email={}, {}->{}]",
                event.eventId(), event.email(), event.oldStatus(), event.newStatus());

        boolean isApproval = "APPROVED".equals(event.newStatus());
        boolean isReversal = "APPROVED".equals(event.oldStatus())
                          && "REJECTED".equals(event.newStatus());

        if (!isApproval && !isReversal) {
            log.debug("Ignoring non-referral status transition [eventId={}]", event.eventId());
            return;
        }

        // ── Idempotency guard ───────────────────────────────────────────────
        if (eventLogRepo.existsByEventId(event.eventId())) {
            log.debug("Duplicate event ignored [eventId={}]", event.eventId());
            return;
        }

        // ── Look up referral by referee e-mail ──────────────────────────────
        var referralOpt = referralRepo.findByRefereeEmail(event.email());
        String action = "NOOP";

        if (referralOpt.isEmpty()) {
            log.debug("No referral row for referee [email={}] — skipping points", event.email());
        } else {
            var referral = referralOpt.get();

            if (isApproval && !referral.isConverted()) {
                referral.setConverted(true);
                referralRepo.save(referral);
                referralService.awardPoints(referral.getReferrerEmail(), 10);
                action = "AWARD";
                log.info("Referral converted — 10 pts awarded [referrer={}, referee={}]",
                        referral.getReferrerEmail(), event.email());

            } else if (isReversal && referral.isConverted()) {
                referral.setConverted(false);
                referralRepo.save(referral);
                referralService.awardPoints(referral.getReferrerEmail(), -10);
                action = "REVERSE";
                log.info("Referral reverted — 10 pts reversed [referrer={}, referee={}]",
                        referral.getReferrerEmail(), event.email());

            } else {
                log.debug("Referral already in desired state — no-op [eventId={}, converted={}]",
                        event.eventId(), referral.isConverted());
            }
        }

        // ── Write idempotency record (always, even for NOOP) ────────────────
        persistEventLog(event.eventId(), action);
    }

    private void persistEventLog(UUID eventId, String action) {
        var entry = new ReferralEventLog();
        entry.setEventId(eventId);
        entry.setAction(action);
        eventLogRepo.save(entry);
    }
}
