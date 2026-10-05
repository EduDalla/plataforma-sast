package com.fiap.sast.quality;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.javadoc.JavadocBlockTag;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Gate de qualidade sem dependências Maven adicionais, executado pelo verify. */
class BackendQualityTest {
    private static final String BASELINE = "quality-baseline.txt";

    @Test
    void novosProblemasDeEstiloDocumentacaoEDesempenhoFalhamOBuild() throws Exception {
        Path backend = Files.isDirectory(Path.of("src/backend/src/main/java"))
                ? Path.of("src/backend") : Path.of(".");
        Path sourceRoot = backend.resolve("src/main/java");
        var parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        Map<String, Integer> actual = new TreeMap<>();
        List<String> hardFailures = new ArrayList<>();

        try (var files = Files.walk(sourceRoot)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String path = sourceRoot.relativize(file).toString().replace('\\', '/');
                String source = Files.readString(file, StandardCharsets.UTF_8);
                inspectLines(path, source, actual, hardFailures);
                var parsed = parser.parse(source).getResult().orElseThrow();
                parsed.findAll(MethodDeclaration.class).stream().filter(MethodDeclaration::isPublic)
                        .forEach(method -> inspectJavadoc(path, method, actual));
                parsed.findAll(ConstructorDeclaration.class).stream().filter(ConstructorDeclaration::isPublic)
                        .forEach(ctor -> inspectJavadoc(path, ctor, actual));
                inspectPerformance(path, parsed.findAll(MethodDeclaration.class), hardFailures);
            }
        }

        var baselinePath = backend.resolve("config").resolve(BASELINE);
        var baseline = new HashMap<String, Integer>();
        for (var line : Files.readAllLines(baselinePath, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int separator = line.lastIndexOf('|');
            baseline.put(line.substring(0, separator), Integer.parseInt(line.substring(separator + 1)));
        }
        actual.forEach((issue, count) -> {
            if (count > baseline.getOrDefault(issue, 0)) hardFailures.add(issue + " (" + count + ")");
        });
        assertTrue(hardFailures.isEmpty(), "Novas violações de qualidade: " + hardFailures);
    }

    private static void inspectLines(String path, String source, Map<String, Integer> actual,
            List<String> hardFailures) throws Exception {
        if (!source.endsWith("\n")) hardFailures.add(path + ": sem quebra de linha final");
        for (var line : source.split("\n", -1)) {
            if (line.contains("\t")) hardFailures.add(path + ": tabulação");
            if (!line.isEmpty() && Character.isWhitespace(line.charAt(line.length() - 1))) {
                hardFailures.add(path + ": espaço ao fim da linha");
            }
            if (line.length() > 120) add(actual, "LONG_LINE|" + path + "|" + sha(line));
            if (line.matches("\\s*import (static )?[^;]*\\*;\\s*")) {
                add(actual, "STAR_IMPORT|" + path + "|" + sha(line));
            }
        }
    }

    private static void inspectJavadoc(String path, CallableDeclaration<?> callable,
            Map<String, Integer> actual) {
        if (callable.getAnnotationByName("Override").isPresent()) return;
        String identity = path + "|" + callable.getSignature();
        var javadoc = callable.getJavadoc();
        if (javadoc.isEmpty()) {
            add(actual, "JAVADOC_MISSING|" + identity);
            return;
        }
        if (javadoc.orElseThrow().getDescription().toText().isBlank()) {
            add(actual, "JAVADOC_OBJECTIVE|" + identity);
        }
        var tags = javadoc.orElseThrow().getBlockTags();
        for (var parameter : callable.getParameters()) {
            boolean documented = tags.stream().anyMatch(tag -> tag.getType() == JavadocBlockTag.Type.PARAM
                    && tag.getName().filter(parameter.getNameAsString()::equals).isPresent()
                    && !tag.getContent().toText().isBlank());
            if (!documented) add(actual, "JAVADOC_PARAM|" + identity + "|" + parameter.getNameAsString());
        }
        if (callable instanceof MethodDeclaration method && !method.getType().isVoidType()) {
            boolean documented = tags.stream().anyMatch(tag -> tag.getType() == JavadocBlockTag.Type.RETURN
                    && !tag.getContent().toText().isBlank());
            if (!documented) add(actual, "JAVADOC_RETURN|" + identity);
        }
        for (var exception : callable.getThrownExceptions()) {
            boolean documented = tags.stream().anyMatch(tag -> tag.getType() == JavadocBlockTag.Type.THROWS
                    && tag.getName().filter(exception.asString()::equals).isPresent());
            if (!documented) add(actual, "JAVADOC_THROWS|" + identity + "|" + exception.asString());
        }
    }

    private static void inspectPerformance(String path, List<MethodDeclaration> methods,
            List<String> hardFailures) {
        for (var method : methods) {
            Set<String> stringVariables = method.findAll(VariableDeclarator.class).stream()
                    .filter(variable -> variable.getType().asString().equals("String"))
                    .map(VariableDeclarator::getNameAsString).collect(java.util.stream.Collectors.toSet());
            for (var assignment : method.findAll(AssignExpr.class)) {
                if (assignment.getOperator() != AssignExpr.Operator.PLUS
                        || !(assignment.getTarget() instanceof NameExpr target)
                        || !stringVariables.contains(target.getNameAsString())) continue;
                boolean inLoop = assignment.findAncestor(ForStmt.class).isPresent()
                        || assignment.findAncestor(ForEachStmt.class).isPresent()
                        || assignment.findAncestor(WhileStmt.class).isPresent()
                        || assignment.findAncestor(DoStmt.class).isPresent();
                if (inLoop) hardFailures.add(path + ": concatenação de String em laço: " + method.getNameAsString());
            }
        }
        if (path.endsWith("/web/AnalysisController.java")) {
            // A leitura irrestrita de todas as análises aumenta o custo da consulta periódica.
            Path controller = Path.of("src/main/java").resolve(path);
            if (!Files.exists(controller)) controller = Path.of("src/backend/src/main/java").resolve(path);
            try {
                assertTrue(!Files.readString(controller).contains("repo.findByUserIdOrderByCreatedAtDescIdDesc"),
                        "Controller voltou a carregar todas as análises do usuário");
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static void add(Map<String, Integer> issues, String key) {
        issues.merge(key, 1, Integer::sum);
    }

    private static String sha(String line) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(line.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest).substring(0, 16);
    }
}
