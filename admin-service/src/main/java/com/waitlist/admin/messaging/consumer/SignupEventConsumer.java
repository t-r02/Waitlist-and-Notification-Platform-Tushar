package com.waitlist.admin.messaging.consumer;

import com.waitlist.admin.mapper.WaitlistEntryMapper;
import com.waitlist.admin.repository.WaitlistEntryRepository;
import com.waitlist.events.SignupEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SignupEventConsumer {

    private final WaitlistEntryRepository repository;
    private final WaitlistEntryMapper     mapper;

    @KafkaListener(topics = "waitlist.signup", groupId = "admin-service")
    public void handleSignup(SignupEvent event) {
        if (repository.findByEmail(event.email()).isPresent()) return;
        repository.save(mapper.fromEvent(event));
    }
}
