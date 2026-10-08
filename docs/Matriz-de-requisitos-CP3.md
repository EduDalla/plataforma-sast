# Matriz de requisitos para a entrega final

Esta matriz compara o checkout atual com o pedido de adequação e com o arquivo original `CP - Cyber - Documentação de Desenvolvimento.docx`. O contrato técnico adotado para esta versão está em [Decisões de escopo](Decisoes-de-escopo.md) e foi confirmado pelo grupo. A rastreabilidade por requisito, com cenário, fixture, teste e evidência, está em [BDD da entrega](Rastreabilidade-BDD.md). A verificação local atual está no [registro E-01](evidencias/E-01-2026-10-07.md); pendências externas não são tratadas como capacidades ausentes do código.

**Legenda:** atendido = há implementação e teste localizado; parcial = há implementação com limite relevante; pendente = não há implementação demonstrável; decisão = as duas descrições do trabalho exigem alinhamento antes de mudar a arquitetura.

## Requisitos funcionais do documento original

| ID | Exigência | Estado atual e evidência | Trabalho para aceite |
| --- | --- | --- | --- |
| [RF01](Rastreabilidade-BDD.md) | Receber código-fonte para análise | **Contrato D-01:** a API recebe URL e referência de repositório público em `POST /api/analyses`; não recebe texto ou pacote enviado pelo usuário. | Validar formalmente se o acesso por URL satisfaz a entrega. Se upload for obrigatório, especificar novo fluxo sem executar ou persistir fonte integral antes de implementá-lo. |
| [RF02](Rastreabilidade-BDD.md) | Identificar linguagem, com uma linguagem inicial e expansão futura | **Contrato D-01:** a entrega identifica Java pelo escopo configurado e filtra `.java`; não há suporte multilinguagem. | Validar se Java único atende à entrega final. Se outra linguagem for obrigatória, projetar parser/regras e impacto antes de alterar a implementação. |
| [RF03](Rastreabilidade-BDD.md) | Validar entradas | **Atendido no contrato atual.** `GitHubClientTest` e `OperationIntegrationTest` verificam origem, extração segura e recusa HTTP antes de criar análise/outbox. | Preservar testes dos limites no contrato por URL; upload continua fora do escopo. |
| [RF04](Rastreabilidade-BDD.md) | Analisar estaticamente sem executar o código | **Atendido no escopo Java.** O worker lê o snapshot e usa AST; `SastEngineTest` contém teste de não execução. | Preservar esta propriedade em CI e em qualquer expansão. |
| [RF05](Rastreabilidade-BDD.md) | Parser funcional | **Atendido no escopo Java.** `JavaParserSourceParser` usa JavaParser com sintaxe Java 21. | Demonstrar parse válido e erro de sintaxe. |
| [RF06](Rastreabilidade-BDD.md) | Construir AST | **Atendido no escopo Java.** As regras recebem `CompilationUnit`. | Manter AST somente durante a tentativa de análise. |
| [RF07](Rastreabilidade-BDD.md) | Motor de regras | **Atendido.** `SastEngine` recebe implementações de `SecurityRule`. | Preservar os testes BDD-E-03, que comprovam achados distintos e deduplicação de ocorrências idênticas. |
| [RF08](Rastreabilidade-BDD.md) | Detectar vulnerabilidades iniciais | **Contrato D-01:** as três violações são credencial hardcoded (CWE-798), `Runtime.exec()`/injeção de comando (CWE-78) e `ObjectInputStream.readObject()` (CWE-502), com taint intraprocedural. `eval()` e `innerHTML` são apenas exemplos do DOCX. | Validar a aceitação dos equivalentes Java; demonstrar três violações e manter o mapeamento registrado. |
| [RF09](Rastreabilidade-BDD.md) | Severidade, CWE e descrição | **Atendido.** `SecurityFinding` e o resultado HTTP expõem esses campos. | Preservar a severidade determinística diante da sugestão consultiva da IA. |
| [RF10](Rastreabilidade-BDD.md) | Arquivo, linha e trecho | **Atendido.** Findings contêm localização e snippet. | Testar precisão de arquivo/linha inclusive com múltiplos achados na mesma linha. |
| [RF11](Rastreabilidade-BDD.md) | Persistir análise e histórico | **Atendido.** PostgreSQL, migrações Flyway e histórico por repositório. | Manter isolamento por usuário e não persistir código integral. |
| [RF12](Rastreabilidade-BDD.md) | Exibir resultados na web | **Atendido.** Há página de resultados, estados parciais e avaliações consultivas; O-01 verifica polling, conclusão e IA degradada com dados preservados. | Preservar testes de apresentação e de estados falhos/parciais. |
| [RF13](Rastreabilidade-BDD.md) | Dashboard: quantidade, severidade, tendência, arquivos críticos e histórico | **Parcial avançado.** Totais, distribuição, revisão de arquivos, histórico e tendência acessível por tabela estão implementados; exportação formal continua fora do contrato. | BDD-E-04 testa alta, queda, estabilidade e cobertura parcial com histórico sintético; OP-01 verifica isolamento. Demonstração visual integrada e aprovação do formato pela orientação permanecem pendentes. |
| [RF14](Rastreabilidade-BDD.md) | IA semântica e redução de falsos positivos | **Parcial com evidência de contrato.** Ollama avalia findings, sugere severidade e provável falso positivo, sem alterar a regra; os cenários BDD cobrem respostas válidas, inválidas e degradação. Ensaio real e métrica de impacto continuam separados. | Preservar o relato do ensaio real Q-01 e completar revisão qualitativa e identificação reprodutível do ensaio. Não há medida de redução real de falsos positivos; IA não decide o gate. Ver [Qualidade taint e IA](Qualidade-taint-e-IA.md). |
| [RF15](Rastreabilidade-BDD.md) | Taint analysis | **Parcial com corpus.** O corpus BDD tem 14 casos: TP=8, FP=0, TN=4, FN=2, precisão=100% e recall=80% nessa amostra. | Preservar os limites intraprocedurais e ampliar o corpus somente com casos autorizados; não alegar cobertura universal. |
| [RF16](Rastreabilidade-BDD.md) | Sugestão de correção | **Atendido com cobertura limitada.** Ollama gera remediação de findings e sugestões para métodos selecionados. | Demonstrar resposta válida e degradação segura quando IA estiver indisponível. |
| [RF17](Rastreabilidade-BDD.md) | Integração com CI/CD | **Implementado com evidência externa pendente.** O workflow verifica o produto e inclui job autenticado que submete URL/SHA e consulta a análise assíncrona; PRs de forks não recebem secrets. | Registrar execução externa, configurar secrets e comprovar checks obrigatórios na branch protegida (BDD-E-M02). |
| [RF18](Rastreabilidade-BDD.md) | Security Gate e bloqueio de PR inseguro | **Parcial.** `.sast/security-gate.json` e `scripts/security_gate.py` bloqueiam Critical determinístico novo, falhas e cobertura incompleta; a proteção administrativa da branch e o ensaio contra a API ainda não foram registrados. | Configurar secrets, exigir os checks na branch protegida e provar PR seguro, Critical novo, análise falha e resultado incompleto. |

## Requisitos não funcionais do documento original

| ID | Exigência | Estado atual | Trabalho para aceite |
| --- | --- | --- | --- |
| [RNF01](Rastreabilidade-BDD.md) | Não executar código analisado | **Atendido no fluxo atual.** Snapshot em memória e teste de inicializador. | Executar o teste continuamente; nunca compilar ou rodar o repositório analisado. |
| [RNF02](Rastreabilidade-BDD.md) | Arquitetura modular | **Documentado e implementado.** Frontend, API, worker, parser/regras, banco e fila têm responsabilidades separadas; C4 de contexto e contêineres está em [Arquitetura](Arquitetura-e-fluxos.md). | Preservar os contratos modulares; expansão de linguagens permanece fora da entrega. |
| [RNF03](Rastreabilidade-BDD.md) | Docker/Compose | **Atendido na demonstração O-01.** Seis serviços com health checks; ensaio isolado `PASSED` com quatro análises concluídas de uma origem pública. | Preservar reprodução e evidência em [O-01](Operacao-e-escalabilidade.md). |
| [RNF04](Rastreabilidade-BDD.md) | Escalabilidade e processamento assíncrono | **Parcial com evidência operacional.** O-01 testa dois consumidores, duplicação, outbox, interrupção do broker, recuperação de lease e backoff. | Medir carga maior e múltiplas réplicas antes de alegar capacidade de produção; os limites da medição estão em [O-01](Operacao-e-escalabilidade.md). |
| [RNF05](Rastreabilidade-BDD.md) | Baixo índice de falsos positivos | **Parcial.** Corpus Q-01 registra TP=8, FP=0, TN=4 e FN=2, com denominadores explícitos; a amostra é pequena e não representa produção. | Ampliar corpus autorizado e repetir métricas por versão, mantendo os limites documentados. |
| [RNF06](Rastreabilidade-BDD.md) | Testes automatizados | **Parcial com execução local.** Parser, regras, API, IA, frontend e operação têm testes; O-01 acrescenta PostgreSQL/RabbitMQ reais e rastreabilidade BDD. A suíte HTTP síncrona legada continua desabilitada. | Registrar execução limpa no CI e migrar os cenários restantes da suíte legada. |
| [RNF07](Rastreabilidade-BDD.md) | Manutenibilidade | **Parcial.** `SecurityRule` permite novas regras e `BackendQualityTest` verifica qualidade com baseline. | Manual, C4, modelo de dados e BDD consolidados em E-01; preservar links e executar o gate de qualidade do produto. |
| [RNF08](Rastreabilidade-BDD.md) | Privacidade e propriedade intelectual | **Parcial.** O fluxo restringe a origem a repositórios públicos do GitHub e não persiste fonte integral. | Uso restrito a fixtures próprios e origem autorizada O-01. Retenção atual documentada: não há expurgo automático; outras origens precisam de autorização verificável. |

## Tecnologias e entregáveis

O stack atual é **JavaParser + Spring Boot + PostgreSQL + RabbitMQ + React + Docker + Ollama**, com **GitHub Actions** configurado para CI e gate. Tree-sitter, Python AST, Bandit, Semgrep, FastAPI e Redis/Celery não são componentes implementados. A equivalência técnica do stack aguarda validação da orientação; a execução externa do workflow aguarda evidência própria.

| Entregável | Situação | Evidência final esperada |
| --- | --- | --- |
| CP1: arquitetura técnica C4 | Documentado E-01 | [Contexto e contêineres](Arquitetura-e-fluxos.md), protocolos, redes e fronteiras conferidos no Compose. |
| CP1: Compose, parser, AST e três violações | Implementados e verificados no escopo local | O-01 e E-01; fixture de três findings e não execução; origem pública é um caso separado. |
| CP2: taint e IA local | Implementados com limites e BDD | [Qualidade taint e IA](Qualidade-taint-e-IA.md), `TaintQualityCorpusTest`, cenários BDD, testes de degradação e ensaio live de seis findings. |
| CP3: CI/CD, gate e bloqueio de PR | Código e política local implementados; prova externa pendente | Seis testes locais aprovados. PRs reais e proteção da branch dependem de BDD-E-M02. |
| CP3: dashboard e relatório analítico | Parcial avançado | Tendência por execução, distribuição, arquivos críticos e relatório analítico no dashboard; exportação ainda depende de decisão de formato. |
| CP3: documentação e defesa | E-01 documental concluído | Manual, C4, dados V1–V11, BDD e roteiro de 480 segundos. Nomes, demonstração visual e aceite externo pendentes. |

## Decisões registradas pelo D-01

1. RF01: o contrato desta versão usa URL HTTPS pública do GitHub; upload não faz parte do escopo.
2. RF02/RF08: o contrato desta versão usa Java e os três equivalentes Java descritos em `docs/Decisoes-de-escopo.md`.
3. Stack: o contrato usa equivalência técnica e mantém o stack implementado.
4. Relatórios: o contrato desta versão usa dashboard HTTP, resumo e histórico analítico; exportações permanecem fora do escopo implementado.

Essas decisões estão alinhadas ao README, ao roteiro de defesa e ao registro D-01, e foram confirmadas pelo grupo. Elas não autorizam acesso a repositórios privados, execução de código recebido, armazenamento integral da fonte nem alteração de arquitetura sem revisão explícita.
