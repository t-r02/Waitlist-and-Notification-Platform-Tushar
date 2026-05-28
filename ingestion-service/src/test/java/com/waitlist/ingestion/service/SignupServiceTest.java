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
        var req = new SignupRequest();
        req.setEmail(email);
        req.setName("Test User");
        return req;
    }

    @Test
    void signup_normalizesEmailToLowerCaseTrimmed() {
        var req = request("  ALICE@Example.COM  ");
        when(persistence.doInsert(any(), eq("alice@example.com")))
                .thenReturn(new SignupResponse("Successfully registered", "ref12345", false));

        service.signup(req);

        verify(persistence).doInsert(any(), eq("alice@example.com"));
    }

    @Test
    void signup_success_returnsPersistenceResponse() {
        var expected = new SignupResponse("Successfully registered", "abc12345", false);
        when(persistence.doInsert(any(), any())).thenReturn(expected);

        SignupResponse result = service.signup(request("bob@example.com"));

        assertThat(result).isSameAs(expected);
        assertThat(result.isDuplicate()).isFalse();
    }

    @Test
    void signup_dataIntegrityViolation_readsExistingEntryAndReturnsDuplicate() {
        // Race condition: persistence throws because of unique constraint violation
        when(persistence.doInsert(any(), eq("carol@example.com")))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var existing = new WaitlistEntry();
        existing.setEmail("carol@example.com");
        existing.setReferralCode("existref1");
        when(repository.findByEmail("carol@example.com")).thenReturn(Optional.of(existing));

        SignupResponse result = service.signup(request("CAROL@example.com"));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getReferralCode()).isEqualTo("existref1");
        assertThat(result.getMessage()).isEqualTo("Already registered");
    }

    @Test
    void signup_dataIntegrityViolation_whenEntryStillNotFound_rethrows() {
        // Extremely rare: unique violation but we still can't find the row
        when(persistence.doInsert(any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(repository.findByEmail("dave@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signup(request("dave@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
