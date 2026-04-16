package com.api.calendar;

import com.api.common.BusinessException;
import com.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarPaymentServiceTest {

    @Mock
    private CalendarEventRepository calendarEventRepository;

    private CalendarPaymentService calendarPaymentService;

    @BeforeEach
    void setUp() {
        calendarPaymentService = new CalendarPaymentService(calendarEventRepository);
    }

    @Test
    void shouldAcceptCompositionWithinTotalServiceValue() {
        CalendarEvent event = createEventWithTotal(BigDecimal.valueOf(100));
        when(calendarEventRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(event));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CalendarEventPayment> saved = calendarPaymentService.upsertPayments(
                1L,
                8L,
                List.of(
                        new CalendarPaymentService.PaymentInput(PaymentType.DINHEIRO, BigDecimal.valueOf(40), false),
                        new CalendarPaymentService.PaymentInput(PaymentType.PIX, BigDecimal.valueOf(60), false)
                )
        );

        assertEquals(2, saved.size());
        assertEquals(BigDecimal.valueOf(100), saved.get(0).getAmount().add(saved.get(1).getAmount()));
    }

    @Test
    void shouldRejectMoreThanFourPayments() {
        CalendarEvent event = createEventWithTotal(BigDecimal.valueOf(200));
        when(calendarEventRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(event));

        assertThrows(
                BusinessException.class,
                () -> calendarPaymentService.upsertPayments(
                        1L,
                        9L,
                        List.of(
                                new CalendarPaymentService.PaymentInput(PaymentType.DINHEIRO, BigDecimal.valueOf(20), false),
                                new CalendarPaymentService.PaymentInput(PaymentType.PIX, BigDecimal.valueOf(20), false),
                                new CalendarPaymentService.PaymentInput(PaymentType.CREDITO, BigDecimal.valueOf(20), false),
                                new CalendarPaymentService.PaymentInput(PaymentType.DEBITO, BigDecimal.valueOf(20), false),
                                new CalendarPaymentService.PaymentInput(PaymentType.PIX, BigDecimal.valueOf(20), false)
                        )
                )
        );
    }

    @Test
    void shouldRejectMultipleTotalValueFlags() {
        CalendarEvent event = createEventWithTotal(BigDecimal.valueOf(120));
        when(calendarEventRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(event));

        assertThrows(
                BusinessException.class,
                () -> calendarPaymentService.upsertPayments(
                        1L,
                        10L,
                        List.of(
                                new CalendarPaymentService.PaymentInput(PaymentType.DINHEIRO, BigDecimal.valueOf(10), true),
                                new CalendarPaymentService.PaymentInput(PaymentType.PIX, BigDecimal.valueOf(10), true)
                        )
                )
        );
    }

    @Test
    void shouldNormalizeSingleTotalValuePaymentToEventTotal() {
        CalendarEvent event = createEventWithTotal(BigDecimal.valueOf(65));
        when(calendarEventRepository.findByIdAndUserId(11L, 1L)).thenReturn(Optional.of(event));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CalendarEventPayment> saved = calendarPaymentService.upsertPayments(
                1L,
                11L,
                List.of(
                        new CalendarPaymentService.PaymentInput(PaymentType.CREDITO, BigDecimal.ONE, true),
                        new CalendarPaymentService.PaymentInput(PaymentType.PIX, BigDecimal.valueOf(50), false)
                )
        );

        assertEquals(1, saved.size());
        assertEquals(PaymentType.CREDITO, saved.get(0).getPaymentType());
        assertEquals(BigDecimal.valueOf(65), saved.get(0).getAmount());
        assertTrue(saved.get(0).isValueTotal());
    }

    @Test
    void shouldRejectCompositionAboveServiceTotalWhenNoTotalValueSelected() {
        CalendarEvent event = createEventWithTotal(BigDecimal.valueOf(90));
        when(calendarEventRepository.findByIdAndUserId(12L, 1L)).thenReturn(Optional.of(event));

        assertThrows(
                BusinessException.class,
                () -> calendarPaymentService.upsertPayments(
                        1L,
                        12L,
                        List.of(
                                new CalendarPaymentService.PaymentInput(PaymentType.DINHEIRO, BigDecimal.valueOf(50), false),
                                new CalendarPaymentService.PaymentInput(PaymentType.CREDITO, BigDecimal.valueOf(45), false)
                        )
                )
        );
    }

    @Test
    void shouldClearAllPaymentsWhenPayloadIsEmpty() {
        CalendarEvent event = createEventWithTotal(BigDecimal.valueOf(90));
        event.replacePayments(
                List.of(
                        new CalendarEventPayment(
                                event,
                                PaymentType.DINHEIRO,
                                BigDecimal.valueOf(90),
                                true,
                                Instant.now()
                        )
                )
        );
        when(calendarEventRepository.findByIdAndUserId(13L, 1L)).thenReturn(Optional.of(event));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<CalendarEventPayment> saved = calendarPaymentService.upsertPayments(1L, 13L, List.of());

        assertTrue(saved.isEmpty());
        assertTrue(event.getPayments().isEmpty());
    }

    private CalendarEvent createEventWithTotal(BigDecimal totalValue) {
        User user = new User("sub", "user@test.com", "User");
        CalendarEvent event = new CalendarEvent(
                user,
                "google-id",
                "Titulo",
                "titulo",
                Instant.parse("2026-04-04T10:00:00Z"),
                Instant.parse("2026-04-04T11:00:00Z")
        );
        com.api.servicecatalog.Service service = new com.api.servicecatalog.Service(
                user,
                "Servico",
                "servico",
                totalValue
        );
        event.associateService(service);
        return event;
    }
}
