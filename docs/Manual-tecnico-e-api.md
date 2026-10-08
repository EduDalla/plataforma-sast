# Manual técnico e contrato HTTP — E-01

Contrato conferido em 07/10/2026 no checkout identificado no [registro E-01](evidencias/E-01-2026-10-07.md). Fontes: [controller de análises](../src/backend/src/main/java/com/fiap/sast/web/AnalysisController.java), [autenticação](../src/backend/src/main/java/com/fiap/sast/auth/AuthController.java), [configuração](../src/backend/src/main/resources/application.yml) e [migrações](../src/backend/src/main/resources/db/migration). Diagramas em [Arquitetura](Arquitetura-e-fluxos.md).

## Instalação reproduzível

Para o Compose: Docker Engine e Compose, acesso ao GitHub público e espaço para imagens/modelo. Para verificar o produto no host: JDK 21, Maven, Node.js e npm; CI usa Node 22.14.0. Python 3 executa os testes do gate e o ensaio O-01. Docker deve estar acessível ao Testcontainers.

Execute na raiz do monorepo. Crie `.env` somente se ainda não existir:

```bash
test -f .env || cp .env.example .env
# Preencha os valores locais antes de subir os serviços.
docker compose config --quiet
docker compose up --build -d --wait
docker compose ps
docker compose exec ollama ollama pull llama3.2:3b
```

O último comando é opcional e deve usar o modelo configurado. Não substitua um `.env` existente, não publique seu conteúdo e não use `docker compose config` sem `--quiet` em evidências: a saída expandida contém segredos. O Compose constrói apenas o produto local, nunca o repositório analisado.

| Configuração | Uso e padrão |
|---|---|
| `POSTGRES_DB/USER/PASSWORD`, `RABBITMQ_USER/PASSWORD` | Credenciais locais; substituir senhas de exemplo. |
| `SAST_BOOTSTRAP_EMAIL/PASSWORD` | Conta inicial em banco vazio; senha de 12–72 caracteres e até 72 bytes UTF-8. Inicialização concorrente usa lock transacional; contas existentes não são sobrescritas. |
| `SAST_JWT_SECRET` | Base64 de pelo menos 32 bytes; necessário para API e worker. |
| `SAST_JWT_ISSUER`, `SAST_JWT_ACCESS_TOKEN_MINUTES` | `sast-api` e 120 minutos. |
| `WEB_PORT`, `API_PORT`, `DB_PORT` | Exemplo local: 3000, 8080, 5432; bind explícito em loopback pode ser usado em ambiente local. |
| `GITHUB_TOKEN` | Opcional para origem pública; Compose o passa como `SAST_GITHUB_TOKEN` ao backend. Nunca incluir na evidência. |
| `SAST_OLLAMA_MODEL`, `SAST_OLLAMA_BASE_URL` | `llama3.2:3b`, `http://ollama:11434` no Compose. Não há porta Ollama publicada no host. |
| `SAST_OLLAMA_MAX_CANDIDATES/TOTAL_BUDGET_SECONDS` | Padrões 0/0: sem esses limites globais de avaliação, não significa IA desativada. |
| `SAST_OLLAMA_SUGGESTION_MAX_METHODS/SUGGESTION_BUDGET_SECONDS` | 4 métodos e 60 segundos para sugestões. |

No host, o frontend é `http://localhost:3000` e a API `http://localhost:8080`, com as portas de exemplo. Nginx encaminha `/api`; `/health` deve ser consultado na porta da API. No Compose, API e worker usam HTTP interno para Ollama. Não há TLS de entrada configurado.

## Health checks e diagnóstico

| Serviço | Verificação configurada | O que comprova |
|---|---|---|
| db | `pg_isready` | PostgreSQL aceita conexões. |
| rabbitmq | `rabbitmq-diagnostics -q ping` | Nó RabbitMQ responde. |
| api / worker | `curl --fail http://localhost:8080/health` | Endpoint de saúde responde; não comprova execução completa de análise. |
| web | `wget --spider -q http://127.0.0.1/` | Nginx entrega página. |
| ollama | `ollama list` | Serviço responde; não garante modelo baixado ou qualidade da resposta. |

Se uma análise ficar em fila, verificar saúde do broker, API/publicador e worker. Se apenas a IA degradar, conferir disponibilidade do modelo. Para problemas de bootstrap/JWT, conferir formato/configuração sem imprimir valores. Falhas de parse/origem aparecem na consulta da análise. Não anexar logs brutos como evidência; guardar somente estados e contagens revisadas.

`docker compose down` interrompe a instância e preserva volumes nomeados; remoção de volumes apaga dados e não faz parte do roteiro normal. Para falhas provocadas, usar exclusivamente o [ensaio isolado O-01](Operacao-e-escalabilidade.md).

## Autenticação

| Método e rota | Entrada / resposta | Acesso |
|---|---|---|
| POST `/api/auth/register` | `{email,password}`; 201 `{email}` normalizado; duplicata 409; entrada inválida 400 | Público |
| POST `/api/auth/login` | `{email,password}`; 200 `{email,accessToken,tokenType,expiresIn}`; credenciais inválidas 401 | Público |
| GET `/api/auth/session` | 200 `{email}`, sem reemitir token | Bearer |
| POST `/api/auth/logout` | 204, cliente descarta token | Bearer |
| GET `/health` | Saúde sem detalhes internos | Público |

JWT HS256 valida assinatura, emissor e expiração. O access token fica na `sessionStorage` da aba; logout ou 401 removem a sessão no frontend. O backend é stateless: logout não revoga antecipadamente uma cópia do JWT, válida até expirar. Senhas persistem como BCrypt. As configurações de cookie de servlet não substituem o contrato Bearer.

## Análises, tarefas, sistemas e histórico

Todas as rotas abaixo exigem Bearer e restringem dados ao proprietário autenticado.

| Método e rota | Resposta |
|---|---|
| POST `/api/analyses` | Entrada `{repositoryUrl,reference?}`; 202 com `Result` e `Location: /api/analyses/{id}`. |
| GET `/api/analyses/{id}` | 200 `Result`; ID ausente ou de outra pessoa retorna 404. |
| GET `/api/analyses/tasks` | Lista de tarefas em processamento ou encerradas ainda não confirmadas; sem paginação. |
| POST `/api/analyses/tasks/{id}/ack` | 204 inclusive para ID alheio/ausente, sem alterar dados estrangeiros; não exclui análise. |
| GET `/api/analyses/systems?page=0&size=20` | `{systems,page,size,totalSystems,totalAnalyses,totalFindings,totalCritical,totalFiles,resultSummary}`. |
| GET `/api/analyses/systems/{owner}/{repository}/history?page=0&size=20` | `{owner,repositoryName,history,page,size,total}`. Sem execuções visíveis: lista vazia. |

Página é baseada em zero e valores negativos viram zero; tamanho é ajustado para 1–20. A central agrupa todas as execuções visíveis do usuário, incluindo processamento e falhas, e pagina os sistemas em memória; não comprova paginação escalável no banco. Os cartões usam a execução mais recente e permitem abrir o dashboard enquanto ela está em andamento; `SystemCard.totalAnalyses` conta essas execuções visíveis. Os totais da página (`totalAnalyses`, `totalFindings`, `totalCritical`, `totalFiles` e `resultSummary`) consideram somente execuções concluídas e distintas. O histórico inclui os estados em andamento e falhos; a tabela de tendência usa somente execuções concluídas.

```http
POST /api/analyses
Authorization: Bearer <token temporário; não registrar>
Content-Type: application/json

{"repositoryUrl":"https://github.com/OWNER/REPO","reference":"main"}
```

O exemplo é apenas formato, não uma origem autorizada concreta. Para repetir O-01, usar o repositório e SHA identificados no seu registro.

### Contrato Result e resultados derivados

| Grupo | Campos |
|---|---|
| Identificação | `analysisId,status,repositoryUrl,reference,language,createdAt,commitSha` |
| Progresso | `stage,filesAnalyzed,filesProcessed,filesTotal,failureStage,failureMessage` |
| Consultivo | `semanticStatus,suggestionStatus,suggestions` |
| Determinístico | `findings`: `ruleId,title,severity,cwe,description,fileName,line,column,snippet,taintTrace,aiAssessment` |
| Resumo unificado | `resultSummary`: `total,critical,high,medium,low,unclassified,highestPriority` |

O resumo do resultado conta findings e sugestões independentes. Avaliação aninhada não conta como item adicional. Severidades são `Critical`, `High`, `Medium`, `Low`; itens legados sem classificação têm categoria própria.

`taintTrace` contém `engineVersion,source,steps,sink`; cada passo tem `kind,line,column`. `aiAssessment` contém `model,promptVersion,confidence,suggestedSeverity,likelyFalsePositive,rationale,remediation,risk,evidence,falsePositiveReason,limitations,recommendations`; `evidence` e `recommendations` são listas. Uma sugestão independente contém `category,severity,title,fileName,line,evidence,rationale,confidence,recommendation,limitations,model,promptVersion`. Avaliação e trace podem ser nulos; campos consultivos legados podem estar ausentes ou nulos. O frontend deve preservar a origem dos itens e não tratar avaliação aninhada como outra vulnerabilidade.

Cada item do histórico contém `analysisId,reference,createdAt,filesAnalyzed,findings,resultSummary,suggestions,commitSha,coverageStatus,semanticStatus,suggestionStatus,status,stage`. Seu resumo conta **somente findings determinísticos**; sugestões têm contagem separada. Sistemas oferecem `owner,repositoryName,repositoryUrl,latestCreatedAt,totalAnalyses,latest`; `latest` inclui também `status` e `stage` para exibir processamento ou falha. Tarefas oferecem ID, status, etapa, URL, data, estados da IA, resumo e `acknowledged`.

Execuções são deduplicadas pela combinação de repositório, referência, fingerprint de findings, metadados consultivos, hash do snapshot e SHA. Não é deduplicação por SHA isolado. A tendência compara totais determinísticos com a execução anterior comparável; cobertura parcial não vira zero confirmado. O estado `CONFIRMED` exige conclusão, total de arquivos positivo e todos processados, independentemente de IA degradada.

### Estados e falhas

Estados e transições estão em [Arquitetura](Arquitetura-e-fluxos.md). Depois do 202, consultar periodicamente até `COMPLETED` ou `FAILED`; o GET pode retornar 200 com análise falha. `failureStage/failureMessage` registram a falha de processamento de modo seguro. A falha posterior do download não transforma retroativamente o POST em 502.

Rejeições imediatas incluem entrada inválida (400), autenticação ausente/inválida (401), permissão insuficiente (403), recurso inacessível (404) e conflito no cadastro (409). O [tratador HTTP](../src/backend/src/main/java/com/fiap/sast/web/ApiExceptionHandler.java) também mapeia exceções síncronas para 413, 422, 429 e 502; estes códigos não devem ser apresentados como resposta garantida a falhas ocorridas dentro do worker. Não depender de corpo idêntico para todo 401 do filtro de segurança.

## Catálogo e limites

| Regra | CWE / severidade | Escopo |
|---|---|---|
| SAST-JAVA-001 | CWE-798 / Critical | Heurística de credencial hardcoded. |
| SAST-JAVA-002 | CWE-78 / High | Chamada estrutural a Runtime.exec. |
| SAST-JAVA-003 | CWE-502 / High | Chamada estrutural a ObjectInputStream.readObject. |
| TAINT-CMDI-001 | CWE-78 / Critical | Fonte HTTP até Runtime.exec ou construção de ProcessBuilder, intraprocedural. |

As regras estão em [rules](../src/backend/src/main/java/com/fiap/sast/rules) e [taint](../src/backend/src/main/java/com/fiap/sast/taint). A regra estrutural pode reportar comando constante mesmo sem taint. Ver [Q-01](Qualidade-taint-e-IA.md) para corpus, sanitizador heurístico, métricas e limitações; a revisão qualitativa das remediações reais ainda está pendente.

Archive: 100 MiB; até 1.000 arquivos Java; 2 MiB por arquivo; 500 MiB extraídos, inclusive entradas ignoradas; 10.000 entradas ZIP (limite interno). Os quatro primeiros são configuráveis pelas variáveis `SAST_GITHUB_MAX_*` no Compose. Caminhos perigosos são rejeitados; symlinks e diretórios `target,build,out,.gradle,node_modules,vendor` são ignorados. JavaParser está configurado para sintaxe Java 21. Não há execução de fonte analisada.

## Modelo de dados e retenção

| Tabela | Chaves e relações | Conteúdo |
|---|---|---|
| app_users | UUID PK; email único | Email e hash BCrypt; sem senha em claro. |
| analyses | UUID PK; user_id FK para app_users | Origem, referência/SHA, hash, estados, progresso, falha, confirmação e lease. user_id é nullable no schema legado; novas análises autenticadas recebem proprietário. |
| findings | UUID PK; analysis_id FK com exclusão em cascata | Regra, CWE, severidade, localização, snippet e taint_trace serializado em TEXT. |
| ai_assessments | finding_id é PK/FK, 0..1 por finding, cascata | Modelo/versão de prompt, confiança, severidade sugerida, justificativa e remediação; não é prompt bruto. |
| ai_suggestions | UUID PK; analysis_id FK com cascata | Categoria, prioridade, localização, evidência limitada e recomendação consultiva. |
| analysis_outbox | UUID PK; analysis_id FK com cascata | Tipo, datas de criação/disponibilidade/publicação, tentativas e erro seguro. |

Uma pessoa possui várias análises; cada análise pode ter vários findings, sugestões e eventos. A FK de usuário não declara exclusão em cascata. Não existem endpoint de exclusão ou expurgo periódico por idade implementados; dados derivados e eventos publicados permanecem no banco até intervenção administrativa. O ACK só modifica a confirmação. Cache de resultados concluídos é local, limitado e com validade de 15 minutos; não é política de retenção do banco.

| Migração | Evolução |
|---|---|
| V1 | analyses e findings. |
| V2 | app_users, propriedade da análise e índice por usuário. |
| V3 | Trace de taint no finding. |
| V4 | Estados/metadados semânticos e ai_assessments. |
| V5 | Detalhes consultivos de risco, evidência, limitações e recomendações. |
| V6 | ai_suggestions, estado de sugestões, snapshot_hash e índice por análise. |
| V7 | Etapas, contadores e falha assíncrona. |
| V8 | Severidade própria da sugestão. |
| V9 | Confirmação da tarefa. |
| V10 | SHA, tentativas, lease, próxima tentativa e outbox; índices de lease e publicação. |
| V11 | Índices de leitura por usuário/status/data e usuário/repositório/status/data. |

O schema é validado pelo Hibernate e evolui por Flyway. Não reescrever migrações aplicadas. Lease e retomada evitam duplicatas nos cenários testados; detalhes, falhas provocadas e limites de capacidade estão em [O-01](Operacao-e-escalabilidade.md). Retenção automática, alta disponibilidade e testes de grande escala não são entregas comprovadas.

## Verificação

Comandos, versões, resultados atuais e cenários ignorados constam no [registro E-01](evidencias/E-01-2026-10-07.md). Para a demonstração, seguir o [roteiro de oito minutos](Evidencias-e-roteiro-de-defesa.md) e a [rastreabilidade BDD](Rastreabilidade-BDD.md). Nunca executar Maven, Gradle, JVM ou testes sobre o repositório analisado.
