package com.waitlist.ingestion.dto.response;

/**
 * A single leaderboard row returned by GET /api/public/leaderboard.
 * {@code points} comes from the Redis sorted-set score;
 * {@code badge}  is joined from the DB ReferralPoints row.
 */
public record LeaderboardEntry(String email, int points, String badge) {}
