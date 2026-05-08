## Smoke Test: Problematic Account Re-Sync

1. Fazer deploy da API com a correção desta change.
2. Acessar a conta que já apresentou:
   `duplicate key value violates unique constraint "ux_calendar_event_services_event_service"`
   e depois
   `ObjectOptimisticLockingFailureException` com `delete from calendar_event_services where id=?`.
3. Garantir que o catálogo da conta contenha os serviços usados no agendamento problemático.
4. Disparar a sincronização manual da agenda.
5. Confirmar que a sincronização termina com sucesso e sem novo erro de `duplicate key` ou `stale delete`.
6. Consultar o agendamento problemático e confirmar que ele possui exatamente uma associação por serviço reconhecido.
7. Validar que o total agregado do evento continua consistente com a soma dos `serviceLinks`.
8. Disparar uma segunda sincronização sem alterar o evento no Google Calendar.
9. Confirmar novamente ausência de erro e estabilidade do conjunto canônico de associações.
