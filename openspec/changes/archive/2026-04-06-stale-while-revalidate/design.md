## Context

A tela de agenda no app Android executa o pipeline de sincronizacao antes de listar os eventos do dia selecionado. Em cenarios de conta com alto volume de eventos, esse comportamento gera espera perceptivel em cada navegacao por seta (dia anterior/proximo), mesmo quando os dados locais ja permitiriam renderizacao imediata.

Ja existe suporte backend para sincronizacao incremental com `syncToken`, e o contrato de API (`POST /api/calendar/sync`, `GET /api/calendar/events`, `GET /api/calendar/status`) deve permanecer estavel. O objetivo desta change e melhorar UX adotando stale-while-revalidate no cliente, sem dependencia de webhook e sem alterar semantica HTTP atual.

## Goals / Non-Goals

**Goals:**
- Exibir agenda do dia rapidamente ao navegar datas, sem bloquear a UI aguardando sync remoto.
- Executar sincronizacao em background apos renderizacao local.
- Controlar frequencia de sync para evitar execucao redundante em navegacao intensa.
- Revalidar lista visivel quando houver delta real no retorno de sync.
- Preservar comportamentos atuais de aviso de falha parcial e reautenticacao.

**Non-Goals:**
- Alterar contrato de endpoints da API.
- Implementar webhook Google Calendar nesta change.
- Introduzir banco local novo ou mudanca arquitetural grande fora do fluxo atual da tela de agenda.

## Decisions

### 1) Separar carregamento de lista e sincronizacao
Decisao:
- Reestruturar pipeline do `CalendarHomeViewModel` para buscar/listar `eventsByDay(date)` primeiro e disparar `sync()` em segundo plano.

Rationale:
- O gargalo de UX esta no bloqueio de UI pelo sync, nao na capacidade de renderizar dados do dia.

Alternativas consideradas:
- Manter sync antes de listar: simples, mas perpetua latencia na navegacao.
- Tornar sync totalmente manual: reduz carga, mas piora frescor sem politica automatica.

### 2) Introduzir estado de "refresh em andamento" nao bloqueante
Decisao:
- Substituir loading unico por estados distintos: carregamento inicial bloqueante (quando ainda nao ha dados) e revalidacao nao bloqueante (quando ja ha agenda exibida).

Rationale:
- Permite feedback visual de atualizacao sem remover dados da tela.

Alternativas consideradas:
- Nao mostrar indicacao de refresh: reduz ruido visual, mas perde transparencia para o usuario.

### 3) Politica de gatilho de sync por freshness
Decisao:
- Executar sync em gatilhos controlados (ex.: inicializacao e janela minima de freshness), evitando acoplamento 1:1 entre toque em seta e sync bloqueante.

Rationale:
- Navegacao rapida entre dias nao deve gerar tempestade de chamadas de sync.

Alternativas consideradas:
- Sync em todo evento de navegacao: maior atualidade, custo e latencia de UX inaceitaveis.
- Sem sync automatico: risco de dados stale por longos periodos.

### 4) Reconciliacao condicional por delta
Decisao:
- Recarregar `eventsByDay(selectedDate)` apos sync apenas quando houver delta (`created`, `updated`, `deleted` > 0).

Rationale:
- Evita recargas desnecessarias quando backend indica ausencia de mudanca.

Alternativas consideradas:
- Recarregar sempre apos sync: simples, porem aumenta custo e risco de flicker.

### 5) Preservar mensagens e tratamento de erro existentes
Decisao:
- Manter semantica atual de `RecoverableFailure` e `ReauthRequired`, apenas mudando para apresentacao nao bloqueante quando possivel.

Rationale:
- Reduz risco funcional e evita regressao de fluxos ja conhecidos pelo usuario.

Alternativas consideradas:
- Redesenhar completamente as mensagens: maior escopo e risco de regressao.

## Risks / Trade-offs

- [Dados temporariamente desatualizados por alguns segundos/minutos] -> Mitigacao: mostrar estado de atualizacao em background e revalidar em seguida.
- [Condicao de corrida entre navegacao rapida e retorno de sync] -> Mitigacao: manter controle por requestId mais recente e descarte de respostas stale.
- [Politica de freshness conservadora demais] -> Mitigacao: tornar janela configuravel e ajustar com telemetria.
- [Recarregar dia apos delta pode gerar reposicionamento visual] -> Mitigacao: preservar ancoragem/scroll quando possivel e somente recarregar o dia corrente.

## Migration Plan

1. Implementar refatoracao do ViewModel para fluxo list-first e sync em background.
2. Atualizar UI para suportar estado de revalidacao nao bloqueante sem ocultar agenda.
3. Adicionar politica de freshness e coalescencia de sync concorrente.
4. Cobrir com testes de unidade para navegacao, erros, reauth e refresh por delta.
5. Validar em dispositivo com dataset de alto volume e monitorar logs de latencia percebida.

Rollback:
- Reverter para pipeline anterior (sync antes de listagem) se houver regressao critica de consistencia visual.
- Manter contrato de API inalterado para rollback seguro apenas no cliente.

## Open Questions

- Qual janela de freshness inicial devemos usar no app (ex.: 30s, 60s, 5min)?
- Precisamos de botao de "atualizar agora" explicito na tela alem do refresh automatico?
- Devemos registrar metrica de tempo ate primeira renderizacao de agenda para acompanhar ganho real de UX?
