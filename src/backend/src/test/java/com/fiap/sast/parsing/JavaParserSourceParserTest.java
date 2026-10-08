package com.fiap.sast.parsing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserSourceParserTest {
    private final JavaParserSourceParser parser = new JavaParserSourceParser();

    @Test
    @org.junit.jupiter.api.DisplayName("BDD-E-01: Java válido produz AST com localização")
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
    @org.junit.jupiter.api.DisplayName("BDD-E-02: erro sintático rejeita AST parcial")
    void rejeitaJavaInvalidoMesmoQuandoHaAstParcial() {
        var exception = assertThrows(InvalidJavaSourceException.class,
                () -> parser.parse("class Example {\n  void broken( {\n}"));

        assertTrue(exception.line >= 1);
        assertTrue(exception.column >= 1);
        assertFalse(exception.getMessage().isBlank());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("BDD-OP-01: ASTs de chamadas simultâneas não compartilham estado")
    void parserSingletonPreservaFontesConcorrentes() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 2; worker++) {
                final String className = "Source" + worker;
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    for (int round = 0; round < 50; round++) {
                        var ast = parser.parse("class " + className + " { String value=\"" + round + "\"; }");
                        assertTrue(ast.getClassByName(className).isPresent());
                        assertEquals(Integer.toString(round), ast.findFirst(
                                com.github.javaparser.ast.expr.StringLiteralExpr.class).orElseThrow().asString());
                    }
                    return null;
                }));
            }
            for (var task : tasks) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
