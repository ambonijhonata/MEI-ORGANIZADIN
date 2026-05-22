package com.api.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findAllByUserId(Long userId);

    List<Client> findByUserId(Long userId, Sort sort);

    @Query("""
            SELECT c
            FROM Client c
            WHERE c.user.id = :userId
              AND LOWER(c.name) LIKE CONCAT(LOWER(:name), '%')
            """)
    List<Client> findByUserIdAndNameStartsWithIgnoreCase(
            @Param("userId") Long userId,
            @Param("name") String name,
            Sort sort
    );

    Page<Client> findByUserId(Long userId, Pageable pageable);

    @Query("""
            SELECT c
            FROM Client c
            WHERE c.user.id = :userId
              AND LOWER(c.name) LIKE CONCAT(LOWER(:name), '%')
            """)
    Page<Client> findByUserIdAndNameStartsWithIgnoreCase(
            @Param("userId") Long userId,
            @Param("name") String name,
            Pageable pageable
    );

    Optional<Client> findByIdAndUserId(Long id, Long userId);

    Optional<Client> findByUserIdAndNormalizedName(Long userId, String normalizedName);

    boolean existsByUserIdAndNormalizedName(Long userId, String normalizedName);

    boolean existsByUserIdAndNormalizedNameAndIdNot(Long userId, String normalizedName, Long id);
}
