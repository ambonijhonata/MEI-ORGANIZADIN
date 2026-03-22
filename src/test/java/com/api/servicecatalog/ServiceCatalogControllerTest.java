package com.api.servicecatalog;

import com.api.auth.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogControllerTest {

    @Mock
    private ServiceCatalogService serviceCatalogService;

    private ServiceCatalogController controller;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        controller = new ServiceCatalogController(serviceCatalogService);
        user = new AuthenticatedUser(1L, "google-sub", "test@example.com", "Test User");
    }

    @Test
    void shouldListServicesWithDefaultPagination() {
        // Given
        Pageable pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"));
        List<Service> services = List.of(
                createService(1L, "Service 1", new BigDecimal("10.00")),
                createService(2L, "Service 2", new BigDecimal("20.00"))
        );
        Page<Service> page = new PageImpl<>(services, pageable, 2);
        when(serviceCatalogService.listServices(1L, null, pageable)).thenReturn(page);

        // When
        ResponseEntity<ServiceCatalogController.PaginatedServiceResponse> response = controller.listServices(
                user, null, "id", "asc", 0, 25);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ServiceCatalogController.PaginatedServiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.items()).hasSize(2);
        assertThat(body.totalItems()).isEqualTo(2);
        assertThat(body.itemsPerPage()).isEqualTo(25);
        assertThat(body.totalPages()).isEqualTo(1);
        assertThat(body.pageIndex()).isEqualTo(0);
    }

    @Test
    void shouldListServicesWithCustomPagination() {
        // Given
        Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "description"));
        List<Service> services = List.of(
                createService(11L, "Service 11", new BigDecimal("110.00"))
        );
        Page<Service> page = new PageImpl<>(services, pageable, 25);
        when(serviceCatalogService.listServices(1L, null, pageable)).thenReturn(page);

        // When
        ResponseEntity<ServiceCatalogController.PaginatedServiceResponse> response = controller.listServices(
                user, null, "description", "desc", 1, 10);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ServiceCatalogController.PaginatedServiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.items()).hasSize(1);
        assertThat(body.totalItems()).isEqualTo(25);
        assertThat(body.itemsPerPage()).isEqualTo(10);
        assertThat(body.totalPages()).isEqualTo(3);
        assertThat(body.pageIndex()).isEqualTo(1);
    }

    @Test
    void shouldListServicesWithDescriptionFilter() {
        // Given
        Pageable pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"));
        List<Service> services = List.of(
                createService(1L, "Filtered Service", new BigDecimal("50.00"))
        );
        Page<Service> page = new PageImpl<>(services, pageable, 1);
        when(serviceCatalogService.listServices(1L, "filter", pageable)).thenReturn(page);

        // When
        ResponseEntity<ServiceCatalogController.PaginatedServiceResponse> response = controller.listServices(
                user, "filter", "id", "asc", 0, 25);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ServiceCatalogController.PaginatedServiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.items()).hasSize(1);
        assertThat(body.items().get(0).description()).isEqualTo("Filtered Service");
    }

    @Test
    void shouldListServicesWithEmptyResult() {
        // Given
        Pageable pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"));
        Page<Service> page = new PageImpl<>(List.of(), pageable, 0);
        when(serviceCatalogService.listServices(1L, null, pageable)).thenReturn(page);

        // When
        ResponseEntity<ServiceCatalogController.PaginatedServiceResponse> response = controller.listServices(
                user, null, "id", "asc", 0, 25);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ServiceCatalogController.PaginatedServiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.items()).isEmpty();
        assertThat(body.totalItems()).isEqualTo(0);
        assertThat(body.totalPages()).isEqualTo(0);
    }

    private Service createService(Long id, String description, BigDecimal value) {
        Service service = new Service(null, description, "normalized", value);
        // Use reflection or a test-specific constructor if available
        // For simplicity, we'll mock the getters
        Service mockService = org.mockito.Mockito.mock(Service.class);
        when(mockService.getId()).thenReturn(id);
        when(mockService.getDescription()).thenReturn(description);
        when(mockService.getValue()).thenReturn(value);
        when(mockService.getCreatedAt()).thenReturn(Instant.now());
        when(mockService.getUpdatedAt()).thenReturn(Instant.now());
        return mockService;
    }
}