## Context

O endpoint `POST /api/calendar/sync` atualmente opera em dois modos: incremental com `syncToken` e full sync quando nao ha token (com fallback para full em expiracao de token). A mudanca adiciona `startDate` opcional para sincronizacao manual por recorte temporal, mantendo contrato HTTP e sem regressao de comportamento quando o parametro nao e enviado.

O fluxo envolve multiplas camadas (`CalendarController`, `CalendarSyncService`, `GoogleCalendarClient`) e afeta regras de estado de sincronizacao (`SyncState`), com risco de impacto no incremental existente caso o novo caminho reutilize gravação de token de forma indevida.

## Goals / Non-Goals

**Goals:**
- Permitir `POST /api/calendar/sync?startDate=yyyy-mm-dd` como parametro opcional.
- Garantir que, com `startDate`, a busca no Google Calendar ocorra a partir da data informada.
- Preservar integralmente o comportamento atual quando `startDate` estiver ausente.
- Preservar o contrato de sucesso/erro do endpoint.
- Garantir que sync com `startDate` nao sobrescreva o `syncToken` incremental principal.

**Non-Goals:**
- Alterar payload de resposta do endpoint.
- Alterar semantica de erro existente (`INTEGRATION_REVOKED`, `GOOGLE_API_FORBIDDEN`).
- Redesenhar estrategia de sync incremental global.
- Exigir alteracao no cliente atual para continuar funcionando.

## Decisions

### 1) Parametro opcional no controller com fallback para fluxo atual
Decisao:
- Adicionar `startDate` opcional no `CalendarController.triggerSync`.
- Encaminhar para sobrecarga do service: sem parametro segue fluxo atual; com parametro usa fluxo filtrado.

Rationale:
- Minimiza impacto no contrato e em chamadas existentes.
- Mantem explicito o roteamento por intencao de uso.

Alternativas consideradas:
- Substituir metodo atual por assinatura obrigatoria: rejeitada por risco de quebra.
- Ler parametro apenas em camada inferior: reduz clareza da API.

### 2) Caminho de sync filtrado separado do fluxo incremental principal
Decisao:
- Introduzir caminho dedicado para `startDate` no service, com fetch filtrado por data.
- Esse caminho nao atualiza `syncToken` persistido em `SyncState`.

Rationale:
- Evita contaminar estado incremental global com execucao ad-hoc por data.
- Preserva previsibilidade do fluxo incremental sem `startDate`.

Alternativas consideradas:
- Reusar fluxo incremental e gravar novo token: rejeitada por alto risco de regressao funcional.
- Manter token separado em banco para sync filtrado: adia complexidade desnecessaria para esta entrega.

### 3) Extensao do cliente Google Calendar para suportar filtro de data inicial
Decisao:
- Evoluir `GoogleCalendarClient` para aceitar data inicial opcional e configurar `timeMin` apenas quando aplicavel.
- Manter logica existente de paginacao e tratamento de erros.

Rationale:
- Reaproveita o cliente atual e isola mudanca no ponto correto de integracao.
- Evita duplicacao de cliente ou endpoint externo.

Alternativas consideradas:
- Criar novo cliente especifico para startDate: aumenta manutencao sem ganho real.

### 4) Regressao orientada a contrato e invariantes de estado
Decisao:
- Cobrir em testes unitarios cenarios com e sem `startDate`, incluindo garantia de nao sobrescrever token principal.
- Validar manutencao dos codigos HTTP e formato de resposta.

Rationale:
- O risco principal e regressao silenciosa do fluxo existente.
- Testes focados em invariantes reduzem risco de quebra em refactors futuros.

## Risks / Trade-offs

- [Uso de `startDate` em chamadas frequentes aumentar carga de full-like sync] -> Mitigacao: manter parametro opcional e uso explicito, sem trocar fluxo padrao.
- [Implementacao filtrar dados mas ainda afetar estado incremental] -> Mitigacao: regra explicita no design e teste dedicado de preservacao de `syncToken`.
- [Interpretacao incorreta de timezone da data] -> Mitigacao: padronizar conversao para inicio do dia em UTC no ponto de montagem da requisicao Google.
- [Aumento de complexidade no service por multiplos caminhos] -> Mitigacao: separar metodos por intencao e manter pontos comuns compartilhados.
