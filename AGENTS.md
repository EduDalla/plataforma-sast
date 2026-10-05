# Instruções para agentes Codex

Leia este arquivo antes de alterar o monorepo. Estas instruções complementam a solicitação atual, as [regras gerais do sistema](REGRAS.MD) e a documentação técnica em `docs/`.

## Antes de prosseguir

1. Leia este arquivo, `REGRAS.MD` e a documentação relacionada à tarefa.
2. Inspecione a árvore do monorepo, o comportamento implementado e o estado do Git antes de editar.
3. Confirme o impacto da mudança nos fluxos de autenticação, análise, resultados e operação; não trate documentação de entregas anteriores como descrição automática do estado atual.
4. Se a tarefa exigir repositório privado, execução de código de terceiros, armazenamento do código integral ou uma alteração de arquitetura, solicite confirmação explícita antes dessa parte do trabalho.
5. Defina os testes que cobrem a mudança antes de considerá-la concluída.

## Escopo do sistema

- A plataforma recebe URLs de repositórios públicos do GitHub e analisa exclusivamente arquivos Java `.java`, sem executar o código recebido.
- O fluxo é assíncrono: frontend → API e validação → PostgreSQL e outbox → RabbitMQ → worker → download do snapshot → parser/AST e regras → avaliação consultiva local → PostgreSQL → API e frontend por consulta periódica.
- O engine executa regras para credencial hardcoded (CWE-798), `Runtime.exec()` (CWE-78), `ObjectInputStream.readObject()` (CWE-502) e taint analysis intraprocedural para injeção de comando.
- A avaliação por IA local pode enriquecer findings e produzir sugestões independentes de segurança ou desempenho. Ela não cria, remove nem altera findings ou a severidade definida pelas regras.
- O sistema possui cadastro e login, bootstrap de usuário por ambiente, JWT Bearer, análises isoladas por usuário, central de sistemas, histórico e visualização de resultados.

## Segurança obrigatória

- Nunca executar código baixado do GitHub.
- Não usar `git clone`, compilação, testes, shell, Maven, Gradle, JVM ou qualquer runtime sobre o repositório analisado.
- Aceitar somente URLs HTTPS com host exato `github.com`; validar proprietário, repositório e referência antes de montar requisições. Não ampliar o acesso para repositórios privados.
- Baixar somente o archive oficial pela GitHub REST API, validar o redirecionamento para `codeload.github.com` e impor os limites configurados de archive, entradas, arquivos Java, tamanho por arquivo e conteúdo extraído.
- Rejeitar caminhos perigosos e impedir Zip Slip; ignorar links simbólicos e os diretórios `target`, `build`, `out`, `.gradle`, `node_modules` e `vendor`.
- Manter o snapshot e o código-fonte analisado somente em memória durante a tentativa do worker. Fechar streams com try-with-resources; se arquivos temporários forem introduzidos, removê-los em `finally`.
- Nunca enviar tokens do GitHub ou segredos ao frontend, persistir esses valores ou registrá-los em logs. Não registrar código-fonte, snippets, prompts, respostas brutas da IA ou corpos de requisição.
- Não persistir o archive nem o código-fonte integral; salvar apenas metadados, hashes, findings, traces, avaliações, sugestões e trechos necessários ao resultado.

## Limites arquiteturais

- O frontend usa somente contratos HTTP. Não acessa PostgreSQL, RabbitMQ, arquivos do worker, token do GitHub ou Ollama diretamente.
- A API autentica as rotas de análise com JWT Bearer, valida entradas, cria a análise e o evento de outbox na mesma transação e expõe tarefas e resultados. Toda consulta ou confirmação de análise deve filtrar também o usuário proprietário.
- O publicador envia apenas o identificador da análise ao RabbitMQ. O worker assume a tarefa no PostgreSQL, baixa o snapshot e executa a análise; retomadas devem preservar o isolamento e evitar execução duplicada.
- Parser, regras e taint analysis permanecem independentes de HTTP, GitHub e JPA.
- Cada regra implementa `SecurityRule` e pode ser registrada sem alterar o engine.
- A IA local é consultiva e pode ficar degradada sem apagar findings determinísticos. Não apresentar resultado parcial ou cobertura degradada como ausência confirmada de problemas.
- Senhas ficam somente como hash BCrypt no banco; credenciais bootstrap não entram em logs nem sobrescrevem usuários existentes. O frontend mantém o access token apenas na sessão da aba e o descarta no logout ou após resposta não autorizada.
- Toda alteração de contrato deve atualizar API, frontend, testes e documentação pertinente. Mudanças persistentes usam migrações Flyway, sem reescrever migrações já aplicadas.

## Verificação antes de concluir

Execute na raiz do monorepo:

```bash
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
docker compose config
```

Confirme também que o fixture Java vulnerável mantido em memória produz exatamente três findings e que nenhum código analisado é executado durante o teste. Para mudanças de documentação, confira links, nomes de serviços, rotas e estados contra o código atual.

## Higiene do repositório

- Preserve o DOCX original e arquivos existentes do usuário.
- Não adicione segredos, tokens, dumps, archives ou `node_modules` ao Git; não exponha valores do `.env` em saídas de comandos.
- Mantenha comandos executáveis a partir da raiz do monorepo.
- Use nomes e comentários em português quando isso melhorar a compreensão acadêmica do projeto.
- Registre decisões arquiteturais relevantes em `docs/` e mantenha `REGRAS.MD` coerente com o comportamento implementado.
