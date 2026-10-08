# Plano de adequação da plataforma SAST

Este plano transforma as lacunas da [matriz](Matriz-de-requisitos-CP3.md) em tarefas verificáveis. Os estados descrevem o checkout atual; nenhuma tarefa abaixo está implementada apenas por constar deste documento. O fluxo vigente permanece URL pública do GitHub → API → PostgreSQL/outbox → RabbitMQ → worker → JavaParser/regras/IA local → resultados HTTP → frontend.

O checklist operacional consolidado das pendências está em [Tasks pendentes](Tasks-pendentes.md). Ele inclui situação, dependências, responsável, critério de aceite e as tarefas condicionais à validação formal do escopo.

## Ordem de execução sugerida

| Ordem | Pacote | Dependência | Resultado de aceite |
| --- | --- | --- | --- |
| 0 | D-01: decisões de escopo | Orientação da disciplina | Contrato técnico registrado em `docs/Decisoes-de-escopo.md`; validação formal externa permanece explícita. |
| 1 | S-01: precisão do SAST | Nenhuma | Findings diferentes na mesma linha são preservados; duplicatas idênticas não inflam totais. |
| 2 | C-01: CI de testes | JDK 21 e ambiente de integração | PR executa backend, frontend e validação Compose com resultado visível. |
| 3 | C-02: gate SAST | C-01 e política definida | PR seguro passa; PR com achado crítico novo falha; erro de análise não vira aprovação silenciosa. |
| 4 | D-02: tendência e relatórios | Definição de métrica | Dashboard mostra evolução por execução e evidência de arquivos críticos. |
| 5 | Q-01: precisão, IA e taint | S-01 | Corpus rotulado e cobertura/limitações documentadas. |
| 6 | O-01: validação integrada | C-01 e ambiente Compose | Fluxo completo e recuperação de falhas demonstrados. |
| 7 | E-01: documentação e defesa | Todos os anteriores | Artefatos CP1–CP3 e demonstração reproduzível. |

## D-01 — Fixar o contrato da entrega

- Comparar a redação do DOCX com a rubrica vigente e registrar responsável, data, decisão interna e evidência de validação para RF01, RF02/RF08, stack e relatório.
- Se envio direto de código ou multilinguagem forem obrigatórios, desenhar contrato, segurança da origem, limites de tamanho, isolamento, persistência, migrações e efeitos na API/frontend/worker antes de alterar a implementação. Esta etapa representa mudança arquitetural e exige autorização explícita.
- Contrato fixado no checkout: URL pública do GitHub, Java único, equivalentes Java para as três regras, stack atual equivalente e dashboard/histórico como relatório da versão apresentada. Não declarar suporte a Python/JS nem exportação formal.

**Aceite:** `docs/Decisoes-de-escopo.md` registra as quatro decisões, justificativas, responsável e data; README, matriz e defesa usam a mesma interpretação. A tarefa fica tecnicamente documentada, mas a aceitação final da rubrica só ocorre após anexar a validação da orientação.

## S-01 — Melhorar a precisão do motor

- A implementação atual preserva ocorrências distintas e deduplica ocorrências idênticas; os cenários BDD-E-03 documentam os testes de regras/nós na mesma linha. A substituição por `(arquivo, linha)` é o problema histórico que motivou S-01, não o comportamento atual.
- Expandir fixtures com dois achados na mesma linha, duplicata da mesma regra, arquivo inválido, casos seguros parecidos com vulneráveis e exemplos para as três regras.
- Medir precisão e recall em corpus pequeno, rotulado e autorizado; registrar falsos positivos/falsos negativos por regra e versão do conjunto de teste.
- Preservar o fixture atual de exatamente três findings e o teste que prova a não execução de um inicializador Java.

**Aceite:** testes provam preservação dos achados distintos e não execução; relatório de qualidade informa denominadores e resultados por regra, sem alegar cobertura universal.

## C-01 — Pipeline contínuo do projeto

- Criar workflow em `.github/workflows/` para `pull_request` e branch principal, com permissões mínimas e versões controladas de ações/ferramentas.
- Preparar JDK 21, Maven, Node e Docker; executar `mvn --file src/backend/pom.xml verify`, `npm --prefix src/frontend run build`, `npm --prefix src/frontend run test -- --run` e `docker compose config --quiet`.
- Garantir que os testes com Testcontainers tenham Docker disponível e que falhas retornem status não zero. Publicar resumo de testes sem segredos, código-fonte, prompts ou respostas brutas da IA.
- Proteger a branch para exigir o check do workflow antes de merge; documentar o passo administrativo no repositório, pois o arquivo de workflow sozinho não impede merge.

**Aceite:** falha injetada em teste impede o check; execução limpa passa; evidências mostram JDK 21 e testes de backend/frontend executados.

## C-02 — Security Gate para código analisado

- Definir em arquivo versionado a política inicial: bloquear em finding determinístico **Critical novo**; configurar tratamento de High, achados existentes e exceções com responsável e prazo. A severidade sugerida pela IA não altera a decisão automática.
- Definir como o CI obtém os resultados da plataforma para o commit do PR: URL HTTPS pública do mesmo repositório, SHA fixado e autenticação de serviço protegida. Não executar código do repositório analisado; o pipeline só executa testes do **próprio produto** e consulta o serviço SAST para o código alvo.
- Aguardar a análise assíncrona via contrato HTTP (`POST /api/analyses`, `GET /api/analyses/{id}`), com timeout e mensagens seguras. Restringir resultados por usuário/identidade do CI.
- Falhar quando a análise terminar em `FAILED`, expirar, permanecer parcial ou não tiver cobertura determinística confirmada. Falha isolada da IA consultiva deve aparecer como `DEGRADED` sem apagar os findings determinísticos.
- Se o serviço externo não puder ser acessado pelo runner, construir modo de gate compatível com as mesmas regras e testes, sem exigir execução do projeto alvo. Evitar saída que contenha snippets, tokens ou fonte.
- Distinguir findings novos de históricos por identidade estável (regra, caminho e localização/fingerprint), especificando comportamento para arquivo movido ou linha alterada. Publicar somente contagens e localizações necessárias ao PR.

**Aceite:** PR sem findings críticos novos passa, PR de teste com Critical novo falha, análise falha ou incompleta falha, e check obrigatório bloqueia merge em branch protegida. Testes cobrem a política sem depender de Ollama.

## D-02 — Dashboard e relatório analítico

- Definir tendência como série de análises **concluídas** de um mesmo repositório e usuário, ordenadas por data/ID, com contagens de findings determinísticos por severidade. Mostrar sugestões de IA separadamente; não misturar melhorias de desempenho com vulnerabilidades.
- Explicitar se a comparação é com a execução anterior, com o primeiro baseline ou com uma janela fixa. A mesma execução/commit não deve duplicar pontos; referência e SHA devem ser visíveis quando disponíveis.
- Exibir gráfico ou tabela acessível com data, total, críticos, variação e estado de cobertura. Falhas, processamento e cobertura degradada não podem ser desenhados como zero confirmado.
- Calcular arquivos críticos por severidade e quantidade, com regra de desempate estável; permitir chegar aos findings correspondentes.
- Decidir formato do relatório exportável com a orientação. Se exigido, exportar dados derivados autorizados, sem fonte integral ou segredo, identificando escopo, data, SHA, regras, severidades, limitações e estado da IA.
- Atualizar API, tipos TypeScript, frontend, testes e documentação em conjunto. Se houver dados persistentes novos, usar nova migração Flyway.

**Aceite:** testes com pelo menos três execuções mostram alta, queda e estabilidade; dois usuários não veem séries um do outro; a criticidade considera as vulnerabilidades; os dois botões exibem seus respectivos itens mantendo as tags existentes; estado parcial não é tratado como ausência de falhas.

## Q-01 — Taint e IA com evidência de qualidade

- Registrar fontes, propagação, sanitizadores e sinks que o taint atual reconhece; documentar explicitamente o limite intraprocedural e casos fora de cobertura.
- Construir fixtures autorizados para fluxo direto, atribuição, concatenação, sanitização, `ProcessBuilder`, falsos positivos e fluxo entre métodos. Priorizar extensão conforme a rubrica, sem chamar heurística limitada de confirmação geral.
- Avaliar IA local em exemplos rotulados: severidade sugerida, provável falso positivo, qualidade de remediação, respostas inválidas, timeout e indisponibilidade. Registrar modelo, versão do prompt e custo/tempo de execução.
- Manter findings e severidade determinísticos independentes da IA; o gate usa somente evidência determinística enquanto avaliações consultivas não tiverem política e validação próprias.

**Aceite:** [Qualidade-taint-e-IA.md](Qualidade-taint-e-IA.md) registra corpus de 14 casos, critérios de rotulagem, TP=8, FP=0, TN=4, FN=2, limites, cenários BDD rastreáveis, ensaio real de seis findings, revisão qualitativa 6/6 e operação degradada sem perda de findings. O impacto sobre falsos positivos não foi comprovado; não há alegação de cobertura universal.

## O-01 — Operação e escalabilidade

- Subir `db`, `rabbitmq`, `api`, `worker`, `web` e `ollama` via Compose em ambiente controlado; verificar health checks e análise de um repositório público de teste autorizado.
- Testar duas tarefas simultâneas, mensagem duplicada, indisponibilidade temporária do RabbitMQ, retomada de lease e Ollama indisponível. Medir fila, tempo por análise, memória máxima observada e limites de archive/extração.
- Verificar isolamento JWT, logs sem segredos/código, snapshot em memória, recusa de URL malformada e archive perigoso. Manter o token GitHub opcional apenas no backend.
- Registrar limites da demonstração: uma máquina Compose demonstra separação e escalabilidade possível, não capacidade comprovada em produção.

**Aceite verificado em 07/10/2026:** testes de operação com PostgreSQL/RabbitMQ reais e ensaio Compose isolado cobrem concorrência, duplicação, queda do broker, retomada de lease, falha transitória de origem e isolamento JWT. Quatro análises do mesmo SHA público concluíram com duas tentativas ativas nos consumidores de um único serviço worker e findings preservados sob IA `DEGRADED`. BDD, medições e limites estão em [Operação e escalabilidade](Operacao-e-escalabilidade.md) e no [registro derivado](evidencias/O-01-2026-10-07.json). O checklist de [validação](Criterios-de-aceite-e-defesa.md) separa esta carga observada de capacidade de produção.

## E-01 — Documentação e defesa

- Diagramas C4 de contexto e contêineres conferidos contra Compose, redes, protocolos, fronteiras, engine no worker e outbox no banco: [Arquitetura](Arquitetura-e-fluxos.md).
- Instalação, autenticação, contratos HTTP, estados/falhas, regras, limites, retenção e modelo de dados V1–V11 consolidados no [Manual](Manual-tecnico-e-api.md).
- [Roteiro](Evidencias-e-roteiro-de-defesa.md) de exatamente 480 segundos com papéis, preparação e evidências alternativas. CI, gate e tendência implementados; PRs reais, proteção administrativa e demonstração visual integrada continuam pendentes.
- [Rastreabilidade BDD](Rastreabilidade-BDD.md) cobre RF01–RF18 e RNF01–RNF08, ligando cenário, fixture, teste, evidência e limitações; IDs de Q-01 foram reconciliados com os testes existentes.
- [Decisões D-01](Decisoes-de-escopo.md) preservam a necessidade de validação da orientação. Nomes e contribuições devem ser preenchidos pelo grupo; não são inferidos pelo agente.

**Aceite documental concluído em 07/10/2026:** instalação e reprodução documentadas, todos os RF/RNF rastreáveis, verificações locais registradas em [E-01](evidencias/E-01-2026-10-07.md) e roteiro com evidências alternativas. Não houve ensaio de usabilidade com outra pessoa nesta etapa. Aceitação integral continua condicionada às lacunas técnicas e externas da [matriz](Matriz-de-requisitos-CP3.md) e ao aceite da orientação.
