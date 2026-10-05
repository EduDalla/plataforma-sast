# Evidências e roteiro de defesa

Este roteiro separa evidência já registrada de demonstração ainda pendente. Uma funcionalidade não deve ser apresentada como concluída apenas porque existe código correspondente.

## Verificação automatizada

Executar na raiz, em ambiente autorizado:

```bash
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
docker compose config
```

Evidências mínimas a guardar: status de saída, versões de JDK/Node/Docker, contagem de testes, ausência de segredos na saída e resultado do health check. Não anexar `.env`, tokens, archives ou código de terceiros.

## Roteiro de 8 minutos

1. **Contexto (45 s):** explicar SAST para Java, fronteiras de confiança e a regra de não execução do código recebido.
2. **Login e isolamento (60 s):** cadastrar duas contas, autenticar ambas e mostrar que uma não consulta a análise da outra; encerrar sessão e conferir o descarte do token da aba.
3. **Submissão (60 s):** enviar uma URL pública autorizada, mostrar `202`, `Location`, `QUEUED` e a criação assíncrona.
4. **Pipeline (90 s):** mostrar `DOWNLOADING`, `DETERMINISTIC`, progresso de arquivos e a mensagem de que o snapshot é tratado em memória.
5. **Achados (90 s):** abrir fixture autorizado com credencial hardcoded, `Runtime.exec()` e desserialização; explicar CWE, localização, snippet e, quando aplicável, o trace de taint.
6. **IA consultiva (60 s):** distinguir avaliação/sugestão de finding determinístico; desligar Ollama ou usar timeout e mostrar `DEGRADED` sem perda dos achados.
7. **Dashboard e histórico (60 s):** mostrar resumo por severidade, arquivos críticos, sistemas e histórico. Declarar que série temporal de tendência ainda é lacuna documentada, se a versão apresentada não a tiver.
8. **Limitações e próximos passos (75 s):** mencionar CI/CD e Security Gate pendentes, limites intraprocedurais do taint, escopo Java e o status de validação externa do contrato D-01.

## Fixture determinístico

O teste `SastEngineTest` deve ser usado para demonstrar que o fixture permanece em memória, não é executado e produz exatamente três findings (`SAST-JAVA-001`, `SAST-JAVA-002` e `SAST-JAVA-003`). A evidência deve registrar apenas contagens, IDs, CWE e localizações necessárias, nunca o código integral de um repositório externo.

## Evidências por requisito

| Tema | Evidência no checkout | Situação |
| --- | --- | --- |
| Autenticação e isolamento | `AuthController`, `SecurityConfig`, testes de JWT e controller | Implementado; integração HTTP assíncrona deve ser repetida em Compose. |
| Parser, AST e três regras | `JavaParserSourceParser`, `SastEngine`, regras e `SastEngineTest` | Implementado no escopo Java; preservar teste de não execução. |
| Taint | `TaintAnalysisEngineTest` e `TaintTrace` | Implementado com limite intraprocedural. |
| IA consultiva | testes de Ollama e serviços semânticos | Implementado com degradação; ensaio live é opcional. |
| CI/CD e Security Gate | não há workflow/gate versionado no checkout | Pendente. |
| Tendência histórica | dashboard tem histórico/resumos, sem série temporal final | Parcial. |
