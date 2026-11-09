# FISAST · demonstração local

Esta branch apresenta a interface da plataforma SAST com dados fictícios do projeto `acme/projeto-teste`. O navegador simula a análise em Java, o histórico e as avaliações consultivas.

A raiz do repositório é o projeto Vite. Os componentes, os dados de demonstração e os testes ficam em `src/`, e os recursos estáticos ficam em `public/`.

## Executar

Use Node.js 24 com npm. Na raiz do repositório, execute:

```sh
npm ci
npm run dev
```

Abra o endereço local indicado pelo Vite. Entre com **test@gmail.com** e a senha **123456**.

No dashboard, abra **projeto-teste** para consultar duas execuções anteriores. Em **Nova análise**, inicie uma simulação. Em cerca de dez segundos ela apresenta três vulnerabilidades (CWE-798, CWE-78 e CWE-502) e uma sugestão de desempenho. As abas de resultados permitem consultar os trechos fictícios e registrar mudanças de status. Use **Imprimir / salvar PDF** para gerar o relatório da execução selecionada pelo navegador.

A sessão, o histórico e a auditoria ficam na aba. Recarregar preserva esses dados enquanto o armazenamento de sessão estiver disponível. Fechar a aba encerra a demonstração. O tema segue a preferência local do navegador.

## Verificar

```sh
npm run build
npm run test -- --run
```

A documentação acadêmica da plataforma original está no [arquivo histórico](docs-entrega/README.md). As regras desta versão estão em [REGRAS.MD](REGRAS.MD).
