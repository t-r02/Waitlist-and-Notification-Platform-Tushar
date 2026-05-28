package com.waitlist.ingestion.service;

import com.waitlist.ingestion.dto.response.LeaderboardEntry;
import com.waitlist.ingestion.entity.ReferralPoints;
import com.waitlist.ingestion.mapper.LeaderboardMapper;
import com.waitlist.ingestion.repository.ReferralPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Covers weekly-leaderboard and utility methods not tested in {@link LeaderboardServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardServiceWeeklyTest {

    @Mock StringRedisTemplate                  redis;
    @Mock ZSetOperations<String, String>       zSetOps;
    @Mock ReferralPointsRepository             pointsRepo;
    @Mock LeaderboardMapper                    leaderboardMapper;

    LeaderboardService service;

    @BeforeEach
    void setUp() {
        service = new LeaderboardService(redis, pointsRepo, leaderboardMapper);
    }

    private void stubZSetOps() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
    }

    // ── currentWeekKey format ─────────────────────────────────────────────────

    @Test
    void currentWeekKey_hasExpectedFormat() {
        String key = LeaderboardService.currentWeekKey();
        LocalDate today = LocalDate.now();
        int year = today.get(IsoFields.WEEK_BASED_YEAR);
        int week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String expected = "leaderboard:week:%d-%02d".formatted(year, week);

        assertThat(key).isEqualTo(expected);
    }

    // ── weekly leaderboard — Redis hot ────────────────────────────────────────

    @Test
    void getLeaderboard_week_returnsRedisData() {
        stubZSetOps();
        String weekKey = LeaderboardService.currentWeekKey();
        when(zSetOps.reverseRangeWithScores(weekKey, 0, 9))
                .thenReturn(Set.of(ZSetOperations.TypedTuple.of("alice@example.com", 20.0)));

        var rp = new ReferralPoints();
        rp.setEmail("alice@example.com");
        rp.addPoints(20);
        when(pointsRepo.findAllByEmailIn(List.of("alice@example.com"))).thenReturn(List.of(rp));

        List<LeaderboardEntry> entries = service.getLeaderboard("week");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).email()).isEqualTo("alice@example.com");
        assertThat(entries.get(0).points()).isEqualTo(20);
    }

    @Test
    void getLeaderboard_week_flaggedUsersExcluded() {
        stubZSetOps();
        String weekKey = LeaderboardService.currentWeekKey();
        when(zSetOps.reverseRangeWithScores(weekKey, 0, 9))
                .thenReturn(Set.of(ZSetOperations.TypedTuple.of("spammer@example.com", 100.0)));

        var rp = new ReferralPoints();
        rp.setEmail("spammer@example.com");
        rp.addPoints(100);
        rp.setFlagged(true);
        when(pointsRepo.findAllByEmailIn(List.of("spammer@example.com"))).thenReturn(List.of(rp));

        assertThat(service.getLeaderboard("week")).isEmpty();
        // DB fallback is NOT called for weekly
        verify(pointsRepo, never()).findLeaderboard(any());
    }

    // ── syncPoints with negative delta ────────────────────────────────────────

    @Test
    void syncPoints_negativeDelta_zincrbyIsNegative() {
        stubZSetOps();

        service.syncPoints("referrer@example.com", 0, -10);

        verify(zSetOps).add(LeaderboardService.KEY_ALL, "referrer@example.com", 0.0);
        verify(zSetOps).incrementScore(
                LeaderboardService.currentWeekKey(), "referrer@example.com", -10.0);
    }

    // ── reconcile edge cases ──────────────────────────────────────────────────

    @Test
    void reconcile_emptyPointsTable_doesNotTouchRedis() {
        when(pointsRepo.findAll()).thenReturn(List.of());

        service.reconcile();

        verifyNoInteractions(redis);
    }
}
