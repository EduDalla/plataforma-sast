# Qualidade do SAST, taint e IA — Q-01

Este documento registra a evidência reproduzível do pacote Q-01. O corpus é sintético, autorizado e mantido em memória nos testes; nenhum caso Java é compilado ou executado como código analisado.

## Método e escopo

O corpus transversal de precisão da TASK-06 está em `SastQualityCorpusTest`. Ele contém 18 casos
sintéticos e autorizados, mantidos em memória, distribuídos pelas três regras estruturais e pelo
taint. Cada caso tem rótulo revisado, justificativa e regra-alvo; os denominadores não misturam
regras diferentes. A avaliação usa apenas parser/AST e não compila nem executa nenhum caso Java.

Na execução local de 07/10/2026, as métricas reproduzíveis por regra foram:

| Regra | Corpus | TP | FP | TN | FN | Precisão | Recall |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| SAST-JAVA-001 — credencial hardcoded | 5 | 2 | 1 | 1 | 1 | 66,7% (2/3) | 66,7% (2/3) |
| SAST-JAVA-002 — Runtime.exec | 4 | 1 | 0 | 2 | 1 | 100% (1/1) | 50% (1/2) |
| SAST-JAVA-003 — readObject | 4 | 1 | 0 | 2 | 1 | 100% (1/1) | 50% (1/2) |
| TAINT-CMDI-001 — taint de comando | 5 | 2 | 0 | 2 | 1 | 100% (2/2) | 66,7% (2/3) |

Os falsos positivos e negativos estão identificados nos próprios casos: `HC-04` é o falso positivo
de um placeholder chamado `token`; `HC-05` não reconhece `credential`; `RE-03` não acompanha um
alias de `Runtime`; `DS-03` não indexa uma expressão direta de `ObjectInputStream`; e `TA-04` é interprocedural,
fora do escopo atual. `TA-03` registra a convenção de que `replaceAll` com dois argumentos quebra
o rastro; isso não constitui prova de sanitização universal. `TA-02` garante a presença explícita
de `ProcessBuilder` no corpus.

O teste de aceite transversal é `SastQualityCorpusTest.corpusCalculaMetricasPorRegra`; o corpus
taint detalhado abaixo permanece como avaliação ampliada, com 14 casos e rastreabilidade própria.

O corpus contém 14 casos rotulados para o `TaintAnalysisEngine`: oito positivos cobertos, dois positivos fora da cobertura atual, quatro negativos. A rotulagem responde à pergunta “uma entrada controlável chega a um sink de execução sem sanitização conhecida?”. O rótulo é independente do resultado do engine.

O engine reconhece como fontes `@RequestParam`, `@PathVariable`, `@RequestBody`, `@ModelAttribute` e chamadas `getParameter`. A propagação é intraprocedural e cobre atribuição, concatenação e expressão condicional. `replaceAll` com dois argumentos é tratado como sanitizador heurístico. Os sinks são `Runtime.getRuntime().exec()` e a construção de `ProcessBuilder`.

Os limites observados são relevantes: chamadas entre métodos não são seguidas; `trim()` não é reconhecido como sanitização nem como propagação; o reconhecimento não modela todos os caminhos de controle; e a construção de `ProcessBuilder` é reportada sem exigir a chamada posterior de `start()`.

## Feature: evidência do taint

```gherkin
Feature: Evidência do taint
  Scenario: BDD-TAINT-01: entrada HTTP concatenada alcança Runtime.exec
    Given um parâmetro `@RequestParam` concatenado a uma string de comando
    When o engine analisa o método
    Then um finding `TAINT-CMDI-001` é produzido
    And o trace contém fonte, concatenação, atribuição e sink.
```

Teste: `TaintAnalysisEngineTest.parametroHttpConcatenadoAtingindoRuntimeExecGeraFindingComTraceCompleto`.

```gherkin
Feature: Evidência do taint
  Scenario: BDD-TAINT-02: entrada sanitizada não gera finding
    Given um parâmetro HTTP transformado por `replaceAll`
    When o valor chega ao `Runtime.exec()`
    Then nenhum finding de taint é produzido conforme a heurística atual.
```

Teste: `TaintAnalysisEngineTest.sanitizadorReplaceAllQuebraORastroDeTaint`.

```gherkin
Feature: Evidência do taint
  Scenario: BDD-TAINT-03: fluxo entre métodos permanece fora da cobertura
    Given uma entrada HTTP passada a outro método
    When o sink está no método chamado
    Then nenhum finding é produzido pela análise intraprocedural
    And o caso é contabilizado como falso negativo conhecido do corpus.
```

Teste: `TaintAnalysisEngineTest.fluxoInterproceduralNaoEhRastreado`.

```gherkin
Feature: Evidência do taint
  Scenario: BDD-TAINT-04: corpus rotulado produz métricas com denominadores
    Given os 14 casos identificados de T01 a T14
    When cada fonte é analisada em memória
    Then TP, FP, TN e FN somam 14
    And TP=8, FP=0, TN=4 e FN=2 são verificados
    And precisão e recall ficam entre zero e um.
```

Teste: `TaintQualityCorpusTest.bddTaintCorpusCalculaMetricasComDenominadoresExplicitos`.

Na execução de 07/10/2026, a baseline produziu TP=8, FP=0, TN=4 e FN=2: precisão 100% (8/8) e recall 80% (8/10). Os dois falsos negativos são o fluxo entre métodos (T11) e a transformação `trim()` não reconhecida (T14). Esses números medem somente este corpus e não representam cobertura geral.

## Feature: avaliação consultiva da IA

```gherkin
Feature: Avaliação consultiva da IA
  Scenario: BDD-IA-01: severidade determinística permanece independente
    Given um finding determinístico com severidade `High`
    When o modelo devolve uma avaliação válida com severidade sugerida High
    Then a severidade do finding permanece `High`
    And a sugestão fica registrada somente na avaliação consultiva.
```

Teste: `SemanticAnalysisServiceTest.acceptsValidAssessmentWithoutChangingFinding`. O teste atual usa High tanto no finding quanto na sugestão; não comprova uma sugestão divergente. Essa lacuna fica explícita, sem ampliar a asserção por descrição.

```gherkin
Feature: Avaliação consultiva da IA
  Scenario: BDD-IA-02: resposta inválida degrada sem apagar findings
    Given uma resposta inválida, campo extra ou severidade fora do contrato
    When a avaliação é validada
    Then o estado é `DEGRADED`
    And nenhuma avaliação inválida é retornada pelo serviço para persistência.
```

Teste: `SemanticAnalysisServiceTest.degradesForInvalidJsonExtraFieldAndUnavailableModel`.

```gherkin
Feature: Avaliação consultiva da IA
  Scenario: BDD-IA-03: timeout e indisponibilidade preservam a análise determinística
    Given o gateway excede o orçamento ou está indisponível
    When a etapa consultiva é executada
    Then as tentativas obedecem ao contrato de retentativa
    And os findings determinísticos permanecem disponíveis.
```

Teste: `SemanticAnalysisServiceTest.retriesOnlyTransientFailures` verifica tentativas e estado; a preservação persistida de findings com IA indisponível é verificada por `BDD-OP-10`. O timeout HTTP do cliente é coberto separadamente por `OllamaClientTest.timeoutIsRetryable`.

```gherkin
Feature: Avaliação consultiva da IA
  Scenario: BDD-IA-04: seis findings recebem avaliações reais do modelo local
    Given seis findings provenientes de dois fixtures sintéticos em memória
    When o cenário live é executado na rede interna do Compose
    Then seis avaliações válidas são retornadas
    And o estado consultivo é `COMPLETED` sem alterar os findings.
```

Teste: `LiveOllamaDemoTest.sixLabeledFindingsReceiveRealAssessments`.

## Constante sem taint

```gherkin
Feature: Ausência de contaminação no escopo intraprocedural
  Scenario: BDD-TAINT-05 Constante não contaminada
    Given um comando constante sem fonte HTTP
    When o engine de taint analisa o método
    Then nenhum finding de taint é produzido
    And essa conclusão não elimina um possível finding da regra estrutural RuntimeExec
```

Teste: `TaintAnalysisEngineTest.valorConstanteNaoContaminadoNaoGeraFinding`.

## Corpus, rastreabilidade e métricas

| Identificador | Cobertura | Teste/evidência |
| --- | --- | --- |
| T01–T08 | Fontes, atribuição, concatenação, condicional e `ProcessBuilder` | `TaintQualityCorpusTest` |
| T09, T10, T12, T13 | Negativos e falsos positivos | `TaintQualityCorpusTest` |
| T11, T14 | Limitações/falsos negativos conhecidos | `TaintQualityCorpusTest`, `TaintAnalysisEngineTest` |
| BDD-TAINT-01–05 | Trace, sanitização, limite e métricas | `TaintAnalysisEngineTest`, `TaintQualityCorpusTest` |
| BDD-IA-01–04 | Independência, JSON inválido, timeout, indisponibilidade e seis avaliações reais | `SemanticAnalysisServiceTest`, `LiveOllamaDemoTest` |
| Fixture oficial | Três findings e não execução | `SastEngineTest` |

## Ensaio real do Ollama

O ensaio relatado usou Ollama 0.34.4 e o modelo `llama3.2:3b`. Em 07/10/2026, `LiveOllamaDemoTest#sixLabeledFindingsReceiveRealAssessments` foi executado em contêiner Maven na rede interna do Compose com `SAST_RUN_LIVE_OLLAMA=true`: seis findings foram avaliados, seis avaliações válidas foram produzidas, o estado foi `COMPLETED` e a etapa durou aproximadamente 160,9 s. A execução completa dos três testes live também passou: três testes, sem falhas, em aproximadamente 227,7 s; ela incluiu uma sugestão de desempenho independente.

O registro usou modelo `llama3.2:3b`, Ollama 0.34.4, prompt versão `2` e o checkout corrente. A revisão qualitativa das remediações deve registrar notas de 0 a 2 para pertinência, ação concreta e segurança; essas notas não foram inferidas automaticamente a partir do log. Prompts, respostas brutas, tokens, snippets e código integral não devem ser anexados.

## Critério de conclusão

Q-01 possui evidência automatizada quando o corpus, os cenários BDD, os denominadores e as métricas estão versionados. A avaliação real foi executada para seis findings; a revisão qualitativa manual das remediações continua sendo uma evidência operacional complementar. O gate de segurança continua usando apenas findings determinísticos.

## Limites da evidência e ligação E-01

A [rastreabilidade geral](Rastreabilidade-BDD.md) registra fixtures, métodos e situação. O [verify E-01](evidencias/E-01-2026-10-07.md) repete os testes simulados e o corpus, mas não repete o ensaio real de Ollama. O relato histórico acima não contém notas qualitativas nem um manifesto com hash do modelo/checkout; esses itens não são inferidos de seis respostas válidas. A classe live tem quatro testes no checkout atual, todos opcionais; o relato de três refere-se à seleção executada anteriormente.

Os rótulos T08 (construção de ProcessBuilder sem start) e T09 (replaceAll heurístico) refletem a convenção adotada no corpus. Não são demonstração de exploração ou de sanitização universal. Os números medem somente TAINT-CMDI-001 nesse conjunto, não precisão das três regras estruturais nem redução de falsos positivos pela IA. As asserções do corpus verificam contagens agregadas; não validam localização/trace de cada caso, cobertos separadamente em fixtures específicos. Revisão da rotulagem e avaliação qualitativa permanecem pendências de qualidade, sem mudar heurísticas nesta etapa documental.
