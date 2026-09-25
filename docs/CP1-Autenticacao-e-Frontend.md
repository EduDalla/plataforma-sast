# Extensão autorizada — autenticação e frontend

A CP1 foi ampliada por solicitação explícita para incluir login, persistência de usuários, isolamento de análises e dashboard com histórico por sistema. O mockup orienta login, nova análise, processamento, resultados e detalhes. A entrada continua sendo URL pública do GitHub e referência opcional, exclusivamente para Java. Relatórios, configurações, cadastro e recuperação de senha não fazem parte desta extensão.

## Acesso e implantação

A migration V2 cria `app_users` (UUID, e-mail único normalizado, hash BCrypt) e `analyses.user_id` com FK e índice. Registros legados preservam proprietário nulo e não são acessíveis pelos endpoints. Toda nova análise recebe o ID do usuário autenticado pelo JWT.

Se o banco estiver vazio, a API exige `SAST_BOOTSTRAP_EMAIL` válido e `SAST_BOOTSTRAP_PASSWORD` com 12 caracteres a 72 bytes UTF-8. O bootstrap só atua quando não existem usuários e nunca altera senhas existentes. Credenciais não devem ser versionadas, devolvidas pela API ou escritas em logs. Remova as variáveis bootstrap após a primeira inicialização.

A autenticação usa JWT Bearer assinado com HMAC-SHA256. `SAST_JWT_SECRET` deve ser Base64 com pelo menos 256 bits, fica somente no backend e nunca é registrado. O token contém emissor, assunto, escopos, emissão e expiração de 15 minutos. O frontend mantém o access token na `sessionStorage` para sobreviver a um F5 na mesma aba; não usa `localStorage` nem cookies para o token. Fechar a aba, fazer logout ou receber 401 remove o token. Reiniciar a API invalida tokens emitidos com o segredo anterior; a expiração curta limita a janela de reutilização.

## Contratos

| Método e rota | Entrada | Resultado |
|---|---|---|
| POST /api/auth/login | JSON `{email, password}` | 200 com `{email, accessToken, tokenType, expiresIn}`; 401 genérico para credencial inválida |
| GET /api/auth/session | Header `Authorization: Bearer ...` | 200 com `{email}` ou 401 |
| POST /api/auth/logout | Nenhuma (logout local) | 204 |
| POST /api/analyses | Header Bearer e JSON `{repositoryUrl, reference?}` | 201 e Location para uma execução nova; 200 e o mesmo `analysisId` quando os findings da mesma referência não mudaram |
| GET /api/analyses/{id} | Header Bearer | 200 com o mesmo DTO; 404 para ID inexistente, legado ou de outro usuário |
| GET /health | Nenhuma | Saúde da aplicação, sem detalhes internos |

O frontend envia o Bearer em cada requisição protegida. A API não redireciona para páginas de login e usa erros JSON legíveis. Requisições sem token, com assinatura inválida, emissor incorreto ou token expirado recebem 401.

Na área autenticada, a barra superior oferece um seletor de tema claro/escuro. O modo claro usa uma base branco-esverdeada; a escolha do usuário é persistida somente como preferência visual na chave `sast-theme` do `localStorage`. O padrão é o modo claro e a tela de login não participa dessa preferência.

O DTO contém apenas `analysisId, status, repositoryUrl, reference, language, filesAnalyzed, createdAt, findings`. Cada finding mantém regra, título, severidade, CWE, descrição, caminho, linha, coluna e trecho. O timestamp usa precisão de microssegundos, compatível com PostgreSQL. Findings são ordenados por arquivo, linha, coluna e regra.

## Dados temporários e segurança

O archive e arquivos Java são transitórios em memória. Os streams são fechados com try-with-resources; nenhum snapshot é gravado em disco. Downloads e extração são limitados durante a leitura. Os trechos persistidos se restringem à primeira linha do nó AST detectado, limitados a 500 caracteres, evitando salvar arquivos inteiros escritos em uma linha.

A validação exige HTTPS, host github.com exato e owner/repositório/referência válidos. A consulta de metadados é anônima para confirmar acesso público mesmo quando há token configurado. Somente o archive oficial é baixado, com redirecionamento HTTPS para codeload.github.com. Links simbólicos, diretórios de dependências, Zip Slip e limites de tamanho são tratados antes da análise.

## Frontend e testes

Rotas: `/login`, `/analyses/new`, `/analyses/:analysisId`, `/dashboard` e `/systems/:owner/:repository`. A API expõe `/api/analyses/systems` para listar sistemas agrupados por owner/repositório e `/api/analyses/systems/{owner}/{repository}/history` para o histórico paginado. Após autenticar ou recarregar, a pessoa usuária vai à central `/dashboard` quando possui sistemas analisados e à nova análise quando não possui. A central mostra somente cards; cada card abre o dashboard individual do sistema. Esse dashboard seleciona a análise mais recente por padrão e permite trocar a execução por data e referência Git, atualizando todos os indicadores e gráficos para a execução selecionada. O histórico é horizontal, com a execução recente à direita e rolagem para as anteriores. Ao repetir uma análise da mesma referência com findings idênticos, a API atualiza a data da execução existente, sem adicionar item ao histórico. O acesso aos achados completos permanece em `/analyses/:analysisId`. Processamento síncrono mostra indicador, bloqueia novos envios e abre um modal com dicas locais de segurança e curiosidades. A dica inicial é aleatória, troca automaticamente a cada 7 segundos e pode ser avançada manualmente; o modal é encerrado quando a API retorna sucesso ou erro. Sem achados é sucesso explícito. Sessão expirada apaga o resultado em memória e retorna ao login. Não há cancelamento de jobs porque não existem jobs assíncronos.

Vitest cobre login, erro, token em memória, logout, restauração do resultado, carregamento, duplicação, modal de dicas durante o processamento, rotação automática e manual, limpeza do temporizador, cards, detalhes e sucesso vazio. SpringBootTest com PostgreSQL/Testcontainers valida migration, hash, assinatura, expiração, POST 201/Location, GET equivalente, isolamento, registros legados e erros HTTP. O engine lê um fixture Java mantido em memória e exige exatamente três findings sem executá-lo.

Referência técnica: [Spring Security — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
