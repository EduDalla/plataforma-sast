# Registro de decisões de escopo

Este registro fixa o contrato técnico da entrega no checkout atual e evita que o material de defesa transforme exemplos do documento original em funcionalidades não implementadas.

**D-01 — situação em 05/10/2026:** contrato técnico definido pelo grupo para a versão apresentada; a validação formal da orientação ainda deve ser anexada pelo grupo. Até essa validação, não se deve iniciar uma mudança arquitetural motivada pelas divergências abaixo.

| Decisão | Contrato fixado para a entrega | Justificativa e impacto |
| --- | --- | --- |
| RF01 — entrada | `POST /api/analyses` recebe URL HTTPS de um repositório público do GitHub e referência opcional. | Mantém o fluxo implementado, valida a origem no backend e não introduz upload, armazenamento integral ou repositório privado. |
| RF02 — linguagem | A linguagem-alvo da entrega é Java; somente arquivos `.java` elegíveis são analisados. | JavaParser, AST, regras e taint já cobrem o escopo demonstrável. Não declarar Python, JavaScript ou outra linguagem. |
| RF08 — violações | As três violações iniciais são os equivalentes Java: credencial hardcoded (CWE-798), execução de comando (CWE-78) e desserialização insegura (CWE-502), com taint intraprocedural para injeção de comando. | `eval()` e `innerHTML` permanecem exemplos do documento original, não regras implementadas. |
| Stack | A lista aceita equivalência técnica; o stack entregue é JavaParser + Spring Boot + PostgreSQL + RabbitMQ + React + Docker + Ollama. | Não substituir componentes nem adicionar Tree-sitter, Python AST, FastAPI, Redis/Celery ou outro stack sem exigência formal e revisão de impacto. |
| Relatório | A entrega atual considera dashboard HTTP, resumo por execução, histórico e tendência por repositório. | Tendência está implementada e tem teste de componente; demonstração visual integrada permanece pendente. PDF/CSV/SARIF não estão implementados. CI e política de Security Gate possuem implementação própria, sem comprovação externa de bloqueio de merge. |
| IA | A IA local é consultiva e degradável. | Pode enriquecer findings e criar sugestões independentes, mas não cria, remove ou reclassifica findings determinísticos. |

## Registro de validação externa

| Responsável | Data da decisão interna | Validação da orientação | Evidência |
| --- | --- | --- | --- |
| Grupo do projeto — nomes a preencher | 05/10/2026 | Pendente | Anexar ata, mensagem ou rubrica validada; não inferir aceite pela existência deste arquivo. |

As quatro perguntas que precisam ser confirmadas são: (1) URL pública satisfaz formalmente “receber código-fonte”; (2) Java único e os três equivalentes atendem à rubrica; (3) o stack é recomendação ou exigência literal; (4) dashboard/histórico bastam ou é obrigatória exportação.

Até que a validação seja registrada, o contrato acima orienta a documentação e os testes, mas não autoriza ampliar a arquitetura para upload, multilinguagem, repositórios privados ou exportação. O workflow e o gate existentes não comprovam configuração administrativa da branch; essa validação externa continua pendente.

**Revisão documental E-01 — 07/10/2026:** atualizada a descrição de tendência, CI e gate conforme o código atual, preservando a data da decisão interna e a ausência de validação externa. Responsáveis são distribuídos por papéis no [roteiro](Evidencias-e-roteiro-de-defesa.md); nomes serão preenchidos pelo grupo. Ver cenários BDD-E-M02/M03 em [Rastreabilidade](Rastreabilidade-BDD.md).
