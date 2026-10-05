# Critérios de aceite e evidências da defesa

Use esta lista para concluir a entrega, mantendo a [matriz de requisitos](Matriz-de-requisitos-CP3.md) e o [plano](Plano-de-adequacao-CP3.md) atualizados. Uma caixa marcada exige evidência reproduzível; não deve ser marcada apenas porque o código parece existir.

## Preparação do ambiente

- [x] Ambiente do host com JDK 21, Maven, Node.js, Docker e Docker Compose documentado. **Evidência em 05/10/2026:** `java -version` e `mvn -version` mostram OpenJDK 21.0.12.1; Docker e Compose também estão disponíveis.
- [x] Ambiente containerizado com JDK 21 disponível para build e execução do backend/worker. **Evidência em 05/10/2026:** as imagens usam `maven:3.9-eclipse-temurin-21` e `eclipse-temurin:21-jre`; os serviços iniciaram com Java 21.
- [x] `mvn --file src/backend/pom.xml verify` passa com PostgreSQL de teste disponível ao Testcontainers. **Evidência enviada em 05/10/2026:** no host, a execução com `MAVEN_OPTS="-Djdk.attach.allowAttachSelf=true"` e `-Dsast.build.directory=/tmp/cp1-cyber-build` terminou em `BUILD SUCCESS`: 64 testes contabilizados, zero falhas, zero erros e 12 ignorados. O teste `AnalysisReadRepositoryIntegrationTest` executou com Testcontainers.
- [x] `npm --prefix src/frontend run build` passa. **Evidência em 05/10/2026:** Vite concluiu o build de produção.
- [x] `npm --prefix src/frontend run test -- --run` passa. **Evidência em 05/10/2026:** 5 arquivos e 39 testes passaram.
- [x] `docker compose config --quiet` passa sem revelar variáveis do `.env`. **Evidência em 05/10/2026:** comando concluído sem saída.
- [x] `docker compose up --build -d` inicia `db`, `rabbitmq`, `api`, `worker`, `web` e `ollama`; health check da API responde em `/health`. **Evidência em 05/10/2026:** seis serviços em execução; `{"groups":["liveness","readiness"],"status":"UP"}` consultado dentro do contêiner da API.

O `verify` acima foi executado no host com JDK 21, Docker disponível e agente do Mockito habilitado pela opção de JVM. Dos 12 testes ignorados, nove pertencem a `AuthenticatedAnalysisIntegrationTest`, desabilitada enquanto os cenários síncronos são adaptados para polling, e três são testes opcionais de Ollama real, habilitados apenas por `SAST_RUN_LIVE_OLLAMA=true`. O `mvn package -DskipTests` usado no Dockerfile não substitui essa verificação. Permanecem pendentes a integração HTTP assíncrona e o ensaio com modelo local.

Uma repetição no ambiente isolado do agente em 05/10/2026 não reproduziu o `verify` do host: o Testcontainers não teve acesso ao Docker e o Mockito não conseguiu carregar o agente, resultando em 15 erros de ambiente. No mesmo ambiente isolado, o build do frontend, seus 39 testes e `docker compose config --quiet` passaram. O resultado positivo do backend nesta tabela é a evidência do log enviado pelo usuário, não uma verificação independente nesse ambiente isolado.

## Casos funcionais a demonstrar

Esta demonstração deve ser feita com uma conta de teste e um repositório público autorizado. Os comandos abaixo são executados a partir da raiz do monorepo; `API_URL`, `TOKEN_A` e `TOKEN_B` são valores temporários da sessão e não devem ser registrados no documento ou nos logs.

| Caso | Roteiro reproduzível | Evidência observável | Situação no checkout |
| --- | --- | --- | --- |
| Cadastro, login, isolamento e logout | `POST /api/auth/register` para duas contas; `POST /api/auth/login` para obter dois Bearer tokens; criar uma análise com `TOKEN_A` e consultar o UUID com `TOKEN_B`; depois executar `POST /api/auth/logout` e recarregar a aplicação. | A consulta do segundo usuário retorna `404`/ausência de dados; cada navegador vê somente suas tarefas; `sessionStorage` deixa de conter `sast-access-token` após logout. | Há testes de autenticação e isolamento, mas `AuthenticatedAnalysisIntegrationTest` está desabilitado enquanto os cenários síncronos são substituídos por polling; ensaio com Compose pendente. |
| Origem válida e origem inválida | Com `TOKEN_A`, enviar `POST /api/analyses` com `{"repositoryUrl":"https://github.com/OWNER/REPO","reference":"main"}`; repetir com host diferente, esquema HTTP, caminho incompleto e referência inválida. | A origem válida retorna `202 Accepted`, `Location` e estágio `QUEUED`; cada origem inválida falha antes de criar tarefa ou evento de outbox. | Validação coberta por `GitHubClientTest`; falta registrar a execução HTTP integrada. |
| Andamento, resultado, tarefas e histórico | Consultar `GET /api/analyses/{id}` até `status=COMPLETED` ou `FAILED`; consultar `GET /api/analyses/tasks` e `/api/analyses/systems/{owner}/{repo}/history` com o mesmo token. | A resposta mostra `stage`, `filesProcessed`, `filesTotal`, estados semânticos e findings; tarefas e histórico pertencem ao usuário autenticado. | Contrato implementado e testes de controller existentes; a demonstração ponta a ponta permanece pendente. |
| Fixture Java sem execução | Executar `mvn --file src/backend/pom.xml -Dtest=SastEngineTest test`; guardar a saída do teste `fixtureVulneravelProduzTresFindingsComTodosOsCampos` e `analiseNaoExecutaInicializadorDoCodigoFonte`. | Exatamente três IDs (`SAST-JAVA-001`, `SAST-JAVA-002`, `SAST-JAVA-003`), com CWE, severidade, arquivo, linha, coluna e trecho; o marcador do inicializador não é criado. | Os quatro testes de `SastEngineTest` passaram no `verify` de 05/10/2026, inclusive o fixture de três findings e a prova de que o inicializador do código analisado não é executado. |
| Achados distintos na mesma linha | Executar o teste dedicado depois de corrigir a identidade do finding para incluir regra, coluna e nó; incluir no fixture credencial e `Runtime.exec()` na mesma linha. | Os dois achados aparecem no JSON e no total; uma duplicata idêntica continua sendo deduplicada. | O teste atual `mantemUltimaVerificacaoNaMesmaLinhaDoMesmoArquivo` documenta a limitação antiga e ainda espera substituição. Caso bloqueado até a correção do motor. |
| Taint positivo e sanitizado | Executar `mvn --file src/backend/pom.xml -Dtest=TaintAnalysisEngineTest test` com o fluxo `@RequestParam → concatenação → Runtime.exec()` e com `replaceAll` sanitizador. | O caso positivo contém `source`, passos de propagação e `sink` em `taintTrace`; o sanitizado e o fluxo interprocedural não geram o alerta. | Os cinco testes de `TaintAnalysisEngineTest` passaram no `verify` de 05/10/2026. |
| IA consultiva e degradação | Com Ollama disponível, executar `SAST_RUN_LIVE_OLLAMA=true mvn --file src/backend/pom.xml -Dtest=LiveOllamaDemoTest test`; repetir com Ollama indisponível usando os testes de `SemanticAnalysisServiceTest`. | `aiAssessment.suggestedSeverity` e remediação aparecem separadas da severidade do finding; indisponibilidade produz `DEGRADED` e mantém os findings determinísticos. | Cobertura de contrato e degradação existe; ensaio com modelo local é opcional e ainda não registrado. |
| Dashboard, histórico e estados não seguros | Abrir o dashboard após três execuções concluídas do mesmo repositório e uma execução falha/parcial; comparar `/systems` e `/history`. | A tabela acessível mostra data, referência/SHA, total, críticas, variação, cobertura e recomendações; aumento, queda e estabilidade são calculados somente entre execuções confirmadas. Falha, processamento e cobertura degradada não são desenhados como zero seguro. | Implementado no dashboard e no contrato de histórico; falta apenas registrar a demonstração integrada com Compose. |
| Pipeline e Security Gate | Submeter um PR seguro e um PR de fixture com `Critical` novo ao workflow versionado; repetir com análise `FAILED` ou sem cobertura determinística. | `Security Gate` consulta o SHA fixado, compara baseline e bloqueia Critical novo, falha ou cobertura incompleta; severidade sugerida pela IA não decide. | Implementado em `.sast/security-gate.json`, `scripts/security_gate.py` e `.github/workflows/ci.yml`; secrets, branch protegida e ensaio HTTP ainda pendentes. |
| Branch protegida | No GitHub, configurar a branch principal para exigir o check do workflow e registrar a configuração administrativa separadamente do YAML. | Um merge sem check obrigatório é bloqueado; o documento identifica o nome exato do check e a branch protegida. | Pendente junto com CI/Security Gate; não declarar esta proteção apenas pela existência de um arquivo de workflow. |

### Comandos mínimos para a defesa

```bash
# Regras, parser, taint e prova de não execução do fixture em memória
mvn --file src/backend/pom.xml -Dtest=SastEngineTest,TaintAnalysisEngineTest test

# Contratos de frontend (sem acessar GitHub ou executar código analisado)
npm --prefix src/frontend run test -- --run

# Verificação do contrato de serviços
docker compose config --quiet
```

Para o ensaio HTTP, guardar somente status, UUID, estados, contagens e localizações. Não copiar token, corpo de código, prompt, resposta bruta do Ollama ou conteúdo do `.env` para a ata, o relatório ou a tabela de evidências.

## Evidências de segurança, qualidade e operação

Registro verificado em 05/10/2026 contra o checkout atual. A classificação abaixo separa evidência automatizada no código do produto, ensaio operacional e lacunas que ainda exigem um ambiente autorizado.

| Item | Status | Evidência reproduzível e limite da conclusão |
| --- | --- | --- |
| Fonte analisada permanece em memória; nenhum processo do repositório alvo é executado, compilado ou testado | Comprovado no escopo do teste | `GitHubClient.download` usa archive em memória e `SastEngineTest.analiseNaoExecutaInicializadorDoCodigoFonte` confirma que um inicializador Java não é executado. O teste do fixture `SastEngineTest.fixtureVulneravelProduzTresFindingsComTodosOsCampos` produz exatamente três findings. Isso cobre o fluxo unitário; ainda não substitui um ensaio ponta a ponta com um repositório público autorizado. |
| Archive inseguro, link simbólico, caminho perigoso e limite excedido são rejeitados/ignorados | Comprovado por teste unitário | `GitHubClientTest.archiveFilteringAndLimits` cobre diretório `target`, Zip Slip e limite por arquivo; `GitHubClientTest.ignoresUnixSymlinkFromCentralDirectory` cobre link simbólico. A implementação também limita entradas, arquivos Java e bytes extraídos e valida o redirecionamento para `codeload.github.com`. |
| O serviço de CI não publica token, segredo, snippet, prompt, resposta bruta de IA nem código-fonte integral | Parcial | O checkout não possui workflow em `.github/workflows/`; portanto não há evidência de CI nem artefato publicado. No produto, `RequestLoggingFilter`, `ApiExceptionHandler` e os serviços semânticos registram eventos, contagens, estados e tipos de erro sem corpos, snippets, prompts ou respostas brutas; `SAST_GITHUB_TOKEN` é consumido apenas no backend. A política de CI continua pendente até C-01. |
| Corpus rotulado de regras e taint registra falsos positivos e falsos negativos; IA é consultiva | Parcial | Há fixtures e testes para regras, taint, respostas inválidas e estado `DEGRADED` (`SecurityRulesTest`, `TaintAnalysisEngineTest`, `SemanticAnalysisServiceTest` e `SemanticSuggestionServiceTest`). Não há corpus versionado com rótulos, denominadores, falsos positivos/falsos negativos ou métrica de impacto da IA; Q-01 permanece pendente. |
| Retentativa de publicação, mensagem duplicada e retomada do worker têm evidência de teste ou ensaio operacional | Parcial | O código implementa outbox com confirmação, backoff, claim por lease, `basicAck` de mensagens duplicadas e reconciliação de leases (`AnalysisOutboxPublisher`, `AnalysisWorkerListener` e `AnalysisLeaseReconciler`). `AnalysisControllerTest` cobre a criação transacional da outbox, mas não há teste dedicado de falha/duplicação/retomada nem ensaio com RabbitMQ; O-01 permanece pendente. |
| Capacidade observada (tempo, memória, concorrência e limites) é registrada sem alegar escalabilidade de produção | Pendente | Os limites de archive, arquivos Java, arquivo individual e bytes extraídos estão configurados no Compose e exercitados por teste. Ainda não foram registrados tempo por análise, pico de memória, concorrência ou comportamento sob duas tarefas; não há base para declarar capacidade de produção. |
| Código usado na demonstração é próprio, licenciado para esse uso ou possui autorização documentada | Parcial | O fixture principal é uma string Java mantida em memória no teste (`SastEngineTest`), sem cópia de repositório de terceiros. A demonstração com repositório público ainda precisa registrar a URL, SHA, licença ou autorização no pacote de evidências, sem anexar o código integral. |

### Comandos e resultados registrados

No checkout verificado, `npm --prefix src/frontend run build`, `npm --prefix src/frontend run test -- --run` (5 arquivos e 39 testes) e `docker compose config --quiet` passaram. Os testes unitários específicos de `SastEngineTest`, `GitHubClientTest`, `TaintAnalysisEngineTest`, `SemanticAnalysisServiceTest`, `SemanticSuggestionServiceTest` e `AnalysisJobServiceTest` passaram quando isolados dos testes que exigem recursos bloqueados pelo sandbox.

O `mvn verify` completo não foi considerado evidência positiva neste ambiente: o teste de integração com Testcontainers não encontrou um daemon Docker. A execução também não é uma prova negativa do produto. Os testes que usam Mockito falharam ao carregar o agente Byte Buddy e os testes HTTP locais do cliente Ollama não puderam abrir socket; esses resultados devem ser repetidos em ambiente de desenvolvimento/CI com Docker e permissões de instrumentação habilitados. Nenhum teste executou código baixado de repositório analisado.

Não marcar como concluídos os itens de CI, corpus de precisão, ensaio de RabbitMQ/worker, capacidade ou autorização do repositório de demonstração sem anexar a evidência correspondente ao registro abaixo.

## Pacote documental da entrega

- [x] Diagrama C4 de contexto e contêineres: usuário, GitHub público, frontend, API, PostgreSQL, RabbitMQ, worker e Ollama; setas e fronteiras de confiança corretas. Ver [Arquitetura e fluxos](Arquitetura-e-fluxos.md).
- [x] Manual de instalação e configuração sem valores reais de `.env`. Ver [Manual técnico e API](Manual-tecnico-e-api.md).
- [x] Contratos HTTP e estados `QUEUED`, `DOWNLOADING`, `DETERMINISTIC`, `SEMANTIC`, `SUGGESTIONS`, `COMPLETED` e `FAILED` documentados. Ver [Manual técnico e API](Manual-tecnico-e-api.md).
- [ ] Catálogo de regras e CWE, limites da taint analysis, política do Security Gate e comportamento de degradação da IA documentados.
- [x] Relatório analítico no dashboard com repositório, referência/SHA, severidades, tendência, arquivos críticos e limitações de cobertura; exportação não faz parte do contrato atual.
- [ ] Matriz RF01–RF18 e RNF01–RNF08 revisada com links para código, testes e evidências reais.
- [x] Roteiro de defesa e matriz de evidências produzidos. Ver [Evidências e roteiro de defesa](Evidencias-e-roteiro-de-defesa.md). Responsáveis do grupo e gravação alternativa continuam pendentes.

O catálogo técnico, as limitações, as evidências existentes e as lacunas estão descritos nos quatro documentos acima; o relatório analítico com tendência está implementado no dashboard, enquanto a evidência integrada e a proteção administrativa da branch continuam pendentes.

## Roteiro curto de defesa

1. Apresentar o problema, escopo Java e decisão registrada sobre RF01/RF02 e tecnologias.
2. Mostrar C4 e seguir uma tarefa da API até a outbox, RabbitMQ, worker, AST, regras, PostgreSQL e dashboard.
3. Demonstrar três violações, taint e remediação consultiva; explicar limites e métricas de precisão.
4. Exibir dois PRs de teste: um aprovado e outro bloqueado por Critical novo; explicar timeout e falha segura.
5. Comparar execuções no dashboard e mostrar tendência, arquivos críticos e relatório.
6. Encerrar com testes, ética de uso, riscos conhecidos e divisão das contribuições do grupo.

## Registro de evidências

Preencher para cada item concluído:

| Item | Responsável | Commit/PR | Comando ou caso | Resultado | Data |
| --- | --- | --- | --- | --- | --- |
| Exemplo: RF18 | A definir | A definir | PR com fixture Critical | Pendente | A definir |

Não registrar segredos, dados pessoais, código integral de terceiros nem conteúdo do `.env` nesta tabela.
