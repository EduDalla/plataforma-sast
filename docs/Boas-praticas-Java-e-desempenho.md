# Boas práticas Java e desempenho do backend

Estas convenções se aplicam ao **código da plataforma** em `src/backend`. Elas não acrescentam regras à análise dos repositórios recebidos.

## Código legível e JavaDoc

- Use UTF-8, LF, quatro espaços por nível e uma instrução por linha. A configuração compartilhada fica em `.editorconfig`.
- Separe responsabilidades de API, worker, persistência e engine. Prefira nomes que indiquem a operação e evite trabalho de banco ou rede dentro de laços quando houver consulta em lote.
- Documente funções públicas importantes e funções cujo ciclo de vida, contrato ou regra de segurança não sejam evidentes. O JavaDoc deve explicar **o objetivo da função** e conter um `@param` descritivo para **cada parâmetro**. Inclua `@return` para funções com retorno e `@throws` para exceções contratuais. Não repita apenas o nome do método.
- A adoção da formatação é gradual: classes novas e alteradas seguem o padrão; arquivos antigos não devem ser reformatados sem necessidade funcional.

## Verificações no build

`mvn --file src/backend/pom.xml verify` executa `BackendQualityTest` com JavaParser, sem precisar baixar plugins Maven adicionais. O gate verifica tabulações, espaços finais, fim de arquivo, imports globais, linhas acima de 120 colunas e JavaDoc de métodos e construtores públicos. Também rejeita concatenação repetida de `String` em laços e impede que a API volte a carregar todas as análises de um usuário em suas consultas periódicas. As ocorrências anteriores à adoção estão identificadas por regra, arquivo e assinatura em `src/backend/config/quality-baseline.txt`, com contagem; uma ocorrência nova reprova o build.

## Cache e consultas

- O worker compartilha a AST de cada arquivo entre regras determinísticas, avaliação de findings e seleção de sugestões **somente na tentativa atual**. O cache é fechado em `finally` implícito pelo `try-with-resources`; ele não guarda archives ou fontes após a tentativa.
- A API guarda somente o DTO de resultados `COMPLETED` por até **15 minutos**, com chave `(userId, analysisId)`, limite de 32 MiB estimados e limite de 2 MiB por entrada. O JWT continua obrigatório e o usuário é identificado antes da leitura do cache. Resultados em processamento, tarefas, central e histórico sempre consultam o banco para refletir a conclusão de novas análises imediatamente.
- Consultas da central e do histórico filtram execuções concluídas no banco; tarefas filtram estados visíveis no banco. As coleções de findings e sugestões são carregadas em lotes de até 50, e a migração `V11` acrescenta índices para as ordenações mais frequentes.
- Se futuramente houver exclusão ou edição de análises concluídas, a operação deverá invalidar o DTO correspondente na API. Atualmente as respostas concluídas são imutáveis para esse contrato.

## Verificação funcional

Os testes devem cobrir expiração, isolamento entre usuários, limites do cache, ausência de cache para estados parciais, equivalência dos findings com e sem AST compartilhada e visibilidade imediata de novas análises na central e no histórico. O fixture vulnerável continua mantido em memória e produz exatamente três findings sem executar seu código.
