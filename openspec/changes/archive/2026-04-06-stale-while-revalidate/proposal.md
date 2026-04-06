## Why

A navegacao por dia no calendario (setas anterior/proximo) esta bloqueando a listagem ao esperar `POST /api/calendar/sync`, o que causa espera de varios minutos e degrada a experiencia do usuario. Precisamos manter dados atualizados sem bloquear a renderizacao inicial da agenda.

## What Changes

- Adotar estrategia stale-while-revalidate na tela de agenda: listar eventos locais do dia imediatamente e sincronizar em background.
- Separar estados de carregamento bloqueante e atualizacao em background para evitar que o loading esconda a agenda durante refresh.
- Executar `sync` por gatilhos controlados (carga inicial e politica de freshness), removendo dependencia de sync bloqueante em cada navegacao por setas.
- Revalidar a lista do dia apos `sync` quando houver alteracoes (`created`, `updated`, `deleted` > 0), preservando mensagens de reauth/falha parcial ja existentes.
- Manter contrato atual da API (`POST /api/calendar/sync`, `GET /api/calendar/events`, `GET /api/calendar/status`) sem alteracoes de payload/HTTP.

## Capabilities

### New Capabilities
- `calendar-stale-while-revalidate`: Define comportamento nao bloqueante para listar agenda primeiro e revalidar dados em background com sincronizacao incremental.

### Modified Capabilities
- Nenhuma.

## Impact

- Android app:
  - `App/AndroidNative/app/src/main/java/com/tcc/androidnative/feature/calendar/ui/CalendarHomeViewModel.kt`
  - `App/AndroidNative/app/src/main/java/com/tcc/androidnative/feature/calendar/ui/CalendarHomeScreen.kt`
  - `App/AndroidNative/app/src/main/java/com/tcc/androidnative/feature/calendar/data/CalendarRepository.kt`
  - `App/AndroidNative/app/src/main/res/values/strings.xml`
  - testes de ViewModel/UI de calendario.
- Backend API: sem mudanca de contrato; apenas consumo diferente no cliente.
