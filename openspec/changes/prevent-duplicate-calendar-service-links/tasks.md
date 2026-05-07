## 1. Diagnose And Remediate Existing Data

- [ ] 1.1 Executar a query de diagnostico em producao para listar grupos duplicados em `calendar_event_services` e registrar a quantidade de ocorrencias por `calendar_event_id`.
- [ ] 1.2 Executar o saneamento SQL abaixo em producao para remover links duplicados, preservando a menor `id` de cada grupo:

```sql
BEGIN;

WITH ranked AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY
                calendar_event_id,
                COALESCE(service_id, -1),
                service_description_snapshot,
                service_value_snapshot
            ORDER BY id
        ) AS rn
    FROM calendar_event_services
)
DELETE FROM calendar_event_services ces
USING ranked r
WHERE ces.id = r.id
  AND r.rn > 1;

COMMIT;
```

- [ ] 1.3 Executar a query de verificacao apos o saneamento e confirmar que nao restaram grupos com `COUNT(*) > 1` para a mesma chave semantica de link.

## 2. Harden Database Constraints

- [x] 2.1 Criar uma migracao Flyway para adicionar um indice unico parcial em `calendar_event_services(calendar_event_id, service_id)` quando `service_id IS NOT NULL`.
- [x] 2.2 Criar uma migracao Flyway para adicionar um indice unico parcial em `calendar_event_services(calendar_event_id, service_description_snapshot, service_value_snapshot)` quando `service_id IS NULL`.
- [x] 2.3 Atualizar a migracao para executar o saneamento transacional antes de criar os indices e manter o script manual apenas como fallback operacional.

## 3. Serialize Service-Association Mutations

- [x] 3.1 Extrair a logica de lock por usuario de `CalendarSyncService` para um componente compartilhado reutilizavel.
- [x] 3.2 Atualizar `CalendarSyncService` para usar o componente compartilhado sem alterar o comportamento atual de sync bem-sucedido e tratamento de erros.
- [x] 3.3 Atualizar `CalendarEventReprocessor` para executar o reprocessamento sob o mesmo lock por usuario usado pelo sync.
- [x] 3.4 Revisar o fluxo de cadastro e atualizacao de servicos para garantir que o disparo assincrono de reprocessamento nao volte a escrever concorrente sem exclusao mutua.

## 4. Replace Event Service Links Safely

- [x] 4.1 Introduzir uma estrategia explicita de replace para `calendar_event_services` em eventos ja persistidos, removendo links antigos antes de gravar o novo conjunto canonico.
- [x] 4.2 Ajustar a associacao de servicos para garantir que um evento com o mesmo conjunto de servicos nao acumule novos links em syncs ou reprocessamentos repetidos.
- [x] 4.3 Garantir que eventos multi-servico preservem exatamente um link por servico e mantenham `serviceValueSnapshot` coerente com a soma do conjunto canonico.

## 5. Regression Tests

- [x] 5.1 Adicionar teste cobrindo o cenario real: sync inicial com usuario sem servicos, cadastro de servicos, reprocessamento, novo sync e ausencia de links duplicados.
- [x] 5.2 Adicionar teste cobrindo concorrencia entre sync e `reprocessUnidentifiedEvents(userId)` para o mesmo usuario, confirmando estado final canonico.
- [x] 5.3 Adicionar teste cobrindo evento simples de um servico para garantir que o fluxo de caixa nao dobre o valor apos processamentos repetidos.
- [x] 5.4 Adicionar teste cobrindo evento multi-servico para garantir que o fluxo de caixa preserve a soma correta do snapshot apos processamentos repetidos.

## 6. Validation And Rollout

- [x] 6.1 Executar a suite focada de testes backend para sync, reprocessamento e relatorios financeiros.
- [ ] 6.2 Validar manualmente no banco de homologacao ou producao controlada que `calendar_events.service_value_snapshot` e a soma de `calendar_event_services` permanecem iguais apos novos syncs.
- [ ] 6.3 Reemitir o relatorio de fluxo de caixa para o periodo `2026-04-11` e confirmar que `sobrancelha` volta a `528,00`, `buco` fica em `23,00` e o total volta a `551,00`.
