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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
        verify(pointsRepo, never()).findLeaderboard(any());
    }

    // ── syncPoints reversal — member removed when score hits zero ─────────────

    @Test
    void syncPoints_reversalDropsAllTimeToZero_removesFromAllTime() {
        stubZSetOps();
        String weekKey = LeaderboardService.currentWeekKey();
        when(zSetOps.incrementScore(eq(weekKey), anyString(), anyDouble())).thenReturn(0.0);

        // All-time total is now 0 — ZREM should replace ZADD
        service.syncPoints("referrer@example.com", 0, -10);

        verify(zSetOps).remove(LeaderboardService.KEY_ALL, "referrer@example.com");
        verify(zSetOps, never()).add(eq(LeaderboardService.KEY_ALL), anyString(), anyDouble());

        // Weekly score also hit 0 — member removed from weekly key too
        verify(zSetOps).remove(weekKey, "referrer@example.com");
    }

    @Test
    void syncPoints_positiveAllTimeButZeroWeekly_removesOnlyFromWeekly() {
        stubZSetOps();
        String weekKey = LeaderboardService.currentWeekKey();
        // Weekly reversed to zero, but all-time is still positive
        when(zSetOps.incrementScore(eq(weekKey), anyString(), anyDouble())).thenReturn(0.0);

        service.syncPoints("alice@example.com", 10, -10);

        // All-time: ZADD (score > 0)
        verify(zSetOps).add(LeaderboardService.KEY_ALL, "alice@example.com", 10.0);
        // Weekly: ZREM (score hit 0)
        verify(zSetOps).remove(weekKey, "alice@example.com");
    }

    // ── reconcile — empty DB still prunes Redis ───────────────────────────────

    @Test
    void reconcile_emptyPointsTable_stillPrunesRedis() {
        stubZSetOps();
        when(pointsRepo.findAll()).thenReturn(List.of());
        when(zSetOps.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        service.reconcile();

        // No ZADD calls (nothing to add), but the prune sweep must still run
        verify(zSetOps, never()).add(anyString(), anyString(), anyDouble());
        verify(zSetOps).removeRangeByScore(eq(LeaderboardService.KEY_ALL),
                eq(Double.NEGATIVE_INFINITY), eq(0.0));
    }
}
