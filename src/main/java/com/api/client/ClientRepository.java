package com.api.client;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByUserId(Long userId, Sort sort);

    List<Client> findByUserIdAndNameContainingIgnoreCase(Long userId, String name, Sort sort);

    Optional<Client> findByIdAndUserId(Long id, Long userId);

    Optional<Client> findByUserIdAndNormalizedName(Long userId, String normalizedName);
}
