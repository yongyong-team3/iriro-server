package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.domain.EmailCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailCredentialRepository extends JpaRepository<EmailCredential, Long> {

    boolean existsByEmailNormalized(String emailNormalized);

    Optional<EmailCredential> findByEmailNormalized(String emailNormalized);
}
