## 1. Visão geral da solução

A solução tem como objetivo permitir que profissionais autônomos e pequenos negócios acompanhem seu faturamento e fluxo de caixa a partir dos agendamentos já cadastrados no Google Agenda, sem precisar de nenhuma entrada manual de dados operacionais repetitivos.

A solução é composta por três partes:

**Aplicativo:** interface pela qual o usuário utiliza a solução. É por meio dele que o usuário faz login com sua conta Google, cadastra seus serviços, acompanha seus agendamentos sincronizados e acessa os relatórios de fluxo de caixa e faturamento.

**API backend:** responsável por receber as credenciais Google do usuário enviadas pelo aplicativo, persistir os serviços cadastrados pelo usuário, importar os agendamentos do Google Agenda, identificar o serviço associado a cada agendamento, controlar o estado de sincronização e disponibilizar os relatórios para o aplicativo.

**Base operacional e analítica:** camada de persistência própria da solução, responsável por armazenar usuários, credenciais OAuth, serviços cadastrados, eventos sincronizados, estado de sincronização e os dados históricos necessários para geração correta dos relatórios.

O fluxo de uso é o seguinte:

1. O usuário abre o aplicativo e faz login com sua conta Google.
2. No mesmo fluxo, o aplicativo obtém do Google:
   - `idToken`, usado para autenticar o usuário perante a API;
   - `authorizationCode`, usado pelo backend para obter credenciais OAuth do Google Calendar.
3. O usuário cadastra seus serviços no aplicativo, informando descrição e valor.
4. O aplicativo envia as credenciais Google e os dados de serviços para a API backend.
5. A API backend valida o `idToken`, identifica o usuário, troca o `authorizationCode` por tokens Google, persiste os serviços do usuário, importa os agendamentos do **calendário principal** do Google Agenda e tenta identificar, para cada agendamento, qual serviço cadastrado corresponde ao título do evento.
6. Quando houver correspondência entre o título do agendamento e um serviço cadastrado pelo mesmo usuário, a API associa o evento ao serviço e registra o valor do serviço no próprio evento sincronizado para preservar o histórico financeiro.
7. Nas chamadas subsequentes, o aplicativo utiliza o próprio `idToken` do Google como bearer token para consumir a API.
8. Os relatórios de faturamento e fluxo de caixa são gerados a partir dos eventos sincronizados, identificados e elegíveis, sempre usando o valor histórico registrado em cada evento no momento da associação com o serviço.
9. O relatório de faturamento deve representar quanto o usuário faturou em um determinado período, somando os valores dos eventos elegíveis daquele intervalo.
10. O relatório de fluxo de caixa deve representar a distribuição temporal dos valores ao longo do período consultado, permitindo visualizar entradas por dia, semana ou mês, conforme o recorte solicitado.
11. Antes de emitir qualquer relatório, a solução deve verificar a atualidade dos dados sincronizados usados como base.
12. Se os dados estiverem atualizados dentro da política definida pela solução, o relatório poderá ser emitido normalmente.
13. Se os dados não estiverem atualizados, a solução deverá deixar explícito no resultado do relatório a data e o horário da última sincronização considerada, informando de forma clara que o relatório foi gerado com base naquele estado de dados.
14. A solução deve tratar exclusivamente o **calendário principal da conta Google do usuário**, não devendo sincronizar calendários secundários, compartilhados ou múltiplos calendários na mesma conta.
15. Se um agendamento deixar de existir no Google Agenda, o agendamento sincronizado correspondente também deve ser excluído da base própria da solução.
16. Se a autorização Google do usuário for revogada ou se o `refresh_token` se tornar inválido, a solução deve marcar a integração como inválida, interromper novas sincronizações automáticas e exigir nova autorização do usuário para restabelecer o vínculo.

---

## 2. Objetivo

Construir uma única API backend multiusuário em Java 21 + Spring Boot, com persistência em base própria, capaz de:

- autenticar usuários do aplicativo via Google;
- usar o login Google como mecanismo de autenticação também para a API;
- possibilitar o cadastro de serviços por usuário, informando descrição e valor;
- permitir exclusão de serviços apenas quando não houver agendamentos vinculados;
- associar cada requisição ao usuário correto;
- integrar com Google Calendar em nome do usuário;
- persistir os agendamentos em base própria;
- identificar automaticamente qual serviço foi agendado com base no título do evento do Google Agenda;
- registrar no evento sincronizado o serviço identificado e o valor correspondente no momento da associação;
- gerar relatórios de fluxo de caixa e faturamento com base nos eventos sincronizados, identificados e elegíveis do período consultado;
- limitar o relatório de fluxo de caixa a consultas de no máximo 7 dias corridos;
- limitar o relatório de faturamento a consultas de no máximo 12 meses;
- deixar explícito o significado de cada relatório e a regra de composição de seus valores;
- garantir que os dados usados nos relatórios estejam atualizados conforme a política de sincronização definida pela solução;
- informar explicitamente, quando necessário, a data e o horário da última sincronização utilizada como base do relatório;
- operar exclusivamente sobre o calendário principal da conta Google do usuário;
- tratar explicitamente cenários de revogação ou invalidação de tokens OAuth;
- garantir que cada usuário acesse apenas seus próprios dados;
- sustentar alta performance, estabilidade e escalabilidade;
- ser desenvolvida com TDD como metodologia obrigatória;
- garantir cobertura de comportamento por meio de testes unitários com JUnit e testes de integração com Rest Assured.

---

## 3. Decisões arquiteturais principais

### 3.1 Backend único e multiusuário

Haverá uma única aplicação backend, compartilhada por todos os usuários do app.  
Isso implica que a arquitetura deve obrigatoriamente tratar:

- isolamento lógico por usuário;
- autenticação forte;
- autorização por contexto autenticado;
- consultas sempre filtradas pelo usuário autenticado;
- proteção contra acesso cruzado de dados.

### 3.2 Base própria como fonte principal de leitura

Os eventos do Google Calendar não devem ser buscados em tempo real no hot path da API.  
Em vez disso:

- os eventos são sincronizados do Google para a base própria da solução;
- os serviços cadastrados pelo usuário são persistidos nessa base;
- as leituras do app acontecem sobre a base própria;
- a sincronização ocorre de forma assíncrona ou desacoplada do fluxo principal de leitura.

**Motivo**

Essa decisão é essencial para cumprir:

- p50 de 80 ms;
- p95 de 300 ms;
- p99 de 500 ms;
- 1000 RPS;
- erro < 0,1%.

### 3.3 Estratégia de identificação do serviço no agendamento

Inicialmente, cada agendamento conterá **um único serviço**.

A identificação seguirá a seguinte regra funcional:

- o **título do agendamento** no Google Agenda representa o nome do serviço;
- a **descrição do serviço cadastrada pelo usuário** representa a referência mestra do serviço;
- durante a sincronização, a API deve comparar o título do evento com os serviços cadastrados pelo mesmo usuário;
- havendo correspondência, o evento deve ser vinculado ao serviço.

**Exemplo**

- título do agendamento no Google Agenda: `Design de sobrancelha`
- serviço cadastrado pelo usuário: `Design de sobrancelha`
- resultado: o evento é vinculado a esse serviço.

### 3.4 Estratégia de comparação textual

Para tornar o processo mais robusto, a comparação entre título do evento e descrição do serviço deve ser feita por um campo normalizado.

A normalização inicial deve incluir:

- remoção de espaços excedentes nas extremidades;
- colapso de espaços duplicados;
- comparação case insensitive;
- remoção de acentos;
- armazenamento de uma versão normalizada da descrição do serviço;
- geração de uma versão normalizada do título do evento no momento da sincronização.

**Exemplo**

- `Design de sobrancelha`
- `design de sobrancelha`
- ` design   de sobrancelha `
- `Design de Sobrancelha`

Todos devem ser tratados como equivalentes.

### 3.5 Persistência do valor histórico do serviço

Para relatórios financeiros confiáveis, o valor do serviço **não deve ser lido apenas do cadastro atual de serviços em tempo de consulta**, porque o usuário pode alterar o preço futuramente.

Por isso, quando um evento for associado a um serviço, a API deve registrar no próprio evento sincronizado:

- a referência do serviço identificado;
- a descrição do serviço identificada;
- o valor do serviço no momento da associação.

Isso garante consistência histórica nos relatórios.

### 3.6 Garantia de atualidade dos dados para relatórios

Os relatórios financeiros não podem ser apresentados de forma ambígua quanto à atualidade da informação.

A arquitetura deve obrigatoriamente prever:

- verificação do estado de sincronização antes da emissão de qualquer relatório;
- definição de política de atualização aceitável para uso em relatórios;
- capacidade de identificar a data e o horário da última sincronização efetivamente considerada;
- indicação clara ao usuário quando o relatório estiver sendo emitido com base em dados potencialmente desatualizados.

**Regra funcional obrigatória**

- se os dados estiverem atualizados conforme a política definida, o relatório pode ser emitido como atualizado;
- se os dados não estiverem atualizados, o relatório ainda poderá ser emitido, mas deverá informar explicitamente algo equivalente a:
  - que os dados refletem a última sincronização disponível;
  - a data e o horário exatos dessa sincronização;
  - que pode haver eventos mais recentes ainda não incorporados.

### 3.7 Definição funcional dos relatórios

A solução deve tratar dois relatórios financeiros distintos, com objetivos diferentes e complementares.

**Relatório de faturamento**

É o relatório que responde quanto o usuário faturou em um período.  
Seu foco é o total financeiro acumulado no intervalo consultado.

Esse relatório deve:

- considerar apenas eventos elegíveis dentro do período informado;
- somar os valores históricos registrados nos eventos identificados;
- permitir consolidação por período total e por agrupamentos, quando aplicável;
- refletir o valor que estava associado ao evento no momento em que ele foi identificado, preservando o histórico;
- aceitar período máximo de **12 meses**.

**Relatório de fluxo de caixa**

É o relatório que responde como os valores se distribuem ao longo do tempo dentro de um período.  
Seu foco é a evolução temporal das entradas, e não apenas o total consolidado.

Esse relatório deve:

- considerar apenas eventos elegíveis dentro do período informado;
- agrupar os valores por unidade temporal adequada ao recorte consultado, como dia, semana ou mês;
- apresentar a sequência cronológica das entradas apuradas;
- possibilitar visualização de oscilações de volume financeiro ao longo do tempo;
- aceitar período máximo de **7 dias corridos**.

**Diferença prática entre eles**

- faturamento: visão consolidada de quanto foi gerado no período;
- fluxo de caixa: visão temporal de como esse valor se distribuiu no período.

### 3.8 Escopo do Google Calendar

A solução deve operar exclusivamente sobre o **calendário principal** da conta Google do usuário autenticado.

Isso implica que a implementação:

- não deve listar ou sincronizar calendários secundários;
- não deve sincronizar calendários compartilhados;
- não deve permitir escolha manual de múltiplos calendários nesta versão;
- deve manter, por usuário, apenas um contexto de sincronização referente ao calendário principal.

### 3.9 Tratamento de revogação de OAuth

A arquitetura deve prever explicitamente o cenário em que o usuário revoga o acesso da aplicação no Google ou em que o `refresh_token` se torna inválido.

Nesse caso, a solução deve:

- detectar a falha de renovação/autorização;
- marcar a integração como inválida ou pendente de reautenticação;
- registrar o erro no estado de sincronização;
- interromper novas tentativas automáticas de sincronização até nova autorização do usuário;
- preservar os dados já sincronizados na base própria;
- deixar explícito para o cliente que a integração precisa ser refeita.

### 3.10 Stack tecnológica

**Backend**

- Java 21
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- Bean Validation

**Persistência**

- base relacional, documental ou outra estratégia de persistência adequada, a critério do implementador, desde que suporte consistência, rastreabilidade histórica, isolamento por usuário e performance compatível com as metas da solução

**Qualidade e testes**

- JUnit 5 para testes unitários
- Rest Assured para testes de integração
- mecanismo de ambiente isolado de testes para validação integrada da persistência

**Complementares recomendados**

- ferramenta de versionamento de esquema/evolução de estrutura de dados
- pool de conexões, quando aplicável
- cache opcional
- k6 para testes de carga

---

## 4. Diretriz obrigatória de desenvolvimento: TDD

### 4.1 Metodologia oficial do projeto

O desenvolvimento deve seguir TDD (Test-Driven Development) como padrão obrigatório.  
O ciclo será:

- **Red:** escrever primeiro o teste unitário para o comportamento desejado.
- **Green:** implementar o mínimo de código necessário para fazer o teste passar.
- **Refactor:** melhorar o código mantendo todos os testes passando.

### 4.2 Regra principal

Nenhuma implementação de regra de negócio deve começar sem que o respectivo teste unitário tenha sido escrito antes.  
Isso significa que:

- primeiro define-se o comportamento esperado;
- depois cria-se o teste unitário em JUnit;
- somente após isso o código de produção é implementado;
- ao longo da implementação, os testes devem ser reexecutados continuamente.

Essa regra vale também para:

- cadastro de serviços;
- atualização e exclusão de serviços;
- normalização de descrições;
- identificação do serviço na sincronização;
- associação de serviço ao evento;
- persistência do valor histórico usado nos relatórios;
- validação da atualidade dos dados usados nos relatórios;
- composição do relatório de faturamento;
- composição do relatório de fluxo de caixa;
- validação dos limites máximos de período dos relatórios;
- exclusão sincronizada de eventos removidos no Google;
- tratamento de revogação de OAuth;
- exibição da data e horário da última sincronização no relatório quando os dados não estiverem atualizados.

### 4.3 Reexecução contínua dos testes unitários

À medida que o código for sendo desenvolvido, alterado ou consumido por outras camadas, os testes unitários devem ser reexecutados.  
Isso deve ocorrer:

- após cada pequena implementação;
- após cada refatoração;
- antes de criar novos testes;
- antes de subir código para repositório;
- no pipeline de CI.

**Objetivo**

Garantir:

- regressão zero ou mínima;
- consistência do comportamento já implementado;
- segurança para refatorações;
- detecção precoce de quebras.

### 4.4 Ordem correta de construção

A ordem de trabalho deve ser:

1. especificar comportamento;
2. escrever testes unitários com JUnit;
3. executar os testes e confirmar falha inicial;
4. implementar código mínimo;
5. reexecutar testes unitários;
6. refatorar;
7. reexecutar testes unitários novamente;
8. depois escrever testes de integração com Rest Assured para validar contratos e fluxos ponta a ponta.

---

## 5. Arquitetura lógica da aplicação

A aplicação pode ser organizada em módulos/camadas bem definidos.

### 5.1 Camadas sugeridas

**1. Camada de API / Controller**

Responsável por:

- receber requisições HTTP;
- validar payloads;
- extrair contexto autenticado;
- delegar para services;
- retornar DTOs.

**2. Camada de Application / Service**

Responsável por:

- regras de negócio;
- orquestração de autenticação;
- sincronização;
- autorização;
- cadastro de serviços;
- atualização e exclusão de serviços;
- identificação de serviço no evento;
- paginação;
- montagem de respostas;
- geração de relatórios;
- verificação da atualidade dos dados antes da emissão de relatórios;
- tratamento de revogação de integração OAuth.

**3. Camada de Domínio**

Responsável por:

- entidades;
- regras centrais;
- contratos;
- invariantes.

**4. Camada de Infraestrutura**

Responsável por:

- persistência;
- integração com Google OAuth / Google Calendar;
- mensageria;
- caching;
- observabilidade.

### 5.2 Organização sugerida de pacotes

```text
com.seuprojeto.api
  ├── auth
  ├── servicecatalog
  ├── calendar
  ├── report
  ├── user
  ├── security
  ├── common
  └── infrastructure
```

---

## 6. Fluxo principal da solução

### 6.1 Fluxo de autenticação inicial unificado com Google

1. O usuário instala o app.
2. O usuário faz login com Google no app Android.
3. No mesmo fluxo, o app obtém:
   - `idToken`
   - `authorizationCode`
4. O app envia ambos ao backend.
5. A API valida o `idToken`.
6. A API identifica o usuário pelo `sub`.
7. A API cria ou localiza o usuário interno.
8. A API troca o `authorizationCode` por tokens Google.
9. A API persiste os tokens OAuth associados ao usuário.
10. A API dispara a sincronização inicial completa dos eventos do **calendário principal**.
11. A API não emite token próprio da aplicação.
12. As chamadas subsequentes do app para recursos privados utilizam o próprio `idToken` do Google como bearer token.

**Observação importante**

- O `idToken` é usado para autenticação do usuário perante a API.
- O `authorizationCode` é usado apenas para que o backend obtenha `access_token` e `refresh_token` necessários para acessar o Google Calendar em nome do usuário.

### 6.2 Fluxo de cadastro de serviços

1. O usuário acessa a funcionalidade de cadastro de serviços no aplicativo.
2. O usuário informa:
   - descrição;
   - valor.
3. O aplicativo envia a solicitação autenticada com o `idToken`.
4. A API valida o token e resolve o usuário autenticado.
5. A API normaliza a descrição informada.
6. A API persiste o serviço vinculado ao usuário autenticado.
7. O serviço fica disponível para associação automática nos próximos ciclos de sincronização.

### 6.3 Fluxo de atualização e exclusão de serviços

1. O usuário acessa a funcionalidade de manutenção de serviços no aplicativo.
2. O aplicativo envia a solicitação autenticada com o `idToken`.
3. A API valida o token e resolve o usuário autenticado.
4. Em caso de atualização, a API altera os dados do serviço do usuário autenticado e pode disparar reprocessamento assíncrono de eventos não identificados.
5. Em caso de exclusão, a API deve verificar se o serviço possui agendamentos vinculados.
6. Se o serviço **não possuir vínculos**, a exclusão pode ser realizada.
7. Se o serviço **possuir qualquer vínculo com agendamento sincronizado**, a exclusão deve ser rejeitada.
8. A rejeição deve ser tratada como erro de negócio explícito, preservando a integridade histórica e referencial.

### 6.4 Fluxo de leitura de eventos

1. O aplicativo solicita a leitura dos eventos sincronizados.
2. O aplicativo envia autenticação com o `idToken`.
3. A API valida o bearer token Google.
4. O contexto autenticado resolve o usuário a partir do `google_sub`.
5. A camada de serviço consulta a base própria filtrando pelo usuário autenticado.
6. A resposta retorna somente eventos daquele usuário, incluindo, quando existir, o serviço identificado, o valor associado e o status de identificação.

### 6.5 Fluxo de sincronização com identificação de serviço

A sincronização segue uma estratégia diferenciada entre a primeira execução e as execuções subsequentes.

Para cada evento sincronizado, a API deve:

1. ler o título do evento do Google Agenda;
2. gerar o título normalizado;
3. buscar, entre os serviços do mesmo usuário, um serviço com descrição normalizada equivalente;
4. se houver correspondência:
   - vincular o evento ao serviço;
   - copiar para o evento o valor do serviço no momento da sincronização;
   - marcar o evento como identificado;
5. se não houver correspondência:
   - manter o evento sem serviço associado;
   - marcar o evento como não identificado para futura conciliação.

### 6.6 Primeira sincronização (full sync)

Executada logo após a autenticação inicial do usuário:

1. A API identifica que não existe estado prévio de sincronização reaproveitável para o usuário.
2. Busca todos os agendamentos do **calendário principal** do Google Calendar do usuário, sem filtro de data, percorrendo todas as páginas disponíveis.
3. Para cada evento, tenta identificar o serviço a partir do título.
4. Persiste todos os eventos na base própria da solução.
5. Ao final, armazena o estado necessário para sincronizações futuras.
6. Registra o timestamp da última sincronização completa.

### 6.7 Sincronizações incrementais (incremental sync)

Executadas em todas as sincronizações seguintes, sejam periódicas, automáticas ou manuais:

1. A API identifica que já existe um estado de sincronização salvo para o usuário.
2. Utiliza esse estado para solicitar ao Google Calendar apenas os eventos criados, alterados ou removidos desde a última sincronização.
3. Para cada evento retornado, reaplica a lógica de identificação do serviço com base no título atual do evento.
4. Aplica as alterações na base local:
   - inserção;
   - atualização;
   - exclusão local do evento quando ele tiver sido removido no Google.
5. Atualiza o estado de sincronização para o novo valor retornado.
6. Registra o timestamp da última sincronização incremental.

**Regra funcional obrigatória**

A solução não deve tratar o evento removido do Google como “cancelado” para fins de persistência local.  
Se o agendamento deixar de existir no Google, o agendamento sincronizado correspondente deve ser excluído da base própria.

### 6.8 Resync completo (fallback)

Caso o Google retorne erro indicando que o estado incremental expirou ou é inválido:

1. A API descarta o estado incremental atual.
2. Executa um novo full sync conforme descrito acima.
3. Atualiza o status de sincronização e os timestamps correspondentes.

### 6.9 Reprocessamento após cadastro ou alteração de serviço

Como um usuário pode cadastrar serviços depois que eventos já foram sincronizados, a arquitetura deve prever reprocessamento.

Estratégia recomendada:

- quando um serviço for criado ou alterado, a API pode disparar um processo assíncrono para tentar reconciliar eventos do mesmo usuário ainda não identificados;
- o reconciliador recalcula a associação com base na descrição normalizada do novo serviço;
- se houver correspondência, atualiza o evento com a referência do serviço, descrição snapshot e valor snapshot.

### 6.10 Fluxo de geração de relatórios com garantia de transparência de atualização

Antes de gerar qualquer relatório, a solução deve:

1. verificar o estado de sincronização do usuário;
2. avaliar se os dados disponíveis atendem à política de atualização definida;
3. validar se o período solicitado respeita o limite do tipo de relatório;
4. selecionar apenas eventos elegíveis para composição do relatório;
5. aplicar a lógica de agregação correspondente ao tipo de relatório solicitado;
6. decidir se o relatório será emitido como atualizado ou como relatório baseado na última sincronização disponível;
7. incluir no resultado metadados claros sobre a sincronização utilizada.

O comportamento mínimo esperado é:

- se a base estiver atualizada dentro da política do sistema, o relatório pode ser apresentado como atualizado;
- se a base não estiver atualizada, o relatório deve informar explicitamente:
  - que foi emitido com base na última sincronização disponível;
  - a data e o horário dessa sincronização;
  - eventual indicação de que dados mais recentes podem ainda não ter sido incorporados.

### 6.11 Fluxo funcional do relatório de faturamento

O relatório de faturamento deve ser gerado da seguinte forma:

1. receber o período de consulta;
2. validar que o período não ultrapassa **12 meses**;
3. localizar os eventos do usuário dentro desse período;
4. considerar apenas eventos elegíveis para faturamento;
5. somar os valores históricos dos eventos identificados;
6. produzir a visão consolidada do total do período;
7. opcionalmente produzir quebras por serviço ou por subperíodo, se essa capacidade for disponibilizada;
8. retornar o resultado acompanhado do estado de atualização dos dados utilizados.

### 6.12 Fluxo funcional do relatório de fluxo de caixa

O relatório de fluxo de caixa deve ser gerado da seguinte forma:

1. receber o período de consulta;
2. validar que o período não ultrapassa **7 dias corridos**;
3. localizar os eventos do usuário dentro desse período;
4. considerar apenas eventos elegíveis para composição financeira;
5. distribuir os valores históricos dos eventos de acordo com a data de ocorrência de cada evento;
6. agrupar os valores por unidade temporal compatível com o recorte consultado;
7. montar a série cronológica de entradas do período;
8. retornar o resultado acompanhado do estado de atualização dos dados utilizados.

### 6.13 Fluxo de tratamento de revogação de OAuth

Quando, durante uma sincronização ou renovação de credenciais, o Google indicar que a autorização do usuário não é mais válida:

1. a API registra a falha como erro de integração;
2. marca o vínculo Google do usuário como inválido ou pendente de reautenticação;
3. interrompe novas tentativas automáticas de sincronização;
4. preserva os dados já sincronizados na base própria;
5. informa explicitamente ao aplicativo que é necessária nova autorização do usuário;
6. somente após novo bootstrap de autenticação a sincronização poderá ser reativada.

---

## 7. Estratégia de autenticação e segurança no Spring Boot

### 7.1 Modelo adotado

A solução adotará **autenticação unificada via Google**.

O usuário realizará apenas um login no aplicativo, usando sua conta Google.  
Esse mesmo login servirá para:

- autenticar o usuário perante a API;
- autorizar o backend a acessar o Google Calendar em nome do usuário.

A API não emitirá token próprio da aplicação.

### 7.2 Como o modelo funciona

- O aplicativo envia o **Google ID token** nas chamadas à API usando `Authorization: Bearer <token>`.
- O backend valida esse token em cada requisição autenticada.
- O backend extrai o `sub` do Google como identidade externa estável.
- O backend resolve o identificador interno do usuário da aplicação a partir do `google_sub`.
- O backend utiliza os tokens OAuth do Google armazenados para sincronização com o Google Calendar.

### 7.3 Validações obrigatórias do ID token

A API deve validar, no mínimo:

- assinatura do token;
- `aud` compatível com o client ID esperado;
- `iss` válido;
- `exp` ainda válido.

### 7.4 Spring Security

O Spring Security deve ser configurado para:

- proteger todos os recursos privados;
- permitir apenas os recursos públicos necessários;
- atuar como camada de autenticação bearer para validação do token Google;
- popular um `AuthenticatedUser` com:
  - `userId`
  - `googleSub`
  - `email`
  - `name`
  - `roles/scopes`, se aplicável.

### 7.5 Regra crítica de autorização

O backend nunca deve aceitar identificador de usuário vindo do cliente como fonte de verdade.  
O usuário da operação deve ser obtido exclusivamente do contexto autenticado no Spring Security.

**Correto**
```java
UUID userId = authenticatedUser.userId();
```

**Errado**
```java
UUID userId = request.getUserId();
```

### 7.6 Regra crítica para serviços e eventos

Toda operação de:

- cadastro de serviço;
- listagem de serviço;
- atualização de serviço;
- exclusão de serviço;
- sincronização;
- associação entre evento e serviço;
- geração de relatório

deve sempre considerar somente os dados do usuário autenticado.

Nenhum serviço de um usuário pode ser usado para identificar evento de outro usuário.

### 7.7 Separação de responsabilidades dos artefatos Google

Para evitar ambiguidade na implementação:

- **`idToken`**: autentica o usuário nas chamadas à API;
- **`authorizationCode`**: usado apenas no bootstrap da integração para obtenção de tokens OAuth;
- **`access_token` e `refresh_token` do Google**: usados exclusivamente pelo backend para acessar o Google Calendar;
- **token próprio da API**: não será utilizado.

### 7.8 Tratamento de revogação de credenciais

Se o backend identificar que o `refresh_token` está inválido, revogado ou não autorizado:

- a integração do usuário com Google deve ser marcada como inválida;
- o sistema deve interromper novas sincronizações automáticas;
- o erro deve ficar registrado no estado de sincronização;
- o usuário deve ser informado de que precisa autenticar novamente para restaurar o sync;
- a API não deve mascarar esse cenário como simples falha transitória.

---

## 8. Diretrizes de persistência e rastreabilidade

### 8.1 Princípios obrigatórios

A solução deve garantir, independentemente da tecnologia de persistência escolhida:

- todo dado de negócio vinculado ao usuário correto;
- isolamento lógico por usuário;
- persistência dos serviços cadastrados;
- persistência dos eventos sincronizados;
- persistência do estado de sincronização;
- preservação do histórico necessário para relatórios financeiros corretos;
- capacidade de auditoria e rastreabilidade operacional;
- suporte à detecção da data e hora da última sincronização utilizada nos relatórios.

### 8.2 Regras de modelagem em alto nível

Independentemente do desenho físico adotado, a persistência deve suportar:

- identidade estável do usuário autenticado via Google;
- credenciais necessárias para acesso ao Google Calendar em nome do usuário;
- catálogo de serviços por usuário;
- eventos sincronizados do calendário principal;
- estado da sincronização por usuário;
- armazenamento do vínculo entre evento e serviço identificado;
- armazenamento do valor histórico copiado para o evento no momento da associação;
- armazenamento do status de identificação do evento;
- armazenamento da informação temporal de última sincronização relevante para relatórios;
- armazenamento do status da integração Google;
- armazenamento da categoria ou código do último erro de sincronização;
- armazenamento de indicação de necessidade de reautenticação.

### 8.3 Regras de consulta

A implementação deve ser capaz de atender, com performance compatível com as metas da solução, consultas que envolvam:

- usuário autenticado;
- intervalo de datas;
- paginação;
- ordenação temporal;
- filtragem por status de identificação;
- agregações para relatórios financeiros;
- recuperação da data e hora da última sincronização usada como referência.

### 8.4 Regras de integridade para serviços

A persistência deve garantir que:

- um serviço pertença a um único usuário;
- um serviço só possa ser excluído quando não houver agendamentos vinculados;
- tentativas de exclusão de serviço com vínculo sejam bloqueadas;
- a integridade histórica dos eventos sincronizados seja preservada.

---

## 9. Estratégia de persistência em Spring Boot

### 9.1 Abordagem geral

A persistência deve priorizar produtividade, auditabilidade e performance, com atenção a:

- evitar N+1;
- usar projeções/DTOs quando necessário;
- controlar fetch strategy;
- manter consultas críticas auditáveis e performáticas.

### 9.2 Gestão de conexões e recursos

Se a solução utilizar banco relacional ou outro recurso com pool de conexões, o pool deve ser cuidadosamente ajustado.

Parâmetros que merecem tuning:

- capacidade máxima;
- capacidade mínima ociosa;
- timeout de aquisição;
- timeout de ociosidade;
- tempo de vida de conexões.

**Importante**

Configuração inadequada impacta diretamente:

- latência;
- throughput;
- erros sob carga;
- estabilidade.

---

## 10. Estratégia de sincronização com Google Calendar

### 10.1 Sincronização fora do caminho crítico

A sincronização deve ocorrer em:

- sync inicial completo após autenticação, buscando todos os agendamentos do **calendário principal** do usuário;
- sync incremental periódico, buscando apenas eventos novos, alterados ou removidos desde a última sincronização;
- sync manual sob demanda, seguindo a mesma lógica incremental, salvo quando for necessário um resync completo;
- resync completo para recuperação em caso de estado incremental expirado ou inválido.

### 10.2 Estratégia full sync vs incremental sync

**Full sync**

- Executado na primeira autenticação do usuário ou após invalidação do estado incremental.
- Busca todos os eventos do **calendário principal** do Google Calendar, sem restrição de período, paginando até o fim.
- Para cada evento, tenta identificar o serviço a partir do título.
- Ao final, persiste o estado necessário para uso nas próximas sincronizações.

**Incremental sync**

- Executado em todas as sincronizações subsequentes ao full sync.
- Utiliza o estado de sincronização armazenado para solicitar ao Google apenas os eventos criados, modificados ou removidos desde a última sincronização.
- Para cada evento, reaplica a lógica de identificação do serviço a partir do título atual.
- Aplica as diferenças na base local:
  - insere novos eventos;
  - atualiza alterados;
  - exclui localmente os eventos removidos no Google.
- Atualiza o estado de sincronização ao final de cada ciclo.

### 10.3 Algoritmo inicial de identificação do serviço

A primeira versão da identificação será propositalmente simples e previsível.

Regra:

1. obter `summary` do evento do Google Calendar;
2. considerar esse valor como o título do agendamento;
3. normalizar o texto do título;
4. buscar entre os serviços do mesmo usuário um registro com descrição normalizada equivalente;
5. se encontrado:
   - associar o serviço;
   - preencher o snapshot de descrição;
   - preencher o snapshot de valor;
   - marcar o evento como identificado;
6. se não encontrado:
   - manter o evento sem serviço associado;
   - marcar o evento como não identificado.

### 10.4 Tratamento de exclusão de eventos do Google

A solução deve considerar que, para o domínio desta aplicação, um evento removido no Google deve ser removido também da base sincronizada.

Portanto:

- eventos removidos no Google não devem permanecer apenas como “cancelados” localmente;
- o backend deve localizar o evento sincronizado correspondente e excluí-lo da base própria;
- eventos removidos não devem compor relatórios futuros;
- a leitura operacional deve refletir apenas eventos ainda existentes na base sincronizada.

### 10.5 Tratamento de falhas

- retries controlados;
- marcação de erro no estado de sincronização;
- full resync em casos necessários, especialmente quando o estado incremental expirar;
- isolamento entre falha de sincronização e leitura do app;
- interrupção do sync automático quando houver revogação ou invalidação de credencial OAuth.

### 10.6 Regra de credencial usada no sync

A sincronização com Google Calendar deve usar exclusivamente a credencial OAuth persistida do usuário:

- `access_token` atual;
- `refresh_token`, quando necessário para renovação.

O `idToken` usado para autenticação do app na API não deve ser usado como credencial de acesso ao Google Calendar.

### 10.7 Tratamento de revogação de token OAuth

Se o Google indicar que o `refresh_token` foi revogado, expirou de forma não recuperável ou se tornou inválido:

- a API deve marcar a integração como `REAUTH_REQUIRED` ou equivalente;
- deve registrar o timestamp da falha e o motivo conhecido;
- deve interromper novas tentativas automáticas de sincronização;
- deve manter os dados locais já sincronizados;
- deve exigir novo fluxo de autenticação/autorização para restabelecer a integração.

### 10.8 Reprocessamento após cadastro ou alteração de serviço

Como um usuário pode cadastrar serviços depois que eventos já foram sincronizados, a arquitetura deve prever reprocessamento.

Estratégia recomendada:

- quando um serviço for criado ou alterado, a API pode disparar um processo assíncrono para tentar reconciliar eventos ainda não identificados do mesmo usuário;
- o reconciliador recalcula a associação com base na descrição normalizada do novo serviço;
- se houver correspondência, atualiza o evento com referência do serviço, descrição snapshot e valor snapshot.

---

## 11. Estratégia de funcionalidades expostas pela API

A API deve disponibilizar, em alto nível, capacidades para:

- autenticação inicial com Google;
- bootstrap da integração com Google Calendar;
- cadastro, listagem, atualização e exclusão de serviços do usuário autenticado;
- leitura paginada dos eventos sincronizados do usuário autenticado;
- disparo de sincronização manual;
- consulta de relatórios de faturamento;
- consulta de relatórios de fluxo de caixa;
- consulta do estado da integração Google, incluindo necessidade de reautenticação quando aplicável.

**Diretriz importante**

O plano define as capacidades funcionais esperadas, mas não impõe o desenho específico de endpoints, contratos HTTP, formato de payload ou convenções de roteamento.  
Essas decisões ficam a critério do implementador, desde que preservem os comportamentos e garantias descritos neste documento.

---

## 12. Estratégia de geração de relatórios

### 12.1 Premissa principal

Os relatórios de fluxo de caixa e faturamento dependem de duas informações:

- frequência dos serviços agendados;
- valor financeiro associado a cada serviço no momento em que o evento foi identificado.

### 12.2 Definição do relatório de faturamento

O relatório de faturamento é a visão consolidada do total financeiro gerado pelo usuário em um período.

Seu objetivo é responder:

- quanto foi faturado no intervalo consultado;
- quanto cada serviço representou dentro desse total, quando esse detalhamento existir;
- como o total se distribui por agrupamentos de interesse, quando essa capacidade for disponibilizada.

A composição do faturamento deve sempre usar o valor histórico registrado no evento sincronizado, e não o valor atual do serviço no catálogo.

**Limite obrigatório**

O período solicitado para esse relatório deve ser de no máximo **12 meses**.

### 12.3 Definição do relatório de fluxo de caixa

O relatório de fluxo de caixa é a visão cronológica das entradas financeiras associadas aos eventos identificados em um período.

Seu objetivo é responder:

- como os valores se comportaram ao longo do tempo;
- em quais datas houve maior ou menor volume de entradas;
- como as entradas se distribuem por dia, semana ou mês dentro do recorte consultado.

A composição do fluxo de caixa deve sempre usar a data do evento e o valor histórico registrado naquele evento.

**Limite obrigatório**

O período solicitado para esse relatório deve ser de no máximo **7 dias corridos**.

### 12.4 Fonte de verdade para os relatórios

Os relatórios devem ser gerados a partir dos eventos sincronizados e enriquecidos pela solução, e não diretamente do cadastro atual de serviços.

**Motivo**

- os eventos sincronizados contêm o vínculo efetivo entre agendamento e serviço;
- os eventos preservam o valor histórico por meio do snapshot capturado no momento da associação;
- alterações futuras de preço no catálogo de serviços não distorcem resultados passados.

### 12.5 Regra de elegibilidade para composição dos relatórios

Somente eventos que estejam:

- existentes na base sincronizada;
- dentro do período consultado;
- identificados com sucesso;
- com valor histórico preenchido

devem compor automaticamente os relatórios financeiros.

Eventos não identificados podem ser:

- excluídos do cálculo automático;
- exibidos em relatórios de pendência de identificação.

### 12.6 Regra de geração do relatório de faturamento

A geração do relatório de faturamento deve seguir, em alto nível, a seguinte lógica:

1. validar o período solicitado;
2. rejeitar a consulta se o intervalo ultrapassar 12 meses;
3. verificar a atualidade dos dados sincronizados;
4. selecionar os eventos elegíveis do usuário dentro do período;
5. somar os valores históricos desses eventos;
6. consolidar o resultado total do intervalo;
7. opcionalmente detalhar por serviço ou por subperíodo, se previsto pela implementação;
8. devolver o relatório com os metadados de atualização dos dados utilizados.

### 12.7 Regra de geração do relatório de fluxo de caixa

A geração do relatório de fluxo de caixa deve seguir, em alto nível, a seguinte lógica:

1. validar o período solicitado;
2. rejeitar a consulta se o intervalo ultrapassar 7 dias corridos;
3. verificar a atualidade dos dados sincronizados;
4. selecionar os eventos elegíveis do usuário dentro do período;
5. posicionar cada valor histórico na data correspondente do evento;
6. agrupar os valores por unidade temporal compatível com o recorte da consulta;
7. ordenar os agrupamentos cronologicamente;
8. devolver a série temporal apurada com os metadados de atualização dos dados utilizados.

### 12.8 Garantia de atualização e transparência

Todo relatório deve carregar informação explícita sobre a atualização dos dados que serviram de base para seu cálculo.

A solução deve suportar os dois cenários abaixo:

**Cenário A — dados atualizados**

- a solução verifica que a sincronização está dentro da janela de atualização aceitável;
- o relatório pode ser apresentado como gerado com dados atualizados;
- o resultado ainda deve ser capaz de informar qual foi a última sincronização considerada, para rastreabilidade.

**Cenário B — dados não atualizados**

- a solução identifica que a sincronização não está dentro da janela de atualização aceitável ou que não foi possível garantir atualização recente;
- o relatório não deve ser apresentado como se refletisse o estado mais recente do calendário;
- o resultado deve deixar claro que foi emitido com base na última sincronização disponível, informando a data e o horário exatos utilizados como referência.

**Cenário C — integração revogada ou inválida**

- se a sincronização não puder prosseguir porque a integração Google foi revogada ou o token OAuth está inválido, o relatório ainda pode ser emitido com base na última base local disponível, se a implementação permitir;
- nesse caso, o relatório deve informar explicitamente que a integração está inválida e que os dados podem estar desatualizados desde a última sincronização bem-sucedida;
- a solução não deve apresentar esse relatório como atualizado.

### 12.9 Comportamento mínimo obrigatório da saída do relatório

Independentemente do formato técnico adotado, o relatório deve expor metadados equivalentes a:

- tipo de relatório emitido;
- período considerado;
- status de atualização do relatório;
- data e hora da última sincronização considerada;
- indicação de possível defasagem, quando aplicável;
- indicação de necessidade de reautenticação Google, quando aplicável.

### 12.10 Métricas habilitadas por essa modelagem

Com a modelagem lógica proposta, a API poderá calcular:

- faturamento por período;
- quantidade de atendimentos por serviço;
- faturamento por serviço;
- frequência de agendamentos por serviço;
- total diário, semanal e mensal;
- série temporal de entradas financeiras;
- eventos sem serviço identificado;
- relatórios acompanhados do estado de atualização dos dados utilizados.

---

## 13. Estratégia de performance para Java 21 + Spring Boot + persistência própria

### 13.1 Metas

- p50 = 80 ms
- p95 = 300 ms
- p99 = 500 ms
- 1000 RPS
- 200 usuários concorrentes
- erro < 0,1%

### 13.2 Para atingir as metas

**Minimizar trabalho por requisição**

Cada request deve:

- autenticar rapidamente;
- resolver o usuário autenticado;
- executar consulta eficiente;
- serializar DTO pequeno;
- responder.

**Não chamar Google no hot path**

Sem chamadas remotas externas no fluxo principal de listagem nem nos relatórios.

**DTOs enxutos**

Não retornar campos desnecessários para o consumidor.

**Paginação obrigatória**

Evita payloads gigantes.

**Faixa máxima de consulta**

Restringir range de datas para evitar consultas abusivas.

**Estratégia de indexação e otimização adequada**

A implementação escolhida deve refletir os principais padrões de leitura e agregação da solução.

**Cache opcional**

Se houver padrão forte de leitura repetida, pode usar Redis ou equivalente.

### 13.3 Ajustes importantes no Spring Boot

**Serialização**

- evitar objetos excessivamente aninhados;
- usar DTOs específicos.

**Logging**

- evitar logs excessivos em fluxos quentes;
- nunca logar tokens.

**Threads**

- ajustar pools de execução assíncrona;
- não bloquear desnecessariamente.

**GC e memória**

Java 21 já traz bons ganhos, mas é necessário:

- observar uso de heap;
- evitar retenção de objetos grandes;
- fazer soak test.

---

## 14. Estratégia de confiabilidade

### 14.1 Sem vazamento de memória

Práticas obrigatórias:

- evitar caches locais sem TTL;
- evitar manter listas grandes em memória;
- monitorar heap;
- monitorar threads;
- monitorar conexões ou recursos equivalentes;
- limitar batches de sincronização.

### 14.2 Erro total < 0,1%

Necessário:

- timeouts explícitos;
- tratamento robusto de exceções;
- respostas consistentes;
- fallback operacional para falha de sync;
- leitura independente do estado momentâneo do Google.

### 14.3 Logs

Logs estruturados com:

- correlation id
- identificador interno do usuário quando apropriado
- recurso ou operação executada
- tempo de execução
- resultado da operação

**Nunca logar:**

- `access_token`
- `refresh_token`
- `idToken` completo
- `authorizationCode`

### 14.4 Confiabilidade da integração OAuth

A solução deve distinguir falhas transitórias de falhas definitivas de autorização.

Portanto:

- falhas transitórias podem ser tratadas com retry controlado;
- falhas de revogação ou invalidação de `refresh_token` devem marcar a integração como inválida;
- após revogação detectada, a aplicação não deve insistir indefinidamente em novas sincronizações automáticas;
- o estado da integração deve permanecer observável e auditável.

---

## 15. Estratégia de testes

### 15.1 Princípios gerais

A estratégia de testes será dividida em:

- testes unitários com JUnit;
- testes de integração com Rest Assured;
- testes de segurança;
- testes de carga.

A regra principal é que os testes unitários são escritos antes da implementação do código de produção.

### 15.2 Testes unitários com JUnit

Os testes unitários devem cobrir:

- validações;
- regras de negócio;
- autorização;
- tratamento de erros;
- services;
- mapeamentos;
- utilitários;
- políticas de segurança em nível de classe/método;
- comportamento de componentes isolados.

**Diretrizes**

- usar JUnit 5;
- testar comportamento, não detalhes internos desnecessários;
- mockar dependências externas quando necessário;
- manter execução rápida;
- organizar cenários com nomes claros;
- criar testes antes da implementação de cada funcionalidade;
- buscar cobertura completa dos comportamentos relevantes.

**Exemplos de alvos unitários**

- `GoogleAuthService`
- `GoogleIdTokenValidator`
- `AuthenticatedUserResolver`
- `ServiceCatalogService`
- `ServiceDescriptionNormalizer`
- `CalendarEventService`
- `CalendarSyncService`
- `CalendarEventServiceMatcher`
- `RevenueReportService`
- `CashFlowReportService`
- validadores de request
- componentes de autorização
- conversores de DTO
- regras de paginação e filtros
- lógica de decisão entre full sync e incremental sync
- lógica de decisão sobre atualidade dos dados do relatório

**Cenários obrigatórios adicionais**

- validação de `idToken` válido;
- rejeição de token com `aud` inválido;
- rejeição de token expirado;
- rejeição de token com `iss` inválido;
- resolução de usuário interno a partir de `google_sub`;
- garantia de que `authorizationCode` é usado apenas no bootstrap inicial;
- garantia de que sync usa credencial OAuth persistida, e não `idToken`;
- cadastro de serviço com descrição e valor;
- rejeição de descrição vazia;
- rejeição de valor inválido;
- rejeição de duplicidade lógica de serviço;
- normalização correta de descrição;
- exclusão permitida de serviço sem vínculo;
- rejeição de exclusão de serviço com agendamento vinculado;
- matching de título do evento com serviço cadastrado;
- não associação quando não houver serviço equivalente;
- associação apenas dentro do mesmo usuário;
- persistência do valor snapshot no evento;
- garantia de que alteração posterior do preço do serviço não altera o histórico do evento;
- reprocessamento de eventos não identificados após novo cadastro de serviço;
- exclusão local de evento removido no Google;
- composição correta do relatório de faturamento;
- composição cronológica correta do relatório de fluxo de caixa;
- rejeição de relatório de fluxo de caixa com período superior a 7 dias;
- rejeição de relatório de faturamento com período superior a 12 meses;
- emissão de relatório com dados atualizados;
- emissão de relatório com dados não atualizados contendo a data e horário da última sincronização utilizada;
- marcação da integração como inválida quando o token OAuth for revogado;
- interrupção de sync automático após revogação;
- rejeição de apresentação ambígua de relatório quando não for possível garantir atualização.

**Regra operacional**

Sempre que uma nova regra for criada:

1. escrever teste JUnit;
2. executar;
3. confirmar falha;
4. implementar;
5. reexecutar todos os testes unitários relevantes;
6. refatorar;
7. reexecutar novamente.

### 15.3 Testes de integração com Rest Assured

Os testes de integração devem validar:

- contratos HTTP;
- serialização e desserialização;
- status codes;
- autenticação e autorização;
- integração entre controller, service, segurança e persistência;
- comportamento real das capacidades expostas pela API.

**Ferramentas**

- Rest Assured
- Spring Boot Test
- ambiente de persistência dedicado para testes
- mecanismo de ambiente isolado para testes integrados, quando aplicável

**Cenários obrigatórios**

- autenticação inicial com payload válido;
- falha de autenticação com payload inválido;
- cadastro de serviço com payload válido;
- rejeição de duplicidade de serviço;
- listagem retornando apenas serviços do usuário autenticado;
- alteração de descrição e valor do serviço;
- exclusão de serviço sem vínculos;
- rejeição de exclusão de serviço com vínculos;
- leitura de eventos autenticada com bearer Google válido;
- rejeição quando bearer estiver ausente;
- rejeição quando bearer estiver expirado;
- isolamento de dados entre usuário A e usuário B;
- paginação;
- range de datas;
- token inválido;
- disparo de sincronização com autenticação válida;
- respostas de erro padronizadas;
- comportamento de sync quando não há estado incremental prévio;
- comportamento de sync quando há estado incremental válido;
- comportamento de resync quando o estado incremental está expirado;
- sincronização de evento cujo título corresponde a serviço cadastrado;
- sincronização de evento cujo título não corresponde a nenhum serviço;
- exclusão local quando um evento sincronizado for removido no Google;
- garantia de que identificador de usuário enviado pelo cliente é ignorado, se presente;
- relatórios calculando corretamente com base em eventos identificados;
- relatório de faturamento retornando total correto para o período;
- relatório de fluxo de caixa retornando agregação cronológica correta para o período;
- rejeição de relatório de fluxo de caixa com mais de 7 dias;
- rejeição de relatório de faturamento com mais de 12 meses;
- relatório retornando indicação de atualização quando os dados estiverem atualizados;
- relatório retornando data e horário da última sincronização quando os dados não estiverem atualizados;
- revogação de credencial Google exigindo nova autenticação;
- manutenção da leitura sobre base local após revogação da integração.

**Objetivo dos testes de integração**

Garantir que a aplicação funcione corretamente como sistema HTTP real, e não apenas em componentes isolados.

### 15.4 Testes de segurança

Verificar:

- usuário A não acessa dados do usuário B;
- token inválido;
- token expirado;
- ausência de IDOR;
- manipulação indevida de parâmetros;
- proteção de recursos privados;
- impossibilidade de usar identificador de usuário enviado pelo cliente para burlar autorização;
- usuário A não acessa serviços do usuário B;
- usuário A não altera serviço do usuário B;
- usuário A não exclui serviço do usuário B;
- serviços de um usuário não são usados para identificar eventos de outro usuário.

### 15.5 Testes de carga

Com k6, validar:

- latência p50, p95 e p99;
- throughput;
- taxa de erro;
- comportamento sob concorrência;
- estabilidade prolongada;
- impacto do sync concorrente com leitura;
- impacto do sync concorrente com consultas de relatório.

### 15.6 Critério de aprovação por etapa

Nenhuma funcionalidade deve ser considerada concluída se:

- os testes unitários em JUnit não estiverem verdes;
- os testes de integração com Rest Assured não estiverem verdes;
- os testes de segurança do fluxo correspondente não estiverem validados.

---

## 16. Fluxo prático de desenvolvimento com TDD

### 16.1 Sequência padrão por funcionalidade

Para cada funcionalidade, seguir a sequência:

1. definir regra de negócio;
2. escrever testes unitários em JUnit;
3. executar testes e validar falha inicial;
4. implementar o mínimo necessário;
5. reexecutar testes unitários;
6. refatorar código;
7. reexecutar testes unitários novamente;
8. implementar ou ajustar integração;
9. escrever testes de integração com Rest Assured;
10. executar suíte de integração;
11. corrigir desvios;
12. liberar apenas após suíte verde.

### 16.2 Exemplo prático

**Exemplo: identificação de serviço no sync**

Primeiro, escrever testes unitários para:

- normalização do título;
- busca do serviço equivalente;
- associação correta por usuário;
- persistência do valor snapshot;
- comportamento quando não há correspondência.

Depois, implementar:

- normalizador;
- matcher;
- service de sincronização.

Em seguida, reexecutar testes unitários.

Depois, escrever testes de integração para:

- sincronização do evento com título equivalente ao serviço;
- evento sem correspondência;
- isolamento entre usuários;
- retorno correto na leitura dos eventos.

---

## 17. Plano de implementação por fases

### Fase 1 — Fundação do projeto

- criar projeto Spring Boot com Java 21;
- configurar mecanismo de persistência escolhido;
- configurar estratégia de evolução estrutural;
- configurar Spring Security;
- configurar estrutura de testes com JUnit 5;
- configurar base de testes de integração com Rest Assured;
- definir convenções de TDD no projeto.

### Fase 2 — Modelo de autenticação unificada

Ordem obrigatória:

1. escrever testes unitários em JUnit para autenticação;
2. implementar validação de `idToken`;
3. implementar resolução de usuário interno a partir de `google_sub`;
4. implementar troca de `authorizationCode` por credenciais OAuth;
5. remover dependência de token próprio da aplicação;
6. reexecutar testes unitários;
7. escrever testes de integração para autenticação inicial e acesso autenticado com bearer Google.

### Fase 3 — Catálogo de serviços e persistência

- estruturar armazenamento de usuários, credenciais, serviços, eventos e estado de sincronização;
- validar regras de unicidade lógica e isolamento por usuário;
- criar testes unitários para regras de persistência quando houver lógica;
- criar testes de integração para fluxos principais.

Ordem obrigatória:

1. escrever testes unitários para cadastro de serviço;
2. implementar persistência do catálogo de serviços;
3. implementar normalização de descrição;
4. implementar a capacidade de criação de serviço;
5. implementar a capacidade de listagem de serviços;
6. implementar a capacidade de atualização de serviços;
7. implementar a capacidade de exclusão de serviços com bloqueio quando houver vínculos;
8. validar duplicidade lógica por descrição normalizada.

### Fase 4 — Integração com Google Calendar e identificação de serviço

Ordem:

1. escrever testes unitários para serviços de sync e tratamento de falhas;
2. escrever testes unitários para a lógica de decisão entre full sync e incremental sync;
3. escrever testes unitários para matching entre título do evento e serviço cadastrado;
4. implementar client Google OAuth;
5. implementar troca de code por tokens;
6. implementar full sync inicial, buscando todos os agendamentos do calendário principal e armazenando o estado de sincronização;
7. implementar identificação de serviço pelo título do evento;
8. implementar incremental sync, utilizando o estado salvo para buscar apenas eventos novos, alterados ou removidos;
9. implementar remoção local de eventos excluídos no Google;
10. implementar resync completo para casos de expiração do estado incremental;
11. implementar tratamento de revogação de token OAuth com exigência de reautenticação;
12. implementar reprocessamento de eventos não identificados;
13. reexecutar testes unitários continuamente;
14. validar fluxos integrados.

### Fase 5 — Capacidade de leitura

Ordem:

1. escrever testes unitários de service, autorização e paginação;
2. implementar leitura dos eventos sincronizados;
3. incluir na resposta dados do serviço identificado, valor snapshot e status de identificação;
4. reexecutar testes unitários;
5. refatorar;
6. criar testes de integração;
7. validar isolamento por usuário.

### Fase 6 — Relatórios

Ordem:

1. escrever testes unitários para faturamento e fluxo de caixa;
2. escrever testes unitários para verificação de atualidade dos dados do relatório;
3. escrever testes unitários para validação dos limites máximos de período;
4. implementar consultas agregadas sobre eventos sincronizados;
5. implementar a capacidade de geração de relatório de faturamento;
6. implementar a capacidade de geração de relatório de fluxo de caixa;
7. garantir que o relatório de faturamento some corretamente os valores históricos dos eventos elegíveis do período;
8. garantir que o relatório de fluxo de caixa organize cronologicamente os valores históricos dos eventos elegíveis no período;
9. validar cálculos considerando apenas eventos identificados;
10. validar a exibição da data e horário da última sincronização quando os dados não estiverem atualizados;
11. validar a transparência do relatório quando a integração Google estiver revogada.

### Fase 7 — Otimização

- tuning de consultas e persistência;
- tuning de pool ou gestão de recursos equivalente;
- cache opcional;
- profiling;
- benchmark;
- garantir que otimizações não quebrem testes existentes;
- reexecutar suíte unitária a cada ajuste sensível.

### Fase 8 — Hardening de produção

- dashboards;
- alertas;
- rate limiting;
- runbooks;
- readiness/liveness probes;
- pipelines com execução automática de testes unitários e integração.

---

## 18. Critérios de sucesso

A solução estará correta quando:

- uma única API atender todos os usuários;
- cada usuário autenticado receber apenas seus próprios agendamentos e serviços;
- o login Google no aplicativo também servir como autenticação para a API;
- a API não precisar emitir token próprio da aplicação;
- a API permitir o cadastro de serviços com descrição e valor;
- a API permitir a exclusão de serviços apenas quando não houver agendamentos vinculados;
- o título do agendamento do Google Agenda puder ser comparado ao serviço cadastrado do usuário;
- a sincronização identificar corretamente o serviço equivalente quando houver correspondência;
- o evento sincronizado armazenar o vínculo com o serviço e o valor histórico correspondente;
- eventos sem correspondência ficarem marcados como não identificados;
- a base própria da solução for a fonte principal das consultas do app;
- a sincronização com Google ocorrer fora do fluxo crítico;
- a primeira sincronização buscar todos os agendamentos do calendário principal do usuário no Google Calendar;
- as sincronizações subsequentes buscarem apenas eventos novos, alterados ou removidos, utilizando o estado incremental apropriado;
- eventos removidos no Google sejam removidos também da base sincronizada;
- o resync completo for acionado automaticamente em caso de expiração ou invalidação do estado incremental;
- o relatório de faturamento representar corretamente o total financeiro dos eventos elegíveis do período;
- o relatório de fluxo de caixa representar corretamente a distribuição cronológica dos valores dos eventos elegíveis do período;
- o relatório de fluxo de caixa aceitar apenas períodos de no máximo 7 dias corridos;
- o relatório de faturamento aceitar apenas períodos de no máximo 12 meses;
- os relatórios de faturamento e fluxo de caixa forem gerados com base na frequência dos eventos identificados e no valor associado a cada evento;
- a solução verificar a atualidade dos dados antes de emitir relatórios;
- quando os dados não estiverem atualizados, o relatório informar claramente a data e o horário da última sincronização utilizada;
- a alteração futura do preço do serviço não distorcer relatórios históricos;
- a aplicação tratar exclusivamente o calendário principal da conta Google;
- a revogação ou invalidação de OAuth marcar a integração como inválida e exigir nova autenticação;
- a aplicação suportar carga com latência controlada;
- não houver crescimento progressivo de heap, threads ou conexões;
- os testes comprovarem isolamento, estabilidade e segurança;
- o desenvolvimento tiver seguido TDD;
- todos os comportamentos relevantes tiverem sido especificados primeiro em testes unitários com JUnit;
- os fluxos integrados tiverem sido validados por testes de integração com Rest Assured;
- os testes unitários tiverem sido reexecutados continuamente durante a implementação.

---

## 19. Recomendação final de implementação

Para começar de forma equilibrada entre produtividade, performance e qualidade:

- Java 21
- Spring Boot
- Spring Security
- estratégia de persistência adequada ao contexto da solução
- ferramenta de evolução estrutural compatível com a persistência escolhida
- mecanismo eficiente de gestão de conexões, quando aplicável
- JUnit 5 para testes unitários
- Rest Assured para testes de integração
- TDD como metodologia obrigatória
- ambiente isolado para testes de integração
- k6 para carga

**Diretriz de autenticação adotada**

- autenticação unificada via Google;
- uso de `idToken` como bearer token nas chamadas do aplicativo;
- uso de `authorizationCode` apenas para obtenção de credenciais OAuth do Google Calendar;
- ausência de token próprio da API.

**Diretriz de identificação de serviços adotada**

- cada agendamento conterá inicialmente um único serviço;
- o serviço será identificado pelo título do agendamento;
- a comparação será feita com a descrição do serviço cadastrado pelo usuário;
- a associação será feita por texto normalizado;
- o valor do serviço será copiado para o evento sincronizado para preservar histórico financeiro;
- eventos sem correspondência permanecerão registrados, porém marcados como não identificados.

**Diretriz de atualização dos relatórios adotada**

- a solução deve verificar a atualidade dos dados antes de emitir qualquer relatório;
- relatórios só podem ser apresentados como atualizados quando essa condição puder ser garantida;
- quando essa garantia não existir, o relatório deve informar explicitamente que foi emitido com base na última sincronização disponível, incluindo data e horário.

**Diretriz funcional dos relatórios adotada**

- relatório de faturamento: visão consolidada do total financeiro gerado no período;
- relatório de fluxo de caixa: visão cronológica da distribuição das entradas no período;
- ambos devem ser gerados exclusivamente a partir de eventos sincronizados, identificados e com valor histórico preservado;
- ambos devem informar o estado de atualização dos dados utilizados como base;
- relatório de fluxo de caixa: período máximo de 7 dias corridos;
- relatório de faturamento: período máximo de 12 meses.

**Diretriz de escopo Google Calendar adotada**

- a solução cobre somente o calendário principal da conta Google do usuário;
- calendários secundários, compartilhados ou múltiplos calendários não fazem parte do escopo desta versão.

**Diretriz de integridade de serviços adotada**

- um serviço só pode ser excluído se não possuir agendamentos vinculados;
- se houver vínculo com qualquer evento sincronizado, a exclusão deve ser rejeitada.

**Diretriz de exclusão de eventos adotada**

- se um agendamento for removido no Google Agenda, o evento sincronizado correspondente deve ser excluído da base própria;
- eventos removidos não devem permanecer apenas como cancelados para fins operacionais da solução.
