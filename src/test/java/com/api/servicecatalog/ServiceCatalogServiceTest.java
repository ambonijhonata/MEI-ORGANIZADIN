package com.api.servicecatalog;

import com.api.calendar.CalendarEventRepository;
import com.api.calendar.CalendarEventReprocessor;
import com.api.calendar.CalendarEventServiceLinkRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.user.User;
import com.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {

    @Mock private ServiceRepository serviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventServiceLinkRepository serviceLinkRepository;
    @Mock private ServiceDescriptionNormalizer normalizer;
    @Mock private CalendarEventReprocessor reprocessor;

    private ServiceCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ServiceCatalogService(serviceRepository, userRepository,
                calendarEventRepository, serviceLinkRepository, normalizer, reprocessor);
    }

    @Test
    void shouldCreateServiceSuccessfully() {
        User user = new User("sub", "email@test.com", "Name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(normalizer.normalize("Corte de Cabelo")).thenReturn("corte de cabelo");
        when(serviceRepository.existsByUserIdAndNormalizedDescription(1L, "corte de cabelo")).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenAnswer(inv -> inv.getArgument(0));

        Service result = service.createService(1L, "Corte de Cabelo", new BigDecimal("50.00"));

        assertNotNull(result);
        assertEquals("Corte de Cabelo", result.getDescription());
        assertEquals("corte de cabelo", result.getNormalizedDescription());
        verify(reprocessor).reprocessUnidentifiedEvents(1L);
    }

    @Test
    void shouldRejectDuplicateDescription() {
        User user = new User("sub", "email@test.com", "Name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(normalizer.normalize("Corte")).thenReturn("corte");
        when(serviceRepository.existsByUserIdAndNormalizedDescription(1L, "corte")).thenReturn(true);

        assertThrows(BusinessException.class, () ->
                service.createService(1L, "Corte", new BigDecimal("50.00")));
    }

    @Test
    void shouldListServicesForUser() {
        Pageable pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"));
        when(serviceRepository.findByUserId(1L, pageable)).thenReturn(Page.empty());
        Page<Service> result = service.listServices(1L, null, pageable);
        assertNotNull(result);
        verify(serviceRepository).findByUserId(1L, pageable);
    }

    @Test
    void shouldUpdateServiceAndReprocess() {
        User user = new User("sub", "email@test.com", "Name");
        Service existing = new Service(user, "Old", "old", new BigDecimal("30.00"));
        when(serviceRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(normalizer.normalize("New")).thenReturn("new");
        when(serviceRepository.findByUserIdAndNormalizedDescription(1L, "new")).thenReturn(Optional.empty());
        when(serviceRepository.save(existing)).thenReturn(existing);

        Service result = service.updateService(1L, 1L, "New", new BigDecimal("60.00"));

        assertEquals("New", result.getDescription());
        assertEquals(new BigDecimal("60.00"), result.getValue());
        verify(reprocessor).reprocessUnidentifiedEvents(1L);
    }

    @Test
    void shouldBlockDeleteWhenServiceHasLinkedEvents() {
        User user = new User("sub", "email@test.com", "Name");
        Service existing = new Service(user, "Test", "test", new BigDecimal("30.00"));
        when(serviceRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(calendarEventRepository.existsByServiceId(1L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.deleteService(1L, 1L));
        verify(serviceRepository, never()).delete(any());
    }

    @Test
    void shouldDeleteServiceWhenNoLinks() {
        User user = new User("sub", "email@test.com", "Name");
        Service existing = new Service(user, "Test", "test", new BigDecimal("30.00"));
        when(serviceRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(calendarEventRepository.existsByServiceId(1L)).thenReturn(false);

        service.deleteService(1L, 1L);
        verify(serviceRepository).delete(existing);
    }

    @Test
    void shouldThrowNotFoundForMissingService() {
        when(serviceRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getService(1L, 99L));
    }

    @Test
    void shouldDeleteManyServicesWithMixedResult() {
        Service deletable = serviceWithId(10L);
        Service linked = serviceWithId(11L);
        when(serviceRepository.findByUserIdAndIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(deletable, linked));
        when(serviceLinkRepository.existsByServiceId(10L)).thenReturn(false);
        when(calendarEventRepository.existsByServiceId(10L)).thenReturn(false);
        when(serviceLinkRepository.existsByServiceId(11L)).thenReturn(true);

        ServiceCatalogService.BulkDeleteResult result = service.deleteServices(1L, List.of(10L, 11L));

        assertEquals(1, result.deleted());
        assertEquals(1, result.hasLink());
        verify(serviceRepository).delete(deletable);
        verify(serviceRepository, never()).delete(linked);
    }

    @Test
    void shouldReturnOnlyHasLinkWhenAllServicesAreLinked() {
        Service linkedA = serviceWithId(20L);
        Service linkedB = serviceWithId(21L);
        when(serviceRepository.findByUserIdAndIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(linkedA, linkedB));
        when(serviceLinkRepository.existsByServiceId(20L)).thenReturn(true);
        when(serviceLinkRepository.existsByServiceId(21L)).thenReturn(true);

        ServiceCatalogService.BulkDeleteResult result = service.deleteServices(1L, List.of(20L, 21L));

        assertEquals(0, result.deleted());
        assertEquals(2, result.hasLink());
        verify(serviceRepository, never()).delete(any());
    }

    @Test
    void shouldIgnoreUnknownIdsAndDuplicatesInBulkDelete() {
        Service owned = serviceWithId(30L);
        when(serviceRepository.findByUserIdAndIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(owned));
        when(serviceLinkRepository.existsByServiceId(30L)).thenReturn(false);
        when(calendarEventRepository.existsByServiceId(30L)).thenReturn(false);

        ServiceCatalogService.BulkDeleteResult result = service.deleteServices(1L, Arrays.asList(30L, 30L, 999L, null));

        assertEquals(1, result.deleted());
        assertEquals(0, result.hasLink());
        verify(serviceRepository).delete(owned);
        verify(serviceLinkRepository, times(1)).existsByServiceId(30L);
    }

    @Test
    void shouldReturnZeroCountersForEmptyBulkDeleteList() {
        ServiceCatalogService.BulkDeleteResult result = service.deleteServices(1L, List.of());

        assertEquals(0, result.deleted());
        assertEquals(0, result.hasLink());
        verify(serviceRepository, never()).findByUserIdAndIdIn(anyLong(), anyCollection());
    }

    private Service serviceWithId(Long id) {
        Service service = mock(Service.class);
        when(service.getId()).thenReturn(id);
        return service;
    }
}
