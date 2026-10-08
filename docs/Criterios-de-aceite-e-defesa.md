# Critérios de aceite e defesa — E-01

O aceite documental permite instalar, verificar e explicar a versão apresentada. Ele não equivale ao aceite da orientação nem comprova proteção administrativa da branch. A [matriz RF/RNF](Matriz-de-requisitos-CP3.md) registra o escopo, e a [rastreabilidade BDD](Rastreabilidade-BDD.md) liga todos os requisitos a testes, evidências ou decisões.

## Verificação local desta etapa

Execução de 07/10/2026 no checkout com alterações locais, identificada em [E-01](evidencias/E-01-2026-10-07.md).

| Verificação | Resultado efetivo |
|---|---|
| Maven verify / JDK 21 | BUILD SUCCESS; 80 testes contabilizados, 67 executados, 13 ignorados; zero falhas/erros. |
| Operação integrada | Oito testes com PostgreSQL/RabbitMQ reais aprovados dentro do verify; GitHub/Ollama simulados. |
| Fixture oficial e não execução | OP-07 aprovado: três findings e marcador não criado. |
| Corpus taint | TAINT-04 aprovado: 14 casos, TP 8, FP 0, TN 4, FN 2; precisão 8/8 e recall 8/10. |
| Build frontend | TypeScript e Vite aprovados. |
| Vitest | Cinco arquivos, 40 testes aprovados. |
| Política local do gate | Seis testes Python aprovados; não são PRs reais. |
| Compose | Configuração validada com `--quiet`, sem expor variáveis. |

Os 13 ignorados são nove cenários de `AuthenticatedAnalysisIntegrationTest` desabilitados por pressuporem fluxo síncrono legado e quatro cenários opcionais de `LiveOllamaDemoTest`, dependentes de `SAST_RUN_LIVE_OLLAMA=true`. OP-01 acrescenta evidência de polling/isolamento, mas não torna os nove testes legados executados. O build Docker com testes pulados também não substitui verify.

As falhas ambientais relatadas em 05/10/2026 (Docker indisponível ao Testcontainers e agente Mockito) pertencem ao registro histórico. Nesta execução E-01, essas dependências funcionaram. O relato live Q-01 e o JSON externo O-01 são anteriores e não foram reexecutados como ensaios externos nesta etapa.

## Critérios observáveis em BDD

| Critério | Cenários / evidência | Limite de aceite |
|---|---|---|
| Cadastro, sessão e isolamento | E-06/07, OP-01/12 | Interface simulada e JWT/DB reais nos testes; logout não revoga cópias de JWT no servidor. |
| Entrada segura e download controlado | OP-05/06 | Recusa antes de criar tarefa e ZIPs em memória; autorização acadêmica do contrato URL ainda depende de D-01. |
| Parser, AST, regras e localização | E-01/02/03, OP-07 | Três findings no fixture, identidade preservada e não execução. |
| Taint e métricas | TAINT-01–05 | Intraprocedural, sanitizador heurístico e amostra limitada; sem conclusão universal. |
| IA consultiva degradável | IA-01–04, OP-10 | Contrato e preservação operacional; comparação de severidades divergentes e qualidade manual ainda têm lacunas em Q-01. |
| Resultados e tendência | E-04/05, OP-01 | Testes de componente e isolamento aprovados; demonstração visual integrada M01 pendente. |
| Filas, retentativa e recuperação | OP-01–04/08/09 | Aprovados nos cenários controlados; simulação de lease expirado não comprova todos os casos de worker antigo ativo. |
| Logs e dados mínimos | OP-11 e O-01 | Nenhum segredo/snippet conhecido encontrado; auditoria não prova ausência de todo dado sensível possível. |
| Gate determinístico | E-08–12 | Política local testada; PR, checks externos e bloqueio administrativo M02 pendentes. |

Definições Given/When/Then, fixtures e métodos estão em [Rastreabilidade BDD](Rastreabilidade-BDD.md). Os testes não são duplicados para a documentação.

## Aceite documental E-01

- [x] C4 de contexto e contêineres coerente com serviços, redes, protocolos e fronteiras reais.
- [x] Manual de instalação, configuração, contratos HTTP, estados e falhas síncronas/assíncronas.
- [x] Modelo de dados e evolução V1–V11 documentados, inclusive retenção sem expurgo automático.
- [x] Todos os RF01–RF18 e RNF01–RNF08 ligados a evidência de código, teste ou decisão.
- [x] Cenários BDD e identificadores dos testes reconciliados.
- [x] Roteiro de 480 segundos, responsabilidades por papel e evidências alternativas.
- [x] Verificações locais executadas e resultados desta etapa registrados.
- [ ] Nomes dos integrantes e contribuições confirmados pelo grupo.
- [ ] Demonstração visual integrada e ata M01 registradas.
- [ ] PRs seguro/inseguro, execução externa de CI e proteção da branch comprovados.
- [ ] Revisão qualitativa das seis remediações e demais lacunas Q-01 concluídas.
- [x] Validação do escopo D-01 confirmada pelo grupo e registrada.

O pacote documental está concluído no escopo E-01; os itens externos pendentes não são declarados concluídos. Capacidade de produção, exportação PDF/CSV/SARIF, multilinguagem, upload e repositórios privados não são capacidades entregues por esta etapa.

## Operação e reprodução

O ensaio [O-01](Operacao-e-escalabilidade.md), registrado em [JSON](evidencias/O-01-2026-10-07.json), analisou quatro vezes o mesmo SHA público deste projeto, com 66/66 arquivos e um finding por análise. Observou duas tentativas ativas, fila até quatro mensagens e máximos amostrados de 602,2 MiB para API e 666,4 MiB para worker. Não é o fixture unitário de três findings, nem medição de SLA.

Para repetir, seguir o manual e o script isolado O-01, sem parar a instância de desenvolvimento. Comandos de verificação e divisão de tempo estão no [roteiro](Evidencias-e-roteiro-de-defesa.md). Guardar apenas revisão, versões, comandos, estados, contagens e métricas revisadas; não anexar logs completos, tokens ou corpos de IA.
