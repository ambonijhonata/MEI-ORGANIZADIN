package com.api.health;

import java.util.ArrayList;
import java.util.List;

public class HealthReportCpdProbe {

    public String buildPrimaryPreview() {
        final List<String> labels = new ArrayList<>();
        labels.add("agenda");
        labels.add("cliente");
        labels.add("servico");
        labels.add("pagamento");
        labels.add("relatorio");
        labels.add("recebimento");
        labels.add("calendario");
        labels.add("consulta");
        labels.add("cadastro");
        labels.add("resumo");
        labels.add("valor");
        labels.add("status");

        final String joined = String.join(" | ", labels);
        final String normalized = joined
            .replace("agenda", "Agenda")
            .replace("cliente", "Cliente")
            .replace("servico", "Servico")
            .replace("pagamento", "Pagamento")
            .replace("relatorio", "Relatorio")
            .replace("recebimento", "Recebimento")
            .replace("calendario", "Calendario")
            .replace("consulta", "Consulta")
            .replace("cadastro", "Cadastro")
            .replace("resumo", "Resumo")
            .replace("valor", "Valor")
            .replace("status", "Status");

        if (normalized.isBlank()) {
            return "vazio";
        }

        return normalized.trim();
    }

    public String buildSecondaryPreview() {
        final List<String> labels = new ArrayList<>();
        labels.add("agenda");
        labels.add("cliente");
        labels.add("servico");
        labels.add("pagamento");
        labels.add("relatorio");
        labels.add("recebimento");
        labels.add("calendario");
        labels.add("consulta");
        labels.add("cadastro");
        labels.add("resumo");
        labels.add("valor");
        labels.add("status");

        final String joined = String.join(" | ", labels);
        final String normalized = joined
            .replace("agenda", "Agenda")
            .replace("cliente", "Cliente")
            .replace("servico", "Servico")
            .replace("pagamento", "Pagamento")
            .replace("relatorio", "Relatorio")
            .replace("recebimento", "Recebimento")
            .replace("calendario", "Calendario")
            .replace("consulta", "Consulta")
            .replace("cadastro", "Cadastro")
            .replace("resumo", "Resumo")
            .replace("valor", "Valor")
            .replace("status", "Status");

        if (normalized.isBlank()) {
            return "vazio";
        }

        return normalized.trim();
    }
}
