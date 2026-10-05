# Plano de adequação da plataforma SAST

Este plano transforma as lacunas da [matriz](Matriz-de-requisitos-CP3.md) em tarefas verificáveis. Os estados descrevem o checkout atual; nenhuma tarefa abaixo está implementada apenas por constar deste documento. O fluxo vigente permanece URL pública do GitHub → API → PostgreSQL/outbox → RabbitMQ → worker → JavaParser/regras/IA local → resultados HTTP → frontend.

## Ordem de execução sugerida

| Ordem | Pacote | Dependência | Resultado de aceite |
| --- | --- | --- | --- |
| 0 | D-01: decisões de escopo | Orientação da disciplina | Registrar as quatro decisões da matriz e manter Java/URL até haver decisão distinta. |
| 1 | S-01: precisão do SAST | Nenhuma | Findings diferentes na mesma linha são preservados; duplicatas idênticas não inflam totais. |
| 2 | C-01: CI de testes | JDK 21 e ambiente de integração | PR executa backend, frontend e validação Compose com resultado visível. |
| 3 | C-02: gate SAST | C-01 e política definida | PR seguro passa; PR com achado crítico novo falha; erro de análise não vira aprovação silenciosa. |
| 4 | D-02: tendência e relatórios | Definição de métrica | Dashboard mostra evolução por execução e evidência de arquivos críticos. |
| 5 | Q-01: precisão, IA e taint | S-01 | Corpus rotulado e cobertura/limitações documentadas. |
| 6 | O-01: validação integrada | C-01 e ambiente Compose | Fluxo completo e recuperação de falhas demonstrados. |
| 7 | E-01: documentação e defesa | Todos os anteriores | Artefatos CP1–CP3 e demonstração reproduzível. |

## D-01 — Fixar o contrato da entrega

- Comparar a redação do DOCX com a rubrica vigente e registrar responsável, data e decisão para RF01, RF02/RF08, stack e relatório.
- Se envio direto de código ou multilinguagem forem obrigatórios, desenhar contrato, segurança da origem, limites de tamanho, isolamento, persistência, migrações e efeitos na API/frontend/worker antes de alterar a implementação. Esta etapa representa mudança arquitetural e exige autorização explícita.
- Se Java único e URL pública forem aceitos, registrar o equivalente Java das três regras e a justificativa das tecnologias escolhidas; não declarar suporte a Python/JS.

**Aceite:** uma decisão assinada pelo grupo ou validada pela orientação resolve cada divergência; README, matriz e defesa usam a mesma interpretação.

## S-01 — Melhorar a precisão do motor

- Revisar `SastEngine`: a chave atual de deduplicação é `(arquivo, linha)` e uma regra substitui outra. Definir identidade por regra, arquivo, linha, coluna e localização do nó; não eliminar vulnerabilidades distintas.
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

**Aceite:** testes com pelo menos três execuções mostram alta, queda e estabilidade; dois usuários não veem séries um do outro; estado parcial não é tratado como ausência de falhas.

## Q-01 — Taint e IA com evidência de qualidade

- Registrar fontes, propagação, sanitizadores e sinks que o taint atual reconhece; documentar explicitamente o limite intraprocedural e casos fora de cobertura.
- Construir fixtures autorizados para fluxo direto, atribuição, concatenação, sanitização, `ProcessBuilder`, falsos positivos e fluxo entre métodos. Priorizar extensão conforme a rubrica, sem chamar heurística limitada de confirmação geral.
- Avaliar IA local em exemplos rotulados: severidade sugerida, provável falso positivo, qualidade de remediação, respostas inválidas, timeout e indisponibilidade. Registrar modelo, versão do prompt e custo/tempo de execução.
- Manter findings e severidade determinísticos independentes da IA; o gate usa somente evidência determinística enquanto avaliações consultivas não tiverem política e validação próprias.

**Aceite:** relatório mostra tamanho do corpus, critérios de rotulagem, contagens de acertos/erros, limites e exemplo de operação degradada sem perda de findings.

## O-01 — Operação e escalabilidade

- Subir `db`, `rabbitmq`, `api`, `worker`, `web` e `ollama` via Compose em ambiente controlado; verificar health checks e análise de um repositório público de teste autorizado.
- Testar duas tarefas simultâneas, mensagem duplicada, indisponibilidade temporária do RabbitMQ, retomada de lease e Ollama indisponível. Medir fila, tempo por análise, memória máxima observada e limites de archive/extração.
- Verificar isolamento JWT, logs sem segredos/código, snapshot em memória, recusa de URL malformada e archive perigoso. Manter o token GitHub opcional apenas no backend.
- Registrar limites da demonstração: uma máquina Compose demonstra separação e escalabilidade possível, não capacidade comprovada em produção.

**Aceite:** checklist de [validação](Criterios-de-aceite-e-defesa.md) preenchido com evidências e comandos reproduzíveis, sem dados sensíveis.

## E-01 — Documentação e defesa

- Produzir diagramas C4 de contexto e contêineres coerentes com o Compose, mostrando fronteiras de confiança e fluxo assíncrono.
- Completar documentação técnica de API, autenticação, estados, modelo de dados, regras/CWE, taint, IA, gate, limites e operação. Corrigir referências antigas no README.
- Preparar roteiro de demonstração: submissão autorizada, AST e três violações, trace de taint, IA consultiva, dashboard/tendência, PR seguro e PR bloqueado, além de falha de Ollama.
- Dividir responsáveis no grupo e guardar evidências de execução e decisões, sem copiar repositórios de terceiros para o material público.

**Aceite:** outra pessoa consegue instalar, verificar e repetir a demonstração a partir dos documentos; cada RF/RNF aponta para evidência de código, teste ou decisão registrada.
