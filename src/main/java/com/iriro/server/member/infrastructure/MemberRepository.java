package com.iriro.server.member.infrastructure;

import com.iriro.server.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByMemberNameKey(String memberNameKey);
}
