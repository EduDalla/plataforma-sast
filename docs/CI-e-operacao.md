# CI do produto

O workflow [CI](../.github/workflows/ci.yml) executa em `pull_request` e em pushes para `main`. O job `Verificar produto` usa Ubuntu 24.04, JDK 21 Temurin e Node.js 22.14.0, com permissões somente de leitura do conteúdo.

## Verificações

Na mesma execução são feitos:

```bash
docker compose config --quiet
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend ci
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
```

O Maven usa um diretório temporário do runner para não persistir artefatos no checkout. O Docker disponível no runner é usado pelos testes de integração que dependem do Testcontainers. O workflow não baixa, compila, testa ou executa o repositório que eventualmente será analisado pela plataforma; o checkout é somente o código do próprio produto.

O resumo do job publica apenas versões, estados dos comandos e limites operacionais. Não devem ser adicionados ao workflow tokens, conteúdo de `.env`, archives, código-fonte, snippets, prompts ou respostas brutas da IA.

## Proteção da branch

O YAML cria o check, mas não protege a branch sozinho. No GitHub, a administração do repositório deve exigir o check `Verificar produto` para merge em `main`, bloquear force-push e exigir atualização da branch quando a política adotada determinar isso. Essa configuração administrativa deve ser registrada como evidência separada.

O Security Gate para findings do repositório analisado não faz parte deste C-01; ele é o C-02 e deve consumir somente resultados determinísticos por contrato HTTP, sem executar o código-alvo.
