# Extensão autorizada — autenticação e frontend

A CP1 foi ampliada por solicitação explícita para incluir login, persistência de usuários e isolamento de análises. O mockup orienta login, nova análise, processamento, resultados e detalhes. A entrada continua sendo URL pública do GitHub e referência opcional, exclusivamente para Java. Dashboard, histórico, relatórios, configurações, cadastro e recuperação de senha não fazem parte desta extensão.

## Acesso e implantação

A migration V2 cria `app_users` (UUID, e-mail único normalizado, hash BCrypt) e `analyses.user_id` com FK e índice. Registros legados preservam proprietário nulo e não são acessíveis pelos endpoints. Toda nova análise recebe o ID do usuário autenticado pelo JWT.

Se o banco estiver vazio, a API exige `SAST_BOOTSTRAP_EMAIL` válido e `SAST_BOOTSTRAP_PASSWORD` com 12 caracteres a 72 bytes UTF-8. O bootstrap só atua quando não existem usuários e nunca altera senhas existentes. Credenciais não devem ser versionadas, devolvidas pela API ou escritas em logs. Remova as variáveis bootstrap após a primeira inicialização.

A autenticação usa JWT Bearer assinado com HMAC-SHA256. `SAST_JWT_SECRET` deve ser Base64 com pelo menos 256 bits, fica somente no backend e nunca é registrado. O token contém emissor, assunto, escopos, emissão e expiração de 15 minutos. O frontend mantém o access token somente em memória; não usa localStorage, sessionStorage ou cookies para o token. Reiniciar a API invalida tokens emitidos com o segredo anterior. Logout limpa o token local; a expiração curta limita a janela de reutilização.

## Contratos

| Método e rota | Entrada | Resultado |
|---|---|---|
| POST /api/auth/login | JSON `{email, password}` | 200 com `{email, accessToken, tokenType, expiresIn}`; 401 genérico para credencial inválida |
| GET /api/auth/session | Header `Authorization: Bearer ...` | 200 com `{email}` ou 401 |
| POST /api/auth/logout | Nenhuma (logout local) | 204 |
| POST /api/analyses | Header Bearer e JSON `{repositoryUrl, reference?}` | 201, Location e DTO da análise |
| GET /api/analyses/{id} | Header Bearer | 200 com o mesmo DTO; 404 para ID inexistente, legado ou de outro usuário |
| GET /health | Nenhuma | Saúde da aplicação, sem detalhes internos |

O frontend envia o Bearer em cada requisição protegida. A API não redireciona para páginas de login e usa erros JSON legíveis. Requisições sem token, com assinatura inválida, emissor incorreto ou token expirado recebem 401.

O DTO contém apenas `analysisId, status, repositoryUrl, reference, language, filesAnalyzed, createdAt, findings`. Cada finding mantém regra, título, severidade, CWE, descrição, caminho, linha, coluna e trecho. O timestamp usa precisão de microssegundos, compatível com PostgreSQL. Findings são ordenados por arquivo, linha, coluna e regra.

## Dados temporários e segurança

O archive e arquivos Java são transitórios em memória. Os streams são fechados com try-with-resources; nenhum snapshot é gravado em disco. Downloads e extração são limitados durante a leitura. Os trechos persistidos se restringem à primeira linha do nó AST detectado, limitados a 500 caracteres, evitando salvar arquivos inteiros escritos em uma linha.

A validação exige HTTPS, host github.com exato e owner/repositório/referência válidos. A consulta de metadados é anônima para confirmar acesso público mesmo quando há token configurado. Somente o archive oficial é baixado, com redirecionamento HTTPS para codeload.github.com. Links simbólicos, diretórios de dependências, Zip Slip e limites de tamanho são tratados antes da análise.

## Frontend e testes

Rotas: `/login`, `/analyses/new`, `/analyses/:analysisId`. Recarregar uma URL restaura sessão e busca o resultado salvo. Os detalhes usam disclosure acessível por teclado. Processamento síncrono mostra indicador e bloqueia novos envios. Sem achados é sucesso explícito. Sessão expirada apaga o resultado em memória e retorna ao login. Não há cancelamento de jobs porque não existem jobs assíncronos.

Vitest cobre login, erro, token em memória, logout, restauração do resultado, carregamento, duplicação, cards, detalhes e sucesso vazio. SpringBootTest com PostgreSQL/Testcontainers valida migration, hash, assinatura, expiração, POST 201/Location, GET equivalente, isolamento, registros legados e erros HTTP. O engine lê a amostra oficial e exige exatamente três findings sem executá-la.

Referência técnica: [Spring Security — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
