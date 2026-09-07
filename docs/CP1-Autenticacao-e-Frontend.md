# Extensão autorizada — autenticação e frontend

A CP1 foi ampliada por solicitação explícita para incluir login, persistência de usuários e isolamento de análises. O mockup orienta login, nova análise, processamento, resultados e detalhes. A entrada continua sendo URL pública do GitHub e referência opcional, exclusivamente para Java. Dashboard, histórico, relatórios, configurações, cadastro e recuperação de senha não fazem parte desta extensão.

## Acesso e implantação

A migration V2 cria `app_users` (UUID, e-mail único normalizado, hash BCrypt) e `analyses.user_id` com FK e índice. Registros legados preservam proprietário nulo e não são acessíveis pelos endpoints. Toda nova análise recebe o ID do usuário da sessão.

Se o banco estiver vazio, a API exige `SAST_BOOTSTRAP_EMAIL` válido e `SAST_BOOTSTRAP_PASSWORD` com 12 caracteres a 72 bytes UTF-8. O bootstrap só atua quando não existem usuários e nunca altera senhas existentes. Credenciais não devem ser versionadas, devolvidas pela API ou escritas em logs. Remova as variáveis bootstrap após a primeira inicialização.

A autenticação usa sessão Spring Security com cookie JSESSIONID HttpOnly, SameSite=Lax, 30 minutos de inatividade, rotação do ID após login e invalidação após logout. Em HTTPS, configure `SAST_COOKIE_SECURE=true`; o padrão false permite a demonstração HTTP local. Reiniciar a API encerra as sessões em memória. O frontend guarda apenas a identidade em memória e usa cookies da mesma origem, sem tokens no localStorage.

## Contratos

| Método e rota | Entrada | Resultado |
|---|---|---|
| GET /api/auth/csrf | Nenhuma | 200 com `{headerName, token}`; cria token de sessão |
| POST /api/auth/login | JSON `{email, password}` e header CSRF | 200 com `{email}`; 401 genérico para credencial inválida |
| GET /api/auth/session | Cookie de sessão | 200 com `{email}` ou 401 |
| POST /api/auth/logout | Cookie e header CSRF | 204 e sessão invalidada |
| POST /api/analyses | Cookie, header CSRF, JSON `{repositoryUrl, reference?}` | 201, Location e DTO da análise |
| GET /api/analyses/{id} | Cookie de sessão | 200 com o mesmo DTO; 404 para ID inexistente, legado ou de outro usuário |
| GET /health | Nenhuma | Saúde da aplicação, sem detalhes internos |

O frontend busca CSRF antes de cada POST, incluindo login/logout; tokens antigos deixam de valer depois do login. Requisições sem CSRF válido recebem 403. A API não redireciona para páginas de login e usa erros JSON legíveis.

O DTO contém apenas `analysisId, status, repositoryUrl, reference, language, filesAnalyzed, createdAt, findings`. Cada finding mantém regra, título, severidade, CWE, descrição, caminho, linha, coluna e trecho. O timestamp usa precisão de microssegundos, compatível com PostgreSQL. Findings são ordenados por arquivo, linha, coluna e regra.

## Dados temporários e segurança

O archive e arquivos Java são transitórios em memória. Os streams são fechados com try-with-resources; nenhum snapshot é gravado em disco. Downloads e extração são limitados durante a leitura. Os trechos persistidos se restringem à primeira linha do nó AST detectado, limitados a 500 caracteres, evitando salvar arquivos inteiros escritos em uma linha.

A validação exige HTTPS, host github.com exato e owner/repositório/referência válidos. A consulta de metadados é anônima para confirmar acesso público mesmo quando há token configurado. Somente o archive oficial é baixado, com redirecionamento HTTPS para codeload.github.com. Links simbólicos, diretórios de dependências, Zip Slip e limites de tamanho são tratados antes da análise.

## Frontend e testes

Rotas: `/login`, `/analyses/new`, `/analyses/:analysisId`. Recarregar uma URL restaura sessão e busca o resultado salvo. Os detalhes usam disclosure acessível por teclado. Processamento síncrono mostra indicador e bloqueia novos envios. Sem achados é sucesso explícito. Sessão expirada apaga o resultado em memória e retorna ao login. Não há cancelamento de jobs porque não existem jobs assíncronos.

Vitest cobre login, erro, sessão, logout, restauração do resultado, carregamento, duplicação, cards, detalhes e sucesso vazio. SpringBootTest com PostgreSQL/Testcontainers valida migration, hash, sessão, rotação, CSRF, POST 201/Location, GET equivalente, isolamento, registros legados e erros HTTP. O engine lê a amostra oficial e exige exatamente três findings sem executá-la.

Referência usada para a sessão e a renovação CSRF: [Spring Security — CSRF](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html).
