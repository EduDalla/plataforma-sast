package com.fiap.sast.rules;
import com.fiap.sast.analysis.SecurityFinding;
import com.fiap.sast.analysis.TaintTrace;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class RuleSupport {
    private RuleSupport() {
    }

    static SecurityFinding finding(String id, String title, String severity, String cwe,
                                   String description, String file, String source, Node node) {
        return finding(id, title, severity, cwe, description, file, source, node, null);
    }

    public static SecurityFinding finding(String id, String title, String severity, String cwe,
                                   String description, String file, String source, Node node, TaintTrace taintTrace) {
        var range = node.getRange().orElseThrow();
        var lines = source.split("\\R", -1);
        var lineIndex = Math.max(0, Math.min(lines.length - 1, range.begin.line - 1));
        var line = lines[lineIndex];
        int start = Math.min(line.length(), range.begin.column - 1);
        int end = range.begin.line == range.end.line ? Math.min(line.length(), range.end.column) : line.length();
        var snippet = line.substring(start, Math.min(end, start + 500)).trim();
        var context = node.findAncestor(MethodDeclaration.class)
                .map(method -> method.getNameAsString() + "("
                        + method.getParameters().stream().map(parameter -> parameter.getTypeAsString())
                                .reduce((left, right) -> left + "," + right).orElse("") + ")")
                .orElse("<arquivo>");
        var expression = node.toString().replaceAll("\\s+", "");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var expressionHash = java.util.HexFormat.of().formatHex(
                    digest.digest(expression.getBytes(StandardCharsets.UTF_8)));
            return new SecurityFinding(id, title, severity, cwe, description, file,
                    range.begin.line, range.begin.column, snippet, taintTrace, context, expressionHash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
