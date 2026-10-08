# Arquivos da entrega e cobertura das especificações

Use esta tabela como checklist do material final. Os arquivos referenciados estão todos neste diretório e devem ser incluídos ou convertidos para PDF conforme a regra da disciplina.

| Bloco da especificação | Arquivo canônico | O que comprova | Situação atual |
|---|---|---|---|
| Arquitetura técnica containerizada | [`Arquitetura-e-fluxos.md`](Arquitetura-e-fluxos.md), [`Operacao-e-escalabilidade.md`](Operacao-e-escalabilidade.md), `compose.yaml` | Serviços, fronteiras, comunicação assíncrona, health checks e operação | Implementado; capacidade de produção não comprovada |
| Motor SAST, parser, AST e regras | [`Matriz-de-requisitos-CP3.md`](Matriz-de-requisitos-CP3.md), [`Rastreabilidade-BDD.md`](Rastreabilidade-BDD.md) | JavaParser, findings, CWE-798/CWE-78/CWE-502, localização e testes | Implementado no escopo Java |
| Taint analysis | [`Qualidade-taint-e-IA.md`](Qualidade-taint-e-IA.md) | Corpus, métricas, rastros e limite intraprocedural | Implementado; precisão/recall são da amostra documentada |
| IA semântica e remediação | [`Qualidade-taint-e-IA.md`](Qualidade-taint-e-IA.md), [`Manual-tecnico-e-api.md`](Manual-tecnico-e-api.md) | Ollama local, avaliação consultiva, sugestões, preservação da severidade e degradação | Implementado; redução causal de falsos positivos não comprovada |
| Dashboard executivo | [`README.md`](../README.md), [`Matriz-de-requisitos-CP3.md`](Matriz-de-requisitos-CP3.md) | Severidade, tendência, histórico, arquivos críticos e distinção de itens | Implementado; demonstração visual ainda precisa de ata |
| DevSecOps, CI/CD e Security Gate | [`CI-e-operacao.md`](CI-e-operacao.md), [`Rastreabilidade-BDD.md`](Rastreabilidade-BDD.md), `.github/workflows/ci.yml`, `.sast/security-gate.json` | Workflow, gate determinístico, baseline confiável e política para forks | Código/testes locais prontos; execução externa e branch protegida pendentes |
| Testes e qualidade | [`evidencias/E-01-2026-10-07.md`](evidencias/E-01-2026-10-07.md) | Maven, frontend, gate local, Compose e fixture de três findings sem execução | Verificado no registro E-01 |
| Defesa final | [`Evidencias-e-roteiro-de-defesa.md`](Evidencias-e-roteiro-de-defesa.md), [`ROTEIRO-E-ATA-DA-DEFESA.md`](ROTEIRO-E-ATA-DA-DEFESA.md) | Ordem da apresentação, tempo, responsáveis, alternativas e ata | Preparado; ensaio visual e confirmação nominal pendentes |
| Decisões e limites | [`Decisoes-de-escopo.md`](Decisoes-de-escopo.md), [`REGRAS.MD`](../REGRAS.MD) | Linguagem alvo, ética, propriedade intelectual, não execução e limites | Confirmado/documentado |

## Tecnologias sugeridas, mas não implementadas neste checkout

Tree-sitter, Python AST, Bandit, Semgrep, FastAPI e Redis/Celery aparecem na especificação como tecnologias recomendadas. O projeto entregue usa a equivalência JavaParser, Spring Boot, PostgreSQL, RabbitMQ, React, Docker, GitHub Actions e Ollama. Não incluir as tecnologias sugeridas na capa como componentes entregues; apresentá-las como alternativas avaliadas ou como escopo futuro, conforme D-01.

## Arquivos que não devem entrar no pacote

`.env`, tokens, senhas, `node_modules`, `target`, archives baixados, código integral de terceiros, dumps de banco, logs completos, prompts e respostas brutas do Ollama.
