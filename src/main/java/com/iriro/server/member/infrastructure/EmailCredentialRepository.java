package com.iriro.server.member.infrastructure;

import com.iriro.server.member.domain.EmailCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailCredentialRepository  extends JpaRepository<EmailCredential, Long> {
    Optional<EmailCredential> findByMemberId(Long memberId);

}
