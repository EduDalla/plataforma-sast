# Plataforma SAST

Monorepo de uma plataforma de análise estática de segurança (SAST) para arquivos Java de repositórios públicos do GitHub.

A plataforma baixa um snapshot do repositório, inspeciona o código sem executá-lo e apresenta:

- achados de regras determinísticas;
- rastros de taint;
- avaliações consultivas feitas por uma IA local.

Cada pessoa acompanha suas análises, sistemas e histórico pelo frontend.

## Sumário

- [Visão geral](#visão-geral)
- [Integrantes](#integrantes)
- [Funcionalidades](#funcionalidades)
- [Como funciona](#como-funciona)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Inicialização](#inicialização)
- [Depuração remota do backend](#depuração-remota-do-backend)
- [Verificação](#verificação)
- [Documentação](#documentação)

## Visão geral

O contrato técnico desta versão é:

| Item | Definição |
| --- | --- |
| Entrada | URL HTTPS de repositório público do GitHub |
| Linguagem analisada | Exclusivamente Java |
| Stack | JavaParser, Spring Boot, PostgreSQL, RabbitMQ, React, Docker e Ollama |
| Relatório | Dashboard HTTP, resumo, histórico e tendência |

Essa interpretação está alinhada ao registro D-01, à matriz de requisitos e ao roteiro de defesa, e foi confirmada pelo grupo.

## Integrantes

| Nome | RM |
| --- | --- |
| Mariana Neugebauer | 550494 |
| Heloísa Real | 554535 |
| Fernando Luiz Silva Antonio | 555201 |
| Abner de Paiva Barbosa | 558468 |
| Beatriz Vieira de Novais | 554746 |
| Eduardo Dallabella Lima | 556803 |

## Funcionalidades

**Acesso e análise**

- Cadastro, login e análises isoladas por usuário, com JWT Bearer.
- Análise assíncrona, com acompanhamento das etapas e dos resultados parciais.

**Regras de detecção**

- Credencial hardcoded (CWE-798).
- `Runtime.exec()` (CWE-78).
- `ObjectInputStream.readObject()` (CWE-502).
- Taint analysis intraprocedural para injeção de comando.

**Avaliação por IA**

- Avaliação consultiva dos achados e sugestões independentes de segurança ou desempenho, feitas pelo Ollama local.
- A IA não altera os achados nem as severidades determinísticas.

**Acompanhamento**

- Central de sistemas, dashboard por repositório, tendência determinística por execução, histórico e resultados com origem e prioridade dos itens.

**Integração contínua**

- Workflow do GitHub Actions e política local de Security Gate implementados.
- A execução externa e a proteção da branch exigem evidência própria.

## Como funciona

```text
Frontend → API → PostgreSQL (tarefa + outbox) → publicador → RabbitMQ → worker
Worker → GitHub REST API → snapshot Java em memória → parser/AST e regras → Ollama local
Worker → PostgreSQL → API → frontend (consulta periódica)
```

1. `POST /api/analyses` valida a URL e cria uma tarefa, respondendo `202 Accepted`.
2. O worker baixa o snapshot oficial, seleciona os arquivos `.java`, executa as regras e registra o progresso.
3. O frontend consulta a API até a conclusão ou falha.

Se a IA estiver indisponível, a etapa consultiva pode ficar degradada, sem remover os findings determinísticos. O código do repositório analisado nunca é compilado ou executado.

## Estrutura do repositório

| Caminho | Conteúdo |
| --- | --- |
| `src/frontend` | Aplicação React, TypeScript e Vite. O Nginx serve os arquivos e encaminha `/api` à API. |
| `src/backend` | API e worker Spring Boot, integração com GitHub, JavaParser, regras, taint analysis, persistência e integração com Ollama. |
| `compose.yaml` e `docker/` | Ambiente local com frontend, API, worker, PostgreSQL, RabbitMQ e Ollama. |
| `docs-entrega/` | Decisões, detalhes técnicos, evidências e materiais da defesa. Os testes automatizados ficam junto ao backend e ao frontend. |

## Inicialização

### Pré-requisitos

Docker e Docker Compose.

### Passo a passo

Execute na raiz do monorepo:

```bash
test -f .env || cp .env.example .env
# Configure no .env: SAST_BOOTSTRAP_EMAIL, SAST_BOOTSTRAP_PASSWORD e SAST_JWT_SECRET.
docker compose up --build -d --wait
docker compose exec ollama ollama pull llama3.2:3b
```

### Configuração do `.env`

| Variável | Requisito |
| --- | --- |
| `SAST_BOOTSTRAP_EMAIL` | E-mail da conta criada no primeiro início com banco vazio. |
| `SAST_BOOTSTRAP_PASSWORD` | Mínimo de 12 caracteres e máximo de 72 bytes UTF-8. |
| `SAST_JWT_SECRET` | Chave Base64 de pelo menos 32 bytes. |

Observações:

- Substitua também as senhas de exemplo do PostgreSQL e do RabbitMQ no `.env`.
- O bootstrap cria a conta uma única vez e não sobrescreve usuários existentes.
- Depois da inicialização, também é possível criar contas pela tela de cadastro.
- O token do GitHub é opcional para repositórios públicos e deve ficar somente no backend.

### Modelo de IA

O comando `ollama pull` baixa o modelo indicado por `SAST_OLLAMA_MODEL`. Ajuste o nome no comando se alterar essa variável. Sem o modelo pronto, a análise determinística continua disponível e a avaliação consultiva pode aparecer como degradada.

### Endereços

Com as portas de `.env.example`:

| Serviço | URL |
| --- | --- |
| Frontend | http://localhost:3000 |
| API | http://localhost:8080 |
| Health check | http://localhost:8080/health |

As portas podem ser alteradas por `WEB_PORT` e `API_PORT` no `.env`.

### Sessão e acesso

- O login devolve um JWT válido por 120 minutos por padrão.
- O frontend guarda o token na `sessionStorage` da aba.
- Cada usuário vê apenas suas próprias análises.
- Não há recuperação de senha implementada.

## Depuração remota do backend

Para expor a porta JDWP da API apenas no computador local:

```bash
docker compose -f compose.yaml -f compose.debug.yaml up --build
```

- `compose.debug.yaml` é um override e precisa ser usado junto com `compose.yaml`.
- Conecte a IDE a `localhost:5005` por socket.
- A aplicação inicia sem aguardar o depurador (`suspend=n`).
- `DEBUG_PORT` altera apenas a porta externa.

## Verificação

### Pré-requisitos

JDK 21, Maven, Node.js e Docker.

### Comandos

Execute na raiz:

```bash
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
python3 -m unittest discover -s scripts -p 'test_security_gate.py' -v
docker compose config --quiet
```

Instale as dependências do frontend com `npm --prefix src/frontend ci` quando necessário.

### Notas

- Os testes de integração usam PostgreSQL e RabbitMQ descartáveis, via Testcontainers.
- O fixture Java vulnerável em memória deve produzir exatamente três findings. Seu código é lido como texto e não é executado.
- Não publique a saída expandida do Compose nem o conteúdo do `.env`.

### Quando `src/backend/target` não é gravável

Isso pode acontecer, por exemplo, depois de uma execução em contêiner que criou arquivos pertencentes a outro usuário. Nesse caso, não use `sudo`, `chown` nem remova o conteúdo do checkout. Execute o procedimento seguro:

```bash
bash scripts/maven_local_verify.sh
```

O procedimento cria um diretório temporário pertencente ao usuário atual, direciona todo o build do Maven para ele e o remove ao terminar. Por padrão, usa JDK 21 e `MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true'`.

## Documentação

**Referências gerais**

- [Regras gerais do sistema](REGRAS.MD) e [instruções para agentes](AGENTS.md).
- [Pacote `docs-entrega`](docs-entrega/README.md): índice dos arquivos exigidos, contribuições e ata/roteiro da defesa.

**Planejamento e escopo**

- [Matriz de requisitos da entrega final](docs-entrega/Matriz-de-requisitos-CP3.md): estado atual, lacunas e decisões de escopo.
- [Plano de adequação](docs-entrega/Plano-de-adequacao-CP3.md): ordem de implementação e critérios de aceite.
- [Decisões de escopo](docs-entrega/Decisoes-de-escopo.md): contrato D-01, justificativas e registro da validação da orientação.

**Arquitetura e operação**

- [Arquitetura e fluxos](docs-entrega/Arquitetura-e-fluxos.md): contexto, contêineres, estados e fronteiras de confiança.
- [Manual técnico e API](docs-entrega/Manual-tecnico-e-api.md): inicialização, autenticação, rotas e limites de origem.
- [CI e operação](docs-entrega/CI-e-operacao.md): workflow, verificações automatizadas e limite entre CI e Security Gate.
- [Operação e escalabilidade](docs-entrega/Operacao-e-escalabilidade.md): evidências O-01 e limites.

**Qualidade, evidências e defesa**

- [Critérios de aceite e defesa](docs-entrega/Criterios-de-aceite-e-defesa.md): verificações, evidências e roteiro de apresentação.
- [Evidências e roteiro de defesa](docs-entrega/Evidencias-e-roteiro-de-defesa.md): comandos, demonstração e situação por tema.
- [Rastreabilidade BDD](docs-entrega/Rastreabilidade-BDD.md): requisitos, cenários, fixtures, testes e evidências.
- [Qualidade do taint e da IA](docs-entrega/Qualidade-taint-e-IA.md): evidências Q-01 e limites.
- [Registro E-01](docs-entrega/evidencias/E-01-2026-10-07.md): revisão, ambiente e resultados desta verificação documental.

Esses documentos distinguem implementação, testes locais e evidências externas. Uma validação externa pendente não significa que a funcionalidade esteja ausente do código; observe a situação específica de cada requisito.
