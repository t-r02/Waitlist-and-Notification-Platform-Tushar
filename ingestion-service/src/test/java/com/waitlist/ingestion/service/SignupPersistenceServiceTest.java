package com.waitlist.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.entity.OutboxEntry;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.mapper.SignupMapper;
import com.waitlist.ingestion.repository.OutboxRepository;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupPersistenceServiceTest {

    @Mock WaitlistEntryRepository repository;
    @Mock OutboxRepository        outboxRepository;
    @Mock ReferralService         referralService;
    @Mock SignupMapper             signupMapper;

    ObjectMapper objectMapper;
    SignupPersistenceService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new SignupPersistenceService(
                repository, outboxRepository, objectMapper, referralService, signupMapper);
    }

    private SignupRequest request(String email, String referralCode) {
        var req = new SignupRequest();
        req.setEmail(email);
        req.setName("Test User");
        req.setReferralCode(referralCode);
        return req;
    }

    private WaitlistEntry blankEntry() {
        return new WaitlistEntry();
    }

    @Test
    void doInsert_newEmail_savesEntryAndOutbox() {
        var req = request("alice@example.com", null);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        SignupResponse resp = service.doInsert(req, "alice@example.com");

        assertThat(resp.isDuplicate()).isFalse();
        assertThat(resp.getMessage()).isEqualTo("Successfully registered");

        verify(repository).save(any(WaitlistEntry.class));
        verify(outboxRepository).save(any(OutboxEntry.class));
    }

    @Test
    void doInsert_existingEmail_returnsDuplicateWithoutSaving() {
        var existing = new WaitlistEntry();
        existing.setEmail("bob@example.com");
        existing.setReferralCode("existcode");
        when(repository.findByEmail("bob@example.com")).thenReturn(Optional.of(existing));

        var req = request("bob@example.com", null);
        SignupResponse resp = service.doInsert(req, "bob@example.com");

        assertThat(resp.isDuplicate()).isTrue();
        assertThat(resp.getReferralCode()).isEqualTo("existcode");
        verify(repository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void doInsert_withReferralCode_callsTrackReferral() {
        var req = request("carol@example.com", "ref12345");
        when(repository.findByEmail("carol@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        service.doInsert(req, "carol@example.com");

        verify(referralService).trackReferral(eq("ref12345"), eq("carol@example.com"));
    }

    @Test
    void doInsert_withoutReferralCode_doesNotCallTrackReferral() {
        var req = request("dave@example.com", null);
        when(repository.findByEmail("dave@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        service.doInsert(req, "dave@example.com");

        verify(referralService, never()).trackReferral(any(), any());
    }

    @Test
    void doInsert_outboxEventContainsCorrectEmail() {
        var req = request("eve@example.com", null);
        when(repository.findByEmail("eve@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        service.doInsert(req, "eve@example.com");

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEntry outbox = captor.getValue();
        assertThat(outbox.getAggregateId()).isEqualTo("eve@example.com");
        assertThat(outbox.getEventType()).isEqualTo("SignupEvent");
        assertThat(outbox.getPayload()).contains("eve@example.com");
    }

    @Test
    void doInsert_assignsNonNullReferralCode() {
        var req = request("frank@example.com", null);
        when(repository.findByEmail("frank@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        SignupResponse resp = service.doInsert(req, "frank@example.com");

        assertThat(resp.getReferralCode()).isNotNull().hasSize(8);
    }
}
