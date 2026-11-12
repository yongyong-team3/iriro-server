package com.iriro.server.member.infrastructure;

import com.iriro.server.member.domain.MemberProfilePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberProfilePhotoRepository extends JpaRepository<MemberProfilePhoto, Long> {
}
