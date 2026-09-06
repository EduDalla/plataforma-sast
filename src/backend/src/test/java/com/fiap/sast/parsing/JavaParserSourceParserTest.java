package com.fiap.sast.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserSourceParserTest {
    private final JavaParserSourceParser parser = new JavaParserSourceParser();

    @Test
    void geraAstComLocalizacaoParaJavaValido() {
        var ast = parser.parse("class Example {\n  String password = \"x\";\n}");

        var declaration = ast.getClassByName("Example").orElseThrow();
        var variable = declaration.findFirst(com.github.javaparser.ast.body.VariableDeclarator.class)
                .orElseThrow();

        assertEquals(1, declaration.getRange().orElseThrow().begin.line);
        assertEquals(2, variable.getRange().orElseThrow().begin.line);
        assertTrue(variable.getRange().orElseThrow().begin.column > 0);
    }

    @Test
    void rejeitaJavaInvalidoMesmoQuandoHaAstParcial() {
        var exception = assertThrows(InvalidJavaSourceException.class,
                () -> parser.parse("class Example {\n  void broken( {\n}"));

        assertTrue(exception.line >= 1);
        assertTrue(exception.column >= 1);
        assertFalse(exception.getMessage().isBlank());
    }
}
