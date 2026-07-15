package com.api.common;

import com.api.client.Client;
import com.api.client.ClientRepository;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceRepository;
import com.api.user.ApplicationUser;
import com.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true"
})
class RepositoryQuerySafetyDataJpaTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    private ApplicationUser user;

    @BeforeEach
    void setUp() {
        serviceRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(new ApplicationUser("sub-jpa-safety", "safety@test.com", "Safety Test"));
    }

    @Test
    void shouldTreatSqlInjectionLikeClientNameAsPlainText() {
        clientRepository.save(new Client(user, "Maria Silva", "maria silva"));

        var page = clientRepository.findByUserIdAndNameStartsWithIgnoreCase(
                user.getId(),
                "' OR 1=1 --",
                PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void shouldFilterClientsByPrefixOnlyCaseInsensitive() {
        clientRepository.save(new Client(user, "Angelo", "angelo"));
        clientRepository.save(new Client(user, "Eliane", "eliane"));
        clientRepository.save(new Client(user, "Silvana", "silvana"));
        clientRepository.save(new Client(user, "Angela", "angela"));

        var page = clientRepository.findByUserIdAndNameStartsWithIgnoreCase(
                user.getId(),
                "An",
                PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(page.getContent()).extracting(Client::getName)
                .containsExactly("Angelo", "Angela");

        var lowercasePage = clientRepository.findByUserIdAndNameStartsWithIgnoreCase(
                user.getId(),
                "an",
                PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(lowercasePage.getContent()).extracting(Client::getName)
                .containsExactly("Angelo", "Angela");

        var containsOnly = clientRepository.findByUserIdAndNameStartsWithIgnoreCase(
                user.getId(),
                "lv",
                PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(containsOnly.getTotalElements()).isZero();
        assertThat(containsOnly.getContent()).isEmpty();
    }

    @Test
    void shouldTreatSqlInjectionLikeServiceDescriptionAsPlainText() {
        serviceRepository.save(new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("80.00")));

        var page = serviceRepository.findByUserIdAndDescriptionContainingIgnoreCase(
                user.getId(),
                "'; DROP TABLE services; --",
                PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"))
        );

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }
}
