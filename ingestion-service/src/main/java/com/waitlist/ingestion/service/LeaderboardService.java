package com.waitlist.ingestion.service;

import com.waitlist.ingestion.dto.response.LeaderboardEntry;
import com.waitlist.ingestion.entity.ReferralPoints;
import com.waitlist.ingestion.mapper.LeaderboardMapper;
import com.waitlist.ingestion.repository.ReferralPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Owns the Redis sorted-set leaderboard.
 *
 * <p>Two sets are maintained:
 * <ul>
 *   <li>{@code leaderboard:all}  — all-time scores (score = total points)</li>
 *   <li>{@code leaderboard:week:<isoYear>-<isoWeek>} — weekly incremental
 *       scores, 35-day TTL so old keys expire automatically</li>
 * </ul>
 *
 * <p>On startup, {@link #reconcile()} rebuilds {@code leaderboard:all} from
 * the DB so a Redis flush never causes stale data.  Weekly keys cannot be
 * reconstructed from the DB (point-in-time information is not stored), so
 * they fill back up naturally as new approvals arrive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    public static final String KEY_ALL          = "leaderboard:all";
    public static final long   WEEKLY_TTL_DAYS  = 35;
    public static final int    TOP_N            = 10;

    private final StringRedisTemplate      redis;
    private final ReferralPointsRepository pointsRepo;
    private final LeaderboardMapper        leaderboardMapper;

    // ── Write path ────────────────────────────────────────────────────────────

    /**
     * Called after the DB transaction that changed points has committed.
     *
     * @param email    the referrer whose points changed
     * @param newTotal their new all-time total (used as the ZADD score)
     * @param delta    the signed increment (+10 award / -10 reversal), used
     *                 as ZINCRBY on the current week's key
     */
    public void syncPoints(String email, int newTotal, int delta) {
        try {
            // All-time: overwrite with current total (idempotent ZADD)
            redis.opsForZSet().add(KEY_ALL, email, newTotal);

            // Weekly: add the signed delta to this week's member score
            String weekKey = currentWeekKey();
            redis.opsForZSet().incrementScore(weekKey, email, delta);
            // Refresh TTL every write (EXPIRE is O(1))
            redis.expire(weekKey, WEEKLY_TTL_DAYS, TimeUnit.DAYS);

            log.debug("Leaderboard synced [email={}, total={}, delta={}]", email, newTotal, delta);
        } catch (Exception ex) {
            // Redis failure must never abort a successful DB write.
            // The startup reconcile will correct any drift.
            log.error("Redis leaderboard sync failed for {} — will reconcile on restart", email, ex);
        }
    }

    // ── Read path ─────────────────────────────────────────────────────────────

    /**
     * Returns the top-10 leaderboard for the requested window.
     *
     * @param window {@code "all"} for all-time, {@code "week"} for the
     *               current ISO week
     * @throws IllegalArgumentException if {@code window} is not recognised
     */
    public List<LeaderboardEntry> getLeaderboard(String window) {
        String key = resolveKey(window);          // throws if window unknown

        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            tuples = redis.opsForZSet().reverseRangeWithScores(key, 0, TOP_N - 1);
        } catch (Exception ex) {
            log.warn("Redis unavailable for leaderboard read — falling back to DB", ex);
            tuples = null;
        }

        // Redis cold / unavailable — fall back to DB for all-time only
        if (tuples == null || tuples.isEmpty()) {
            if ("all".equals(window)) {
                log.debug("Redis empty for leaderboard:all — using DB fallback");
                return pointsRepo.findLeaderboard(PageRequest.of(0, TOP_N)).stream()
                        .map(leaderboardMapper::toDto)
                        .toList();
            }
            log.debug("Redis empty for {} and weekly data cannot be rebuilt from DB", key);
            return Collections.emptyList();
        }

        // Batch-join back to DB for badge + flagged status
        List<String> emails = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .toList();

        Map<String, ReferralPoints> byEmail = pointsRepo.findAllByEmailIn(emails).stream()
                .collect(Collectors.toMap(ReferralPoints::getEmail, rp -> rp));

        return tuples.stream()
                .filter(t -> t.getValue() != null && t.getScore() != null)
                .filter(t -> {
                    // Exclude flagged accounts (consistent with the DB fallback query)
                    ReferralPoints rp = byEmail.get(t.getValue());
                    return rp == null || !rp.isFlagged();
                })
                .map(t -> {
                    String email = t.getValue();
                    int score    = t.getScore().intValue();
                    String badge = Optional.ofNullable(byEmail.get(email))
                            .map(ReferralPoints::getBadge)
                            .orElse(null);
                    return new LeaderboardEntry(email, score, badge);
                })
                .toList();
    }

    // ── Startup reconciliation ────────────────────────────────────────────────

    /**
     * Rebuilds {@code leaderboard:all} from the DB on startup so the sorted
     * set is correct even after a Redis flush.  Weekly keys cannot be rebuilt
     * (the DB stores totals, not per-week deltas) and will fill organically.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        log.info("Reconciling Redis leaderboard:all from DB...");
        try {
            List<ReferralPoints> rows = pointsRepo.findAll().stream()
                    .filter(rp -> !rp.isFlagged() && rp.getPoints() > 0)
                    .toList();

            if (rows.isEmpty()) {
                log.info("No referral-points rows to reconcile");
                return;
            }

            // ZADD each row; existing scores are overwritten (idempotent)
            for (ReferralPoints rp : rows) {
                redis.opsForZSet().add(KEY_ALL, rp.getEmail(), rp.getPoints());
            }
            log.info("Leaderboard reconciled [{} entries written to Redis]", rows.size());
        } catch (Exception ex) {
            // Non-fatal: Redis may be temporarily unreachable at startup
            log.error("Leaderboard reconciliation failed — will retry on next restart", ex);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String resolveKey(String window) {
        return switch (window) {
            case "all"  -> KEY_ALL;
            case "week" -> currentWeekKey();
            default -> throw new IllegalArgumentException(
                    "Unknown leaderboard window '%s' — valid values: all, week".formatted(window));
        };
    }

    public static String currentWeekKey() {
        LocalDate today = LocalDate.now();
        int year = today.get(IsoFields.WEEK_BASED_YEAR);
        int week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return "leaderboard:week:%d-%02d".formatted(year, week);
    }
}
