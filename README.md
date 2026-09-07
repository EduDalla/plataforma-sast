# Plataforma SAST — CP1

Monorepo da plataforma de análise estática de segurança desenvolvida para a CP1 — Fundação e Parsers.

Nesta etapa, a aplicação recebe a URL de um repositório público do GitHub, baixa seu snapshot, analisa arquivos Java sem executá-los e apresenta vulnerabilidades encontradas por regras baseadas em AST.

## Documentação

- [Guia de implementação da CP1](docs/CP1-Implementacao-Fundacao-e-Parsers.md)
- [Regras para agentes Codex](AGENTS.md)

## Estrutura

- `src/frontend`: aplicação React;
- `src/backend`: API Spring Boot, JavaParser e Rules Engine;
- `tests`: testes automatizados;
- `samples`: arquivo vulnerável para demonstração;
- `docker` e `compose.yaml`: ambiente local;
- `docs`: documentação técnica.

## Inicialização da CP1

```bash
cp .env.example .env
# Preencha SAST_BOOTSTRAP_EMAIL e SAST_BOOTSTRAP_PASSWORD no .env antes de iniciar.
docker compose up --build
```

- Frontend: http://localhost:3000
- API: http://localhost:8085
- Health check: http://localhost:8085/health

## Depuração remota do backend no Docker

Para iniciar a API com a porta JDWP disponível somente no computador local:

```bash
docker compose -f compose.yaml -f compose.debug.yaml up --build
```

`compose.debug.yaml` é um override e não deve ser informado sozinho.

Anexe a IDE a `localhost:5005` usando o transportador socket. A aplicação não fica suspensa na inicialização (`suspend=n`). Para usar outra porta externa, defina `DEBUG_PORT` no `.env`; a porta interna do container permanece `5005`.

Consulte o guia da CP1 para criar os projetos, configurar o GitHub, executar testes e realizar a demonstração.

## Primeiro acesso

Configure `SAST_BOOTSTRAP_EMAIL` e `SAST_BOOTSTRAP_PASSWORD` no seu `.env` local. Use uma senha de pelo menos 12 caracteres e no máximo 72 bytes UTF-8. A primeira inicialização cria a conta e salva somente o hash BCrypt. Depois disso, as variáveis podem ser removidas: não atualizam contas já existentes. Não há cadastro público ou recuperação de senha nesta entrega.

Abra o frontend e entre com essa conta. O access token JWT dura 15 minutos e permanece somente em memória. Cada usuário consulta apenas suas próprias análises; registros anteriores à autenticação são preservados, mas ficam inacessíveis. Configure `SAST_JWT_SECRET` com uma chave Base64 de pelo menos 32 bytes.

O fluxo visual inclui login, nova análise de repositório público Java, processamento, resultados e detalhes expansíveis. A URL de um resultado pode ser reaberta pelo mesmo usuário. Dashboard, histórico visual e relatórios continuam fora do escopo.

## Verificação

Com JDK 21, Maven, Node.js e Docker disponíveis:

```bash
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
docker compose config --quiet
```

Os testes de integração iniciam um PostgreSQL descartável pelo Testcontainers. A amostra vulnerável é lida como texto; nunca é compilada ou executada.

Decisões e contratos da extensão: [Autenticação e frontend](docs/CP1-Autenticacao-e-Frontend.md).
