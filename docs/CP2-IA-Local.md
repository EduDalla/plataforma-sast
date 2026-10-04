# CP2 — Classificação consultiva com IA local

Esta entrega implementa o segundo estágio da [ADR-001](adr/ADR-001-ia-e-analise-semantica.md). Após as regras da CP1 e o taint analysis, `SemanticAnalysisService` avalia findings com o modelo `llama3.2:3b` no Ollama local. A IA nunca cria, remove ou altera findings ou suas severidades determinísticas. A avaliação `aiAssessment` permanece dentro do finding; a severidade da regra continua sendo sua prioridade visual.

Uma extensão consultiva adicional examina métodos Java selecionados pela AST mesmo quando não há findings. Findings e sugestões independentes são apresentados como uma lista única de vulnerabilidades/melhorias. Cada item preserva sua origem (regra, IA de segurança ou IA de desempenho) e sua prioridade por severidade. Sugestões independentes continuam sendo itens próprios, inclusive quando compartilham arquivo e linha com um finding; não há fusão automática. N+1 exige confirmação com consultas observadas em execução.

## Operação

Configure o `.env` conforme `.env.example` e inicie os serviços com `docker compose up --build -d`. Na primeira execução, baixe o modelo para o volume nomeado com:

```bash
docker compose exec ollama ollama pull llama3.2:3b
```

O Ollama não publica porta no host. A API usa `http://ollama:11434` no Compose; fora dele, `SAST_OLLAMA_BASE_URL` pode apontar para um Ollama local. `SAST_OLLAMA_MODEL` define o modelo. `SAST_OLLAMA_MAX_CANDIDATES=0` e `SAST_OLLAMA_TOTAL_BUDGET_SECONDS=0` significam analisar todos os candidatos sem orçamento agregado. Cada avaliação de finding tem timeout máximo de 60 segundos e uma repetição apenas para falha transitória. Esse limite permite que o modelo local conclua o JSON estruturado em uma máquina sem GPU. O proxy Nginx aguarda até 180 segundos por resposta da API, incluindo o download do GitHub.

O serviço `ollama` tem um alias explícito na rede `ollama_net`. Se as avaliações e sugestões terminarem em `DEGRADED` mesmo com o modelo instalado, confira a resolução do nome e a conectividade a partir da API:

```bash
docker compose exec -T api getent hosts ollama
docker compose exec -T api curl --fail --silent --show-error http://ollama:11434/api/tags
```

Se o alias tiver sido adicionado após a criação dos contêineres, aplique a configuração com `docker compose up -d --no-deps --force-recreate ollama`. O volume `ollama_data` preserva o modelo. Repita uma análise para atualizar os estados degradados; o código do repositório analisado continua sem ser executado.

A varredura de métodos usa `SAST_OLLAMA_SUGGESTION_MAX_METHODS=4` e `SAST_OLLAMA_SUGGESTION_BUDGET_SECONDS=60` por padrão. Cada tentativa tem timeout máximo de 30 segundos e pede no máximo uma hipótese curta por método. A AST prioriza consultas de dados dentro de laços e operações sensíveis de segurança; laços genéricos não são enviados. O contexto se concentra na operação selecionada e tem no máximo 1.800 caracteres. O limite de métodos ou de tempo produz `suggestionStatus: DEGRADED`, indicando cobertura parcial; `COMPLETED` indica que todos os métodos candidatos foram consultados e `NOT_APPLICABLE` indica ausência de candidatos. A varredura não promete cobertura de todos os métodos do repositório. O orçamento dessa etapa é adicional ao enriquecimento dos findings, portanto análises grandes ainda podem atingir o timeout do proxy.

Se o modelo ainda não tiver sido baixado, a API continua funcionando: `semanticStatus` será `DEGRADED` e todos os findings determinísticos permanecerão disponíveis. Uma nova análise com o modelo pronto tenta enriquecer novamente o resultado degradado.

## Execução assíncrona e polling

`POST /api/analyses` apenas valida a URL, grava a análise e um evento de outbox na mesma transação. Um publicador envia somente o UUID ao RabbitMQ e aguarda publisher confirm; a resposta é `202 Accepted`, com `Location: /api/analyses/{id}` e estado inicial `status: PROCESSING`, `stage: QUEUED`. O serviço Compose `worker` separado usa dois consumidores com prefetch 1. A execução é uma task persistida: o consumidor faz claim atômico, confirma a mensagem e o worker executa a tentativa inteira em memória. Entregas duplicadas são descartadas pela posse no PostgreSQL.

O worker publica as etapas `DOWNLOADING`, `DETERMINISTIC`, `SEMANTIC`, `SUGGESTIONS` e `COMPLETED`. `filesProcessed` e `filesTotal` permitem acompanhar o progresso; findings determinísticos e seus contadores são persistidos por arquivo antes das avaliações do Ollama. Sugestões consultivas são gravadas individualmente e aparecem no polling seguinte. O frontend atualiza os contadores unificados durante o polling; `aiAssessment` não cria um item adicional. O primeiro ciclo fixa o SHA do commit pela API de commits do GitHub; retomadas baixam esse mesmo SHA sem persistir o código. A concessão é renovada durante o processamento; um reconciliador cria novo evento de outbox após expiração. Cada tentativa limpa resultados parciais, há no máximo três tentativas com esperas de 30 segundos, 2 minutos e 5 minutos, e falhas finais marcam `FAILED` com mensagem segura. A task concluída gera um único aviso final por execução, com total e maior prioridade.

## Contrato e dados

`POST /api/analyses` e `GET /api/analyses/{id}` retornam `semanticStatus`: `NOT_APPLICABLE` se não houver findings, `COMPLETED` se todos forem avaliados, `DEGRADED` se algum ficar sem avaliação. O campo opcional `aiAssessment` de cada finding contém `model`, `promptVersion`, `confidence`, `suggestedSeverity`, `likelyFalsePositive`, `rationale`, `remediation`, `risk`, `evidence`, `falsePositiveReason`, `limitations` e `recommendations`. O campo `severity` original permanece derivado apenas das regras. A resposta também inclui `resultSummary` com `total`, contagens `critical`, `high`, `medium`, `low`, `unclassified` e `highestPriority`. O resumo conta findings e sugestões persistidos; a avaliação aninhada não entra na contagem. `total` é a soma das cinco categorias e `highestPriority` usa a ordem Crítica, Alta, Média, Baixa, Sem classificação, ou `null` quando não há itens.

O resultado também retorna `suggestionStatus` e `suggestions` (lista, possivelmente vazia). Cada sugestão contém `category` (`SECURITY` ou `PERFORMANCE`), `severity` (`Critical`, `High`, `Medium` ou `Low`), `title`, `fileName`, `line`, `evidence`, `rationale`, `confidence`, `recommendation`, `limitations`, `model` e `promptVersion`. A severidade da sugestão usa a mesma escala de prioridade dos findings. Sugestões antigas sem esse campo são contabilizadas em `unclassified` e aparecem como `Sem classificação` até uma nova análise. A linha e a evidência são extraídas pela API do código da operação selecionada pela AST; o modelo não pode inventá-las. Respostas JSON inválidas são descartadas e deixam a etapa degradada. Uma resposta válida com lista vazia conclui a etapa sem criar sugestão.

As avaliações ficam em `ai_assessments`, vinculadas aos findings; a análise guarda estado, modelo e versão do prompt. Resultados completos só são reutilizados quando findings e traces, modelo e versão do prompt coincidirem. O contexto transitório enviado ao modelo contém somente metadados, trace e até 6.000 caracteres do método do finding e de até dois métodos chamados diretamente no mesmo arquivo quando a chamada é inequívoca. Respostas JSON são validadas; código, prompts e respostas brutas não entram em logs nem no banco.

As hipóteses ficam em `ai_suggestions`, vinculadas à análise e ao usuário por essa relação. A análise guarda `suggestion_status` e um hash SHA-256 do snapshot Java; a reutilização exige o mesmo hash, modelo, versões dos prompts e ambas as etapas concluídas ou não aplicáveis. O hash não armazena o código-fonte. Cada método candidato fornece no máximo 1.800 caracteres de contexto transitório ao Ollama local; apenas sugestões validadas são persistidas. Em novas gravações, uma sugestão independente substitui a anterior quando análise, arquivo e linha forem iguais; sugestões antigas não são reorganizadas.

## Demonstração

1. Com o modelo pronto, analise pelo frontend uma URL pública e referência de um repositório Java vulnerável. Os três findings determinísticos devem continuar preservados; cada um apresenta a seção “Sugestão da IA”.
2. Para ver um trace, use um repositório Java público com parâmetro HTTP alcançando `Runtime.exec()`, ou execute o cenário determinístico em `TaintAnalysisEngineTest`. O finding `TAINT-CMDI-001` apresenta o trace e a avaliação consultiva. Não execute nem compile o repositório analisado.
3. Com o Ollama indisponível, repita a análise: o estado fica `DEGRADED`, mas os findings e as contagens não mudam.

Os testes automatizados simulam o Ollama e não precisam de rede nem de pesos do modelo.

Para repetir a prova com o modelo real sem alterar os dados da aplicação, após iniciar o Ollama e baixar o modelo, execute na raiz do monorepo:

```bash
docker run --rm --network cp1-cyber_ollama_net \
  -e SAST_RUN_LIVE_OLLAMA=true -e SAST_OLLAMA_BASE_URL=http://ollama:11434 \
  -v "$PWD":/workspace -w /workspace maven:3.9-eclipse-temurin-21 \
  mvn --file src/backend/pom.xml -Dtest=LiveOllamaDemoTest test
```

O teste lê as amostras como texto, nunca as executa, e exige três avaliações válidas, uma avaliação com trace e uma hipótese consultiva de N+1 sem finding determinístico. Se o nome de projeto do Compose for alterado, ajuste o nome da rede no comando.

## Visão geral no dashboard do sistema

O dashboard da execução selecionada separa achados determinísticos de sugestões consultivas de segurança e performance. Os contadores de vulnerabilidades, críticas e arquivos Java pertencem à execução selecionada; o contador de análises usa o total do histórico do sistema, inclusive quando o histórico está paginado. Arquivos com pontos de atenção contam caminhos distintos presentes nos achados ou nas sugestões.

As prioridades de revisão combinam findings e sugestões e mostram os quatro itens de maior prioridade, cada qual com origem visível. O gráfico, os totais, os arquivos com ocorrências, os cartões dos sistemas e o histórico usam o mesmo resumo; o dashboard da execução selecionada é atualizado quando se troca a execução no histórico. O endpoint da central resume globalmente as execuções consideradas, inclusive as que não estão na página atual, enquanto cada cartão apresenta a última execução daquele sistema. O estado da varredura informa cobertura parcial, ausência de candidatos, processamento ou cobertura desconhecida mesmo quando não existem sugestões. Zero achados não atesta segurança nem ausência de oportunidades de desempenho. O gráfico com tendência ilustrativa foi removido para evitar apresentar uma evolução sem dados históricos reais. Os detalhes das sugestões (evidência, justificativa, recomendação, confiança e limitações) aparecem na página `/analyses/{id}`; a origem e a prioridade também aparecem na lista unificada. A análise distingue ausência confirmada de itens, cobertura parcial, processamento e falha. Resultado parcial sem itens não é apresentado como ausência de problemas.

A mudança adiciona `resultSummary` às respostas da análise, ao histórico e aos totais da central. Os campos antigos permanecem com semântica determinística para compatibilidade; não há migração de banco. A mudança não amplia as regras ou a seleção de métodos enviados ao modelo. Os testes cobrem resultados mistos, somente sugestões, itens antigos sem severidade, estados parciais e prioridade preservada em `aiAssessment`.


## Resumo de prioridade nas rotas

`GET /api/analyses/{id}` retorna `resultSummary` para os itens daquela execução. Cada entrada de `GET /api/analyses/systems/{owner}/{repository}/history` inclui o mesmo resumo. `GET /api/analyses/systems` inclui `resultSummary` agregado às execuções distintas concluídas consideradas pelo endpoint, sem limitar o total à página de cartões; `latest.resultSummary` em cada cartão representa somente a execução mais recente daquele sistema. Não há alteração de persistência, paginação ou isolamento por usuário.
