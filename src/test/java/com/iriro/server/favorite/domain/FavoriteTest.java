package com.iriro.server.favorite.domain;

import com.iriro.server.member.domain.Member;
import com.iriro.server.trail.domain.Route;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FavoriteTest {

    @Test
    @DisplayName("생성자로 회원/코스가 설정되고 createdAt 이 현재 시각으로 채워진다")
    void create() {
        Member member = mock(Member.class);
        Route route = mock(Route.class);

        LocalDateTime before = LocalDateTime.now();
        Favorite favorite = new Favorite(member, route);
        LocalDateTime after = LocalDateTime.now();

        assertThat(favorite.getMember()).isSameAs(member);
        assertThat(favorite.getRoute()).isSameAs(route);
        assertThat(favorite.getId()).isNull();
        assertThat(favorite.getCreatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("(member_id, route_id) 유니크 제약이 선언되어 있다")
    void uniqueConstraintDeclared() {
        Table table = Favorite.class.getAnnotation(Table.class);

        assertThat(table.name()).isEqualTo("favorites");
        assertThat(table.uniqueConstraints()).hasSize(1);

        UniqueConstraint uc = table.uniqueConstraints()[0];
        assertThat(uc.name()).isEqualTo("uk_favorites_member_route");
        assertThat(uc.columnNames()).containsExactly("member_id", "route_id");
    }
}