package com.api.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByUserId(Long userId, Sort sort);

    List<Client> findByUserIdAndNameContainingIgnoreCase(Long userId, String name, Sort sort);

    Page<Client> findByUserId(Long userId, Pageable pageable);

    Page<Client> findByUserIdAndNameContainingIgnoreCase(Long userId, String name, Pageable pageable);

    Optional<Client> findByIdAndUserId(Long id, Long userId);

    Optional<Client> findByUserIdAndNormalizedName(Long userId, String normalizedName);
}
