# Matriz de requisitos para a entrega final

Esta matriz compara o checkout atual com o pedido de adequação e com o arquivo original `CP - Cyber - Documentação de Desenvolvimento.docx`. O contrato técnico adotado para esta versão está em [Decisões de escopo](Decisoes-de-escopo.md); a validação formal da orientação ainda está pendente. É um diagnóstico de planejamento, não uma declaração de que as funções pendentes já existem.

**Legenda:** atendido = há implementação e teste localizado; parcial = há implementação com limite relevante; pendente = não há implementação demonstrável; decisão = as duas descrições do trabalho exigem alinhamento antes de mudar a arquitetura.

## Requisitos funcionais do documento original

| ID | Exigência | Estado atual e evidência | Trabalho para aceite |
| --- | --- | --- | --- |
| RF01 | Receber código-fonte para análise | **Contrato D-01:** a API recebe URL e referência de repositório público em `POST /api/analyses`; não recebe texto ou pacote enviado pelo usuário. | Validar formalmente se o acesso por URL satisfaz a entrega. Se upload for obrigatório, especificar novo fluxo sem executar ou persistir fonte integral antes de implementá-lo. |
| RF02 | Identificar linguagem, com uma linguagem inicial e expansão futura | **Contrato D-01:** a entrega identifica Java pelo escopo configurado e filtra `.java`; não há suporte multilinguagem. | Validar se Java único atende à entrega final. Se outra linguagem for obrigatória, projetar parser/regras e impacto antes de alterar a implementação. |
| RF03 | Validar entradas | **Parcial.** URL HTTPS, host `github.com`, proprietário, repositório e referência são validados em `GitHubClient.validate`. | Testar também os limites e mensagens de erro do fluxo de entrada que for aprovado para RF01. |
| RF04 | Analisar estaticamente sem executar o código | **Atendido no escopo Java.** O worker lê o snapshot e usa AST; `SastEngineTest` contém teste de não execução. | Preservar esta propriedade em CI e em qualquer expansão. |
| RF05 | Parser funcional | **Atendido no escopo Java.** `JavaParserSourceParser` usa JavaParser com sintaxe Java 21. | Demonstrar parse válido e erro de sintaxe. |
| RF06 | Construir AST | **Atendido no escopo Java.** As regras recebem `CompilationUnit`. | Manter AST somente durante a tentativa de análise. |
| RF07 | Motor de regras | **Atendido.** `SastEngine` recebe implementações de `SecurityRule`. | Corrigir a substituição de achados distintos na mesma linha. |
| RF08 | Detectar vulnerabilidades iniciais | **Contrato D-01:** as três violações são credencial hardcoded (CWE-798), `Runtime.exec()`/injeção de comando (CWE-78) e `ObjectInputStream.readObject()` (CWE-502), com taint intraprocedural. `eval()` e `innerHTML` são apenas exemplos do DOCX. | Validar a aceitação dos equivalentes Java; demonstrar três violações e manter o mapeamento registrado. |
| RF09 | Severidade, CWE e descrição | **Atendido.** `SecurityFinding` e o resultado HTTP expõem esses campos. | Preservar a severidade determinística diante da sugestão consultiva da IA. |
| RF10 | Arquivo, linha e trecho | **Atendido.** Findings contêm localização e snippet. | Testar precisão de arquivo/linha inclusive com múltiplos achados na mesma linha. |
| RF11 | Persistir análise e histórico | **Atendido.** PostgreSQL, migrações Flyway e histórico por repositório. | Manter isolamento por usuário e não persistir código integral. |
| RF12 | Exibir resultados na web | **Atendido.** Há página de resultados, estados parciais e avaliações consultivas. | Testar estados concluído, degradado e falho. |
| RF13 | Dashboard: quantidade, severidade, tendência, arquivos críticos e histórico | **Parcial.** Totais, distribuição, arquivos com mais itens e histórico existem; falta tendência calculada e exibida entre execuções. | Exibir série temporal do mesmo repositório, com definição explícita de métrica e tratamento de execuções parciais/falhas. |
| RF14 | IA semântica e redução de falsos positivos | **Parcial.** Ollama avalia findings, sugere severidade e provável falso positivo, sem alterar a regra; não há medição documentada de precisão/impacto. | Medir em corpus rotulado e apresentar limites, confiança e taxa de avaliações inválidas/degradadas. |
| RF15 | Taint analysis | **Parcial.** Rastreia algumas entradas HTTP até execução de comando dentro de um método. | Documentar limites, ampliar casos prioritários se a rubrica exigir e testar fontes, propagação, sanitização e sinks. |
| RF16 | Sugestão de correção | **Atendido com cobertura limitada.** Ollama gera remediação de findings e sugestões para métodos selecionados. | Demonstrar resposta válida e degradação segura quando IA estiver indisponível. |
| RF17 | Integração com CI/CD | **Parcial.** `.github/workflows/ci.yml` executa backend, frontend e validação Compose em PR e em `main`; ainda não consulta a análise SAST do repositório-alvo. | Exigir o check na branch protegida e integrar a análise SAST assíncrona no C-02, sem executar o código-alvo. |
| RF18 | Security Gate e bloqueio de PR inseguro | **Parcial.** `.sast/security-gate.json` e `scripts/security_gate.py` bloqueiam Critical determinístico novo, falhas e cobertura incompleta; a proteção administrativa da branch e o ensaio contra a API ainda não foram registrados. | Configurar secrets, exigir os checks na branch protegida e provar PR seguro, Critical novo, análise falha e resultado incompleto. |

## Requisitos não funcionais do documento original

| ID | Exigência | Estado atual | Trabalho para aceite |
| --- | --- | --- | --- |
| RNF01 | Não executar código analisado | **Atendido no fluxo atual.** Snapshot em memória e teste de inicializador. | Executar o teste continuamente; nunca compilar ou rodar o repositório analisado. |
| RNF02 | Arquitetura modular | **Parcial.** Frontend, API, worker, parser/regras, banco e fila têm responsabilidades separadas. | Registrar diagrama C4 e validar a extensão de regras e, se necessária, de linguagens. |
| RNF03 | Docker/Compose | **Parcial.** `compose.yaml` define os serviços e `docker compose config` valida a configuração. | Fazer demonstração funcional com contêineres, health checks e análise de ponta a ponta. |
| RNF04 | Escalabilidade e processamento assíncrono | **Parcial.** Há RabbitMQ, outbox, claim e reconciliação. | Testar concorrência, duplicação, retomada e limites de recursos; documentar capacidade observada. |
| RNF05 | Baixo índice de falsos positivos | **Pendente de evidência.** Há testes de regras, mas não corpus rotulado nem métrica de precisão. | Definir amostras positivas/negativas e registrar falsos positivos, falsos negativos e limites do parser/taint. |
| RNF06 | Testes automatizados | **Parcial.** Existem testes de parser, regras, API, IA e frontend, e o workflow C-01 os executa em CI; há limitações ambientais registradas para Testcontainers/Mockito fora do runner configurado. | Registrar execução limpa no CI e acrescentar testes do gate e das lacunas corrigidas. |
| RNF07 | Manutenibilidade | **Parcial.** `SecurityRule` permite novas regras e `BackendQualityTest` verifica qualidade com baseline. | Publicar documentação técnica final, convenções e decisões de arquitetura. |
| RNF08 | Privacidade e propriedade intelectual | **Parcial.** O fluxo restringe a origem a repositórios públicos do GitHub e não persiste fonte integral. | Registrar política de uso autorizado, retenção de dados derivados e corpus de demonstração com licença verificável. |

## Tecnologias e entregáveis

O stack atual é **JavaParser + Spring Boot + PostgreSQL + RabbitMQ + React + Docker + Ollama**. Tree-sitter, Python AST, Bandit, Semgrep, FastAPI, Redis/Celery e GitHub Actions constam como exemplos no resumo recebido; não são componentes implementados. Antes de substituir componentes, confirmar se a avaliação aceita equivalência técnica ou exige nomes específicos. GitHub Actions é o candidato direto para RF17–RF18, mas ainda não foi implementado.

| Entregável | Situação | Evidência final esperada |
| --- | --- | --- |
| CP1: arquitetura técnica C4 | Pendente como artefato | Diagramas de contexto e contêineres coerentes com `compose.yaml` e fronteiras de confiança. |
| CP1: Compose, parser, AST e três violações | Implementados, validação integrada pendente | Execução reproduzível e fixture de três findings sem execução do código. |
| CP2: taint e IA local | Implementados com limites | Casos de teste, corpus de precisão e demonstração de remediação/degradação. |
| CP3: CI/CD, gate e bloqueio de PR | Pendente | Workflow versionado, política, PR seguro aprovado e PR crítico bloqueado. |
| CP3: dashboard e relatório analítico | Parcial | Tendência por execução, distribuição, arquivos críticos e relatório exportável ou artefato equivalente aprovado. |
| CP3: documentação e defesa | Parcial | Manual técnico, evidências de testes e roteiro de apresentação concluídos. |

## Decisões registradas pelo D-01

1. RF01: o contrato desta versão usa URL HTTPS pública do GitHub; upload não faz parte do escopo.
2. RF02/RF08: o contrato desta versão usa Java e os três equivalentes Java descritos em `docs/Decisoes-de-escopo.md`.
3. Stack: o contrato usa equivalência técnica e mantém o stack implementado.
4. Relatórios: o contrato desta versão usa dashboard HTTP, resumo e histórico; exportações e Security Gate continuam fora do escopo implementado.

Essas decisões aguardam validação formal da orientação e não autorizam acesso a repositórios privados, execução de código recebido, armazenamento integral da fonte nem alteração de arquitetura sem revisão explícita.
