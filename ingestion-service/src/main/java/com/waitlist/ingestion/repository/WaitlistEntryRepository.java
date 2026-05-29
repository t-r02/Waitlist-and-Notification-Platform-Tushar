package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    Optional<WaitlistEntry> findByEmail(String email);
    Optional<WaitlistEntry> findByReferralCode(String referralCode);
    Optional<WaitlistEntry> findByVerificationToken(String verificationToken);
}
