package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventReprocessorTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventServiceMatcher matcher;
    @Mock private EventTitleParser titleParser;
    @Mock private ServiceDescriptionNormalizer normalizer;

    private CalendarEventReprocessor reprocessor;

    @BeforeEach
    void setUp() {
        reprocessor = new CalendarEventReprocessor(calendarEventRepository, matcher, titleParser, normalizer);
    }

    @Test
    void shouldReprocessAndMatchUnidentifiedEvents() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "maria-corte+barba (pix)", "maria-corte+barba (pix)", Instant.now(), Instant.now());
        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service barba = new Service(user, "Barba", "barba", new BigDecimal("30.00"));

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", corte);
            put("barba", barba);
        }});
        when(titleParser.parse("maria-corte+barba (pix)"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte", "barba"), PaymentType.PIX));
        when(normalizer.normalize("corte")).thenReturn("corte");
        when(normalizer.normalize("barba")).thenReturn("barba");

        reprocessor.reprocessUnidentifiedEvents(1L);

        assertTrue(event.isIdentified());
        assertEquals(2, event.getServiceLinks().size());
        assertEquals("Corte", event.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("80.00"), event.getServiceValueSnapshot());
        assertEquals(PaymentType.PIX, event.getPaymentType());
        verify(calendarEventRepository).saveAll(List.of(event));
    }

    @Test
    void shouldNotMatchWhenNoServiceFound() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "maria-unknown (boleto)", "maria-unknown (boleto)", Instant.now(), Instant.now());

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>());
        when(titleParser.parse("maria-unknown (boleto)"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("unknown"), null));
        when(normalizer.normalize("unknown")).thenReturn("unknown");

        reprocessor.reprocessUnidentifiedEvents(1L);

        assertFalse(event.isIdentified());
        assertNull(event.getPaymentType());
        verify(calendarEventRepository).saveAll(List.of(event));
    }

    @Test
    void shouldHandleEmptyUnidentifiedList() {
        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of());
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>());

        reprocessor.reprocessUnidentifiedEvents(1L);

        verify(calendarEventRepository).saveAll(List.of());
    }
}
