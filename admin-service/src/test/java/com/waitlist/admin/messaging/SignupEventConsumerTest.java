package com.waitlist.admin.messaging;

import com.waitlist.admin.entity.WaitlistEntry;
import com.waitlist.admin.mapper.WaitlistEntryMapper;
import com.waitlist.admin.messaging.consumer.SignupEventConsumer;
import com.waitlist.admin.repository.WaitlistEntryRepository;
import com.waitlist.events.SignupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupEventConsumerTest {

    @Mock WaitlistEntryRepository repository;
    @Mock WaitlistEntryMapper     mapper;

    SignupEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SignupEventConsumer(repository, mapper);
    }

    private SignupEvent event(String email) {
        return new SignupEvent(UUID.randomUUID(), Instant.now(),
                1L, email, "Test User", "Acme", "ref12345", null);
    }

    @Test
    void handleSignup_newEmail_savesEntry() {
        var ev = event("alice@example.com");
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        var mapped = new WaitlistEntry();
        when(mapper.fromEvent(ev)).thenReturn(mapped);

        consumer.handleSignup(ev);

        verify(repository).save(mapped);
    }

    @Test
    void handleSignup_existingEmail_isIdempotentAndDoesNotSave() {
        var ev = event("bob@example.com");
        var existing = new WaitlistEntry();
        existing.setEmail("bob@example.com");
        when(repository.findByEmail("bob@example.com")).thenReturn(Optional.of(existing));

        consumer.handleSignup(ev);

        verify(repository, never()).save(any());
        verify(mapper, never()).fromEvent(any());
    }

    @Test
    void handleSignup_duplicateEvent_sameIdempotencyBehavior() {
        var ev = event("carol@example.com");

        // First call: not present → saves
        when(repository.findByEmail("carol@example.com")).thenReturn(Optional.empty());
        var mapped = new WaitlistEntry();
        when(mapper.fromEvent(ev)).thenReturn(mapped);
        consumer.handleSignup(ev);

        // Second call: now present → no-op
        when(repository.findByEmail("carol@example.com")).thenReturn(Optional.of(mapped));
        consumer.handleSignup(ev);

        // save called exactly once
        verify(repository, times(1)).save(any());
    }
}
