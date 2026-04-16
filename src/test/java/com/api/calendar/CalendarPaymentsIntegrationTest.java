package com.api.calendar;

import com.api.auth.AuthenticatedUser;
import com.api.auth.GoogleIdTokenAuthenticationFilter;
import com.api.auth.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = CalendarPaymentsIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@Import(CalendarPaymentsIntegrationTest.TestTransactionConfig.class)
class CalendarPaymentsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CalendarSyncService calendarSyncService;

    @MockBean
    private CalendarEventRepository calendarEventRepository;

    @MockBean
    private CalendarEventPaymentRepository calendarEventPaymentRepository;

    @MockBean
    private SyncStateRepository syncStateRepository;

    @MockBean
    private GoogleIdTokenAuthenticationFilter googleIdTokenAuthenticationFilter;

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
    })
    @Import({CalendarController.class, CalendarPaymentService.class, SecurityConfig.class})
    static class TestApplication {
    }

    @BeforeEach
    void forwardSecurityFilterChain() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(googleIdTokenAuthenticationFilter)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    void shouldReturnPersistedPaymentsWithoutLazyInitializationFailure() throws Exception {
        CalendarEvent event = mock(CalendarEvent.class);
        List<CalendarEventPayment> storedPayments = new ArrayList<>(List.of(
                new CalendarEventPayment(
                        event,
                        PaymentType.PIX,
                        new BigDecimal("40.00"),
                        false,
                        Instant.parse("2026-04-16T14:00:00Z")
                ),
                new CalendarEventPayment(
                        event,
                        PaymentType.DINHEIRO,
                        new BigDecimal("48.00"),
                        true,
                        Instant.parse("2026-04-16T14:01:00Z")
                )
        ));
        LazyAwarePaymentList lazyPayments = new LazyAwarePaymentList(storedPayments);

        when(event.getPayments()).thenReturn(lazyPayments);
        when(calendarEventRepository.findByIdAndUserId(19900L, 1L)).thenReturn(Optional.of(event));

        mockMvc.perform(get("/api/calendar/events/{eventId}/payments", 19900L)
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(19900))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].paymentType").value("PIX"))
                .andExpect(jsonPath("$.payments[0].amount").value(40.00))
                .andExpect(jsonPath("$.payments[0].valueTotal").value(false))
                .andExpect(jsonPath("$.payments[0].paidAt").value("2026-04-16T14:00:00Z"))
                .andExpect(jsonPath("$.payments[1].paymentType").value("DINHEIRO"))
                .andExpect(jsonPath("$.payments[1].amount").value(48.00))
                .andExpect(jsonPath("$.payments[1].valueTotal").value(true))
                .andExpect(jsonPath("$.payments[1].paidAt").value("2026-04-16T14:01:00Z"));
    }

    @Test
    void shouldReturnSavedPaymentsWithoutLazyInitializationFailure() throws Exception {
        CalendarEvent event = mock(CalendarEvent.class);
        List<CalendarEventPayment> storedPayments = new ArrayList<>();
        LazyAwarePaymentList lazyPayments = new LazyAwarePaymentList(storedPayments);

        when(event.getPayments()).thenReturn(lazyPayments);
        when(event.getServiceValueSnapshot()).thenReturn(new BigDecimal("88.00"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<CalendarEventPayment> newPayments = (List<CalendarEventPayment>) invocation.getArgument(0);
            storedPayments.clear();
            storedPayments.addAll(newPayments);
            return null;
        }).when(event).replacePayments(any());
        when(calendarEventRepository.findByIdAndUserId(19901L, 1L)).thenReturn(Optional.of(event));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/calendar/events/{eventId}/payments", 19901L)
                        .with(authenticatedUser())
                        .contentType("application/json")
                        .content("""
                                {"payments":[
                                  {"paymentType":"DINHEIRO","amount":40.00,"valueTotal":false},
                                  {"paymentType":"PIX","amount":48.00,"valueTotal":false}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(19901))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.payments[0].paymentType").value("DINHEIRO"))
                .andExpect(jsonPath("$.payments[0].amount").value(40.00))
                .andExpect(jsonPath("$.payments[0].valueTotal").value(false))
                .andExpect(jsonPath("$.payments[1].paymentType").value("PIX"))
                .andExpect(jsonPath("$.payments[1].amount").value(48.00))
                .andExpect(jsonPath("$.payments[1].valueTotal").value(false));
    }

    private RequestPostProcessor authenticatedUser() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    private static final class LazyAwarePaymentList extends AbstractList<CalendarEventPayment> {
        private final List<CalendarEventPayment> delegate;

        private LazyAwarePaymentList(List<CalendarEventPayment> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CalendarEventPayment get(int index) {
            ensureTransactionActive();
            return delegate.get(index);
        }

        @Override
        public int size() {
            ensureTransactionActive();
            return delegate.size();
        }

        private void ensureTransactionActive() {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new LazyInitializationException("Payments collection accessed outside transaction");
            }
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestTransactionConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }
}
