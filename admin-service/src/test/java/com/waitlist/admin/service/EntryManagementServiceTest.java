package com.waitlist.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.waitlist.admin.entity.OutboxEntry;
import com.waitlist.admin.entity.Status;
import com.waitlist.admin.entity.StatusAuditLog;
import com.waitlist.admin.entity.WaitlistEntry;
import com.waitlist.admin.repository.OutboxRepository;
import com.waitlist.admin.repository.StatusAuditRepository;
import com.waitlist.admin.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntryManagementServiceTest {

    @Mock WaitlistEntryRepository entryRepo;
    @Mock StatusAuditRepository   auditRepo;
    @Mock OutboxRepository        outboxRepository;

    ObjectMapper objectMapper;
    StateMachineGuard guard;
    EntryManagementService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        guard = new StateMachineGuard();
        service = new EntryManagementService(entryRepo, auditRepo, outboxRepository, objectMapper, guard);
    }

    private WaitlistEntry pendingEntry(Long id) {
        var entry = new WaitlistEntry();
        entry.setId(id);
        entry.setIngestionId(id * 10);
        entry.setEmail("user" + id + "@example.com");
        entry.setStatus(Status.PENDING);
        return entry;
    }

    // ── updateStatus — success path ───────────────────────────────────────────

    @Test
    void updateStatus_validTransition_updatesStatusSavesAuditAndOutbox() {
        var entry = pendingEntry(1L);
        when(entryRepo.findById(1L)).thenReturn(Optional.of(entry));

        service.updateStatus(1L, Status.APPROVED, "admin");

        // Entry status updated
        assertThat(entry.getStatus()).isEqualTo(Status.APPROVED);
        verify(entryRepo).save(entry);

        // Audit log saved
        ArgumentCaptor<StatusAuditLog> auditCaptor = ArgumentCaptor.forClass(StatusAuditLog.class);
        verify(auditRepo).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getOldStatus()).isEqualTo(Status.PENDING);
        assertThat(auditCaptor.getValue().getNewStatus()).isEqualTo(Status.APPROVED);
        assertThat(auditCaptor.getValue().getChangedBy()).isEqualTo("admin");

        // Outbox event saved
        ArgumentCaptor<OutboxEntry> outboxCaptor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("StatusChangedEvent");
        assertThat(outboxCaptor.getValue().getPayload()).contains("APPROVED");
    }

    @Test
    void updateStatus_pendingToRejected_isAlsoValid() {
        var entry = pendingEntry(2L);
        when(entryRepo.findById(2L)).thenReturn(Optional.of(entry));

        service.updateStatus(2L, Status.REJECTED, "moderator");

        assertThat(entry.getStatus()).isEqualTo(Status.REJECTED);
        verify(entryRepo).save(entry);
    }

    // ── updateStatus — entry not found ────────────────────────────────────────

    @Test
    void updateStatus_entryNotFound_throwsNoSuchElementException() {
        when(entryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, Status.APPROVED, "admin"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    // ── updateStatus — invalid state transition ───────────────────────────────

    @Test
    void updateStatus_invalidTransition_throwsIllegalStateException() {
        var entry = pendingEntry(3L);
        entry.setStatus(Status.INVITED); // terminal state
        when(entryRepo.findById(3L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.updateStatus(3L, Status.APPROVED, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVITED")
                .hasMessageContaining("APPROVED");

        verify(entryRepo, never()).save(any());
        verify(auditRepo, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    // ── listAll and filterByStatus ────────────────────────────────────────────

    @Test
    void listAll_delegatesToRepository() {
        var entry = pendingEntry(4L);
        when(entryRepo.findAll()).thenReturn(java.util.List.of(entry));

        var result = service.listAll();

        assertThat(result).containsExactly(entry);
    }

    @Test
    void filterByStatus_delegatesToRepository() {
        var approved = pendingEntry(5L);
        approved.setStatus(Status.APPROVED);
        when(entryRepo.findByStatus(Status.APPROVED)).thenReturn(java.util.List.of(approved));

        var result = service.filterByStatus(Status.APPROVED);

        assertThat(result).containsExactly(approved);
    }
}
