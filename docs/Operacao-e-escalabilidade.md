# O-01 — Operação e escalabilidade

Os ensaios deste pacote verificam a operação da plataforma Java na arquitetura existente. O worker recebe somente UUIDs do RabbitMQ e analisa snapshots públicos em memória. Os testes executam o produto; o código recebido continua sendo dado para o parser, nunca um programa a compilar ou executar.

## Ambiente e reprodução

Execute a partir da raiz com JDK 21, Maven, Node, Docker e Python 3. Os testes de integração criam PostgreSQL 18 e RabbitMQ 4 separados dos serviços do usuário. GitHub e Ollama são simulados nesses testes; parser, regras, persistência, JWT, publicador, listener e reconciliador são reais.

```bash
MAVEN_OPTS="-Djdk.attach.allowAttachSelf=true" mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
docker compose config --quiet

# Ensaio real: constrói o produto e cria um projeto Compose temporário com seis serviços.
# Use um destino novo: o script não sobrescreve evidências anteriores.
python3 scripts/operation_smoke.py --output /tmp/sast-o01-evidence.json
```

O script usa o repositório público deste projeto, `https://github.com/EduDalla/plataforma-sast`, e fixa o SHA da referência `main` antes da submissão. O uso está restrito à análise estática do projeto em escopo desta solicitação; não é uma autorização para copiar ou executar outros repositórios. Outra origem precisa ser pública e autorizada; informe `--repository-url` e `--reference` quando necessário.

O projeto Compose recebe nome aleatório `sast-o01-...`, portas dinâmicas em loopback, banco e volumes próprios, credenciais temporárias em memória e nenhum token GitHub. O script não lê o `.env`. Ao terminar, seu `finally` remove apenas os contêineres, redes e volumes desse projeto temporário; imagens de build e os serviços existentes ficam disponíveis. Caso a limpeza falhe, a evidência informa o nome exato do projeto para recuperação.

Ollama é inicialmente iniciado para conferir sua versão e depois interrompido somente na instância temporária. Os orçamentos consultivos do ensaio são um candidato e dois segundos por etapa, suficientes para provar degradação por indisponibilidade sem carregar modelo. A evidência de qualidade com modelo real continua em [Q-01](Qualidade-taint-e-IA.md).

`db`, `rabbitmq`, `api`, `worker`, `web` e `ollama` possuem health checks. A fila usa dois consumidores e prefetch de uma mensagem por consumidor. Cada lease dura 60 s e é renovado a cada 15 s; o reconciliador é acionado a cada 10 s. O publicador roda a cada segundo e exige confirmação do broker (espera de até 10 s após o envio); a conexão RabbitMQ tem timeout de 5 s. Uma publicação não confirmada mantém o evento, com backoff de `min(60, 5 × tentativas)` segundos. Falhas transitórias de origem permitem até três tentativas; os atrasos definidos são 30, 120 e 300 segundos, embora o terceiro erro encerre a análise e não agende uma quarta tentativa.

## Cenários BDD e rastreabilidade

Os IDs abaixo aparecem nos nomes de exibição JUnit; a documentação usa Gherkin com passos em português, sem exigir Cucumber.

```gherkin
Feature: Operação segura da plataforma assíncrona

  Scenario: BDD-OP-01 - Duas análises são processadas simultaneamente
    Given duas tarefas aceitas e persistidas com eventos de outbox
    When os dois consumidores assumem tarefas distintas ao mesmo tempo
    Then ambas concluem com seus findings e o SHA fixado
    And cada chamada ao parser mantém estado próprio
    And um segundo usuário não consulta nem confirma a tarefa alheia

  Scenario: BDD-OP-02 - Uma mensagem duplicada não duplica a análise
    Given uma mensagem com o UUID de uma análise já assumida ou concluída
    When o broker entrega a duplicata ao worker
    Then o claim não inicia outra execução
    And a contagem de tentativas e findings permanece estável

  Scenario: BDD-OP-03 - RabbitMQ fica temporariamente indisponível
    Given uma análise com evento de outbox ainda não confirmado
    When o RabbitMQ é interrompido na instância de teste
    Then o evento permanece pendente com backoff
    When o RabbitMQ volta a aceitar conexões
    Then o publicador confirma a entrega e a análise conclui

  Scenario: BDD-OP-04 - Um worker interrompido deixa um lease expirado
    Given uma tarefa com lease vencido e dados parciais da tentativa anterior
    When o reconciliador recupera a tarefa
    Then somente um evento pendente é criado
    And a nova tentativa substitui os dados parciais
    And o SHA já resolvido é preservado

  Scenario: BDD-OP-05 - URL malformada é recusada antes da persistência
    Given uma URL HTTP ou um host diferente do github.com exato
    When o usuário solicita uma análise autenticada
    Then a API responde 400
    And nenhuma análise, evento ou download é criado

  Scenario: BDD-OP-06 - Archive perigoso ou acima dos limites é recusado
    Given um archive sintético em memória com caminho perigoso ou tamanho excedido
    When o cliente aplica a filtragem de entradas
    Then caminhos absolutos, traversal e barras Windows são rejeitados
    And links simbólicos e diretórios de build são ignorados
    And limites de arquivos Java e bytes extraídos são impostos

  Scenario: BDD-OP-07 - O código recebido nunca é executado
    Given o fixture Java vulnerável mantido em memória
    When o parser e as regras determinísticas analisam seu conteúdo
    Then existem exatamente três findings no fixture oficial
    And o inicializador Java não cria seu arquivo marcador

  Scenario: BDD-OP-08 - Falha transitória de origem é retomada com o mesmo SHA
    Given o download falha depois de resolver o commit
    When o prazo de backoff ainda não expirou
    Then o reconciliador não antecipa outra tentativa
    When o prazo expira e a origem volta a responder
    Then a análise conclui na segunda tentativa com o mesmo SHA

  Scenario: BDD-OP-09 - Payload inválido não é executado
    Given uma mensagem cujo corpo não é um UUID
    When o consumidor valida o payload
    Then a mensagem é rejeitada sem requeue e encaminhada à DLQ
    And nenhum download ou tarefa é iniciado

  Scenario: BDD-OP-10 - Ollama indisponível não apaga findings
    Given a etapa determinística terminou e persistiu seus findings
    When o Ollama está indisponível na instância de teste
    Then a análise conclui com a etapa consultiva DEGRADED
    And findings e severidades determinísticos permanecem disponíveis

  Scenario: BDD-OP-11 - Evidência operacional não publica dados sensíveis
    Given contas temporárias e resultados com snippets necessários à apresentação
    When logs e resultados do ensaio são inspecionados
    Then os logs não contêm senhas, tokens ou snippets conhecidos
    And o relatório contém apenas IDs, estados, contagens, tempos e métricas de recursos

  Scenario: BDD-OP-12 - API e worker iniciam com banco vazio
    Given duas instâncias tentam criar o mesmo usuário bootstrap
    When ambas iniciam transações concorrentes no PostgreSQL
    Then somente uma conta é criada com hash BCrypt
    And a segunda instância preserva a conta existente sem conflito de unicidade
```

| Cenário | Teste automatizado ou ensaio | Evidência |
| --- | --- | --- |
| OP-01 | `OperationIntegrationTest.concurrentTasksAndIsolation`, `JavaParserSourceParserTest.parserSingletonPreservaFontesConcorrentes`, `operation_smoke.py` | Barreira de dois downloads, ASTs distintas, duas tentativas ativas observadas e JWT. |
| OP-02 | `OperationIntegrationTest.duplicateDelivery`, script | Uma tentativa normal, sem findings adicionais; duplicata também enviada enquanto tarefas aguardam na fila. |
| OP-03 | `OperationIntegrationTest.brokerOutage`, script | Broker real interrompido e recuperado, outbox pendente e depois confirmada. |
| OP-04 | `OperationIntegrationTest.expiredLease`, script | Evento de recuperação, segunda tentativa e substituição de finding parcial. |
| OP-05 | `OperationIntegrationTest.invalidOriginIsRejectedBeforePersistence`, `GitHubClientTest`, script | Rejeição HTTP e contagens zero antes das submissões válidas. |
| OP-06 | `GitHubClientTest.operationArchiveLimits`, `archiveFilteringAndLimits`, `ignoresUnixSymlinkFromCentralDirectory` | ZIPs sintéticos em memória, nenhuma extração em disco. |
| OP-07 | `SastEngineTest.fixtureVulneravelProduzTresFindingsComTodosOsCampos`, `analiseNaoExecutaInicializadorDoCodigoFonte` | Três regras e marcador inexistente. |
| OP-08 | `OperationIntegrationTest.retryAfterTransientSnapshotFailure` | Backoff exercitado por prazos controlados no banco de teste e resolução de SHA apenas uma vez. |
| OP-09 | `OperationIntegrationTest.malformedMessageIsDeadLettered` | Uma mensagem na DLQ e nenhum download. |
| OP-10 | Cada conclusão em `OperationIntegrationTest`, script | `DEGRADED` e findings preservados; indisponibilidade simulada no JUnit e real no Compose. |
| OP-11 | `OperationIntegrationTest.concurrentTasksAndIsolation`, script | Comparação de sentinelas e valores transitórios, sem gravar os logs no relatório. |
| OP-12 | `OperationIntegrationTest.concurrentBootstrapCreatesOnlyOneUser`, inicialização do Compose | Duas transações no PostgreSQL e uma única conta, sem senha nos logs. |

## Correções encontradas durante os ensaios

A instância singleton do parser compartilhava um `JavaParser` mutável entre consumidores. O cenário de dois downloads sincronizados não concluía corretamente; a criação de um parser/configuração por chamada elimina a interferência mantendo Java 21, os contratos e o cache de AST restrito à tentativa.

O leitor ZIP normalizava barras Windows antes de `getName()`. Agora o cliente também verifica `getRawName()` para recusar barras no nome original. Isso preserva o requisito de caminho seguro antes de qualquer processamento do conteúdo.

Na criação de um banco vazio, API e worker podiam observar zero usuários e inserir o mesmo bootstrap simultaneamente. O bootstrap agora adquire um advisory lock transacional PostgreSQL de chave fixa `638019001` antes de consultar a existência de usuários. O lock termina com a transação, não adiciona tabelas e não sobrescreve usuários; a concorrência é exercitada por duas transações no cenário OP-12.

O health check do frontend usa `127.0.0.1` porque `localhost` pode resolver para IPv6 enquanto a configuração atual do Nginx escuta em IPv4. Os health checks acrescentados ao worker, web e Ollama completam a verificação dos seis serviços.

## Evidências e limites da medição

O relatório JSON do script registra a versão do Ollama, o SHA analisado, limites configurados, UUIDs, estados, contagens, tentativas, tempo até conclusão, backlog da fila e máximo de memória observado da API/worker. A execução de 07/10/2026 está em [evidência O-01](evidencias/O-01-2026-10-07.json): resultado `PASSED`, entre 20:37:32 e 20:39:08 (America/Sao_Paulo), host x86_64 com 16 CPUs, Ollama 0.34.4.

O SHA público analisado foi `f1a32e2e315acb1cc7b56377151f98a3635a58eb`. As quatro análises processaram 66/66 arquivos Java e preservaram um finding determinístico cada, com avaliações e sugestões em `DEGRADED`. O fixture unitário oficial continua sendo outro caso: nele são exatamente três findings, sem execução.

| Medida | Resultado observado |
| --- | --- |
| Tentativas ativas simultâneas | 2 |
| Pico amostrado de mensagens prontas na fila | 4 |
| Amostras de memória/fila durante processamento | 6 |
| Memória máxima observada da API | 631.452.467 bytes (602,2 MiB) |
| Memória máxima observada do worker | 698.771.046 bytes (666,4 MiB) |
| Execução submetida durante queda do broker | 37,434 s; uma tentativa do worker, evento preservado |
| Duas submissões simultâneas | 23,565 s e 27,139 s; uma tentativa cada |
| Recuperação do lease expirado | 23,672 s; duas tentativas contabilizadas, um finding final |
| Valores sensíveis conhecidos encontrados nos logs | 0 |
| Isolamento JWT, duplicata e retomada | Verificados |
| Ollama recuperado e projeto temporário removido | Sim |

O `verify` final registrou 80 testes, zero falhas/erros e 13 ignorados; oito cenários de operação rodaram com PostgreSQL e RabbitMQ reais. O frontend passou no build e em 40 testes; `docker compose config --quiet` passou. Os ignorados são nove cenários HTTP síncronos legados e quatro cenários opcionais de IA real, cuja evidência está no Q-01.

O tempo inclui espera na fila, interrupções provocadas, inicialização do worker e polling. A memória é o máximo **amostrado** de `docker stats`, não um pico absoluto, nem a medição exclusiva de heap Java. O polling e a coleta têm custo próprio e podem perder picos entre amostras. O backlog retido é deliberado para exercitar fila e duplicação.

A máquina continuou executando a instância de desenvolvimento e outros processos. O projeto Compose foi isolado em dados, rede e portas, mas não recebeu uma máquina dedicada nem reserva de CPU. Os tempos registrados são observações dessa execução, não um benchmark de hardware isolado.

O prazo do evento de outbox é antecipado no banco de teste depois de provar a falha de publicação, para evitar espera desnecessária. A recuperação de lease é provocada por um registro de tentativa interrompida, retirada da mensagem já confirmada e expiração da concessão; não é uma morte real de processo no meio de uma análise. Os testes de integração também controlam os prazos no seu banco isolado. Nenhum desses ajustes atinge o banco da instância de desenvolvimento.

Uma única máquina Compose com duas tentativas ativas comprova separação dos serviços e concorrência nessa carga. Não estabelece throughput, percentis de latência, capacidade de múltiplas réplicas, alta disponibilidade ou limites de produção. O ensaio de lease simula processo morto com concessão expirada; não comprova proteção contra um proprietário antigo que continue executando depois de perder a concessão.

Os limites impostos pelo cliente são archive de 100 MiB, até 10.000 entradas, até 1.000 arquivos Java, 2 MiB por arquivo Java e 500 MiB extraídos. O conteúdo ignorado também entra no orçamento de extração. Esses valores são limites de segurança configurados; a medição não afirma que análises no teto terminam dentro de um SLA.

Tokens e credenciais do ensaio permanecem transitórios. O script não preserva archives, código integral, prompts, respostas brutas ou logs completos. A auditoria compara segredos gerados e snippets conhecidos; isso não prova ausência de qualquer informação sensível possível.
