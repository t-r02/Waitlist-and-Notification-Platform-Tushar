package com.waitlist.ingestion;

import com.waitlist.events.StatusChangedEvent;
import com.waitlist.ingestion.entity.Referral;
import com.waitlist.ingestion.messaging.consumer.StatusChangedConsumer;
import com.waitlist.ingestion.repository.ReferralEventLogRepository;
import com.waitlist.ingestion.repository.ReferralRepository;
import com.waitlist.ingestion.service.ReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusChangedConsumerTest {

    @Mock ReferralRepository        referralRepo;
    @Mock ReferralEventLogRepository eventLogRepo;
    @Mock ReferralService           referralService;

    StatusChangedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new StatusChangedConsumer(referralRepo, eventLogRepo, referralService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Referral referral(String referrer, String referee, boolean converted) {
        var r = new Referral();
        r.setReferrerEmail(referrer);
        r.setRefereeEmail(referee);
        r.setConverted(converted);
        return r;
    }

    private StatusChangedEvent approveEvent(String email) {
        return new StatusChangedEvent(UUID.randomUUID(), Instant.now(),
                1L, email, "PENDING", "APPROVED", "admin");
    }

    private StatusChangedEvent rejectEvent(String email) {
        return new StatusChangedEvent(UUID.randomUUID(), Instant.now(),
                1L, email, "APPROVED", "REJECTED", "admin");
    }

    // ── APPROVE awards points ─────────────────────────────────────────────────

    @Test
    void approve_awardsPointsAndMarksConverted() {
        var ref = referral("bob@example.com", "carol@example.com", false);
        when(referralRepo.findByRefereeEmail("carol@example.com")).thenReturn(Optional.of(ref));
        when(eventLogRepo.existsByEventId(any())).thenReturn(false);

        consumer.onStatusChanged(approveEvent("carol@example.com"));

        assertThat(ref.isConverted()).isTrue();
        verify(referralRepo).save(ref);
        verify(referralService).awardPoints("bob@example.com", 10);
        verify(eventLogRepo).save(argThat(log -> "AWARD".equals(log.getAction())));
    }

    // ── Idempotency — replay of same event must be a no-op ───────────────────

    @Test
    void approve_replay_isIdempotent() {
        UUID eventId = UUID.randomUUID();
        var event = new StatusChangedEvent(eventId, Instant.now(),
                1L, "carol@example.com", "PENDING", "APPROVED", "admin");

        // Simulate: this eventId was already processed
        when(eventLogRepo.existsByEventId(eventId)).thenReturn(true);

        consumer.onStatusChanged(event);

        // Nothing should be touched
        verify(referralRepo, never()).findByRefereeEmail(any());
        verify(referralRepo, never()).save(any());
        verify(referralService, never()).awardPoints(any(), anyInt());
        verify(eventLogRepo, never()).save(any());
    }

    // ── REJECT after APPROVE reverses points ─────────────────────────────────

    @Test
    void rejectAfterApprove_reversesPoints() {
        var ref = referral("bob@example.com", "carol@example.com", true); // already converted
        when(referralRepo.findByRefereeEmail("carol@example.com")).thenReturn(Optional.of(ref));
        when(eventLogRepo.existsByEventId(any())).thenReturn(false);

        consumer.onStatusChanged(rejectEvent("carol@example.com"));

        assertThat(ref.isConverted()).isFalse();
        verify(referralRepo).save(ref);
        verify(referralService).awardPoints("bob@example.com", -10);
        verify(eventLogRepo).save(argThat(log -> "REVERSE".equals(log.getAction())));
    }

    // ── APPROVE → REJECT → APPROVE nets exactly one award ────────────────────

    @Test
    void approveRejectApprove_netsOneAward() {
        var ref = referral("bob@example.com", "carol@example.com", false);
        when(referralRepo.findByRefereeEmail("carol@example.com")).thenReturn(Optional.of(ref));
        when(eventLogRepo.existsByEventId(any())).thenReturn(false); // every event is new

        // Event 1: APPROVE
        consumer.onStatusChanged(approveEvent("carol@example.com")); // converted=true, +10
        // Event 2: REJECT (oldStatus=APPROVED)
        consumer.onStatusChanged(rejectEvent("carol@example.com"));  // converted=false, -10
        // Event 3: APPROVE again (new event ID)
        consumer.onStatusChanged(approveEvent("carol@example.com")); // converted=true, +10

        // Two +10 awards and one -10 reversal → net +10
        verify(referralService, times(2)).awardPoints("bob@example.com", 10);
        verify(referralService, times(1)).awardPoints("bob@example.com", -10);
        assertThat(ref.isConverted()).isTrue();
    }

    // ── No referral row → NOOP but event is still logged ─────────────────────

    @Test
    void approve_noReferralRow_logsNoopButDoesNotAwardPoints() {
        when(referralRepo.findByRefereeEmail("orphan@example.com")).thenReturn(Optional.empty());
        when(eventLogRepo.existsByEventId(any())).thenReturn(false);

        consumer.onStatusChanged(approveEvent("orphan@example.com"));

        verify(referralService, never()).awardPoints(any(), anyInt());
        verify(eventLogRepo).save(argThat(log -> "NOOP".equals(log.getAction())));
    }

    // ── Non-referral transitions are ignored entirely ─────────────────────────

    @Test
    void nonReferralTransition_isIgnoredCompletely() {
        // e.g. PENDING → REJECTED (never went through APPROVED)
        var event = new StatusChangedEvent(UUID.randomUUID(), Instant.now(),
                1L, "carol@example.com", "PENDING", "REJECTED", "admin");

        consumer.onStatusChanged(event);

        verifyNoInteractions(referralRepo, referralService, eventLogRepo);
    }
}
