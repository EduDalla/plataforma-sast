# Autorização e licença das origens demonstrativas — TASK-11

Registro sanitizado em **07/10/2026**, no fuso `America/Sao_Paulo (UTC−03)`. Este documento identifica somente as origens usadas nas demonstrações e nos testes; não reproduz código-fonte, snippets, archives, tokens ou segredos.

## Matriz de origens

| Origem | Uso demonstrativo | Evidência de uso permitido | Escopo e limite |
|---|---|---|---|
| [EduDalla/plataforma-sast](https://github.com/EduDalla/plataforma-sast) | Ensaio operacional O-01, com quatro análises do snapshot público | Repositório visível como **Public** no GitHub; a organização/conta `EduDalla` corresponde ao remote do checkout e ao autor do projeto; uso declarado pelo titular para a demonstração acadêmica do próprio projeto | SHA analisado: `f1a32e2e315acb1cc7b56377151f98a3635a58eb`. Uso restrito à análise estática e aos resultados derivados registrados em [O-01](evidencias/O-01-2026-10-07.json). Não há licença open source explícita identificada na raiz; esta evidência não autoriza copiar ou redistribuir código. |
| Fixture oficial em memória | Teste `SastEngineTest`, com exatamente três findings e prova de não execução | Fixture original, sintético e mantido pelo grupo dentro do teste; autorização decorre da autoria do projeto e do uso interno para validação | Não é repositório externo nem demonstração de origem de terceiros; o texto permanece no código de teste, não nesta documentação. |
| Corpus sintético de qualidade/taint | Testes `SastQualityCorpusTest` e `TaintQualityCorpusTest` | Casos construídos pelo grupo, rotulados e mantidos em memória | Uso apenas para métricas reproduzíveis das heurísticas atuais; não representa corpus público nem licença de terceiros. |
| ZIPs e respostas de GitHub simulados | Testes `GitHubClientTest` e `OperationIntegrationTest` | Dados sintéticos criados nos testes | Não são snapshots reais nem código de terceiros; permanecem em memória e não constituem origem demonstrativa externa. |

## Conclusão de autorização

As demonstrações externas registradas nesta entrega usam somente o próprio repositório público do grupo, com a referência fixa acima. Os demais casos são fixtures sintéticos próprios. Nenhuma origem privada, repositório de terceiro ou código integral de terceiro foi incluído na documentação.

A visibilidade pública do repositório não é tratada como licença ampla. Até que o grupo registre uma licença ou autorização formal adicional, não se deve copiar, publicar ou redistribuir o código analisado fora do escopo da demonstração acadêmica. Outra origem exige nova verificação e registro antes do uso.

## Evidências rastreáveis

- [Registro O-01](evidencias/O-01-2026-10-07.json): URL, SHA, contagens e resultados derivados do ensaio operacional.
- [Operação e escalabilidade](Operacao-e-escalabilidade.md): procedimento e identificação da origem usada no ensaio.
- [Rastreabilidade BDD](Rastreabilidade-BDD.md): fixtures, testes e limites de cada cenário.
- [Repositório público](https://github.com/EduDalla/plataforma-sast): visibilidade e origem declarada no GitHub.

## Pendência acadêmica

Permanece pendente apenas a confirmação nominal do grupo sobre a autorização para a apresentação, caso a disciplina exija ata, mensagem ou rubrica específica. Essa pendência não altera a evidência técnica de que o ensaio usou o próprio repositório público e fixtures autorais.
