package com.fiap.sast.parsing;
import com.github.javaparser.ast.CompilationUnit;
public interface JavaSourceParser {
    /**
     * Constrói uma AST a partir do texto Java sem compilar ou executar o código.
     *
     * @param source conteúdo Java não confiável mantido em memória
     * @return unidade de compilação analisável
     */
    CompilationUnit parse(String source);
}
