# Tarefas pendentes da plataforma SAST

Este arquivo transforma as lacunas identificadas na auditoria do checkout atual em tarefas verificáveis. Ele não reclassifica como pendente o que já está implementado e testado. Os responsáveis permanecem como **A definir pelo grupo** até a divisão oficial do trabalho.

## Tarefas prioritárias

| ID | Prioridade | Situação | Dependências | Ação | Critério de aceite | Responsável |
| --- | --- | --- | --- | --- | --- | --- |
| TASK-01 | P0 | Concluída — confirmação recebida do grupo | Orientação da disciplina | Validar formalmente o escopo Java, a entrada por URL pública, o uso de stack equivalente e o formato do relatório. | Confirmação registrada; `docs/Decisoes-de-escopo.md` atualizado; README, matriz e defesa usam a mesma interpretação. | Grupo do projeto |
| TASK-02 | P0 | Parcial — reprodução local concluída; evidência do GitHub pendente | Acesso ao GitHub Actions | Comprovar a execução limpa do CI no GitHub e uma execução com falha controlada. | Execução limpa aprovada e falha controlada produzindo check reprovado, com links das execuções e sem dados sensíveis. | A definir pelo grupo |
| TASK-03 | P0 | Pendente | TASK-02; secrets de serviço; proteção da branch | Validar o Security Gate integrado à API e exigir seus checks na branch protegida. | PR seguro aprovado; Critical novo, análise falha e cobertura incompleta bloqueados; merge impedido pelo check obrigatório. | A definir pelo grupo |
| TASK-04 | P1 | Concluída — política conservadora e testes locais | TASK-03 | Revisar a baseline do gate e definir tratamento seguro para PRs de forks. | A política não aceita automaticamente achados de uma execução insegura anterior; forks são documentados e testados sem exposição de secrets. | A definir pelo grupo |
| TASK-05 | P1 | Pendente | Definição de métrica do dashboard | Melhorar o ranking de arquivos críticos. | Ordenação considera severidade dos findings determinísticos, quantidade e desempate estável; sugestões consultivas aparecem separadamente. | A definir pelo grupo |
| TASK-06 | P1 | Pendente | TASK-01; corpus autorizado | Revisar heurísticas e ampliar a avaliação de precisão do SAST. | Corpus inclui as três regras e taint, casos de `replaceAll` e `ProcessBuilder`, métricas por regra e falsos positivos/negativos documentados. | A definir pelo grupo |
| TASK-07 | P1 | Pendente | TASK-06; acesso ao modelo local | Concluir a avaliação qualitativa e a reprodutibilidade da IA. | Remediações revisadas; modelo, prompt e checkout identificados; teste com severidade divergente preserva o finding original; impacto sobre falsos positivos medido ou explicitamente não comprovado. | A definir pelo grupo |
| TASK-08 | P1 | Pendente | Fluxo assíncrono estabilizado | Migrar os nove testes HTTP legados desabilitados para o fluxo assíncrono. | Cenários relevantes executam com criação, polling, resultados e isolamento por usuário, sem depender do contrato síncrono antigo. | A definir pelo grupo |
| TASK-09 | P1 | Pendente | Ambiente local e permissões do checkout | Corrigir a reprodução local do Maven quando `target` tiver permissões incompatíveis. | Procedimento seguro documentado e verificável sem sobrescrever arquivos do usuário nem exigir execução como root. | A definir pelo grupo |
| TASK-10 | P1 | Pendente | TASK-01; roteiro de defesa | Registrar a demonstração integrada e concluir a preparação da defesa. | Cadastro, análise, resultados, tendência e IA degradada demonstrados; integrantes e contribuições preenchidos; roteiro ensaiado. | A definir pelo grupo |
| TASK-11 | P1 | Pendente | Repositórios e fixtures escolhidos | Registrar autorização ou licença das origens usadas nas demonstrações. | Evidências de uso permitido identificadas; nenhum código integral ou segredo incluído na documentação. | A definir pelo grupo |
| TASK-12 | P2 | Pendente | Compose; instrumentação operacional | Medir capacidade com carga maior e múltiplas réplicas de worker. | Relatório informa carga, throughput, latências, memória, fila e recuperação; avalia perda de lease sem escrita indevida pelo worker antigo. | A definir pelo grupo |

## Tarefas condicionais à definição de escopo

As tarefas abaixo dependem da conclusão da TASK-01 e de aprovação explícita antes de qualquer mudança arquitetural. Todas começam como **Aguardando definição de escopo**.

| ID | Prioridade | Situação | Dependências | Ação | Critério de aceite | Responsável |
| --- | --- | --- | --- | --- | --- | --- |
| TASK-13 | P1 | Aguardando definição de escopo | TASK-01; decisão sobre linguagens | Implementar parsing multilinguagem se a orientação exigir suporte além de Java. | Linguagens adicionais, limites, parsers, regras, testes, contratos e documentação aprovados antes da implementação; nenhuma linguagem é escolhida antecipadamente. | A definir pelo grupo |
| TASK-14 | P1 | Aguardando definição de escopo | TASK-01; critério de cobertura acordado | Expandir o taint para chamadas entre métodos se essa cobertura for exigida. | Modelo de fluxo, limites de segurança, falsos positivos/negativos e testes interprocedurais aprovados antes da alteração. | A definir pelo grupo |
| TASK-15 | P1 | Aguardando definição de escopo | TASK-01; decisão sobre relatório | Implementar exportação de relatório se dashboard e histórico forem insuficientes. | Formato, campos derivados, retenção e controles de privacidade aprovados; exportação não contém fonte integral nem segredos. | A definir pelo grupo |
| TASK-16 | P1 | Aguardando definição de escopo | TASK-01; requisitos de entrega | Implementar pipeline de implantação automatizada caso CI/CD exija entrega contínua. | Ambiente, secrets, aprovações, rollback e observabilidade definidos; pipeline validado sem executar código do repositório analisado. | A definir pelo grupo |
| TASK-17 | P1 | Aguardando definição de escopo | TASK-01; decisão sobre tecnologias | Adaptar o stack se as tecnologias recomendadas forem exigências literais. | Substituições, compatibilidade, migrações, impacto operacional e critérios de aceite aprovados antes de alterar a arquitetura. | A definir pelo grupo |

## Evidência da auditoria de origem

Esta lista foi baseada na auditoria do checkout atual e deve ser atualizada quando uma tarefa for concluída, mantendo a evidência correspondente:

- backend aprovado em diretório temporário, com `BUILD SUCCESS`, 81 testes contabilizados, 68 executados, 13 ignorados e zero falhas;
- frontend com build aprovado e 41 testes aprovados;
- nove testes locais do Security Gate aprovados;
- testes TASK-04 cobrem baseline confiável explícito e bloqueio de proveniência não atestada;
- `docker compose config --quiet` aprovado;
- fixture Java oficial produzindo exatamente três findings e sem executar o código analisado.

As evidências de execução externa, proteção administrativa da branch e ensaio visual integrado continuam pendentes. A validação de escopo da orientação foi confirmada pelo grupo e está registrada no D-01.

## Verificação deste documento

- Conferir cada tarefa contra `docs/Matriz-de-requisitos-CP3.md` e `docs/Criterios-de-aceite-e-defesa.md`.
- Manter links e nomes de arquivos coerentes com o checkout atual.
- Executar `git diff --check` após alterações documentais.
- Preservar o DOCX original, arquivos existentes, tokens, archives, código-fonte de terceiros e artefatos gerados.
