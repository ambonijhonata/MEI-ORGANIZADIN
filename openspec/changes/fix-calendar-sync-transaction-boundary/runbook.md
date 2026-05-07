# Runbook - Calendar Sync Transaction Boundary

## Objetivo

Separar sinais de falha transacional interna de falha real de autenticacao e definir rollback seguro da correcao transacional.

## Sinais de monitoramento

- Falha transacional interna:
  - Exception contendo `TransactionRequiredException` ou `No EntityManager with actual transaction available`.
  - `SyncState.status=SYNC_FAILED` com `errorCategory=INTERNAL_SYNC_ERROR`.
  - Resposta HTTP esperada: erro de servidor (nao `401` por token invalido).
- Falha de autenticacao/revogacao:
  - `GoogleCalendarClient.OAuthRevokedException` ou `GoogleApiForbiddenException`.
  - `SyncState.status=REAUTH_REQUIRED` ou `SYNC_FAILED` com categoria de autorizacao Google.
  - Resposta HTTP esperada: mapeamento de autorizacao (`401/403`) conforme handler.

## Validacao pos-deploy

1. Executar `POST /api/calendar/sync` com usuario de alto volume.
2. Confirmar ausencia de `TransactionRequiredException` nos logs.
3. Confirmar que `created/updated/deleted` continua sendo retornado em sucesso.
4. Confirmar que falhas internas continuam marcando `SYNC_FAILED` com categoria interna.

## Rollback seguro

1. Reverter o commit da change `fix-calendar-sync-transaction-boundary`.
2. Realizar novo deploy.
3. Rodar smoke:
   - `POST /api/calendar/sync`
   - `GET /api/calendar/status`
4. Validar que contrato da API permanece inalterado (`created/updated/deleted`) e que nao houve regressao de autenticacao.
