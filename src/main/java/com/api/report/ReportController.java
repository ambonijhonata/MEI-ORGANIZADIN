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

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Relatórios Financeiros", description = "Relatórios de faturamento e fluxo de caixa")
public class ReportController {

    private final RevenueReportService revenueReportService;
    private final CashFlowReportService cashFlowReportService;

    public ReportController(RevenueReportService revenueReportService,
                             CashFlowReportService cashFlowReportService) {
        this.revenueReportService = revenueReportService;
        this.cashFlowReportService = cashFlowReportService;
    }

    @GetMapping("/revenue")
    @Operation(summary = "Relatório de faturamento", description = "Retorna o total consolidado de faturamento no período. Máximo 12 meses.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Relatório gerado"),
                    @ApiResponse(responseCode = "400", description = "Período inválido (excede 12 meses ou datas invertidas)")
            })
    public ResponseEntity<RevenueReportService.RevenueReport> getRevenueReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-01-01") LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-03-01") LocalDate endDate,
            @RequestParam(defaultValue = "ALL") PaymentScope paymentScope) {
        RevenueReportService.RevenueReport report = revenueReportService.generateReport(
                user.userId(), startDate, endDate, paymentScope);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/cashflow")
    @Operation(summary = "Relatório de fluxo de caixa", description = "Retorna série temporal de faturamento por dia. Máximo 7 dias.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Relatório gerado"),
                    @ApiResponse(responseCode = "400", description = "Período inválido (excede 7 dias ou datas invertidas)")
            })
    public ResponseEntity<CashFlowReportService.CashFlowReport> getCashFlowReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-03-10") LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Parameter(example = "2026-03-14") LocalDate endDate,
            @RequestParam(defaultValue = "ALL") PaymentScope paymentScope) {
        CashFlowReportService.CashFlowReport report = cashFlowReportService.generateReport(
                user.userId(), startDate, endDate, paymentScope);
        return ResponseEntity.ok(report);
    }
}
