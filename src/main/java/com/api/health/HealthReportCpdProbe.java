package com.api.health;

import java.util.ArrayList;
import java.util.List;

public class HealthReportCpdProbe {

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

    public String buildPrimaryPreview() {
        final List<String> labels = new ArrayList<>();
        labels.add(AGENDA);
        labels.add(CLIENTE);
        labels.add(SERVICO);
        labels.add(PAGAMENTO);
        labels.add(RELATORIO);
        labels.add(RECEBIMENTO);
        labels.add(CALENDARIO);
        labels.add(CONSULTA);
        labels.add(CADASTRO);
        labels.add(RESUMO);
        labels.add(VALOR);
        labels.add(STATUS);

        final String joined = String.join(" | ", labels);
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
        final List<String> labels = new ArrayList<>();
        labels.add(AGENDA);
        labels.add(CLIENTE);
        labels.add(SERVICO);
        labels.add(PAGAMENTO);
        labels.add(RELATORIO);
        labels.add(RECEBIMENTO);
        labels.add(CALENDARIO);
        labels.add(CONSULTA);
        labels.add(CADASTRO);
        labels.add(RESUMO);
        labels.add(VALOR);
        labels.add(STATUS);

        final String joined = String.join(" | ", labels);
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
