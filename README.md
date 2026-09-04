# Plataforma SAST — CP1

Monorepo da plataforma de análise estática de segurança desenvolvida para a CP1 — Fundação e Parsers.

Nesta etapa, a aplicação recebe a URL de um repositório público do GitHub, baixa seu snapshot, analisa arquivos JavaScript sem executá-los e apresenta vulnerabilidades encontradas por regras baseadas em AST.

## Documentação

- [Guia de implementação da CP1](docs/CP1-Implementacao-Fundacao-e-Parsers.md)
- [Regras para agentes Codex](AGENTS.md)

## Estrutura

- `src/frontend`: aplicação React;
- `src/backend/Sast.Api`: API ASP.NET Core e integração GitHub;
- `src/backend/Sast.Engine`: parser, AST e Rules Engine;
- `tests`: testes automatizados;
- `samples`: arquivo vulnerável para demonstração;
- `docker` e `compose.yaml`: ambiente local;
- `docs`: documentação técnica.

## Inicialização da CP1

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:3000
- API: http://localhost:8080
- Health check: http://localhost:8080/health

Consulte o guia da CP1 para criar os projetos, configurar o GitHub, executar testes e realizar a demonstração.
