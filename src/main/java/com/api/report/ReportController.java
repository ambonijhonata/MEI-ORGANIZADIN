package com.api.report;

import com.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@SuppressWarnings("PMD.LongVariable")
@RestController
@RequestMapping("/api/reports")
@Tag(name = "RelatÃ³rios Financeiros", description = "RelatÃ³rios de faturamento e fluxo de caixa")
public class ReportController {

    private final RevenueReportService revenueReportService;
    private final CashFlowReportService cashFlowReportService;
    private final PaymentMethodRevenueReportService paymentMethodRevenueReportService;

    public ReportController(final RevenueReportService revenueReportService,
                             final CashFlowReportService cashFlowReportService,
                             final PaymentMethodRevenueReportService paymentMethodRevenueReportService) {
        this.revenueReportService = revenueReportService;
        this.cashFlowReportService = cashFlowReportService;
        this.paymentMethodRevenueReportService = paymentMethodRevenueReportService;
    }

    @GetMapping("/revenue")
    @Operation(summary = "RelatÃ³rio de faturamento", description = "Retorna o total consolidado de faturamento no perÃ­odo. MÃ¡ximo 12 meses.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "RelatÃ³rio gerado"),
                    @ApiResponse(responseCode = "400", description = "PerÃ­odo invÃ¡lido (excede 12 meses ou datas invertidas)")
            })
    public ResponseEntity<RevenueReportService.RevenueReport> getRevenueReport(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-01-01") final LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-03-01") final LocalDate endDate,
            @RequestParam(defaultValue = "ALL") final PaymentScope paymentScope) {
        final RevenueReportService.RevenueReport report = revenueReportService.generateReport(
                user.userId(), startDate, endDate, paymentScope);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/cashflow")
    @Operation(summary = "RelatÃ³rio de fluxo de caixa", description = "Retorna sÃ©rie temporal de faturamento por dia. MÃ¡ximo 1 mÃªs.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "RelatÃ³rio gerado"),
                    @ApiResponse(responseCode = "400", description = "PerÃ­odo invÃ¡lido (excede 1 mÃªs ou datas invertidas)")
            })
    public ResponseEntity<CashFlowReportService.CashFlowReport> getCashFlowReport(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-03-10") final LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-03-14") final LocalDate endDate,
            @RequestParam(defaultValue = "ALL") final PaymentScope paymentScope) {
        final CashFlowReportService.CashFlowReport report = cashFlowReportService.generateReport(
                user.userId(), startDate, endDate, paymentScope);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/revenue-by-payment-method")
    @Operation(
            summary = "RelatÃ³rio de faturamento por mÃ©todo de pagamento",
            description = "Retorna o total consolidado por mÃ©todo de pagamento no perÃ­odo. Considera somente pagamentos persistidos em calendar_event_payments. MÃ¡ximo 12 meses.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "RelatÃ³rio gerado"),
                    @ApiResponse(responseCode = "400", description = "PerÃ­odo invÃ¡lido (excede 12 meses ou datas invertidas)")
            }
    )
    public ResponseEntity<PaymentMethodRevenueReportService.PaymentMethodRevenueReport> getRevenueByPaymentMethodReport(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-01-01") final LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-03-01") final LocalDate endDate) {
        final PaymentMethodRevenueReportService.PaymentMethodRevenueReport report =
                paymentMethodRevenueReportService.generateReport(user.userId(), startDate, endDate);
        return ResponseEntity.ok(report);
    }
}
