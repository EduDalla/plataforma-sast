# Manual técnico e contrato HTTP

## Pré-requisitos

- JDK 21, Maven, Node.js, Docker e Docker Compose.
- Variáveis do `.env` preenchidas a partir de `.env.example`; não registrar seus valores.
- Para avaliação consultiva, um modelo Ollama compatível com `SAST_OLLAMA_MODEL`. O fluxo determinístico funciona sem ele, com estado consultivo degradado quando aplicável.

## Subida local

```bash
cp .env.example .env
docker compose up --build -d
docker compose exec ollama ollama pull llama3.2:3b
```

Serviços padrão: frontend em `http://localhost:3000`, API em `http://localhost:8080` e health check em `/health`. O comando `ollama pull` é opcional para demonstrar a parte consultiva.

## Autenticação

| Método | Rota | Acesso | Resultado |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Público | `201` e e-mail normalizado; senha é armazenada como BCrypt. |
| `POST` | `/api/auth/login` | Público | `200` com `Bearer`, token e expiração. |
| `GET` | `/api/auth/session` | Bearer | E-mail da sessão atual, sem reemitir token. |
| `POST` | `/api/auth/logout` | Bearer | `204`; o cliente descarta o token da `sessionStorage`. |

Senhas têm entre 12 e 72 caracteres na entrada e no máximo 72 bytes UTF-8. O JWT é enviado em `Authorization: Bearer <token>`. Todas as consultas de análise filtram o proprietário; conhecer um UUID não concede acesso a outro usuário.

## Análises

| Método | Rota | Acesso | Uso |
| --- | --- | --- | --- |
| `POST` | `/api/analyses` | Bearer | Cria análise para `repositoryUrl` e `reference` opcional; retorna `202` e `Location`. |
| `GET` | `/api/analyses/{id}` | Bearer | Consulta progresso, estado, findings, sugestões e resumo. |
| `GET` | `/api/analyses/tasks` | Bearer | Lista tarefas e estados do próprio usuário. |
| `POST` | `/api/analyses/tasks/{id}/ack` | Bearer | Confirma uma tarefa do próprio usuário. |
| `GET` | `/api/analyses/systems?page=&size=` | Bearer | Lista sistemas/repositórios do próprio usuário. |
| `GET` | `/api/analyses/systems/{owner}/{repository}/history?page=&size=` | Bearer | Histórico concluído do repositório do próprio usuário. |

Exemplo de requisição válida:

```http
POST /api/analyses
Authorization: Bearer <token>
Content-Type: application/json

{"repositoryUrl":"https://github.com/OWNER/REPO","reference":"main"}
```

O campo `stage` informa a etapa; `filesProcessed` e `filesTotal` permitem acompanhar a análise determinística. Quando resolvido, `commitSha` identifica o commit de 40 caracteres analisado. Findings possuem regra, CWE, severidade, descrição, arquivo, linha, coluna e snippet; quando existente, `taintTrace` registra fonte, propagação e sink. `aiAssessment` é consultivo e não substitui a severidade do finding.

## Limites de origem

São aceitas apenas URLs HTTPS com host exato `github.com`, proprietário e repositório válidos e referência validada. O worker valida o redirecionamento para `codeload.github.com`, rejeita Zip Slip e links simbólicos e ignora diretórios de dependências/build. Nenhum caminho de repositório privado é habilitado por este contrato.

## Operação segura

Não use `git clone`, Maven, Gradle, JVM ou qualquer runtime sobre o repositório analisado. Os comandos de build e teste deste documento executam somente o produto e seus fixtures controlados. Logs devem conter eventos, status e identificadores técnicos, nunca tokens, fonte, prompts ou respostas brutas da IA.
