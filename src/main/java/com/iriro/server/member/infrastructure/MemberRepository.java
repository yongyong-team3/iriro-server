package com.iriro.server.member.infrastructure;

import com.iriro.server.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findById(Long id);

    // 정규화 닉네임 키 중복 여부 확인
    boolean existsByMemberNameKey(String memberNameKey);

    // 본인 제외 다른 회원의 닉네임 키 중복 여부 확인 (동일한 닉네임으로 재수정 시 409 방지)
    boolean existsByMemberNameKeyAndIdNot(String memberNameKey, Long id);
}