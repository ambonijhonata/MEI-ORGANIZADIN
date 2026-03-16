package com.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthCredentialRepository extends JpaRepository<OAuthCredential, Long> {
    Optional<OAuthCredential> findByUserId(Long userId);
}
