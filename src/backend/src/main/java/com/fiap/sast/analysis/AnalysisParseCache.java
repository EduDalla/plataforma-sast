package com.fiap.sast.analysis;

import com.fiap.sast.parsing.JavaSourceParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compartilha ASTs somente entre as etapas de uma tentativa do worker. */
public final class AnalysisParseCache implements AutoCloseable {
    private final JavaSourceParser parser;
    private final Map<String, Parsed> parsed = new HashMap<>();

    /**
     * Cria um cache restrito à tentativa atual.
     *
     * @param parser parser Java usado pelo engine
     */
    public AnalysisParseCache(JavaSourceParser parser) {
        this.parser = parser;
    }

    /**
     * Retorna a AST do arquivo, fazendo parse somente na primeira consulta da fonte.
     *
     * @param path caminho do arquivo dentro do snapshot
     * @param source conteúdo transitório do arquivo
     * @return AST associada ao conteúdo informado
     */
    public CompilationUnit ast(String path, String source) {
        var entry = parsed.get(path);
        if (entry == null || entry.source != source) {
            entry = new Parsed(source, parser.parse(source));
            parsed.put(path, entry);
        }
        return entry.ast;
    }

    /**
     * Retorna os métodos da AST já armazenada para a fonte informada.
     *
     * @param path caminho do arquivo dentro do snapshot
     * @param source conteúdo transitório do arquivo
     * @return métodos do arquivo na ordem da AST
     */
    public List<MethodDeclaration> methods(String path, String source) {
        ast(path, source);
        var entry = parsed.get(path);
        if (entry.methods == null) {
            entry.methods = entry.ast.findAll(MethodDeclaration.class);
        }
        return entry.methods;
    }

    /** Descarta todas as referências à fonte e às ASTs ao fim da tentativa. */
    @Override
    public void close() {
        parsed.clear();
    }

    private static final class Parsed {
        private final String source;
        private final CompilationUnit ast;
        private List<MethodDeclaration> methods;

        private Parsed(String source, CompilationUnit ast) {
            this.source = source;
            this.ast = ast;
        }
    }
}
