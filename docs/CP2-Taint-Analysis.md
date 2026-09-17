# Extensão autorizada — Taint Analysis (CP2, primeiro incremento)

A CP2 foi desenhada na [ADR-001](adr/ADR-001-ia-e-analise-semantica.md) com dois estágios: um Taint Analysis determinístico e um enriquecimento consultivo por LLM local. Esta entrega implementa somente o primeiro estágio — o `TaintAnalysisEngine` — para o cenário vertical inicial de command injection (CWE-78). Integração com Ollama, sugestões de remediação e a demo de varredura semântica continuam fora desta entrega.

## Escopo e limites

O engine é intraprocedural: um fluxo começa e termina dentro do mesmo método, sem seguir chamadas para outros métodos ou classes. A varredura percorre as instruções do corpo do método na ordem em que aparecem no código-fonte (sem grafo de fluxo de controle completo), o que é suficiente para atribuição, reatribuição, concatenação de string e expressões condicionais simples, mas não modela laços complexos nem análise interprocedural — essa limitação é intencional e está descrita na ADR-001.

Como o projeto não configura resolução de símbolos (`JavaSymbolSolver`), o reconhecimento de fontes, sinks e sanitizers usa a mesma heurística sintática já empregada pelas regras da CP1 (correspondência por nome de método/anotação, sem verificação de tipo).

## Catálogos

- **Fontes (sources):** parâmetros de método anotados com `@RequestParam`, `@PathVariable`, `@RequestBody` ou `@ModelAttribute`; e chamadas a `xxx.getParameter("...")` em qualquer escopo.
- **Sinks:** `Runtime.getRuntime().exec(...)` e a criação de `new ProcessBuilder(...)`.
- **Sanitizers:** cadastro explícito e extensível. Nesta entrega, um único sanitizer didático: `String#replaceAll(String, String)`. Se um valor contaminado passar por um `replaceAll` antes de ser reatribuído, o rastro de taint é interrompido nesse ponto. Nenhum outro método é presumido como sanitizador — chamadas não cadastradas simplesmente não propagam taint adiante (evita falso positivo por presunção, ao custo de não perseguir o valor através de wrappers desconhecidos).

Os três catálogos (`TaintSourceCatalog`, `TaintSinkCatalog`, `TaintSanitizerCatalog`) são classes independentes em `com.fiap.sast.taint`; novas entradas não exigem alterar o algoritmo de propagação em `TaintAnalysisEngine`.

## Integração com o pipeline

`TaintAnalysisEngine` implementa `SecurityRule`, a mesma interface das regras da CP1, e é registrado automaticamente pelo Spring na lista injetada em `SastEngine` — nenhuma mudança foi necessária em `SastEngine` ou `AnalysisController` além da propagação do novo campo do contrato. O ruleId é `TAINT-CMDI-001`.

## Contrato HTTP

Cada finding pode conter, opcionalmente, um `taintTrace`:

```json
{
  "taintTrace": {
    "engineVersion": "1.0.0",
    "source": { "kind": "http_param", "line": 2, "column": 20 },
    "steps": [
      { "kind": "concatenation", "line": 3, "column": 20 },
      { "kind": "assignment", "line": 3, "column": 12 }
    ],
    "sink": { "kind": "sink", "line": 4, "column": 5 }
  }
}
```

`taintTrace` é `null` para findings estruturais (CWE-798, CWE-502 e a detecção sintática de `Runtime.exec` da CP1), que continuam inalterados.

## Persistência

A migration `V3__finding_taint_trace.sql` adiciona a coluna anulável `findings.taint_trace TEXT`. O JSON do `TaintTrace` é serializado/desserializado com Jackson na borda do `AnalysisController`, sem introduzir um conversor JPA — mantém o padrão de entidades planas já usado por `Finding`. A fingerprint de deduplicação de execuções (`findingsFingerprint`) passou a incluir o `taintTrace`, garantindo que duas análises com os mesmos achados estruturais mas traces diferentes não sejam incorretamente reaproveitadas.

## Testes

- `TaintAnalysisEngineTest`: cenário positivo (parâmetro HTTP → concatenação → sink, com trace completo), valor constante não contaminado, sanitização por `replaceAll`, limite intraprocedural (chamada a outro método não é seguida) e propagação em múltiplas reatribuições.
- `SastEngineTest`: confirma que a amostra oficial (`samples/VulnerableExample.java`) continua produzindo exatamente 3 findings — o parâmetro dessa amostra não tem anotação HTTP, então o taint engine não adiciona um quarto finding — e que o taint engine participa do pipeline junto às regras estruturais.
- `AuthenticatedAnalysisIntegrationTest`: confirma que o `taintTrace` sobrevive ao ciclo completo (POST cria, coluna no banco, GET retorna o mesmo trace).

## Pendências da CP2

Fora desta entrega, ainda conforme a ADR-001: integração com Ollama/Llama 3 (`SemanticAnalysisService`), campo `semanticStatus` e `aiAssessment` no contrato, sugestões de remediação e a demonstração de varredura semântica. O frontend também não foi alterado nesta etapa — a exibição do `taintTrace` fica para um incremento futuro.
