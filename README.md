# FISAST · demonstração local

Esta branch apresenta a interface da plataforma SAST com dados fictícios do projeto `acme/projeto-teste`. O navegador simula a análise em Java, o histórico e as avaliações consultivas.

## Executar

Use Node.js 24 com npm. Na raiz do repositório, execute:

```sh
npm --prefix src/frontend ci
npm --prefix src/frontend run dev
```

Abra o endereço local indicado pelo Vite. Entre com **test@gmail.com** e a senha **123456**.

No dashboard, abra **projeto-teste** para consultar duas execuções anteriores. Em **Nova análise**, inicie uma simulação. Em cerca de dez segundos ela apresenta três vulnerabilidades (CWE-798, CWE-78 e CWE-502) e uma sugestão de desempenho. As abas de resultados permitem consultar os trechos fictícios e registrar mudanças de status. Use **Imprimir / salvar PDF** para gerar o relatório da execução selecionada pelo navegador.

A sessão, o histórico e a auditoria ficam na aba. Recarregar preserva esses dados enquanto o armazenamento de sessão estiver disponível. Fechar a aba encerra a demonstração. O tema segue a preferência local do navegador.

## Verificar

```sh
npm --prefix src/frontend run build
npm --prefix src/frontend run test -- --run
```

A documentação acadêmica da plataforma original está no [arquivo histórico](docs-entrega/README.md). As regras desta versão estão em [REGRAS.MD](REGRAS.MD).
