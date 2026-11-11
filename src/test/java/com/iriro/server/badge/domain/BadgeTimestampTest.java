package com.iriro.server.badge.domain;

import com.iriro.server.badge.presentation.BadgeElementResponse;
import com.iriro.server.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeTimestampTest {
    @Test
    void storesUtcAndDisplaysKoreanOffset() {
        Member member = new Member("Badge", "badge");
        ReflectionTestUtils.setField(member, "id", 1L);
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);

        BadgeAward award = new BadgeAward(member, BadgeCode.WALK_LOVER);

        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1);
        assertThat(award.getEarnedAt()).isBetween(before, after);
        assertThat(BadgeElementResponse.of(BadgeCode.WALK_LOVER, 20, award.getEarnedAt()).getEarnedAt()
                .getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }
}
