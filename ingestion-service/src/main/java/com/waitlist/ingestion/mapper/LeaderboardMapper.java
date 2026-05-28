package com.waitlist.ingestion.mapper;

import com.waitlist.ingestion.dto.response.LeaderboardEntry;
import com.waitlist.ingestion.entity.ReferralPoints;
import org.mapstruct.Mapper;

/**
 * Maps {@link ReferralPoints} entity rows to the {@link LeaderboardEntry} response record.
 *
 * <p>Used on the DB fallback path in LeaderboardService (when Redis is cold or unavailable).
 * The Redis-backed read path cannot use this mapper because it assembles each entry from
 * two independent sources — the sorted-set score and a batch-joined ReferralPoints row —
 * which requires bespoke assembly logic that MapStruct cannot express as a single-source mapping.
 */
@Mapper(componentModel = "spring")
public interface LeaderboardMapper {

    /**
     * Converts a {@link ReferralPoints} row into a leaderboard response record.
     * Field names match exactly: {@code email}, {@code points}, {@code badge}.
     */
    LeaderboardEntry toDto(ReferralPoints referralPoints);
}
