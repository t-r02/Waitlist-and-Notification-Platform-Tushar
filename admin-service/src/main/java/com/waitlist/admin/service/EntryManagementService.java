package com.waitlist.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waitlist.admin.entity.OutboxEntry;
import com.waitlist.admin.entity.Status;
import com.waitlist.admin.entity.StatusAuditLog;
import com.waitlist.admin.entity.WaitlistEntry;
import com.waitlist.admin.repository.OutboxRepository;
import com.waitlist.admin.repository.StatusAuditRepository;
import com.waitlist.admin.repository.WaitlistEntryRepository;
import com.waitlist.events.StatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntryManagementService {

    private final WaitlistEntryRepository entryRepo;
    private final StatusAuditRepository auditRepo;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final StateMachineGuard guard;

    public List<WaitlistEntry> listAll() {
        return entryRepo.findAll();
    }

    public List<WaitlistEntry> filterByStatus(Status status) {
        return entryRepo.findByStatus(status);
    }

    @Transactional
    public void updateStatus(Long id, Status newStatus, String adminUser) {
        var entry = entryRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Entry not found: " + id));

        if (!guard.isValidTransition(entry.getStatus(), newStatus)) {
            throw new IllegalStateException(
                    "Invalid transition: %s -> %s".formatted(entry.getStatus(), newStatus));
        }

        var log = new StatusAuditLog();
        log.setEntryId(id);
        log.setOldStatus(entry.getStatus());
        log.setNewStatus(newStatus);
        log.setChangedBy(adminUser);
        auditRepo.save(log);

        var event = new StatusChangedEvent(
                UUID.randomUUID(),
                Instant.now(),
                entry.getIngestionId(),
                entry.getEmail(),
                entry.getStatus().name(),
                newStatus.name(),
                adminUser
        );

        var outbox = new OutboxEntry();
        outbox.setAggregateType("WaitlistEntry");
        outbox.setAggregateId(entry.getEmail());
        outbox.setEventType("StatusChangedEvent");
        outbox.setPayload(serialize(event));
        outbox.setCreatedAt(OffsetDateTime.now());
        outboxRepository.save(outbox);

        entry.setStatus(newStatus);
        entry.setUpdatedAt(OffsetDateTime.now());
        entryRepo.save(entry);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
