# Instruções para agentes Codex

Leia este arquivo antes de alterar o monorepo. Estas regras complementam a solicitação atual e a documentação em `docs/`.

## Antes de prosseguir

1. Leia este arquivo e o guia da CP1 em `docs/`.
2. Inspecione a árvore do monorepo e o estado do Git antes de editar.
3. Confirme se a tarefa pertence à CP1; não antecipe funcionalidades de CP2/CP3.
4. Se a tarefa exigir repositório privado, execução de código de terceiros, armazenamento do código integral ou uma alteração de arquitetura, pare e solicite confirmação explícita.
5. Defina os testes que cobrem a mudança antes de considerá-la concluída.

## Escopo da CP1

- A CP1 analisa exclusivamente arquivos Java `.java` de repositórios públicos do GitHub.
- O fluxo é síncrono: URL/referência → archive → filtro de arquivos → parser/AST → Rules Engine → PostgreSQL → frontend.
- As regras iniciais são credencial hardcoded (CWE-798), `Runtime.exec()` (CWE-78) e `ObjectInputStream.readObject()` (CWE-502).
- A extensão autorizada da CP1 inclui autenticação com usuários no PostgreSQL, bootstrap por ambiente, sessão com CSRF e análises isoladas por usuário.
- IA, Taint Analysis, CI/CD, Security Gates, dashboard, histórico e relatórios estão fora da CP1.

## Segurança obrigatória

- Nunca executar código baixado do GitHub.
- Nunca usar `git clone`, compilação, testes, shell, Maven, Gradle, JVM ou qualquer runtime sobre o repositório analisado.
- Aceitar somente URLs HTTPS com host exato `github.com`; validar owner, repository e referência antes de montar a requisição.
- Baixar somente o archive oficial pela GitHub REST API, validar redirecionamento para `codeload.github.com` e impor os limites configurados.
- Impedir Zip Slip, links simbólicos, archives excessivos e caminhos fora do diretório temporário.
- Ignorar `target`, `build`, `out`, `.gradle`, `node_modules` e `vendor`.
- O snapshot permanece somente em memória durante a requisição. Fechar streams com try-with-resources; se forem introduzidos arquivos temporários, removê-los em `finally`.
- Nunca enviar token do GitHub ao frontend, persistir o token ou escrevê-lo em logs.
- Não persistir o archive nem o código-fonte integral; salvar apenas metadados, findings e trechos necessários.

## Limites arquiteturais

- O frontend conhece somente os contratos HTTP; não acessa PostgreSQL, filesystem temporário ou token.
- Rotas de análise exigem sessão autenticada. Nunca consultar uma análise sem filtrar também seu proprietário.
- Senhas ficam somente como hash BCrypt no banco; credenciais bootstrap nunca devem ser registradas ou sobrescrever usuários existentes.
- A API valida a origem, baixa o snapshot, orquestra a análise e persiste o resultado.
- Parser e regras devem permanecer independentes de HTTP, GitHub e JPA.
- Cada regra implementa `SecurityRule` e pode ser registrada sem alterar o engine.
- Toda alteração de contrato deve atualizar API, frontend, testes e a documentação da CP1.

## Verificação antes de concluir

Execute na raiz do monorepo:

```bash
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
docker compose config
```

Confirme também que o exemplo em `samples/VulnerableExample.java` produz exatamente três findings e que nenhum código é executado durante o teste.

## Higiene do repositório

- Preserve o DOCX original e arquivos existentes do usuário.
- Não adicione segredos, tokens, dumps, archives ou `node_modules` ao Git.
- Mantenha comandos executáveis a partir da raiz do monorepo.
- Use nomes e comentários em português quando isso melhorar a compreensão acadêmica do projeto.
- Registre decisões arquiteturais relevantes em `docs/`.
