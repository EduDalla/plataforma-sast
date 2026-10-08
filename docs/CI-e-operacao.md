# CI do produto

O workflow [CI](../.github/workflows/ci.yml) executa em `pull_request` e em pushes para `main`. O job `Verificar produto` usa Ubuntu 24.04, JDK 21 Temurin e Node.js 22.14.0, com permissões somente de leitura do conteúdo.

## Verificações

Na mesma execução são feitos:

```bash
docker compose config --quiet
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend ci
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
```

O Maven usa um diretório temporário do runner para não persistir artefatos no checkout. O Docker disponível no runner é usado pelos testes de integração que dependem do Testcontainers. O workflow não baixa, compila, testa ou executa o repositório que eventualmente será analisado pela plataforma; o checkout é somente o código do próprio produto.

O resumo do job contém texto fixo sobre versões e verificações e roda com `if: always()`: esse texto não prova sucesso de cada comando. Consultar o estado real dos steps/checks antes de registrar aprovação. Não devem ser adicionados ao workflow tokens, conteúdo de `.env`, archives, código-fonte, snippets, prompts ou respostas brutas da IA.

## Proteção da branch

O YAML cria o check, mas não protege a branch sozinho. No GitHub, a administração do repositório deve exigir o check `Verificar produto` para merge em `main`, bloquear force-push e exigir atualização da branch quando a política adotada determinar isso. Essa configuração administrativa deve ser registrada como evidência separada.

## Security Gate

O job `Security Gate` depende do check de verificação do produto e usa os secrets `SAST_GATE_API_URL`, `SAST_GATE_EMAIL` e `SAST_GATE_PASSWORD`. Em PRs do próprio repositório, ele envia a URL pública e o SHA do commit para `POST /api/analyses`, consulta `GET /api/analyses/{id}` até um estado terminal e aplica `.sast/security-gate.json`.

O gate falha quando a análise está `FAILED`, expira, não confirma `COMPLETED`/cobertura determinística, usa SHA diferente ou introduz um finding determinístico `Critical` ausente da execução concluída anterior. Findings `High` são reportados sem bloquear. A identidade do baseline é `ruleId:fileName:line:column`; arquivo ou localização movidos são tratados como findings novos. A avaliação/sugestão da IA é ignorada na decisão, inclusive em `DEGRADED`.

PRs de forks não recebem secrets e não executam o job autenticado; a análise do produto continua sendo executada pelo job de verificação. A branch protegida deve exigir os checks `Verificar produto` e `Security Gate` quando os secrets estiverem configurados.

O workflow e o script nunca baixam, compilam, testam ou executam o código do repositório analisado. Eles apenas consultam a API do SAST e publicam contagens e localizações mínimas.

Os cenários BDD-E-08–12 validam a política local com resultados sintéticos; não executam o caminho HTTP completo do gate. O cenário manual BDD-E-M02 exige URLs de PR, SHA, estado dos checks e evidência da proteção administrativa antes de declarar bloqueio real de merge. A [rastreabilidade](Rastreabilidade-BDD.md) e o [registro E-01](evidencias/E-01-2026-10-07.md) documentam essa separação. PRs de forks precisam de decisão administrativa compatível com o job ignorado; não há prova de uma política completa para forks nesta entrega.

## Ensaios de operação O-01

`OperationIntegrationTest` usa PostgreSQL e RabbitMQ reais em contêineres de teste para exercitar concorrência, duplicação, indisponibilidade do broker, leases e isolamento JWT. Integra o `mvn verify`; fixtures e falhas de GitHub/Ollama são controlados em memória.

O ensaio externo `python3 scripts/operation_smoke.py --output /tmp/sast-o01-evidence.json` cria e remove um projeto Compose próprio. Ele interrompe somente seu RabbitMQ/Ollama e mede fila, duas tentativas ativas e memória amostrada. O roteiro, a rastreabilidade BDD e os limites das conclusões estão em [Operação e escalabilidade](Operacao-e-escalabilidade.md). O ensaio externo não roda automaticamente no CI nem altera a instância de desenvolvimento.
