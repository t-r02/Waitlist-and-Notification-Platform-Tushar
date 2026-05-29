package com.waitlist.ingestion.service;

import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock WaitlistEntryRepository repository;
    @Mock SignupPersistenceService persistence;

    SignupService service;

    @BeforeEach
    void setUp() {
        service = new SignupService(repository, persistence);
    }

    private SignupRequest request(String email) {
        return request(email, "Test User");
    }

    private SignupRequest request(String email, String name) {
        var req = new SignupRequest();
        req.setEmail(email);
        req.setName(name);
        return req;
    }

    @Test
    void signup_normalizesEmailToLowerCaseTrimmed() {
        var req = request("  ALICE@Example.COM  ");
        when(persistence.doInsert(any(), eq("alice@example.com")))
                .thenReturn(new SignupResponse("Please verify your email", null, false, false));

        service.signup(req);

        verify(persistence).doInsert(any(), eq("alice@example.com"));
    }

    @Test
    void signup_success_returnsPersistenceResponse() {
        var expected = new SignupResponse("Please verify your email", null, false, false);
        when(persistence.doInsert(any(), any())).thenReturn(expected);

        SignupResponse result = service.signup(request("bob@example.com"));

        assertThat(result).isSameAs(expected);
        assertThat(result.isDuplicate()).isFalse();
    }

    @Test
    void signup_dataIntegrityViolation_sameName_returnsDuplicateWithCode() {
        when(persistence.doInsert(any(), eq("carol@example.com")))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var existing = new WaitlistEntry();
        existing.setEmail("carol@example.com");
        existing.setName("Test User");
        existing.setReferralCode("existref1");
        existing.setVerified(true);
        when(repository.findByEmail("carol@example.com")).thenReturn(Optional.of(existing));

        SignupResponse result = service.signup(request("CAROL@example.com"));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getReferralCode()).isEqualTo("existref1");
        assertThat(result.getMessage()).isEqualTo("Already registered");
    }

    @Test
    void signup_dataIntegrityViolation_differentName_throwsIllegalArgument() {
        // Wrong name on a verified account is rejected — caller gets an error, not a success card.
        when(persistence.doInsert(any(), eq("carol@example.com")))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var existing = new WaitlistEntry();
        existing.setEmail("carol@example.com");
        existing.setName("Test User");
        existing.setReferralCode("existref1");
        existing.setVerified(true);
        when(repository.findByEmail("carol@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.signup(request("carol@example.com", "Wrong Name")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void signup_dataIntegrityViolation_unverifiedEntry_returnsNullReferralCode() {
        when(persistence.doInsert(any(), eq("dave@example.com")))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var existing = new WaitlistEntry();
        existing.setEmail("dave@example.com");
        existing.setName("Test User");
        existing.setReferralCode("hiddenref");
        existing.setVerified(false);
        when(repository.findByEmail("dave@example.com")).thenReturn(Optional.of(existing));

        SignupResponse result = service.signup(request("dave@example.com"));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getReferralCode()).isNull();
    }

    @Test
    void signup_dataIntegrityViolation_whenEntryStillNotFound_rethrows() {
        when(persistence.doInsert(any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(repository.findByEmail("eve@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signup(request("eve@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
