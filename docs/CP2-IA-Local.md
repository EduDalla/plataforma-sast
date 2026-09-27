# CP2 — Classificação consultiva com IA local

Esta entrega implementa o segundo estágio da [ADR-001](adr/ADR-001-ia-e-analise-semantica.md). Após as regras da CP1 e o taint analysis, `SemanticAnalysisService` avalia findings com o modelo `llama3.2:3b` no Ollama local. A IA nunca cria, remove ou altera findings ou suas severidades determinísticas. O frontend mostra a sugestão separadamente.

Uma extensão consultiva adicional examina métodos Java selecionados pela AST mesmo quando não há findings. Ela apresenta possíveis problemas de segurança e desempenho, inclusive N+1, em uma seção independente. Essas hipóteses não entram na contagem de vulnerabilidades. N+1 exige confirmação com consultas observadas em execução.

## Operação

Configure o `.env` conforme `.env.example` e inicie os serviços com `docker compose up --build -d`. Na primeira execução, baixe o modelo para o volume nomeado com:

```bash
docker compose exec ollama ollama pull llama3.2:3b
```

O Ollama não publica porta no host. A API usa `http://ollama:11434` no Compose; fora dele, `SAST_OLLAMA_BASE_URL` pode apontar para um Ollama local. `SAST_OLLAMA_MODEL` define o modelo. `SAST_OLLAMA_MAX_CANDIDATES=0` e `SAST_OLLAMA_TOTAL_BUDGET_SECONDS=0` significam analisar todos os candidatos sem orçamento agregado. Cada chamada individual tem timeout máximo de 20 segundos e uma repetição apenas para falha transitória. O proxy Nginx aguarda até 180 segundos por resposta da API, incluindo o download do GitHub.

A varredura de métodos usa `SAST_OLLAMA_SUGGESTION_MAX_METHODS=4` e `SAST_OLLAMA_SUGGESTION_BUDGET_SECONDS=60` por padrão. Cada tentativa tem timeout máximo de 30 segundos e pede no máximo uma hipótese curta por método. A AST prioriza consultas de dados dentro de laços e operações sensíveis de segurança; laços genéricos não são enviados. O contexto se concentra na operação selecionada e tem no máximo 1.800 caracteres. O limite de métodos ou de tempo produz `suggestionStatus: DEGRADED`, indicando cobertura parcial; `COMPLETED` indica que todos os métodos candidatos foram consultados e `NOT_APPLICABLE` indica ausência de candidatos. A varredura não promete cobertura de todos os métodos do repositório. O orçamento dessa etapa é adicional ao enriquecimento dos findings, portanto análises grandes ainda podem atingir o timeout do proxy.

Se o modelo ainda não tiver sido baixado, a API continua funcionando: `semanticStatus` será `DEGRADED` e todos os findings determinísticos permanecerão disponíveis. Uma nova análise com o modelo pronto tenta enriquecer novamente o resultado degradado.

## Execução assíncrona e polling

`POST /api/analyses` apenas valida a URL, cria o registro e enfileira um worker. A resposta é `202 Accepted`, com `Location: /api/analyses/{id}` e estado inicial `status: PROCESSING`, `stage: QUEUED`. O frontend navega imediatamente para essa URL e consulta o recurso a cada dois segundos. O polling é cancelado ao sair da tela, fazer logout ou receber `COMPLETED`/`FAILED`.

O worker publica as etapas `DOWNLOADING`, `DETERMINISTIC`, `SEMANTIC`, `SUGGESTIONS` e `COMPLETED`. `filesProcessed` e `filesTotal` permitem acompanhar o progresso; findings determinísticos e seus contadores são persistidos por arquivo antes das avaliações do Ollama. Sugestões consultivas são gravadas individualmente e aparecem no polling seguinte sem alterar a quantidade ou severidade dos findings. Em falhas, `status: FAILED`, `failureStage` e uma mensagem segura preservam o resultado parcial. Jobs que estavam `PROCESSING` quando a API reinicia são marcados como falhos por interrupção do worker.

## Contrato e dados

`POST /api/analyses` e `GET /api/analyses/{id}` retornam `semanticStatus`: `NOT_APPLICABLE` se não houver findings, `COMPLETED` se todos forem avaliados, `DEGRADED` se algum ficar sem avaliação. O campo opcional `aiAssessment` de cada finding contém `model`, `promptVersion`, `confidence`, `suggestedSeverity`, `likelyFalsePositive`, `rationale`, `remediation`, `risk`, `evidence`, `falsePositiveReason`, `limitations` e `recommendations`. O campo `severity` original e todas as contagens continuam derivados apenas das regras.

O resultado também retorna `suggestionStatus` e `suggestions` (lista, possivelmente vazia). Cada sugestão contém `category` (`SECURITY` ou `PERFORMANCE`), `severity` (`Critical`, `High`, `Medium` ou `Low`), `title`, `fileName`, `line`, `evidence`, `rationale`, `confidence`, `recommendation`, `limitations`, `model` e `promptVersion`. A criticidade usa a mesma escala visual dos findings, mas seus contadores permanecem próprios da seção consultiva. Sugestões antigas sem esse campo aparecem como `Sem classificação` até uma nova análise. A linha e a evidência são extraídas pela API do código da operação selecionada pela AST; o modelo não pode inventá-las. Respostas JSON inválidas são descartadas e deixam a etapa degradada. Uma resposta válida com lista vazia conclui a etapa sem criar sugestão.

As avaliações ficam em `ai_assessments`, vinculadas aos findings; a análise guarda estado, modelo e versão do prompt. Resultados completos só são reutilizados quando findings e traces, modelo e versão do prompt coincidirem. O contexto transitório enviado ao modelo contém somente metadados, trace e até 6.000 caracteres do método do finding e de até dois métodos chamados diretamente no mesmo arquivo quando a chamada é inequívoca. Respostas JSON são validadas; código, prompts e respostas brutas não entram em logs nem no banco.

As hipóteses ficam em `ai_suggestions`, vinculadas à análise e ao usuário por essa relação. A análise guarda `suggestion_status` e um hash SHA-256 do snapshot Java; a reutilização exige o mesmo hash, modelo, versões dos prompts e ambas as etapas concluídas ou não aplicáveis. O hash não armazena o código-fonte. Cada método candidato fornece no máximo 1.800 caracteres de contexto transitório ao Ollama local; apenas sugestões validadas são persistidas.

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
