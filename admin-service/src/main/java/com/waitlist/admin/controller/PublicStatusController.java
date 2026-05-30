package com.waitlist.admin.controller;

import com.waitlist.admin.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Unauthenticated endpoint used by the public landing page to check whether
 * an email has been INVITED so the applicant can access early-access content.
 */
@RestController
@RequestMapping("/api/admin/public")
@RequiredArgsConstructor
public class PublicStatusController {

    private final WaitlistEntryRepository repository;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam String email) {
        var entry = repository.findByEmail(email.trim().toLowerCase());
        if (entry.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "email",  entry.get().getEmail(),
                "status", entry.get().getStatus().name()));
    }
}
