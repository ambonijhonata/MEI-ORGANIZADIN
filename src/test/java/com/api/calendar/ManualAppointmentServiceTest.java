package com.api.calendar;

import com.api.client.Client;
import com.api.client.ClientRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.servicecatalog.ServiceRepository;
import com.api.user.ApplicationUser;
import com.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualAppointmentServiceTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private ServiceDescriptionNormalizer normalizer;

    private ManualAppointmentService service;

    @BeforeEach
    void setUp() {
        service = new ManualAppointmentService(
                calendarEventRepository,
                clientRepository,
                serviceRepository,
                userRepository,
                normalizer
        );
    }

    @Test
    void shouldCreateManualAppointmentWithSnapshotsAndOrigin() {
        ApplicationUser user = new ApplicationUser("sub", "test@example.com", "Test");
        setId(user, 1L);
        Client client = new Client(user, "Maria", "maria");
        setId(client, 10L);
        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        setId(corte, 21L);
        Service escova = new Service(user, "Escova", "escova", new BigDecimal("80.00"));
        setId(escova, 22L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(clientRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(client));
        when(serviceRepository.findByUserIdAndIdIn(1L, List.of(21L, 22L))).thenReturn(List.of(corte, escova));
        when(normalizer.normalize("Maria - Corte + Escova")).thenReturn("maria - corte + escova");
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalendarEvent created = service.createManualAppointment(
                1L,
                new ManualAppointmentService.ManualAppointmentRequest(
                        10L,
                        LocalDate.of(2026, 5, 27),
                        LocalTime.of(14, 0),
                        LocalTime.of(14, 30),
                        List.of(21L, 22L)
                )
        );

        assertEquals(CalendarEventSource.MANUAL, created.getSource());
        assertNull(created.getGoogleEventId());
        assertEquals("Maria - Corte + Escova", created.getTitle());
        assertEquals("maria - corte + escova", created.getNormalizedTitle());
        assertEquals(client, created.getClient());
        assertTrue(created.isIdentified());
        assertEquals("Corte", created.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("130.00"), created.getServiceValueSnapshot());
        assertEquals(2, created.getServiceLinks().size());
        assertEquals(Instant.parse("2026-05-27T17:00:00Z"), created.getEventStart());
        assertEquals(Instant.parse("2026-05-27T17:30:00Z"), created.getEventEnd());
    }

    @Test
    void shouldRejectManualAppointmentWhenEndTimeIsNotAfterStartTime() {
        BusinessException error = assertThrows(BusinessException.class, () ->
                service.createManualAppointment(
                        1L,
                        new ManualAppointmentService.ManualAppointmentRequest(
                                10L,
                                LocalDate.of(2026, 5, 27),
                                LocalTime.of(14, 0),
                                LocalTime.of(14, 0),
                                List.of(21L)
                        )
                ));

        assertEquals("End time must be greater than start time", error.getMessage());
    }

    @Test
    void shouldRejectManualAppointmentWhenServiceDoesNotBelongToUser() {
        ApplicationUser user = new ApplicationUser("sub", "test@example.com", "Test");
        setId(user, 1L);
        Client client = new Client(user, "Maria", "maria");
        setId(client, 10L);
        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        setId(corte, 21L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(clientRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(client));
        when(serviceRepository.findByUserIdAndIdIn(1L, List.of(21L, 22L))).thenReturn(List.of(corte));

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class, () ->
                service.createManualAppointment(
                        1L,
                        new ManualAppointmentService.ManualAppointmentRequest(
                                10L,
                                LocalDate.of(2026, 5, 27),
                                LocalTime.of(14, 0),
                                LocalTime.of(14, 30),
                                List.of(21L, 22L)
                        )
                ));

        assertEquals("Service not found", error.getMessage());
    }

    private void setId(Object target, Long id) {
        try {
            String fieldName = target instanceof Service ? "serviceId"
                    : target instanceof ApplicationUser ? "userId"
                    : "id";
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set id for test setup", e);
        }
    }
}
