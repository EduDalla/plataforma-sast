# Registro de decisões de escopo

Este registro evita que o material de defesa transforme exemplos do documento original em funcionalidades não implementadas.

| Decisão | Interpretação adotada no checkout | Consequência |
| --- | --- | --- |
| RF01 — entrada | URL HTTPS de repositório público do GitHub | Não há upload de pacote nem suporte a repositório privado. |
| RF02 — linguagem | Java é a linguagem implementada | Não declarar suporte a Python, JavaScript ou outra linguagem. |
| RF08 — violações | Equivalentes Java: CWE-798, CWE-78 e CWE-502, com taint de comando | Não apresentar `eval()`/`innerHTML` como regras implementadas. |
| IA | Consultiva e local | IA não cria, remove ou reclassifica findings determinísticos. |
| Relatório | Dashboard HTTP e histórico existentes | Exportação PDF/CSV/SARIF e Security Gate dependem de decisão/implementação futura. |

## Pontos que ainda exigem validação da orientação

1. Se a URL pública satisfaz formalmente o requisito de “receber código-fonte”.
2. Se Java único e três equivalentes Java atendem à rubrica final.
3. Se as tecnologias do resumo são obrigatórias ou apenas recomendações.
4. Se o dashboard/evidências substituem exportação formal.

Até que essas decisões sejam confirmadas, não ampliar a arquitetura para upload, multilinguagem, repositórios privados ou gate de merge.

