package com.api.servicecatalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
@SuppressWarnings("PMD.ShortVariable")

public interface ServiceRepository extends JpaRepository<Service, Long> {
    List<Service> findAllByUserId(Long userId);

    Page<Service> findByUserId(Long userId, Pageable pageable);

    Page<Service> findByUserIdAndDescriptionContainingIgnoreCase(Long userId, String description, Pageable pageable);

    Optional<Service> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT s FROM Service s WHERE s.user.id = :userId AND s.normalizedText = :normalizedText")
    Optional<Service> findByUserIdAndNormalizedDescription(@Param("userId") Long userId,
                                                           @Param("normalizedText") String normalizedText);

    @Query("SELECT COUNT(s) > 0 FROM Service s WHERE s.user.id = :userId AND s.normalizedText = :normalizedText")
    boolean existsByUserIdAndNormalizedDescription(@Param("userId") Long userId,
                                                   @Param("normalizedText") String normalizedText);

    Optional<Service> findByUserIdAndNormalizedText(Long userId, String normalizedText);

    boolean existsByUserIdAndNormalizedText(Long userId, String normalizedText);

    List<Service> findByUserIdAndIdIn(Long userId, Collection<Long> ids);
}
