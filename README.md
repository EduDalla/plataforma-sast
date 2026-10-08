# Plataforma SAST

Monorepo de uma plataforma de análise estática de segurança para arquivos Java de repositórios públicos do GitHub. A plataforma baixa um snapshot, inspeciona o código sem executá-lo e apresenta achados de regras determinísticas, rastros de taint e avaliações consultivas por IA local. Cada pessoa acompanha suas análises, sistemas e histórico pelo frontend.

O contrato técnico desta versão é: entrada por URL HTTPS de repositório público do GitHub, análise exclusiva de Java, stack equivalente baseado em JavaParser/Spring Boot/PostgreSQL/RabbitMQ/React/Docker/Ollama e relatório por dashboard HTTP, resumo, histórico e tendência. A interpretação está alinhada ao registro D-01, à matriz e ao roteiro de defesa, e foi confirmada pelo grupo.

## Funcionalidades

- Cadastro, login e análises isoladas por usuário com JWT Bearer.
- Análise assíncrona com acompanhamento das etapas e dos resultados parciais.
- Regras para credencial hardcoded (CWE-798), `Runtime.exec()` (CWE-78), `ObjectInputStream.readObject()` (CWE-502) e taint analysis intraprocedural para injeção de comando.
- Avaliação consultiva dos achados e sugestões independentes de segurança ou desempenho pelo Ollama local. A IA não altera os achados nem suas severidades determinísticas.
- Central de sistemas, dashboard por repositório, tendência determinística por execução, histórico e resultados com origem e prioridade dos itens.
- Workflow GitHub Actions e política local de Security Gate implementados; execução externa e proteção da branch exigem evidência própria.

## Como funciona

```text
Frontend → API → PostgreSQL (tarefa + outbox) → publicador → RabbitMQ → worker
Worker → GitHub REST API → snapshot Java em memória → parser/AST e regras → Ollama local
Worker → PostgreSQL → API → frontend (consulta periódica)
```

`POST /api/analyses` valida a URL e cria uma tarefa com resposta `202 Accepted`. O worker baixa o snapshot oficial, seleciona arquivos `.java`, executa as regras e registra o progresso. O frontend consulta a API até a conclusão ou falha. Se a IA estiver indisponível, a etapa consultiva pode ficar degradada sem remover os findings determinísticos. O código do repositório analisado nunca é compilado ou executado.

## Estrutura

- `src/frontend`: aplicação React, TypeScript e Vite; Nginx serve os arquivos e encaminha `/api` à API.
- `src/backend`: API e worker Spring Boot, integração com GitHub, JavaParser, regras, taint analysis, persistência e integração com Ollama.
- `compose.yaml` e `docker/`: ambiente local com frontend, API, worker, PostgreSQL, RabbitMQ e Ollama.
- `docs/`: decisões e detalhes técnicos; os testes automatizados ficam junto ao backend e ao frontend.

## Inicialização

Com Docker e Docker Compose disponíveis, execute na raiz do monorepo:

```bash
test -f .env || cp .env.example .env
# Configure no .env: SAST_BOOTSTRAP_EMAIL, SAST_BOOTSTRAP_PASSWORD e SAST_JWT_SECRET.
docker compose up --build -d --wait
docker compose exec ollama ollama pull llama3.2:3b
```

No primeiro início com banco vazio, informe um e-mail bootstrap e uma senha com pelo menos 12 caracteres e no máximo 72 bytes UTF-8. `SAST_JWT_SECRET` deve conter uma chave Base64 de pelo menos 32 bytes. Substitua também as senhas de exemplo do PostgreSQL e do RabbitMQ no `.env`. O bootstrap cria a conta uma vez e não sobrescreve usuários existentes. Depois da inicialização, também é possível criar contas pela tela de cadastro. O token do GitHub é opcional para repositórios públicos e deve ficar somente no backend.

O comando `ollama pull` baixa o modelo indicado por `SAST_OLLAMA_MODEL`; ajuste o nome no comando se alterar essa variável. Sem o modelo pronto, a análise determinística continua disponível e a avaliação consultiva pode aparecer como degradada.

Com as portas de `.env.example`:

- Frontend: http://localhost:3000
- API: http://localhost:8080
- Health check: http://localhost:8080/health

As portas podem ser alteradas por `WEB_PORT` e `API_PORT` no `.env`. O login devolve um JWT válido por 120 minutos por padrão; o frontend o guarda na `sessionStorage` da aba. Cada usuário vê apenas suas próprias análises. Não há recuperação de senha implementada.

## Depuração remota do backend

Para expor a porta JDWP da API apenas no computador local:

```bash
docker compose -f compose.yaml -f compose.debug.yaml up --build
```

`compose.debug.yaml` é um override e precisa ser usado com `compose.yaml`. Conecte a IDE a `localhost:5005` por socket. A aplicação inicia sem aguardar o depurador (`suspend=n`); `DEBUG_PORT` altera apenas a porta externa.

## Verificação

Com JDK 21, Maven, Node.js e Docker disponíveis, execute na raiz:

```bash
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
python3 -m unittest discover -s scripts -p 'test_security_gate.py' -v
docker compose config --quiet
```

Os testes de integração usam PostgreSQL e RabbitMQ descartáveis pelo Testcontainers. O fixture Java vulnerável em memória deve produzir exatamente três findings; seu código é lido como texto e não é executado. Instale dependências do frontend com `npm --prefix src/frontend ci` quando necessário. Não publique a saída expandida de Compose nem o conteúdo do `.env`.

Se `src/backend/target` não estiver gravável pelo usuário atual (por exemplo, depois de uma execução em contêiner que criou arquivos pertencentes a outro usuário), não use `sudo`, `chown` nem remova o conteúdo do checkout. Execute o procedimento seguro, que cria um diretório temporário pertencente ao usuário atual, direciona todo o build do Maven para ele e o remove ao terminar:

```bash
bash scripts/maven_local_verify.sh
```

O procedimento usa JDK 21 e `MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true'` por padrão e garante a limpeza do diretório temporário.

## Documentação

- [Regras gerais do sistema](REGRAS.MD) e [instruções para agentes](AGENTS.md).
- [Matriz de requisitos da entrega final](docs/Matriz-de-requisitos-CP3.md): estado atual, lacunas e decisões de escopo.
- [Plano de adequação](docs/Plano-de-adequacao-CP3.md): ordem de implementação e critérios de aceite.
- [Critérios de aceite e defesa](docs/Criterios-de-aceite-e-defesa.md): verificações, evidências e roteiro de apresentação.
- [Arquitetura e fluxos](docs/Arquitetura-e-fluxos.md): contexto, contêineres, estados e fronteiras de confiança.
- [Manual técnico e API](docs/Manual-tecnico-e-api.md): inicialização, autenticação, rotas e limites de origem.
- [Evidências e roteiro de defesa](docs/Evidencias-e-roteiro-de-defesa.md): comandos, demonstração e situação por tema.
- [Decisões de escopo](docs/Decisoes-de-escopo.md): contrato D-01, justificativas e registro da validação da orientação.
- [CI e operação](docs/CI-e-operacao.md): workflow, verificações automatizadas e limite entre CI e Security Gate.
- [Rastreabilidade BDD](docs/Rastreabilidade-BDD.md): requisitos, cenários, fixtures, testes e evidências.
- [Qualidade do taint e da IA](docs/Qualidade-taint-e-IA.md) e [Operação e escalabilidade](docs/Operacao-e-escalabilidade.md): evidências Q-01/O-01 e limites.
- [Registro E-01](docs/evidencias/E-01-2026-10-07.md): revisão, ambiente e resultados desta verificação documental.
- [Pacote `docs-entrega`](docs-entrega/README.md): índice dos arquivos exigidos, contribuições e ata/roteiro da defesa.

Esses documentos distinguem implementação, testes locais e evidências externas. Uma validação externa pendente não significa que a funcionalidade esteja ausente do código; observe a situação específica de cada requisito.
