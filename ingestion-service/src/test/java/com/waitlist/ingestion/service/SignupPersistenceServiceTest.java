package com.waitlist.ingestion.service;

import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.mapper.SignupMapper;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupPersistenceServiceTest {

    @Mock WaitlistEntryRepository repository;
    @Mock ReferralService         referralService;
    @Mock SignupMapper             signupMapper;
    @Mock EmailService            emailService;

    SignupPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new SignupPersistenceService(
                repository, referralService, signupMapper, emailService);
    }

    private SignupRequest request(String email, String referralCode) {
        var req = new SignupRequest();
        req.setEmail(email);
        req.setName("Test User");
        req.setReferralCode(referralCode);
        return req;
    }

    private SignupRequest request(String email, String referralCode, String name) {
        var req = request(email, referralCode);
        req.setName(name);
        return req;
    }

    private WaitlistEntry blankEntry() {
        return new WaitlistEntry();
    }

    @Test
    void doInsert_newEmail_savesEntryWithoutOutbox() {
        // Outbox / SignupEvent is now deferred to VerificationController.verify()
        var req = request("alice@example.com", null);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        SignupResponse resp = service.doInsert(req, "alice@example.com");

        assertThat(resp.isDuplicate()).isFalse();
        assertThat(resp.isVerified()).isFalse();
        assertThat(resp.getReferralCode()).isNull();

        verify(repository).save(any(WaitlistEntry.class));
        verify(emailService).sendVerificationEmail(eq("alice@example.com"), any(String.class));
    }

    @Test
    void doInsert_existingVerifiedEmail_sameName_returnsDuplicateWithReferralCode() {
        var existing = new WaitlistEntry();
        existing.setEmail("bob@example.com");
        existing.setName("Bob Smith");
        existing.setReferralCode("existcode");
        existing.setVerified(true);
        when(repository.findByEmail("bob@example.com")).thenReturn(Optional.of(existing));

        var req = request("bob@example.com", null);
        req.setName("Bob Smith");
        SignupResponse resp = service.doInsert(req, "bob@example.com");

        assertThat(resp.isDuplicate()).isTrue();
        assertThat(resp.isVerified()).isTrue();
        assertThat(resp.getReferralCode()).isEqualTo("existcode");
        verify(repository, never()).save(any());
    }

    @Test
    void doInsert_existingVerifiedEmail_differentName_throwsIllegalArgument() {
        // A verified account requires the name to match; wrong name is rejected with
        // IllegalArgumentException (→ HTTP 400) so the frontend shows an error,
        // not an "already registered" success card.
        var existing = new WaitlistEntry();
        existing.setEmail("bob@example.com");
        existing.setName("Bob Smith");
        existing.setReferralCode("existcode");
        existing.setVerified(true);
        when(repository.findByEmail("bob@example.com")).thenReturn(Optional.of(existing));

        var req = request("bob@example.com", null);
        req.setName("Robert Smith");

        assertThatThrownBy(() -> service.doInsert(req, "bob@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        verify(repository, never()).save(any());
    }

    @Test
    void doInsert_existingUnverifiedEmail_returnsDuplicateWithNullCode() {
        var existing = new WaitlistEntry();
        existing.setEmail("carol@example.com");
        existing.setReferralCode("hiddencode");
        existing.setVerified(false);
        when(repository.findByEmail("carol@example.com")).thenReturn(Optional.of(existing));

        SignupResponse resp = service.doInsert(request("carol@example.com", null), "carol@example.com");

        assertThat(resp.isDuplicate()).isTrue();
        assertThat(resp.isVerified()).isFalse();
        assertThat(resp.getReferralCode()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void doInsert_withReferralCode_callsTrackReferral() {
        var req = request("dave@example.com", "ref12345");
        when(repository.findByEmail("dave@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        service.doInsert(req, "dave@example.com");

        verify(referralService).trackReferral(eq("ref12345"), eq("dave@example.com"));
    }

    @Test
    void doInsert_withoutReferralCode_doesNotCallTrackReferral() {
        var req = request("eve@example.com", null);
        when(repository.findByEmail("eve@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        service.doInsert(req, "eve@example.com");

        verify(referralService, never()).trackReferral(any(), any());
    }

    @Test
    void doInsert_assignsNonNullReferralCodeOnEntity() {
        var req = request("frank@example.com", null);
        when(repository.findByEmail("frank@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        service.doInsert(req, "frank@example.com");

        ArgumentCaptor<WaitlistEntry> captor = ArgumentCaptor.forClass(WaitlistEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReferralCode()).isNotNull().hasSize(8);
    }

    @Test
    void doInsert_setsVerificationToken() {
        var req = request("grace@example.com", null);
        when(repository.findByEmail("grace@example.com")).thenReturn(Optional.empty());
        when(signupMapper.toEntity(req)).thenReturn(blankEntry());

        service.doInsert(req, "grace@example.com");

        ArgumentCaptor<WaitlistEntry> captor = ArgumentCaptor.forClass(WaitlistEntry.class);
        verify(repository).save(captor.capture());
        WaitlistEntry saved = captor.getValue();
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getVerificationToken()).isNotNull().hasSize(64);
        assertThat(saved.getVerificationTokenExpiresAt()).isNotNull();
    }
}
