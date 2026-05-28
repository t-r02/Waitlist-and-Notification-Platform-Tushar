package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    List<Referral> findByReferrerEmail(String email);
    Optional<Referral> findByRefereeEmail(String refereeEmail);
}
