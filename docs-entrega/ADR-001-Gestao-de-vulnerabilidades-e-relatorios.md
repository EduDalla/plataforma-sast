# ADR-001 — Gestão de vulnerabilidades e documentação das análises

- **Data:** 01/11/2026.
- **Situação:** Em implementação — eixos de status, tipo de vulnerabilidade e documentação iniciados em 01/11/2026.
- **Referência anterior:** [D-01 — decisões de escopo](Decisoes-de-escopo.md).

## Contexto

O FISAST já possui autenticação JWT Bearer, isolamento por usuário, análises assíncronas, findings por execução, avaliações e sugestões consultivas de IA, dashboard e histórico por repositório. O fluxo atual utiliza React, API Spring Boot, PostgreSQL/outbox, RabbitMQ e worker com JavaParser, regras, taint analysis e Ollama local. Os contratos e limites estão descritos no [manual técnico](Manual-tecnico-e-api.md), na [arquitetura](Arquitetura-e-fluxos.md) e em [REGRAS.MD](../REGRAS.MD).

Antes destes incrementos, findings representavam somente detecções em uma execução, sem identidade persistente, ciclo de remediação auditável ou exportações PDF/SARIF. Esta ADR registra a evolução incremental em andamento; sua existência não declara todos os critérios abaixo atendidos.

## 1. Implementação Status

### Implementação iniciada

O primeiro incremento funcional deste eixo está implementado no backend e no frontend:

- vulnerabilidades persistentes recebem código estável `VUL-000001`, independente do UUID do finding da execução;
- findings novos são associados por proprietário, repositório, regra, CWE, caminho, assinatura estrutural e hash da expressão normalizada;
- os estados e transições manuais são expostos por `GET /api/vulnerabilities`, `GET /api/vulnerabilities/{code}` e `PATCH /api/vulnerabilities/{code}/status`;
- cada alteração registra responsável, data, status anterior, status novo e observação, com controle de versão otimista;
- `Validada` não pode ser atribuída manualmente: uma análise completa posterior, na mesma referência, só valida quando o arquivo e a regra estiverem cobertos e não houver ocorrência correspondente;
- ocorrências após `Corrigida` ou `Validada` reabrem a vulnerabilidade; falso positivo e risco aceito permanecem até revisão explícita;
- a interface de resultados apresenta o código e o controle autenticado de status sem alterar a severidade determinística.

O incremento não implementa ainda comparação entre execuções, classificação CVE/CVSS nem PDF/SARIF. A situação abaixo continua sendo o contrato de aceitação do eixo completo; os itens restantes não são declarados concluídos por este incremento.

### Decisões

- Adotar os estados **Aberta**, **Em correção**, **Corrigida**, **Validada**, **Falso positivo** e **Risco aceito**. Novas vulnerabilidades começam abertas.
- Registrar alterações manuais em histórico de auditoria com responsável autenticado, data e horário, estado anterior, novo estado e observação. Falso positivo e risco aceito exigem justificativa.
- Tratar Corrigida como declaração de correção aguardando validação. Validada será atribuída por evidência de nova análise, e não por alteração manual direta.
- Validar automaticamente somente com análise posterior à marcação de correção, completa, da mesma referência, com o arquivo e a regra envolvidos efetivamente cobertos e sem ambiguidade de correspondência.
- Não validar por ausência em análise parcial, arquivo removido ou cobertura desconhecida. A degradação da IA deve ser distinguida da cobertura determinística necessária à validação.
- Reabrir uma vulnerabilidade redetectada após validação e registrar a recorrência. Redetecção após Corrigida também reabre o registro; falso positivo e risco aceito permanecem até revisão explícita.
- Detectar conflitos de atualização concorrente e verificar o proprietário em toda consulta ou alteração.

### Justificativa e consequências

A separação entre correção declarada e validada evita apresentar ausência de detecção como comprovação de remediação. A auditoria permite reconstruir decisões sem alterar os resultados originais das análises.

O incremento implementa a persistência do estado e da versão do registro, histórico de alterações, vínculo com ocorrências/referência, cobertura por arquivo e regra, contrato HTTP autenticado e controle na página de detalhes. Filtros e indicadores globais por estado ainda serão ampliados no eixo de identidade/classificação.

### Critérios de aceitação

- Testar estado inicial, transições, justificativas obrigatórias e auditoria com responsável e datas.
- Testar conflitos concorrentes e impedir leitura ou alteração por outro usuário.
- Testar validação na mesma referência e rejeitar validação por análise anterior à marcação, outra referência, análise incompleta, arquivo removido, regra não coberta ou correspondência ambígua.
- Testar reabertura e recorrência sem perder o histórico de validação.

## 2. Implementação tipo vulnerabilidade

### Decisões

- Distinguir vulnerabilidade persistente de finding por execução. Preservar os UUIDs e os dados originais das ocorrências.
- Criar identificador público estável no formato `VUL-000001`, com no mínimo seis dígitos, sem reutilização durante o ciclo de vida.
- Vincular análises a projetos identificados pelo usuário proprietário e repositório GitHub normalizado.
- Associar ocorrências por projeto, regra, CWE, caminho relativo normalizado e contexto estrutural: assinatura do método ou campo e hash da expressão AST normalizada. Números de linha não definirão sozinhos a identidade.
- Usar contexto estrutural adicional para distinguir expressões semelhantes; diante de ambiguidade, criar registros separados em vez de associá-los automaticamente.
- Suportar severidades **Crítica**, **Alta**, **Média**, **Baixa** e **Informativa**, mantendo classificação desconhecida distinta de Informativa.
- Registrar título, descrição, localização, impacto, evidências, recomendações, referências, confiança, primeira identificação e última detecção. Informações ausentes devem permanecer explicitamente indisponíveis.
- Usar CVE somente com associação fundamentada e referência; sem associação conhecida, apresentar “Não aplicável”. Usar CVSS somente com pontuação, versão, vetor e fonte ou metodologia documentada disponíveis; caso contrário, apresentar “Não calculado”.
- Explicitar a origem da confiança. Sem avaliação fundamentada, usar “Não verificada”; uma detecção estática não comprova exploração.
- Manter avaliações e sugestões da IA separadas dos findings determinísticos, sem permitir que alterem sua severidade ou sejam contadas como novas vulnerabilidades.
- Migrar dados antigos conservadoramente, sem sobrescrever resultados originais, baixar novamente repositórios ou inferir validações históricas. Sem AST histórica, usar somente dados existentes e registrar a origem e as limitações da associação. Registros sem proprietário não ganharão acesso público.

### Implementação iniciada

O primeiro incremento do tipo persistente adiciona a migração V14 e mantém a associação separada do finding original:

- projetos novos usam proprietário e repositório GitHub normalizados em minúsculas;
- fingerprints ambíguos recebem `identityOrdinal`, evitando mesclar duas expressões estruturalmente iguais no mesmo contexto;
- a vulnerabilidade persistente passa a reservar classificação, impacto, recomendação, referências, CVE/CVSS e confiança com origem explícita;
- `Informative` e `Unknown` são classificados separadamente, e o detalhe autenticado expõe as ocorrências imutáveis por execução;
- o contrato de findings também expõe o contexto estrutural necessário para rastreabilidade, sem alterar a severidade determinística.

Comparação entre execuções, filtros avançados e a tela própria de vulnerabilidades ainda não são declarados concluídos por este incremento.

### Justificativa e consequências

A identidade persistente permite acompanhar um problema entre execuções sem confundir vulnerabilidades em recursos diferentes. Uma associação conservadora pode manter registros separados, mas evita mesclar indevidamente históricos e decisões de remediação.

A implementação exigirá novas migrações Flyway após V11, sem reescrever migrações aplicadas, relacionamentos com integridade referencial, índices e restrições de unicidade. Associação e persistência deverão ser transacionais e idempotentes para concorrência e retentativas.

Os contratos HTTP precisarão expor identidades persistentes e de ocorrência, classificação e dados de rastreabilidade. Dashboard, listagem paginada com pesquisa/filtros/ordenação e detalhes deverão usar esses dados sem misturar sugestões consultivas nos indicadores de vulnerabilidades.

A comparação entre análises do mesmo projeto distinguirá **novas**, **persistentes**, **não detectadas**, **corrigidas e validadas** e **recorrentes**, indicando confiabilidade da associação e cobertura. Diferenças de severidade usarão as ocorrências originais; “não detectada” não será sinônimo de correção validada.

### Critérios de aceitação

- Testar unicidade e estabilidade do código, deslocamento de linhas e distinção entre regras, arquivos, métodos, projetos e usuários.
- Testar ambiguidades, prevenção de duplicações, concorrência e retentativas idempotentes.
- Testar migração sem perda de resultados e leitura de campos legados indisponíveis.
- Testar classificação desconhecida, ausência de CVE/CVSS e separação entre IA e findings.
- Testar comparação, recorrência, paginação e consistência dos indicadores no contexto selecionado.

## 3. Implementação Documentação sobre a análise

### Implementação iniciada

O primeiro incremento funcional de relatórios está implementado no backend:

- a migração V15 adiciona snapshots sanitizados, versionados e vinculados à análise e ao proprietário;
- análises encerradas geram automaticamente a versão 1, inclusive em falha final, sem persistir archive ou código-fonte integral;
- `GET /api/analyses/{id}/reports` lista versões, `POST` cria uma nova versão sem baixar ou reanalisar o repositório e `GET /{version}` consulta metadados;
- `GET /{version}/sarif` exporta SARIF 2.1.0 somente com findings determinísticos; `GET /{version}/pdf` exporta o relatório técnico paginado;
- sugestões e avaliações consultivas aparecem em seções próprias e não são convertidas em findings SARIF;
- snapshots e downloads são restritos ao proprietário autenticado, usam hash SHA-256, nome de arquivo seguro e `Cache-Control: no-store`;
- o frontend já possui clientes HTTP para listar, criar e baixar as duas exportações.

A geração automática é tolerante a falhas: erro de relatório é registrado sem alterar o estado da análise. Ainda faltam a validação do SARIF contra o schema oficial, a interface de histórico de relatórios no frontend e cenários de integração com PostgreSQL/isolamento entre usuários.

### Decisões

- Disponibilizar automaticamente um relatório consolidado ao encerrar cada análise, inclusive sem findings e em falha com resultados parciais. Explicitar o estado e os limites dos resultados obtidos.
- Documentar escopo, ferramentas, metodologia, período de execução, cobertura conhecida, limitações e verificações não executadas.
- Incluir capa, resumo executivo, indicadores, vulnerabilidades, evidências sanitizadas, recomendações, referências, plano de remediação e conclusão. Priorizar com base em severidade e confiança conhecidas, sem presumir exposição ou explorabilidade.
- Preservar snapshots estruturados, sanitizados, imutáveis e versionados no PostgreSQL. Mudanças posteriores de status poderão gerar novas versões sem substituir as anteriores.
- Permitir reexportação a partir de cada snapshot sem nova análise. Falhas de geração terão estado próprio, sem alterar findings ou transformar uma análise concluída em falha.
- Gerar PDF no backend com Apache PDFBox, fontes locais, capa, sumário, paginação, cabeçalhos, rodapés e quebras controladas de textos e tabelas. A dependência será adicionada apenas na implementação funcional.
- Exportar SARIF 2.1.0 com identificação da ferramenta, regras, mensagens, localizações relativas, classificações e fingerprints versionados. Mapear Crítica/Alta para `error`, Média/Baixa para `warning`, Informativa para `note` e desconhecida para `none`, preservando a severidade interna em propriedades adicionais.
- Não exportar sugestões da IA como findings determinísticos nem preencher campos com dados fictícios.
- Restringir consulta e download ao proprietário por JWT Bearer, sem armazenamento público. Downloads deverão usar nomes seguros e `Cache-Control: no-store`.
- Sanitizar credenciais e tokens antes de novas persistências e nas leituras/exportações legadas. Exibir conteúdo como texto, sem executar HTML ou buscar recursos externos.
- Não afirmar segurança total por ausência de findings nem inventar CVEs, pontuações ou evidências.

### Justificativa e consequências

Relatórios versionados preservam a rastreabilidade entre o resultado original e o acompanhamento posterior. PDF atende à leitura técnica; SARIF permite consumo estruturado por ferramentas de desenvolvimento.

A implementação exige contratos autenticados para listar versões, solicitar uma versão atualizada e exportar arquivos. O histórico oferece acesso às versões, cobertura e datas; duração histórica desconhecida permanece indisponível. A geração reutiliza os componentes existentes, sem criar serviços ou filas adicionais. A tabela `analysis_report_snapshots` guarda o payload sanitizado e seu hash; não guarda prompts, respostas brutas da IA, archive ou código-fonte integral.

O manual técnico deverá documentar rotas, formatos, estados de geração, dependências e limitações. A documentação de entrega só passará a declarar exportações implementadas quando houver código e validação correspondentes.

### Critérios de aceitação

- Testar relatórios sem vulnerabilidades, parciais, falhos, com evidências ausentes e extensos.
- Testar imutabilidade, novas versões após mudanças de status e reexportação sem nova análise.
- Testar autorização em consulta, criação de versão e download, além de sanitização de dados sensíveis.
- Validar SARIF contra o schema oficial e conferir o mapeamento de severidades e as localizações.
- Conferir conteúdo do PDF por extração de texto e inspeção visual das páginas renderizadas, incluindo paginação, sumário, tabelas e evidências longas.

## Dependências, limites e sequência proposta

Identidade e ocorrências do eixo 2 sustentam o ciclo de status do eixo 1, a comparação e os relatórios do eixo 3. A sequência proposta é modelagem/migração e identidade; cobertura e ciclo de vida; contratos e interface; comparação; relatórios/exportações; validação e documentação.

Permanecem os limites anteriores: somente arquivos Java de repositórios públicos do GitHub, nunca executar fontes recebidas, não persistir archive ou código integral, IA local consultiva e arquitetura atual. Frontend continua restrito aos contratos HTTP; parser, regras e taint permanecem independentes de HTTP e JPA. Alterações futuras nos contratos exigem atualização coordenada de API, frontend, testes e documentação.

Esta ADR propõe evolução do contrato de relatórios registrado em D-01, preservando esse registro histórico. O eixo de status possui um primeiro incremento funcional, mas a ADR completa não constitui comprovação de implementação integral nem de aprovação acadêmica externa.

## Verificação

Para esta alteração documental, conferir links locais, terminologia, serviços, estados e rotas mencionados contra o código atual, além de revisar o diff para preservar alterações existentes do usuário.

Na implementação funcional, executar na raiz as verificações de [AGENTS.md](../AGENTS.md):

```bash
mvn --file src/backend/pom.xml verify
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
docker compose config --quiet
```

Usar `--quiet` para validar o Compose sem expor valores expandidos do ambiente. Confirmar que o fixture Java vulnerável em memória continua produzindo exatamente três findings, sem executar o código analisado.
