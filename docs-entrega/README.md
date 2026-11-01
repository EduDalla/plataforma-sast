# Pacote `docs-entrega`

Este diretório contém todos os documentos necessários para a entrega da FISAST — Plataforma SAST e da defesa final, além dos artefatos de organização deste pacote. Não há uma segunda pasta documental: este é o diretório documental oficial do projeto.

## Ordem sugerida para a entrega

1. [Índice de arquivos e requisitos](ARQUIVOS-DA-ENTREGA.md).
2. [Contribuições e responsabilidades](CONTRIBUICOES-E-RESPONSABILIDADES.md).
3. [Roteiro, ata e ensaio da defesa](ROTEIRO-E-ATA-DA-DEFESA.md).
4. Documentos técnicos copiados para esta pasta, preservando seus nomes e a subpasta `evidencias/`.

O pacote não inclui código-fonte integral de repositórios analisados, archives, tokens, senhas, prompts, respostas brutas do modelo ou logs completos. Evidências devem conter somente metadados, estados, contagens, hashes, links autorizados e resultados derivados.

## Decisões arquiteturais propostas

- [ADR-001 — Gestão de vulnerabilidades e documentação das análises](ADR-001-Gestao-de-vulnerabilidades-e-relatorios.md): evolução incremental de status, identidade/classificação de vulnerabilidades e relatórios PDF/SARIF. O eixo de status foi iniciado; comparação e exportações continuam pendentes.

## Situação desta preparação

- Escopo Java, URL pública do GitHub, stack equivalente e dashboard HTTP: confirmado em D-01.
- Arquitetura, parser/AST, regras, taint, IA consultiva, dashboard, Compose e CI/gate: implementados com a situação detalhada no índice.
- Roteiro de oito minutos e divisão preliminar de papéis: preparados.
- Demonstração visual integrada, nomes validados pelo grupo, PR real e proteção administrativa da branch: ainda exigem registro externo; não são declarados concluídos por este pacote.
