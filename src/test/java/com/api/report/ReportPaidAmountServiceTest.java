package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventPaymentTotal;
import com.api.calendar.PaymentType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportPaidAmountServiceTest {

    @Mock private CalendarEventPaymentRepository paymentRepo;

    private ReportPaidAmountService service;

    @BeforeEach
    void setUp() {
        service = new ReportPaidAmountService(paymentRepo);
    }

    @Test
    void shouldLoadNormalizedPaidAmountsByEventId() {
        when(paymentRepo.summarizePaidAmountsByEventIdIn(any()))
                .thenReturn(List.of(
                        new CalendarEventPaymentTotal(10L, new BigDecimal("20")),
                        new CalendarEventPaymentTotal(11L, new BigDecimal("5.5"))
                ));

        Map<Long, BigDecimal> paidByEvent = service.loadPaidAmountsByEventId(List.of(10L, 11L));

        assertEquals(Map.of(
                10L, new BigDecimal("20.00"),
                11L, new BigDecimal("5.50")
        ), paidByEvent);
    }

    @Test
    void shouldFallbackToServiceTotalWhenLegacyPaymentTypeExists() {
        CalendarEvent event = mock(CalendarEvent.class);
        when(event.getId()).thenReturn(10L);
        when(event.getPaymentType()).thenReturn(PaymentType.PIX);

        BigDecimal resolved = service.resolvePaidOnlyEventAmount(
                event,
                new BigDecimal("23.00"),
                Map.of()
        );

        assertEquals(new BigDecimal("23.00"), resolved);
    }

    @Test
    void shouldDistributeAmountProportionallyAndKeepRoundedTotal() {
        List<BigDecimal> allocated = service.distributeAmountProportionally(
                new BigDecimal("20.00"),
                List.of(new BigDecimal("10.00"), new BigDecimal("13.00"))
        );

        assertEquals(List.of(new BigDecimal("8.70"), new BigDecimal("11.30")), allocated);
    }

    @Test
    void shouldReturnZeroAllocationWhenWeightsAreMissingOrInvalid() {
        assertEquals(List.of(), service.distributeAmountProportionally(new BigDecimal("20.00"), List.of()));
        assertEquals(
                List.of(new BigDecimal("0.00"), new BigDecimal("0.00")),
                service.distributeAmountProportionally(new BigDecimal("20.00"), List.of(BigDecimal.ZERO, BigDecimal.ZERO))
        );
    }
}
