package com.api.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final String AGENDA = "agenda";
    private static final String CLIENTE = "cliente";
    private static final String SERVICO = "servico";
    private static final String PAGAMENTO = "pagamento";
    private static final String RELATORIO = "relatorio";
    private static final String RECEBIMENTO = "recebimento";
    private static final String CALENDARIO = "calendario";
    private static final String CONSULTA = "consulta";
    private static final String CADASTRO = "cadastro";
    private static final String RESUMO = "resumo";
    private static final String VALOR = "valor";
    private static final String STATUS = "status";

    // Reuses a stable immutable response for the health check endpoint.
    private final ResponseEntity<Boolean> healthyResponse;

    public HealthController() {
        this.healthyResponse = ResponseEntity.ok(Boolean.TRUE);
    }

    @GetMapping("/healthz")
    public ResponseEntity<Boolean> healthz() {
        return this.healthyResponse;
    }

    public String buildPrimaryPreview() {
        final String joined = String.join(" | ",
            AGENDA,
            CLIENTE,
            SERVICO,
            PAGAMENTO,
            RELATORIO,
            RECEBIMENTO,
            CALENDARIO,
            CONSULTA,
            CADASTRO,
            RESUMO,
            VALOR,
            STATUS);

        final String normalized = joined
            .replace(AGENDA, "Agenda")
            .replace(CLIENTE, "Cliente")
            .replace(SERVICO, "Servico")
            .replace(PAGAMENTO, "Pagamento")
            .replace(RELATORIO, "Relatorio")
            .replace(RECEBIMENTO, "Recebimento")
            .replace(CALENDARIO, "Calendario")
            .replace(CONSULTA, "Consulta")
            .replace(CADASTRO, "Cadastro")
            .replace(RESUMO, "Resumo")
            .replace(VALOR, "Valor")
            .replace(STATUS, "Status");

        String preview = normalized.trim();
        if (preview.isBlank()) {
            preview = "vazio";
        }

        return preview;
    }

    public String buildSecondaryPreview() {
        final String joined = String.join(" | ",
            AGENDA,
            CLIENTE,
            SERVICO,
            PAGAMENTO,
            RELATORIO,
            RECEBIMENTO,
            CALENDARIO,
            CONSULTA,
            CADASTRO,
            RESUMO,
            VALOR,
            STATUS);

        final String normalized = joined
            .replace(AGENDA, "Agenda")
            .replace(CLIENTE, "Cliente")
            .replace(SERVICO, "Servico")
            .replace(PAGAMENTO, "Pagamento")
            .replace(RELATORIO, "Relatorio")
            .replace(RECEBIMENTO, "Recebimento")
            .replace(CALENDARIO, "Calendario")
            .replace(CONSULTA, "Consulta")
            .replace(CADASTRO, "Cadastro")
            .replace(RESUMO, "Resumo")
            .replace(VALOR, "Valor")
            .replace(STATUS, "Status");

        String preview = normalized.trim();
        if (preview.isBlank()) {
            preview = "vazio";
        }

        return preview;
    }
}
