# Tarefas de UX e visual do frontend

## Sistema visual e navegação

- [ ] Consolidar cores, espaçamentos e estados nos estilos existentes.
- [ ] Usar texto principal de 14–16 px e informações secundárias de pelo menos 12 px.
- [ ] Verificar contraste nos temas claro e escuro; reservar cores de severidade para comunicar risco.
- [ ] Destacar a seção ativa e padronizar breadcrumbs.
- [ ] Disponibilizar “Nova análise” na central de sistemas.
- [ ] Adaptar navegação e conta para celular, evitando sobreposições.

## Login, cadastro e nova análise

- [ ] Simplificar login e cadastro, destacando a ação principal.
- [ ] Associar instruções e mensagens de erro aos respectivos campos.
- [ ] Priorizar URL e referência no formulário de análise.
- [ ] Explicar que a referência vazia utiliza a branch padrão.
- [ ] Manter restrições de origem e linguagem visíveis no celular.
- [ ] Preservar os valores do formulário após erro e impedir envios duplicados.

## Processamento, dashboard e histórico

- [ ] Apresentar os estados reais da API com rótulos em português, sem estimativas fictícias.
- [ ] Tornar as dicas de processamento secundárias ao estado da análise.
- [ ] Padronizar cards, destacando repositório, referência, data e quantidade de achados.
- [ ] Evidenciar a execução selecionada no histórico.
- [ ] Manter dados visíveis durante atualizações, com feedback de carregamento e erro.

## Resultados e triagem

- [ ] Organizar resultados em identificação da análise, estado, resumo e achados.
- [ ] Manter arquivo, severidade, título, CWE e localização visíveis.
- [ ] Exibir descrição, código, rastro e avaliação consultiva em detalhes expansíveis.
- [ ] Diferenciar visualmente achados determinísticos e sugestões de IA nos dois temas.
- [ ] Distinguir conclusão sem achados, processamento, falha e ausência de correspondências nos filtros.
- [ ] Remover qualquer indicação de sucesso em análises interrompidas.
- [ ] Adicionar busca local por arquivo, título, identificador da regra e CWE, sem distinção de maiúsculas.
- [ ] Adicionar filtro por severidade combinado com a busca.
- [ ] Oferecer ordenação por severidade ou localização, priorizando maior severidade por padrão.
- [ ] Na ordenação por severidade, priorizar arquivos com achados mais graves e ordenar seus achados por severidade, linha e coluna.
- [ ] Exibir quantidade filtrada e ação “Limpar filtros”, preservando os indicadores gerais da análise.
- [ ] Preservar filtros nas atualizações da mesma análise e reiniciá-los ao trocar de análise.

## Acessibilidade e responsividade

- [ ] Validar navegação por teclado e foco visível.
- [ ] Garantir gerenciamento de foco nos modais e associação acessível dos erros aos campos.
- [ ] Respeitar a preferência por movimento reduzido.
- [ ] Revisar todas as telas nos dois temas, nas larguras de 360, 768 e 1440 px e com zoom de 200%.
- [ ] Evitar rolagem horizontal da página, permitindo rolagem localizada em código e histórico.

## Testes e documentação

- [ ] Testar busca, filtros combinados, ordenação, limpeza e contadores.
- [ ] Testar os estados de conclusão sem achados, processamento e falha.
- [ ] Testar preservação do formulário após erro e bloqueio de envios duplicados.
- [ ] Verificar atualização do histórico e regressões de autenticação e temas.
- [ ] Executar `mvn --file src/backend/pom.xml verify`.
- [ ] Executar `npm --prefix src/frontend run build`.
- [ ] Executar `npm --prefix src/frontend run test -- --run`.
- [ ] Executar `docker compose config`.
- [ ] Confirmar exatamente três findings no fixture Java em memória, sem executar o código analisado.
- [ ] Atualizar a documentação do frontend com os controles e comportamentos revisados.

## Restrições da implementação

Preservar identidade verde, temas, React, rotas, contratos HTTP, autenticação e polling existentes. Utilizar os dados já recebidos para triagem local. Não adicionar dependências de UI, alterar a arquitetura ou ampliar capacidades do backend.
