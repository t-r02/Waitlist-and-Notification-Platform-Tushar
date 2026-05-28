package com.waitlist.ingestion.mapper;

import com.waitlist.ingestion.dto.response.LeaderboardEntry;
import com.waitlist.ingestion.entity.ReferralPoints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardMapperTest {

    LeaderboardMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new LeaderboardMapperImpl();
    }

    @Test
    void toDto_mapsAllFields() {
        var rp = new ReferralPoints();
        rp.setEmail("alice@example.com");
        rp.addPoints(25); // SILVER badge (>=20)

        LeaderboardEntry dto = mapper.toDto(rp);

        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.points()).isEqualTo(25);
        assertThat(dto.badge()).isEqualTo("SILVER");
    }

    @Test
    void toDto_bronzeBadge_whenPointsBetween5And19() {
        var rp = new ReferralPoints();
        rp.setEmail("bob@example.com");
        rp.addPoints(10);

        LeaderboardEntry dto = mapper.toDto(rp);

        assertThat(dto.badge()).isEqualTo("BRONZE");
    }

    @Test
    void toDto_goldBadge_whenPointsAtLeast50() {
        var rp = new ReferralPoints();
        rp.setEmail("topuser@example.com");
        rp.addPoints(50);

        LeaderboardEntry dto = mapper.toDto(rp);

        assertThat(dto.badge()).isEqualTo("GOLD");
    }

    @Test
    void toDto_nullBadge_whenPointsBelow5() {
        var rp = new ReferralPoints();
        rp.setEmail("newbie@example.com");
        rp.addPoints(3);

        LeaderboardEntry dto = mapper.toDto(rp);

        // updateBadge() only sets badge at >=5; below that badge remains null
        assertThat(dto.badge()).isNull();
    }

    @Test
    void toDto_zeroPts_badgeIsNull() {
        var rp = new ReferralPoints();
        rp.setEmail("zero@example.com");

        LeaderboardEntry dto = mapper.toDto(rp);

        assertThat(dto.points()).isZero();
        assertThat(dto.badge()).isNull();
    }
}
