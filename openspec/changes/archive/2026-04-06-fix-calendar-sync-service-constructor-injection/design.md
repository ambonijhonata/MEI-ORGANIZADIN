## Context

O bean `CalendarSyncService` passou a ter dois construtores e, sem selecao explicita para autowiring, o Spring falhou ao instanciar o componente em runtime (`No default constructor found`). O efeito foi indisponibilidade completa da API na inicializacao, apesar de os contratos HTTP e regras de negocio permanecerem conceitualmente os mesmos.

A correção deve restaurar a inicializacao do contexto Spring com uma estrategia de construtor clara, sem alterar payloads, codigos de erro, semantica de `syncToken`, contadores de sync, ou qualquer comportamento funcional dos endpoints.

## Goals / Non-Goals

**Goals:**
- Garantir inicializacao deterministica do bean `CalendarSyncService` no Spring.
- Manter o comportamento atual da API exatamente igual para clientes.
- Preservar testabilidade da classe sem comprometer a construcao de producao.
- Adicionar validacoes de regressao para evitar recorrencia do problema.

**Non-Goals:**
- Alterar contrato HTTP dos endpoints.
- Mudar regras de negocio de sincronizacao.
- Reestruturar arquitetura de modulos da API.
- Introduzir novas dependencias externas.

## Decisions

### 1) Definir construtor de producao unico e explicito para Spring

Decisao:
- Tornar explicita a estrategia de construcao do bean de producao, evitando ambiguidade entre construtores.
- Preferencia de desenho ideal: manter apenas um construtor de producao com todas as dependencias e propriedades de configuracao necessarias.

Rationale:
- Remove risco de erro de instanciação por heuristica de construtor.
- Facilita leitura e manutencao de dependencia/configuracao do bean.

Alternativas consideradas:
- Adicionar construtor vazio: incorreto, mascara problema e pode gerar falhas tardias.
- Manter multiplos construtores sem marcacao: fragil e sujeito a regressao.

### 2) Isolar conveniencia de testes sem impactar DI de producao

Decisao:
- Se houver necessidade de conveniencia em testes, usar construtor/factory de testes que nao conflite com a injeção Spring de producao.
- Garantir que testes continuem cobrindo cenarios de contrato e fallback.

Rationale:
- Preserva produtividade em testes sem reintroduzir ambiguidade no runtime.

Alternativas consideradas:
- Reusar o mesmo construtor de producao em todos os testes: valido, mas pode aumentar verbosidade.

### 3) Tratar compatibilidade comportamental como invariavel

Decisao:
- A correção de instanciação nao deve alterar respostas, codigos HTTP, contadores e semantica de sincronizacao.
- Validar com testes focados nos cenarios de sucesso, erros 403 e fallback por token expirado.

Rationale:
- O objetivo da change e restaurar disponibilidade sem efeitos colaterais de negocio.

## Risks / Trade-offs

- [Ajuste de construtor quebrar testes existentes] -> Mitigacao: adaptar testes com factories/helper e validar suite relevante de sync.
- [Mudanca acidental de comportamento de sync durante refactor] -> Mitigacao: regressao com testes de contrato e cenarios de fallback.
- [Divergencia entre ambiente local e CI] -> Mitigacao: validar inicializacao via `run.bat` e execucao de testes focados.

## Migration Plan

1. Ajustar estrategia de construtor do `CalendarSyncService` para eliminacao de ambiguidade de DI.
2. Atualizar testes impactados pela assinatura/forma de construcao.
3. Executar testes focados de sincronizacao e inicializacao.
4. Validar startup local da API (`run.bat`).
5. Rollback: reverter alteracoes da classe e testes para estado anterior se surgir regressao.

## Open Questions

- Padronizar em todo projeto a regra de "um construtor de producao por bean" para prevenir casos similares?
- Desejamos adicionar um teste de smoke de contexto (`@SpringBootTest`) especifico para detectar falhas de instanciação desse bean no futuro?
