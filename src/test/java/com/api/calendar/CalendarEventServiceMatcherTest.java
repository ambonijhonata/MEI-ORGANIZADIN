package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.servicecatalog.ServiceRepository;
import com.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceMatcherTest {

    @Mock private ServiceRepository serviceRepository;
    @Mock private ServiceDescriptionNormalizer normalizer;

    private CalendarEventServiceMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new CalendarEventServiceMatcher(serviceRepository, normalizer);
    }

    @Test
    void shouldMatchServiceByNormalizedTitle() {
        User user = new User("sub", "email@test.com", "Name");
        Service service = new Service(user, "Corte de Cabelo", "corte de cabelo", new BigDecimal("50.00"));
        when(normalizer.normalize("Corte de Cabelo")).thenReturn("corte de cabelo");
        when(serviceRepository.findByUserIdAndNormalizedDescription(1L, "corte de cabelo"))
                .thenReturn(Optional.of(service));

        Optional<Service> result = matcher.matchService(1L, "Corte de Cabelo");

        assertTrue(result.isPresent());
        assertEquals("Corte de Cabelo", result.get().getDescription());
    }

    @Test
    void shouldReturnEmptyWhenNoMatch() {
        when(normalizer.normalize("Unknown Event")).thenReturn("unknown event");
        when(serviceRepository.findByUserIdAndNormalizedDescription(1L, "unknown event"))
                .thenReturn(Optional.empty());

        Optional<Service> result = matcher.matchService(1L, "Unknown Event");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullTitle() {
        Optional<Service> result = matcher.matchService(1L, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForBlankTitle() {
        Optional<Service> result = matcher.matchService(1L, "   ");
        assertTrue(result.isEmpty());
    }
}
