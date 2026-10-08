# Evidências e roteiro de defesa — E-01

Demonstração de **8 minutos (480 segundos)**, com preparação fora do tempo de apresentação. Cada afirmação deve apontar à [rastreabilidade BDD](Rastreabilidade-BDD.md), ao [registro atual E-01](evidencias/E-01-2026-10-07.md) ou aos ensaios históricos [Q-01](Qualidade-taint-e-IA.md) e [O-01](Operacao-e-escalabilidade.md).

## Preparação e responsabilidades

| Papel | Nome | Preparação |
|---|---|---|
| Arquitetura e escopo | A preencher pelo grupo | Abrir C4, decisões D-01 e matriz RF/RNF; conhecer limitações e perguntas da orientação. |
| Operação e segurança | A preencher pelo grupo | Subir ambiente pelo manual, conferir seis health checks, preparar duas contas de teste e análises autorizadas. |
| Engine e qualidade | A preencher pelo grupo | Abrir testes do fixture oficial, trace de taint, corpus, estados consultivos e evidências sem dados sensíveis. |
| Interface e CI | A preencher pelo grupo | Preparar dashboard/histórico, tabela de tendência e resultados dos seis testes locais do gate. |
| Registro e tempo | A preencher pelo grupo | Cronometrar, registrar data/revisão, verificar links e anotar o que foi efetivamente demonstrado. |

Uma pessoa pode acumular papéis. Não atribuir nomes ou contribuições sem confirmação do grupo. Validação da orientação e divisão nominal permanecem pendentes.

1. Seguir o [manual de instalação](Manual-tecnico-e-api.md), preservando o `.env` existente. Executar os comandos de verificação abaixo e guardar apenas resultados derivados.
2. Usar somente origens públicas autorizadas. O ensaio O-01 usou este próprio projeto e o SHA registrado; repetir com outro SHA produz nova evidência, não substitui silenciosamente o registro antigo.
3. Preparar duas sessões de navegador independentes para isolamento. Criar análises antecipadamente: IA local e falhas provocadas podem ultrapassar oito minutos.
4. Preparar histórico real de execuções autorizadas que exibam variação, quando disponível. Se não houver, demonstrar a tabela com o teste de componente BDD-E-04, identificando-o como fixture simulado.
5. Abrir o fixture oficial em seu teste local. Ele permanece em memória e gera três findings; não é um repositório de demonstração publicado. A análise pública do O-01 gerou **um** finding por execução.
6. Para degradação, usar resultado registrado O-01 ou executar previamente o script isolado. Não interromper serviços da instância de desenvolvimento durante a defesa.
7. Deixar documentos e resultados revisados disponíveis localmente. Não mostrar `.env`, aba de rede com Bearer, logs completos, prompts ou respostas brutas de IA.

## Roteiro cronometrado

| Intervalo / tempo | Papel | Ação e resultado esperado | Evidência alternativa |
|---|---|---|---|
| 00:00–00:45 / 45 s | Arquitetura e escopo | Explicar origem pública Java, fronteiras e não execução. Seguir o C4 de contexto até os seis serviços. | [C4 versionado](Arquitetura-e-fluxos.md), D-01 e OP-07. |
| 00:45–01:45 / 60 s | Operação e segurança | Mostrar login, submeter origem autorizada, identificar aceite 202/Location; mostrar que a outra conta não vê a análise. Evitar expor o token. | OP-01/05 e registro O-01; E-06/07 para interação simulada. |
| 01:45–02:45 / 60 s | Operação e segurança | Mostrar uma tarefa em andamento e um resultado preparado: fila, worker, arquivos processados e SHA. Etapas rápidas podem não aparecer em todas as consultas. | OP-01–04, JSON O-01 e fluxo documentado; não inventar captura de etapa perdida. |
| 02:45–04:15 / 90 s | Engine e qualidade | Mostrar AST/localização e três violações no fixture oficial; explicar CWE e prova do marcador não criado. Abrir trace HTTP separado e métricas restritas ao corpus. | E-01/02/03, OP-07, TAINT-01–05 e resultado Maven E-01. |
| 04:15–05:15 / 60 s | Engine e qualidade | Distinguir finding, avaliação e sugestão. Mostrar análise com IA DEGRADED e findings preservados; informar que notas de qualidade da remediação estão pendentes. | OP-10, IA-01–04; relato live histórico sem tratá-lo como nova execução. |
| 05:15–06:15 / 60 s | Interface e CI | Mostrar tendência, referência/SHA, severidades, arquivos e histórico. Explicar resumo unificado versus tendência determinística e cobertura parcial. | E-04/05 em Vitest, explicitamente simulado se não houver histórico real preparado. |
| 06:15–07:15 / 60 s | Interface e CI | Mostrar workflow e dois resultados da política local: Critical novo bloqueia; Critical histórico/High permite. Informar que PR real e proteção da branch ainda não foram comprovados. | E-08–12, política versionada e seis testes Python; não chamar PASS local de PR aprovado. |
| 07:15–08:00 / 45 s | Arquitetura e escopo | Encerrar com limites intraprocedurais, amostra pequena, operação em uma máquina, retenção e validações externas pendentes. | Matriz RF/RNF, limitações Q-01/O-01 e decisões D-01. |

A apresentação não depende de concluir um novo download ou uma chamada live de IA durante o cronômetro. Se a demonstração falhar, registrar a falha e usar a evidência alternativa datada; não apresentá-la como resultado da sessão atual.

## Reprodução dos testes

```bash
MAVEN_OPTS="-Djdk.attach.allowAttachSelf=true" mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
python3 -m unittest discover -s scripts -p 'test_security_gate.py' -v
docker compose config --quiet
```

Instalar as dependências do frontend com `npm --prefix src/frontend ci` quando necessário. O ajuste de Maven permite o agente Mockito no JDK deste ambiente. O verify executa apenas o produto e seus fixtures; Testcontainers cria dependências de teste. Os cenários opcionais de Ollama não são habilitados por padrão. Reprodução live e falhas provocadas têm procedimentos próprios em Q-01/O-01.

## Ata de demonstração — preenchimento posterior

Vinculada aos cenários manuais BDD-E-M01–M03. Esta tabela é um modelo; não constitui evidência de execução.

| Data e responsável | Bloco / cenário | Revisão e origem autorizada | Resultado efetivo | Evidência sanitizada / pendência |
|---|---|---|---|---|
| A preencher | M01: demonstração visual | A preencher | Não executado nesta etapa | Capturas revisadas ou descrição objetiva de estados/contagens. |
| A preencher | M02: PR seguro e bloqueado | URLs de PR, SHA e checks | Pendente | Configuração de proteção e resultado real, sem secrets. |
| A preencher | M03: aceite da orientação | Decisões D-01 | Pendente | Ata, mensagem ou rubrica validada. |

Não anexar fonte integral de terceiros, archives, credenciais ou dados pessoais das contas de teste. O [registro E-01](evidencias/E-01-2026-10-07.md) contém a verificação local efetivamente executada, separada desta ata.
