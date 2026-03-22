package com.api.servicecatalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    Page<Service> findByUserId(Long userId, Pageable pageable);

    Page<Service> findByUserIdAndDescriptionContainingIgnoreCase(Long userId, String description, Pageable pageable);

    Optional<Service> findByIdAndUserId(Long id, Long userId);

    Optional<Service> findByUserIdAndNormalizedDescription(Long userId, String normalizedDescription);

    boolean existsByUserIdAndNormalizedDescription(Long userId, String normalizedDescription);

    List<Service> findByUserIdAndIdIn(Long userId, Collection<Long> ids);
}
