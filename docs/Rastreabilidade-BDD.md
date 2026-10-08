# Rastreabilidade BDD da entrega — E-01

Os cenários documentam comportamentos verificados pelos testes existentes, sem Cucumber. Identificadores aparecem em `@DisplayName` (JUnit), descrição `it` (Vitest) ou docstring (unittest). O vínculo é documental: Gherkin não é executado por um runner próprio. Resultado atual em [E-01](evidencias/E-01-2026-10-07.md).

Os cenários [BDD-TAINT-01–05 e BDD-IA-01–04](Qualidade-taint-e-IA.md) e [BDD-OP-01–12](Operacao-e-escalabilidade.md) são reutilizados, sem criar cópias de seus testes. A tabela abaixo cobre todos os RF/RNF. “Manual” não significa teste automatizado aprovado; “externo” identifica uma execução com dependências reais e registro próprio.

## Features complementares

```gherkin
Feature: Parser e identidade dos findings
  Scenario: BDD-E-01 Java válido tem AST e localização
    Given um fixture Java sintético mantido em memória
    When JavaParser analisa a classe e a variável
    Then a AST contém os nós esperados
    And suas linhas e colunas são conhecidas

  Scenario: BDD-E-02 Erro sintático rejeita AST parcial
    Given uma classe Java com sintaxe inválida
    When o parser tenta construir a AST
    Then ocorre InvalidJavaSourceException
    And a exceção informa localização e mensagem

  Scenario: BDD-E-03 Identidade preserva ocorrências distintas
    Given regras ou nós diferentes na mesma linha e uma emissão idêntica repetida
    When o engine agrega os findings dos fixtures correspondentes
    Then os achados distintos são preservados
    And somente a ocorrência idêntica é deduplicada

Feature: Apresentação e acesso no frontend
  Scenario: BDD-E-04 Tendência compara execuções confirmadas
    Given histórico sintético com alta, queda, estabilidade e cobertura parcial
    When o dashboard apresenta sua tabela acessível
    Then mostra variações positivas, negativas e estáveis
    And a cobertura parcial aparece como não comparável

  Scenario: BDD-E-05 Ausência de itens não confirma segurança em etapa parcial
    Given uma análise em processamento ou com sugestões degradadas
    When o dashboard apresenta totais vazios
    Then informa processamento ou cobertura parcial
    And não confirma ausência de problemas

Scenario: BDD-E-12 Ranking estável de arquivos críticos
  Given findings determinísticos de severidades e quantidades diferentes em arquivos Java
  When o dashboard monta o ranking de arquivos críticos
  Then compara primeiro a distribuição de severidades, depois a quantidade de findings
  And usa o nome do arquivo como desempate estável
  And exibe sugestões consultivas em uma seção separada sem alterar o ranking

  Scenario: BDD-E-06 Cadastro solicita login pela interface
    Given o formulário de cadastro e respostas HTTP simuladas
    When a pessoa envia os dados válidos
    Then o frontend confirma o cadastro e apresenta o formulário de login
    And não autentica automaticamente nem conserva os campos preenchidos

  Scenario: BDD-E-07 Logout e expiração encerram a sessão visual
    Given uma análise aberta por uma pessoa autenticada
    When ela encerra a sessão ou recebe resposta não autorizada
    Then a interface retorna ao login
    And remove os resultados da área autenticada

Feature: Política local do Security Gate
  Scenario: BDD-E-08 Critical novo bloqueia
    Given um resultado concluído do SHA esperado com Critical determinístico novo
    When a política compara o resultado com o baseline
    Then retorna FAIL
    And informa um Critical novo

  Scenario: BDD-E-09 Critical histórico e High permitem aprovação
    Given um Critical presente no baseline e um High no resultado atual
    When a política local avalia a execução completa
    Then retorna PASS
    And reporta High sem tratá-lo como bloqueante

  Scenario: BDD-E-10 IA degradada não decide o gate
    Given um resultado determinístico completo sem Critical novo e IA degradada
    When a política avalia os findings e sua configuração versionada
    Then retorna PASS com o estado consultivo preservado
    And a configuração não usa severidade sugerida pela IA

  Scenario: BDD-E-11 Cobertura incompleta impede aprovação
    Given dois arquivos esperados e somente um processado
    When o gate valida a cobertura determinística
    Then lança GateFailure
    And não retorna aprovação

  Scenario: BDD-E-12 Origem e SHA devem corresponder à solicitação
    Given um resultado cujo SHA ou repositório diverge do solicitado
    When o gate valida a identidade da análise
    Then lança GateFailure
    And não usa o resultado divergente para aprovar

Feature: Validação externa da entrega
  Scenario: BDD-E-M01 Demonstração integrada no navegador
    Given duas contas de teste e análises autorizadas preparadas
    When o grupo segue os blocos cronometrados do roteiro
    Then registra o resultado visual de cada bloco
    And identifica qualquer etapa não demonstrada sem substituí-la por simulação não declarada

  Scenario: BDD-E-M02 Checks obrigatórios impedem merge
    Given um ambiente de CI configurado e proteção de branch comprovada
    When um PR seguro e outro com Critical novo executam os checks
    Then o grupo registra URLs, SHA e estados reais dos checks
    And comprova o bloqueio de merge do caso inseguro

  Scenario: BDD-E-M03 Orientação valida o contrato acadêmico
    Given as decisões D-01 sobre entrada, linguagem, stack e relatório
    When a orientação avalia o contrato apresentado
    Then o grupo anexa a decisão datada e sua evidência
    And mantém explícitos os pontos não aceitos ou ainda sem resposta
```

M01–M03 são cenários manuais pendentes. A conclusão documental E-01 não os transforma em aprovação acadêmica ou operacional externa.

## Cenário, fixture, teste e evidência

| Cenário | Fixture / preparação | Teste automatizado ou procedimento | Evidência e situação |
|---|---|---|---|
| BDD-E-01/02 | Classes Example válidas/inválidas em memória | [JavaParserSourceParserTest](../src/backend/src/test/java/com/fiap/sast/parsing/JavaParserSourceParserTest.java): `geraAstComLocalizacaoParaJavaValido`, `rejeitaJavaInvalidoMesmoQuandoHaAstParcial` | JUnit E-01, aprovado. |
| BDD-E-03 | Regras distintas, dois nós e regra duplicada | [SastEngineTest](../src/backend/src/test/java/com/fiap/sast/analysis/SastEngineTest.java): `preservaAchadosDeRegrasDiferentesNaMesmaLinha`, `preservaDoisAchadosDaMesmaRegraNaMesmaLinha`, `deduplicaAmesmaOcorrenciaEmitidaMaisDeUmaVez` | JUnit E-01, aprovado. |
| BDD-E-04/05/12 | Histórico de cinco entradas, resultados parciais e ranking estável de arquivos | [Dashboard.test.tsx](../src/frontend/src/Dashboard.test.tsx), testes com esses IDs e teste de `rankCriticalFiles` | Vitest E-01, aprovado; sem comprovar histórico real ponta a ponta. |
| BDD-E-06/07 | Respostas da API simuladas e interação de formulário | [main.test.tsx](../src/frontend/src/main.test.tsx), três testes com esses IDs | Vitest E-01, aprovado; isolamento real vem do OP-01. |
| BDD-E-08 | Resultado Critical novo e baseline vazio | [SecurityGateTest](../scripts/test_security_gate.py).`test_blocks_new_deterministic_critical` | unittest E-01, aprovado; política local. |
| BDD-E-09 | Critical existente e High novo | [SecurityGateTest](../scripts/test_security_gate.py).`test_existing_critical_does_not_block_and_high_is_report_only` | unittest E-01, aprovado; política local. |
| BDD-E-10 | Resultado Low degradado e JSON da política | [SecurityGateTest](../scripts/test_security_gate.py): `test_ai_degradation_does_not_hide_deterministic_result`, `test_policy_keeps_ai_out_of_the_gate` | unittest E-01, aprovado. |
| BDD-E-11/12 | Resultado incompleto, SHA divergente e outra origem | [SecurityGateTest](../scripts/test_security_gate.py): `test_fails_without_deterministic_coverage`, `test_fails_when_commit_or_repository_does_not_match` | unittest E-01, aprovado. |
| TASK-04 | Histórico sem atestado e baseline explícito | [SecurityGateTest](../scripts/test_security_gate.py): `test_history_never_becomes_an_implicit_baseline`, `test_accepts_only_explicitly_trusted_baseline_artifact`, `test_rejects_baseline_without_trusted_provenance` | unittest local, aprovado; fork não expõe secrets e bloqueia aprovação automática. |
| BDD-OP-01/05/10/11/12 | Snapshot Java sintético, duas contas, PostgreSQL e RabbitMQ reais; GitHub/IA simulados | [OperationIntegrationTest](../src/backend/src/test/java/com/fiap/sast/messaging/OperationIntegrationTest.java), métodos na tabela [O-01](Operacao-e-escalabilidade.md) | JUnit E-01 aprovado; [ensaio externo anterior](evidencias/O-01-2026-10-07.json) separado. |
| BDD-OP-02/03/04/08/09 | Duplicata, broker parado, lease expirado, origem transitória e payload inválido | [OperationIntegrationTest](../src/backend/src/test/java/com/fiap/sast/messaging/OperationIntegrationTest.java), métodos na tabela O-01 | JUnit E-01 aprovado; limites da retomada descritos em O-01. |
| BDD-OP-06 | ZIPs sintéticos em memória | [GitHubClientTest](../src/backend/src/test/java/com/fiap/sast/github/GitHubClientTest.java) | JUnit E-01 aprovado; sem executar o conteúdo. |
| BDD-OP-07 | Fixture oficial em memória e inicializador com marcador | [SastEngineTest](../src/backend/src/test/java/com/fiap/sast/analysis/SastEngineTest.java): `fixtureVulneravelProduzTresFindingsComTodosOsCampos`, `analiseNaoExecutaInicializadorDoCodigoFonte` | Exatamente três findings; marcador não criado; JUnit E-01 aprovado. |
| BDD-TAINT-01/02/03/05 | HTTP concatenado, replaceAll, fluxo entre métodos e constante | [TaintAnalysisEngineTest](../src/backend/src/test/java/com/fiap/sast/taint/TaintAnalysisEngineTest.java), métodos nomeados em Q-01 | JUnit E-01 aprovado, conforme heurística atual. |
| BDD-TAINT-04 | Corpus T01–T14 | [TaintQualityCorpusTest](../src/backend/src/test/java/com/fiap/sast/taint/TaintQualityCorpusTest.java).`bddTaintCorpusCalculaMetricasComDenominadoresExplicitos` | JUnit E-01: TP 8, FP 0, TN 4, FN 2. |
| TASK-06 / SAST-QUALITY-01 | Corpus transversal das três regras e taint, com `replaceAll`, `ProcessBuilder`, FP/FN e métricas por regra | [SastQualityCorpusTest](../src/backend/src/test/java/com/fiap/sast/quality/SastQualityCorpusTest.java).`corpusCalculaMetricasPorRegra` | JUnit: 18 casos em memória; métricas reproduzíveis por regra documentadas em Q-01. |
| BDD-IA-01/02/03 | Finding High, severidade divergente e gateway simulado | [SemanticAnalysisServiceTest](../src/backend/src/test/java/com/fiap/sast/semantic/SemanticAnalysisServiceTest.java), métodos nomeados em Q-01 | JUnit TASK-07 aprovado; finding permanece High quando a IA sugere Low. |
| BDD-IA-04 | Fixture oficial mais três comandos constantes | [LiveOllamaDemoTest](../src/backend/src/test/java/com/fiap/sast/semantic/LiveOllamaDemoTest.java).`sixLabeledFindingsReceiveRealAssessments` | Ensaio real e manifesto em Q-01; revisão manual 6/6; impacto em FP não comprovado. |
| BDD-E-M01 | Instância e análises preparadas | [Roteiro de defesa](Evidencias-e-roteiro-de-defesa.md) | Manual; registro visual integrado pendente. |
| BDD-E-M02 | Dois PRs e branch protegida | [CI e operação](CI-e-operacao.md) | Externo; sem PR/check externo registrado nesta etapa. |
| BDD-E-M03 | Decisões D-01 | [Registro de escopo](Decisoes-de-escopo.md) | Externo; nomes e validação da orientação pendentes. |

## Cobertura documental por requisito

| Requisito | Cenário | Código, teste ou decisão | Evidência / situação |
|---|---|---|---|
| RF01 — entrada | OP-05, E-M03 | [AnalysisController](../src/backend/src/main/java/com/fiap/sast/web/AnalysisController.java), D-01 | URL implementada/testada; equivalência acadêmica pendente. |
| RF02 — linguagem | E-01/02, OP-06, E-M03 | Parser e GitHubClientTest | Java apenas; expansão não entregue. |
| RF03 — validação | OP-05/06 | OperationIntegrationTest e GitHubClientTest | JUnit E-01 aprovado. |
| RF04 — análise estática | OP-07 | SastEngineTest | Três findings e não execução aprovados. |
| RF05 — parser | E-01/02 | JavaParserSourceParserTest | Parse/localização e erro aprovados. |
| RF06 — AST | E-01, OP-01 | JavaParserSourceParserTest | AST e concorrência aprovadas. |
| RF07 — motor extensível | E-03 | [SecurityRule](../src/backend/src/main/java/com/fiap/sast/rules/SecurityRule.java), SastEngineTest | Identidade e deduplicação aprovadas. |
| RF08 — violações | OP-07, E-M03 | SastEngineTest, D-01 | Equivalentes Java comprovados; aceite acadêmico pendente. |
| RF09 — CWE/severidade | OP-07, IA-01 | SastEngineTest e SemanticAnalysisServiceTest | Campos aprovados; sugestão divergente preserva a severidade original. |
| RF10 — localização/trecho | OP-07, E-01/03 | SastEngineTest e parser | Campos e ocorrências distintas aprovados. |
| RF11 — persistência/histórico | OP-01/04 | OperationIntegrationTest; [migrações](../src/backend/src/main/resources/db/migration) | Persistência/isolamento aprovados; sem expurgo automático. |
| RF12 — web | E-05/06/07, OP-01, E-M01 | main.test.tsx e OperationIntegrationTest | Testes locais aprovados; demonstração visual pendente. |
| RF13 — dashboard | E-04/05, E-M01 | Dashboard.test.tsx e AnalysisController | Tendência em fixture aprovada; exportação fora do contrato. |
| RF14 — IA | IA-01–04, OP-10 | Q-01 e testes semânticos | Contrato, divergência, degradação e revisão 6/6 registrados; redução real de falsos positivos não medida. |
| RF15 — taint | TAINT-01–05 | Corpus e TaintAnalysisEngineTest | Métricas restritas à amostra e à rotulagem adotada. |
| RF16 — remediação | IA-01/04 | SemanticAnalysisServiceTest e LiveOllamaDemoTest | Resposta estruturada testada; revisão manual das seis remediações registrada em Q-01. |
| RF17 — CI | E-08–12, E-M02 | [Workflow](../.github/workflows/ci.yml) | Implementado; execução externa pendente. |
| RF18 — gate | E-08–12, E-M02 | SecurityGateTest e [política](../.sast/security-gate.json) | Política local aprovada; bloqueio real de merge pendente. |
| RNF01 — não execução | OP-07 | SastEngineTest | Aprovado no fixture. |
| RNF02 — modularidade | E-01/03, OP-01 | [C4](Arquitetura-e-fluxos.md), SecurityRule | Estrutura documentada e testes locais aprovados. |
| RNF03 — Compose | OP-01–12 | [O-01](Operacao-e-escalabilidade.md), compose.yaml | Ensaio anterior real aprovado; config revalidado no E-01. |
| RNF04 — escala/assincronia | OP-01–04/08/09 | OperationIntegrationTest e O-01 | Concorrência/recuperação local; capacidade de produção não comprovada. |
| RNF05 — falsos positivos | TAINT-04, IA-04 | Q-01 | Corpus pequeno; sem generalização estatística. |
| RNF06 — testes | Todos os automatizados | [Registro E-01](evidencias/E-01-2026-10-07.md) | Resultados e motivos dos 13 ignorados explícitos. |
| RNF07 — manutenção | E-03 | [BackendQualityTest](../src/backend/src/test/java/com/fiap/sast/quality/BackendQualityTest.java), manual e C4 | Gate de qualidade passou; não equivale ao Security Gate remoto. |
| RNF08 — privacidade | OP-05/06/07/11, E-M03 | GitHubClientTest, auditoria O-01 e D-01 | Origem autorizada e logs conhecidos auditados; retenção automática ausente. |

Os nomes abreviados OP, TAINT, IA e E nas tabelas conservam o prefixo `BDD-`. Métodos omitidos na última tabela estão ligados por cenário na tabela anterior. Evidências históricas não são reclassificadas como execução nova, e teste simulado não comprova uso real da API externa.
