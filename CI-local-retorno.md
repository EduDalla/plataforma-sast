# Retorno da execução local do CI e Security Gate

Execução realizada em 07/10/2026, com a API local em `http://localhost:8085` e o repositório público `https://github.com/EduDalla/plataforma-sast`.

O comando utilizado foi:

```bash
bash scripts/local_ci_gate.sh
```

## Saída resumida

```text
== Demonstração local equivalente ao GitHub Actions ==
Repositório: https://github.com/EduDalla/plataforma-sast
Commit: 40417b709c4ab9911773cb5bf25787b5a5d44743
API local: http://localhost:8085
Credenciais: configuradas e ocultas

[1/6] Validando Docker Compose
OK

[2/6] Verificando saúde da API local
OK — status UP

[3/6] Verificando backend
BUILD SUCCESS
Tests run: 81, Failures: 0, Errors: 0, Skipped: 13

[4/6] Instalando e construindo frontend
Build aprovado

[5/6] Executando testes do frontend e da política do gate
Test Files: 5 passed
Tests: 41 passed
Security Gate policy tests: 6 passed

[6/6] Executando Security Gate contra a API local
```

## Decisão retornada pelo Security Gate

```json
{
  "status": "FAIL",
  "analysisId": "dd3a073e-f1ea-4507-bb5d-36dec2838e71",
  "commitSha": "40417b709c4ab9911773cb5bf25787b5a5d44743",
  "findings": 2,
  "baselineFindings": 0,
  "newCritical": 2,
  "high": 0,
  "semanticStatus": "COMPLETED",
  "suggestionStatus": "COMPLETED",
  "locations": [
    {
      "ruleId": "SAST-JAVA-001",
      "fileName": "src/backend/src/test/java/com/fiap/sast/messaging/OperationIntegrationTest.java",
      "line": 66,
      "column": 33
    },
    {
      "ruleId": "SAST-JAVA-001",
      "fileName": "src/backend/src/test/java/com/fiap/sast/persistence/AnalysisReadRepositoryIntegrationTest.java",
      "line": 81,
      "column": 9
    }
  ]
}
```

```text
Process exit code: 1
```

## Interpretação

O retorno `FAIL` é esperado para essa demonstração: a primeira análise da conta técnica não possuía baseline e encontrou dois findings determinísticos `Critical`. O gate bloqueou o resultado conforme a política `newCriticalDeterministic=true`.

A etapa semântica terminou como `COMPLETED`, mas não influenciou a decisão. O gate utiliza somente findings determinísticos.

Nenhum token, senha, snippet, prompt ou resposta bruta da IA foi incluído neste arquivo. O retorno contém somente contagens, identificadores, regra e localização mínima necessária para a demonstração.

Referências:

- [Script de execução local](scripts/local_ci_gate.sh)
- [Implementação do Security Gate](scripts/security_gate.py)
