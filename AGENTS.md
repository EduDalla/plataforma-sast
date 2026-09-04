# Instruções para agentes Codex

Leia este arquivo antes de alterar o monorepo. Estas regras complementam a solicitação atual e a documentação em `docs/`.

## Antes de prosseguir

1. Leia este arquivo e o guia da CP1 em `docs/`.
2. Inspecione a árvore do monorepo e o estado do Git antes de editar.
3. Confirme se a tarefa pertence à CP1; não antecipe funcionalidades de CP2/CP3.
4. Se a tarefa exigir repositório privado, execução de código de terceiros, armazenamento do código integral ou uma alteração de arquitetura, pare e solicite confirmação explícita.
5. Defina os testes que cobrem a mudança antes de considerá-la concluída.

## Escopo da CP1

- A CP1 analisa exclusivamente arquivos JavaScript `.js` de repositórios públicos do GitHub.
- O fluxo é síncrono: URL/referência → archive → filtro de arquivos → parser/AST → Rules Engine → PostgreSQL → frontend.
- As regras iniciais são senha hardcoded (CWE-798), `eval()` (CWE-95) e `innerHTML` (CWE-79).
- IA, Taint Analysis, CI/CD, Security Gates, autenticação, dashboard, histórico e relatórios estão fora da CP1.

## Segurança obrigatória

- Nunca executar código baixado do GitHub.
- Nunca usar `git clone`, `npm install`, `npm run`, builds, testes, shell, Jint ou qualquer runtime sobre o repositório analisado.
- Aceitar somente URLs HTTPS com host exato `github.com`; validar owner, repository e referência antes de montar a requisição.
- Baixar somente o archive oficial pela GitHub REST API, validar redirecionamento para `codeload.github.com` e impor os limites configurados.
- Impedir Zip Slip, links simbólicos, archives excessivos e caminhos fora do diretório temporário.
- Ignorar `node_modules`, `dist`, `build`, `coverage`, `vendor` e `*.min.js`.
- Remover o snapshot temporário em `finally`.
- Nunca enviar token do GitHub ao frontend, persistir o token ou escrevê-lo em logs.
- Não persistir o archive nem o código-fonte integral; salvar apenas metadados, findings e trechos necessários.

## Limites arquiteturais

- O frontend conhece somente os contratos HTTP; não acessa PostgreSQL, filesystem temporário ou token.
- A API valida a origem, baixa o snapshot, orquestra a análise e persiste o resultado.
- `Sast.Engine` deve permanecer independente de HTTP, GitHub e EF Core.
- Cada regra implementa `ISecurityRule` e pode ser registrada sem alterar o engine.
- Toda alteração de contrato deve atualizar API, frontend, testes e a documentação da CP1.

## Verificação antes de concluir

Execute na raiz do monorepo:

```bash
dotnet build Sast.sln
dotnet test Sast.sln
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
docker compose config
```

Confirme também que o exemplo em `samples/vulnerable.js` produz exatamente três findings e que nenhum código é executado durante o teste.

## Higiene do repositório

- Preserve o DOCX original e arquivos existentes do usuário.
- Não adicione segredos, tokens, dumps, archives ou `node_modules` ao Git.
- Mantenha comandos executáveis a partir da raiz do monorepo.
- Use nomes e comentários em português quando isso melhorar a compreensão acadêmica do projeto.
- Registre decisões arquiteturais relevantes em `docs/`.
