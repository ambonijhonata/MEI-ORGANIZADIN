## Smoke Test: Late Service Enrichment

1. Cadastrar apenas o servico `sobrancelha` com valor `48,00`.
2. Garantir que exista no Google Calendar um evento com titulo `fulano - sobrancelha + buco + tintura`.
3. Executar a sincronizacao inicial.
4. Confirmar que o agendamento sincronizado foi salvo com apenas `sobrancelha` associada e total `48,00`.
5. Cadastrar depois os servicos `buco` e `tintura`.
6. Aguardar o enriquecimento assíncrono pos-catalogo.
7. Consultar novamente o agendamento sincronizado.
8. Confirmar que o evento agora possui exatamente tres associacoes canonicas: `sobrancelha`, `buco` e `tintura`.
9. Confirmar que o snapshot ja existente de `sobrancelha` foi preservado.
10. Confirmar que o total agregado do evento foi atualizado para a soma final do conjunto enriquecido.
11. Disparar nova sincronizacao sem alterar o evento no Google.
12. Confirmar que nenhuma associacao duplicada foi criada e que o total do agendamento permaneceu estavel.
