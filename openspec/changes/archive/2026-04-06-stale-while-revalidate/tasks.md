## 1. Refatorar pipeline da agenda para stale-while-revalidate

- [x] 1.1 Alterar `CalendarHomeViewModel` para listar `eventsByDay(selectedDate)` antes do `sync` remoto.
- [x] 1.2 Executar `sync` em background apos a renderizacao local, sem bloquear a agenda visivel.
- [x] 1.3 Manter controle de request mais recente para descartar respostas stale em navegacao rapida.

## 2. Ajustar estado de UI para refresh nao bloqueante

- [x] 2.1 Separar estado de carregamento inicial (bloqueante) de estado de revalidacao em background (nao bloqueante).
- [x] 2.2 Atualizar `CalendarHomeScreen` para continuar exibindo agenda durante refresh, removendo retorno precoce quando houver apenas revalidacao.
- [x] 2.3 Adicionar/ajustar mensagem de feedback para "atualizando agenda" sem esconder dados ja renderizados.

## 3. Implementar politica de gatilho de sync e reconciliacao por delta

- [x] 3.1 Definir politica de freshness para evitar sync redundante em cada toque nas setas.
- [x] 3.2 Garantir coalescencia de sync concorrente para evitar tempestade de chamadas durante navegacao intensa.
- [x] 3.3 Recarregar `eventsByDay(selectedDate)` somente quando `sync` retornar delta (`created`, `updated` ou `deleted` > 0).

## 4. Preservar tratamento funcional atual e cobrir com testes

- [x] 4.1 Preservar fluxos de `ReauthRequired` e `RecoverableFailure` com comportamento nao bloqueante de agenda.
- [x] 4.2 Atualizar testes de `CalendarHomeViewModelTest` para cobrir list-first, refresh em background e reconciliacao por delta.
- [x] 4.3 Executar suites de teste da feature de calendario e validar regressao de navegacao por data.

## 5. Validacao de UX e observabilidade

- [x] 5.1 Validar manualmente em conta de alto volume que a navegacao por setas exibe agenda rapidamente.
- [x] 5.2 Medir tempo de primeira renderizacao e tempo de refresh em background nos logs da pipeline de calendario.
- [x] 5.3 Confirmar que o contrato da API permanece inalterado no fluxo final.
