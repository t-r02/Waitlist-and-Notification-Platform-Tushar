package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.entity.ReferralFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReferralFingerprintRepository extends JpaRepository<ReferralFingerprint, Long> {
    Optional<ReferralFingerprint> findByReferrerEmailAndIpHash(String referrerEmail, String ipHash);
}
