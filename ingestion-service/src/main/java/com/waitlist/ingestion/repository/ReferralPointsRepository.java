package com.waitlist.ingestion.repository;

import com.waitlist.ingestion.entity.ReferralPoints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReferralPointsRepository extends JpaRepository<ReferralPoints, Long> {

    Optional<ReferralPoints> findByEmail(String email);

    /** Batch badge/flagged join used by the Redis-backed leaderboard read path. */
    List<ReferralPoints> findAllByEmailIn(Collection<String> emails);

    /** DB fallback when Redis is cold or unavailable (all-time window only). */
    @Query("SELECT r FROM ReferralPoints r WHERE r.flagged = false ORDER BY r.points DESC")
    List<ReferralPoints> findLeaderboard(Pageable pageable);
}
