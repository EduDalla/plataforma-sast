# Regras gerais do sistema

Este documento reúne os fluxos, processos e limites arquiteturais da plataforma SAST conforme o comportamento implementado. Para instruções de trabalho de agentes, consulte [AGENTS.md](AGENTS.md); para detalhes operacionais, consulte [README.md](README.md) e `docs-entrega/`.

## Arquitetura e responsabilidades

- O frontend React apresenta autenticação, criação de análise, andamento, central de sistemas, histórico e resultados. Sua integração com o backend ocorre por HTTP, por meio da API e do proxy Nginx.
- A API Spring Boot valida entradas e autenticação, cria tarefas, expõe resultados e coordena a persistência. O PostgreSQL guarda usuários, análises, progresso, eventos de outbox e resultados.
- O publicador lê a outbox e envia somente o UUID da análise ao RabbitMQ. O worker separado consome a mensagem, assume a tarefa no PostgreSQL e executa a análise. A posse da tarefa e a reconciliação de concessões permitem retomar tentativas sem depender de uma mensagem única.
- O worker usa a GitHub REST API para obter um snapshot público, JavaParser para construir ASTs, `SecurityRule` para os achados determinísticos e Ollama local para avaliações e sugestões consultivas.
- Frontend, API, worker, RabbitMQ, PostgreSQL e Ollama são serviços distintos no Compose. O frontend não se conecta diretamente ao banco, à fila, ao GitHub com token ou ao Ollama.

## Acesso e isolamento

- Cadastro e login são rotas públicas; rotas de análise, tarefas, sistemas, histórico e sessão exigem JWT Bearer válido. O bootstrap por ambiente cria um usuário inicial quando necessário e não substitui contas existentes; um lock transacional PostgreSQL serializa a inicialização concorrente da API e do worker.
- Senhas são armazenadas apenas como hash BCrypt. O access token fica na `sessionStorage` da aba do frontend, é enviado no cabeçalho `Authorization` e é descartado no logout ou quando a API responde `401`.
- Toda leitura ou alteração de uma análise deve considerar seu usuário proprietário, inclusive a consulta de tarefas e sua confirmação. Um identificador conhecido não autoriza acesso aos dados de outra pessoa.

## Envio e processamento de análises

1. A pessoa informa a URL HTTPS de um repositório público em `github.com` e, opcionalmente, uma referência. A API valida host, proprietário, repositório e referência antes de criar a tarefa.
2. `POST /api/analyses` grava a análise em `PROCESSING`/`QUEUED` e o evento de outbox na mesma transação. A resposta é `202 Accepted` com o endereço do resultado em `Location`.
3. O publicador envia o UUID ao RabbitMQ e aguarda a confirmação da publicação. O worker faz o claim da tarefa no PostgreSQL; mensagens duplicadas não devem iniciar uma segunda execução da mesma tentativa.
4. O worker fixa o SHA do commit, baixa o archive oficial via GitHub REST API e aceita somente o redirecionamento HTTPS para `codeload.github.com`. O snapshot é filtrado para arquivos `.java` elegíveis e permanece em memória durante a tentativa.
5. Cada arquivo é transformado em AST. As regras estruturais procuram credencial hardcoded, `Runtime.exec()` e `ObjectInputStream.readObject()`; o taint analysis acompanha um fluxo intraprocedural de entrada até execução de comando. Findings determinísticos são persistidos conforme os arquivos são processados.
6. O Ollama local avalia findings e pode gerar sugestões independentes de segurança ou desempenho para métodos selecionados. A conclusão ou degradação dessas etapas é registrada sem alterar os findings determinísticos.
7. O frontend consulta periodicamente `GET /api/analyses/{id}` para mostrar etapas, contadores, resultados parciais e estado final. A API também oferece tarefas, central de sistemas e histórico do repositório, sempre restritos ao usuário autenticado.

## Resultados, estados e falhas

- Findings trazem regra, CWE, severidade, localização e trecho necessário; alguns incluem `taintTrace` e `aiAssessment`. A avaliação da IA fica dentro do finding e não substitui sua severidade original.
- Sugestões de IA são itens próprios, com origem de segurança ou desempenho e prioridade própria. O resumo unificado conta findings e sugestões persistidos; uma avaliação aninhada não conta como item adicional.
- O andamento pode passar por `QUEUED`, `DOWNLOADING`, `DETERMINISTIC`, `SEMANTIC`, `SUGGESTIONS` e `COMPLETED`. Falhas finais levam a `FAILED`. Estados consultivos como `DEGRADED` indicam avaliação ou cobertura parcial; zero itens nessa condição não comprova ausência de problemas.
- Falhas transitórias de publicação ou processamento podem ser retomadas pela outbox, pela reconciliação da tarefa e pelas tentativas previstas no backend. Uma falha de Ollama não deve apagar achados determinísticos já obtidos. Mensagens ao usuário devem ser seguras e não revelar segredos nem código-fonte.
- A central de sistemas e o histórico usam execuções do próprio usuário. Sistemas aparecem assim que a primeira análise é aceita, inclusive durante processamento ou após falha; o dashboard e a página de resultados devem preservar a origem e a prioridade de cada item e distinguir conclusão sem achados, processamento, cobertura parcial e falha. Os totais globais de resultados consideram somente execuções concluídas e distintas.

## Dados e segurança da origem

- O sistema analisa apenas arquivos Java de repositórios públicos do GitHub. Código recebido é dado não confiável: nunca fazer `git clone`, instalar dependências, compilar, executar scripts, testes, Maven, Gradle, JVM ou qualquer runtime do repositório analisado.
- Impor limites configurados ao archive e à extração; rejeitar caminhos perigosos, ignorar links simbólicos e diretórios de build ou dependências. Fechar streams e não deixar cópias do snapshot em disco.
- Não persistir archive nem código-fonte integral. Persistir somente metadados, hashes, findings, traces, avaliações, sugestões e trechos necessários à apresentação. Tokens, senhas, código, prompts e respostas brutas não devem aparecer em logs.
- A integração com Ollama é local e consultiva. O contexto enviado ao modelo deve permanecer limitado ao necessário; sua resposta precisa ser validada antes de persistir ou exibir sugestões.

## Evolução e verificação

- O código Java da plataforma é verificado pelo [teste de qualidade do backend](src/backend/src/test/java/com/fiap/sast/quality/BackendQualityTest.java), que usa uma baseline versionada. JavaDoc de funções principais descreve o objetivo e os parâmetros. O gate de estilo, documentação e padrões de desempenho integra o `mvn verify`.
- O worker pode reutilizar ASTs apenas durante a tentativa em memória. Cada chamada cria uma instância própria de JavaParser para evitar interferência entre consumidores concorrentes. A API mantém por até 15 minutos somente resultados concluídos em cache local limitado e isolado por usuário; central, histórico e tarefas consultam o banco para refletir novas conclusões imediatamente.
- Alterações nos contratos HTTP exigem atualização coordenada da API, do frontend, dos testes e da documentação. Mudanças no banco devem ser feitas por novas migrações Flyway.
- Novas regras determinísticas implementam `SecurityRule`; parser e engine não devem depender de transporte HTTP, GitHub ou JPA. A IA não deve alterar findings gerados por regras.
- Os testes devem cobrir autenticação e isolamento, validação da origem, fila e retomada, parser e regras, estados degradados, persistência e apresentação dos resultados. O fixture Java vulnerável em memória deve continuar produzindo exatamente três findings, sem executar o código analisado.
- Antes de concluir alterações, executar na raiz os comandos de verificação de `AGENTS.md` e conferir o resultado contra o comportamento atual da aplicação.
- Os ensaios de operação e suas evidências BDD estão em [Operação e escalabilidade](docs-entrega/Operacao-e-escalabilidade.md). Falhas provocadas e limpeza automática devem atingir somente o projeto Compose temporário criado para o ensaio.
- A [rastreabilidade BDD](docs-entrega/Rastreabilidade-BDD.md) liga requisitos, cenários e testes existentes. A documentação deve distinguir asserções automatizadas, ensaios reais e validações externas pendentes; IDs devem permanecer coerentes com nomes de teste, sem criar testes duplicados por motivo documental.
