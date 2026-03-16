package com.api.servicecatalog;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    List<Service> findByUserId(Long userId, Sort sort);

    List<Service> findByUserIdAndDescriptionContainingIgnoreCase(Long userId, String description, Sort sort);

    Optional<Service> findByIdAndUserId(Long id, Long userId);

    Optional<Service> findByUserIdAndNormalizedDescription(Long userId, String normalizedDescription);

    boolean existsByUserIdAndNormalizedDescription(Long userId, String normalizedDescription);
}
