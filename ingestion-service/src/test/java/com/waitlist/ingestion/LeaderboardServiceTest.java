package com.waitlist.ingestion;

import com.waitlist.ingestion.dto.response.LeaderboardEntry;
import com.waitlist.ingestion.entity.ReferralPoints;
import com.waitlist.ingestion.mapper.LeaderboardMapper;
import com.waitlist.ingestion.repository.ReferralPointsRepository;
import com.waitlist.ingestion.service.LeaderboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ZSetOperations<String, String> zSetOps;
    @Mock ReferralPointsRepository pointsRepo;
    @Mock LeaderboardMapper leaderboardMapper;

    LeaderboardService service;

    @BeforeEach
    void setUp() {
        service = new LeaderboardService(redis, pointsRepo, leaderboardMapper);
    }

    private void stubZSetOps() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
    }

    // ── syncPoints — positive delta ────────────────────────────────────────────

    @Test
    void syncPoints_positiveTotal_zaddAllTimeAndZincrbyWeekly() {
        stubZSetOps();
        // incrementScore returning a positive score means no ZREM on weekly
        when(zSetOps.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(10.0);

        service.syncPoints("alice@example.com", 20, 10);

        verify(zSetOps).add(LeaderboardService.KEY_ALL, "alice@example.com", 20.0);
        String weekKey = LeaderboardService.currentWeekKey();
        verify(zSetOps).incrementScore(weekKey, "alice@example.com", 10.0);
        verify(redis).expire(weekKey, LeaderboardService.WEEKLY_TTL_DAYS, TimeUnit.DAYS);
        verify(zSetOps, never()).remove(weekKey, "alice@example.com");
    }

    // ── syncPoints — zero total removes member ────────────────────────────────

    @Test
    void syncPoints_zeroTotal_removesFromAllTimeLeaderboard() {
        stubZSetOps();
        when(zSetOps.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(0.0);

        service.syncPoints("alice@example.com", 0, -10);

        // Score hit zero — remove instead of ZADD
        verify(zSetOps).remove(LeaderboardService.KEY_ALL, "alice@example.com");
        verify(zSetOps, never()).add(eq(LeaderboardService.KEY_ALL), anyString(), anyDouble());
    }

    @Test
    void syncPoints_negativeWeeklyScore_removesFromWeeklyKey() {
        stubZSetOps();
        String weekKey = LeaderboardService.currentWeekKey();
        // Weekly score went negative (reversal on a previously-zero member)
        when(zSetOps.incrementScore(eq(weekKey), anyString(), anyDouble())).thenReturn(-5.0);

        service.syncPoints("alice@example.com", 10, -10);

        verify(zSetOps).remove(weekKey, "alice@example.com");
    }

    @Test
    void syncPoints_redisException_doesNotPropagate() {
        stubZSetOps();
        when(zSetOps.add(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis down"));

        // Must not throw — Redis failure must never roll back a committed DB write
        service.syncPoints("alice@example.com", 20, 10);
    }

    // ── getLeaderboard — zero-score entries filtered ───────────────────────────

    @Test
    void getLeaderboard_zeroScoreEntries_areExcluded() {
        stubZSetOps();
        when(zSetOps.reverseRangeWithScores(LeaderboardService.KEY_ALL, 0, 9))
                .thenReturn(Set.of(
                        ZSetOperations.TypedTuple.of("alice@example.com",  30.0),
                        ZSetOperations.TypedTuple.of("stale@example.com",   0.0)));

        var rp = new ReferralPoints();
        rp.setEmail("alice@example.com");
        rp.addPoints(30);
        when(pointsRepo.findAllByEmailIn(anyList())).thenReturn(List.of(rp));

        List<LeaderboardEntry> entries = service.getLeaderboard("all");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).email()).isEqualTo("alice@example.com");
    }

    // ── getLeaderboard — Redis hot ────────────────────────────────────────────

    @Test
    void getLeaderboard_all_returnsRedisRanking() {
        stubZSetOps();
        when(zSetOps.reverseRangeWithScores(LeaderboardService.KEY_ALL, 0, 9))
                .thenReturn(Set.of(ZSetOperations.TypedTuple.of("alice@example.com", 30.0)));

        var rp = new ReferralPoints();
        rp.setEmail("alice@example.com");
        rp.addPoints(30);
        when(pointsRepo.findAllByEmailIn(List.of("alice@example.com"))).thenReturn(List.of(rp));

        List<LeaderboardEntry> entries = service.getLeaderboard("all");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).email()).isEqualTo("alice@example.com");
        assertThat(entries.get(0).points()).isEqualTo(30);
    }

    @Test
    void getLeaderboard_flaggedUsers_areExcluded() {
        stubZSetOps();
        when(zSetOps.reverseRangeWithScores(LeaderboardService.KEY_ALL, 0, 9))
                .thenReturn(Set.of(ZSetOperations.TypedTuple.of("spammer@example.com", 100.0)));

        var rp = new ReferralPoints();
        rp.setEmail("spammer@example.com");
        rp.addPoints(100);
        rp.setFlagged(true);
        when(pointsRepo.findAllByEmailIn(List.of("spammer@example.com"))).thenReturn(List.of(rp));

        assertThat(service.getLeaderboard("all")).isEmpty();
    }

    // ── getLeaderboard — Redis cold (fallback to DB) ──────────────────────────

    @Test
    void getLeaderboard_redisCold_fallsBackToDb() {
        stubZSetOps();
        when(zSetOps.reverseRangeWithScores(LeaderboardService.KEY_ALL, 0, 9)).thenReturn(null);

        var rp = new ReferralPoints();
        rp.setEmail("bob@example.com");
        rp.addPoints(10);
        when(pointsRepo.findLeaderboard(any(Pageable.class))).thenReturn(List.of(rp));
        when(leaderboardMapper.toDto(rp)).thenReturn(new LeaderboardEntry("bob@example.com", 10, null));

        List<LeaderboardEntry> entries = service.getLeaderboard("all");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).email()).isEqualTo("bob@example.com");
        verify(pointsRepo).findLeaderboard(any(Pageable.class));
    }

    @Test
    void getLeaderboard_weekRedisCold_returnsEmpty() {
        stubZSetOps();
        when(zSetOps.reverseRangeWithScores(LeaderboardService.currentWeekKey(), 0, 9))
                .thenReturn(null);

        assertThat(service.getLeaderboard("week")).isEmpty();
        verify(pointsRepo, never()).findLeaderboard(any());
    }

    // ── bad window ─────────────────────────────────────────────────────────────

    @Test
    void getLeaderboard_unknownWindow_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getLeaderboard("monthly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monthly");
    }

    // ── reconcile ─────────────────────────────────────────────────────────────

    @Test
    void reconcile_writesUnflaggedNonZeroRowsAndPrunesZeroScoreMembers() {
        stubZSetOps();
        when(zSetOps.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        var good = new ReferralPoints();
        good.setEmail("good@example.com");
        good.addPoints(20);

        var flagged = new ReferralPoints();
        flagged.setEmail("bad@example.com");
        flagged.addPoints(50);
        flagged.setFlagged(true);

        var zeroPts = new ReferralPoints();
        zeroPts.setEmail("zero@example.com");

        when(pointsRepo.findAll()).thenReturn(List.of(good, flagged, zeroPts));

        service.reconcile();

        verify(zSetOps).add(LeaderboardService.KEY_ALL, "good@example.com", 20.0);
        verify(zSetOps, never()).add(anyString(), eq("bad@example.com"),  anyDouble());
        verify(zSetOps, never()).add(anyString(), eq("zero@example.com"), anyDouble());
        // Stale zero-score members are swept out after the ZADD pass
        verify(zSetOps).removeRangeByScore(eq(LeaderboardService.KEY_ALL),
                eq(Double.NEGATIVE_INFINITY), eq(0.0));
    }
}
