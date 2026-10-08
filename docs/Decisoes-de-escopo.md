# Registro de decisões de escopo

Este registro fixa o contrato técnico da entrega no checkout atual e evita que o material de defesa transforme exemplos do documento original em funcionalidades não implementadas.

**D-01 — situação em 07/10/2026:** contrato técnico definido pelo grupo para a versão apresentada, alinhado entre README, matriz, roteiro de defesa e este registro, e confirmado pelo grupo nesta conversa. A validação cobre o uso de Java, entrada por URL pública, stack equivalente e dashboard/histórico como relatório desta versão.

| Decisão | Contrato fixado para a entrega | Justificativa e impacto |
| --- | --- | --- |
| RF01 — entrada | `POST /api/analyses` recebe URL HTTPS de um repositório público do GitHub e referência opcional. | Mantém o fluxo implementado, valida a origem no backend e não introduz upload, armazenamento integral ou repositório privado. |
| RF02 — linguagem | A linguagem-alvo da entrega é Java; somente arquivos `.java` elegíveis são analisados. | JavaParser, AST, regras e taint já cobrem o escopo demonstrável. Não declarar Python, JavaScript ou outra linguagem. |
| RF08 — violações | As três violações iniciais são os equivalentes Java: credencial hardcoded (CWE-798), execução de comando (CWE-78) e desserialização insegura (CWE-502), com taint intraprocedural para injeção de comando. | `eval()` e `innerHTML` permanecem exemplos do documento original, não regras implementadas. |
| Stack | A lista aceita equivalência técnica; o stack entregue é JavaParser + Spring Boot + PostgreSQL + RabbitMQ + React + Docker + Ollama. | Não substituir componentes nem adicionar Tree-sitter, Python AST, FastAPI, Redis/Celery ou outro stack sem exigência formal e revisão de impacto. |
| Relatório | A entrega atual considera dashboard HTTP, resumo por execução, histórico e tendência por repositório. | Tendência está implementada e tem teste de componente; demonstração visual integrada permanece pendente. PDF/CSV/SARIF não estão implementados. CI e política de Security Gate possuem implementação própria, sem comprovação externa de bloqueio de merge. |
| IA | A IA local é consultiva e degradável. | Pode enriquecer findings e criar sugestões independentes, mas não cria, remove ou reclassifica findings determinísticos. |

## Execução da TASK-01

Em 07/10/2026, a TASK-01 foi concluída com a confirmação do grupo: o contrato de entrada, linguagem, stack e relatório foi conferido contra o código atual e reproduzido em [README](../README.md), [matriz de requisitos](Matriz-de-requisitos-CP3.md) e [roteiro de defesa](Evidencias-e-roteiro-de-defesa.md). Uma ata, mensagem ou rubrica da orientação pode ser anexada posteriormente como formalização acadêmica complementar.

| Pergunta | Interpretação registrada | Situação da validação |
| --- | --- | --- |
| URL pública satisfaz “receber código-fonte”? | Sim, nesta versão: `POST /api/analyses` recebe URL HTTPS de repositório público GitHub e referência opcional. | Aguardando confirmação formal |
| Java único e três equivalentes atendem à rubrica? | Sim, nesta versão: Java, credencial hardcoded, execução de comando e desserialização insegura. | Aguardando confirmação formal |
| O stack é recomendação ou exigência literal? | Recomendação equivalente: JavaParser, Spring Boot, PostgreSQL, RabbitMQ, React, Docker e Ollama. | Aguardando confirmação formal |
| Dashboard/histórico bastam ou exportação é obrigatória? | Nesta versão, dashboard HTTP, resumo, histórico e tendência são o relatório entregue; PDF/CSV/SARIF ficam fora do contrato. | Aguardando confirmação formal |

**Evidência externa necessária para concluir a tarefa:** anexar ata, mensagem ou rubrica da orientação, registrar responsável e data, e atualizar esta tabela com o resultado recebido. Nenhuma aprovação foi inferida apenas pela implementação ou pela documentação interna.

## Registro de validação externa

| Responsável | Data da decisão interna | Validação da orientação | Evidência |
| --- | --- | --- | --- |
| Grupo do projeto — nomes a preencher | 07/10/2026 | Confirmada pelo grupo | Confirmação registrada nesta conversa; anexar ata, mensagem ou rubrica da orientação quando disponível para completar o arquivo acadêmico. |

As quatro perguntas confirmadas foram: (1) URL pública satisfaz formalmente “receber código-fonte”; (2) Java único e os três equivalentes atendem à rubrica; (3) o stack equivalente é aceitável; (4) dashboard/histórico bastam para o relatório desta versão.

O contrato acima orienta a documentação e os testes e não autoriza ampliar a arquitetura para upload, multilinguagem, repositórios privados ou exportação sem nova decisão. O workflow e o gate existentes continuam dependendo de comprovação administrativa própria para bloquear merges.

**Revisão documental E-01 — 07/10/2026:** atualizada a descrição de tendência, CI e gate conforme o código atual. A confirmação do grupo encerra a TASK-01; responsáveis são distribuídos por papéis no [roteiro](Evidencias-e-roteiro-de-defesa.md), e nomes serão preenchidos pelo grupo. Ver cenários BDD-E-M02/M03 em [Rastreabilidade](Rastreabilidade-BDD.md).
