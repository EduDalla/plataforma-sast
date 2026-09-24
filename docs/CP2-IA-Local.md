# CP2 — Classificação consultiva com IA local

Esta entrega implementa o segundo estágio da [ADR-001](adr/ADR-001-ia-e-analise-semantica.md). Após as regras da CP1 e o taint analysis, `SemanticAnalysisService` avalia findings com o modelo `llama3.2:3b` no Ollama local. A IA nunca cria, remove ou altera findings ou suas severidades determinísticas. O frontend mostra a sugestão separadamente.

## Operação

Configure o `.env` conforme `.env.example` e inicie os serviços com `docker compose up --build -d`. Na primeira execução, baixe o modelo para o volume nomeado com:

```bash
docker compose exec ollama ollama pull llama3.2:3b
```

O Ollama não publica porta no host. A API usa `http://ollama:11434` no Compose; fora dele, `SAST_OLLAMA_BASE_URL` pode apontar para um Ollama local. `SAST_OLLAMA_MODEL` define o modelo; `SAST_OLLAMA_MAX_CANDIDATES` (10) e `SAST_OLLAMA_TOTAL_BUDGET_SECONDS` (45) limitam a etapa semântica. Cada chamada tem timeout máximo de 20 segundos e uma repetição apenas para falha transitória. O proxy Nginx aguarda até 180 segundos por resposta da API, incluindo o download do GitHub.

Se o modelo ainda não tiver sido baixado, a API continua funcionando: `semanticStatus` será `DEGRADED` e todos os findings determinísticos permanecerão disponíveis. Uma nova análise com o modelo pronto tenta enriquecer novamente o resultado degradado.

## Contrato e dados

`POST /api/analyses` e `GET /api/analyses/{id}` retornam `semanticStatus`: `NOT_APPLICABLE` se não houver findings, `COMPLETED` se todos forem avaliados, `DEGRADED` se algum ficar sem avaliação. O campo opcional `aiAssessment` de cada finding contém `model`, `promptVersion`, `confidence`, `suggestedSeverity`, `likelyFalsePositive`, `rationale` e `remediation`. O campo `severity` original e todas as contagens continuam derivados apenas das regras.

As avaliações ficam em `ai_assessments`, vinculadas aos findings; a análise guarda estado, modelo e versão do prompt. Resultados completos só são reutilizados quando findings e traces, modelo e versão do prompt coincidirem. O contexto transitório enviado ao modelo contém somente metadados, trace e até 2.000 caracteres de linhas do método ao redor do finding. Respostas JSON são validadas; código, prompts e respostas brutas não entram em logs nem no banco.

## Demonstração

1. Com o modelo pronto, analise pelo frontend a URL pública e referência do monorepo. `samples/VulnerableExample.java` deve continuar com exatamente três findings; cada um apresenta a seção “Sugestão da IA”.
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

O teste lê a amostra como texto, nunca a executa, e exige três avaliações válidas e uma avaliação com trace. Se o nome de projeto do Compose for alterado, ajuste o nome da rede no comando.
