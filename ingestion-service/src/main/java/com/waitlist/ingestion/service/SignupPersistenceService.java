package com.waitlist.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waitlist.events.SignupEvent;
import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.entity.OutboxEntry;
import com.waitlist.ingestion.mapper.SignupMapper;
import com.waitlist.ingestion.repository.OutboxRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Owns the @Transactional boundary for signup so that SignupService can catch
 * DataIntegrityViolationException *outside* a poisoned transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupPersistenceService {

    private final WaitlistEntryRepository repository;
    private final OutboxRepository        outboxRepository;
    private final ObjectMapper            objectMapper;
    private final ReferralService         referralService;
    private final SignupMapper            signupMapper;

    @Transactional
    public SignupResponse doInsert(SignupRequest req, String normalized) {
        var existing = repository.findByEmail(normalized);
        if (existing.isPresent()) {
            return new SignupResponse("Already registered", existing.get().getReferralCode(), true);
        }

        // Mapper handles name / company / referredBy; computed fields are set below
        var entry = signupMapper.toEntity(req);
        entry.setEmail(normalized);
        entry.setReferralCode(UUID.randomUUID().toString().substring(0, 8));

        repository.save(entry);

        if (req.getReferralCode() != null) {
            try {
                referralService.trackReferral(req.getReferralCode(), normalized);
            } catch (DataIntegrityViolationException e) {
                // Duplicate referee: another request already recorded this referral.
                // trackReferral runs in REQUIRES_NEW so its transaction rolled back cleanly;
                // the outer signup transaction is unaffected.
                log.debug("Duplicate referral skipped [refereeEmail={}]", normalized);
            }
        }

        var event = new SignupEvent(
                UUID.randomUUID(),
                Instant.now(),
                entry.getId(),
                normalized,
                req.getName(),
                req.getCompany(),
                entry.getReferralCode(),
                req.getReferralCode()
        );

        var outbox = new OutboxEntry();
        outbox.setAggregateType("WaitlistEntry");
        outbox.setAggregateId(normalized);
        outbox.setEventType("SignupEvent");
        outbox.setPayload(serialize(event));
        outbox.setCreatedAt(OffsetDateTime.now());
        outboxRepository.save(outbox);

        return new SignupResponse("Successfully registered", entry.getReferralCode(), false);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
