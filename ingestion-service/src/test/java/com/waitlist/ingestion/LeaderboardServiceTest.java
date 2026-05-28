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
        // No shared stubs here — Mockito strict mode treats any unused stub as
        // an error. Each test configures only what it needs.
        service = new LeaderboardService(redis, pointsRepo, leaderboardMapper);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Stub redis.opsForZSet() in tests that reach the Redis code path. */
    private void stubZSetOps() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
    }

    // ── syncPoints ────────────────────────────────────────────────────────────

    @Test
    void syncPoints_zadd_allTime_and_zincrby_weekly() {
        stubZSetOps();

        service.syncPoints("alice@example.com", 20, 10);

        // All-time: absolute score ZADD
        verify(zSetOps).add(LeaderboardService.KEY_ALL, "alice@example.com", 20.0);

        // Weekly: signed increment
        String weekKey = LeaderboardService.currentWeekKey();
        verify(zSetOps).incrementScore(weekKey, "alice@example.com", 10.0);

        // TTL refreshed on every write
        verify(redis).expire(weekKey, LeaderboardService.WEEKLY_TTL_DAYS, TimeUnit.DAYS);
    }

    @Test
    void syncPoints_redisException_doesNotPropagate() {
        stubZSetOps();
        when(zSetOps.add(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis down"));

        // Must not throw — Redis failure must not roll back a committed DB write
        service.syncPoints("alice@example.com", 20, 10);
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
        // Mapper is used on the DB fallback path — stub it to return the expected DTO
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

        // Weekly cannot be rebuilt from DB — returns empty rather than a DB fallback
        assertThat(service.getLeaderboard("week")).isEmpty();
        verify(pointsRepo, never()).findLeaderboard(any());
    }

    // ── bad window — throws before touching Redis ─────────────────────────────

    @Test
    void getLeaderboard_unknownWindow_throwsIllegalArgument() {
        // No Redis stub needed — the exception fires before opsForZSet() is called
        assertThatThrownBy(() -> service.getLeaderboard("monthly"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monthly");
    }

    // ── reconcile ─────────────────────────────────────────────────────────────

    @Test
    void reconcile_writesUnflaggedNonZeroRowsToRedis() {
        stubZSetOps();

        var good = new ReferralPoints();
        good.setEmail("good@example.com");
        good.addPoints(20);

        var flagged = new ReferralPoints();
        flagged.setEmail("bad@example.com");
        flagged.addPoints(50);
        flagged.setFlagged(true);

        var zeroPts = new ReferralPoints();
        zeroPts.setEmail("zero@example.com"); // points = 0, excluded

        when(pointsRepo.findAll()).thenReturn(List.of(good, flagged, zeroPts));

        service.reconcile();

        verify(zSetOps).add(LeaderboardService.KEY_ALL, "good@example.com", 20.0);
        verify(zSetOps, never()).add(anyString(), eq("bad@example.com"),  anyDouble());
        verify(zSetOps, never()).add(anyString(), eq("zero@example.com"), anyDouble());
    }
}
