package com.fiap.sast.rules;
import com.fiap.sast.analysis.SecurityFinding;
import com.github.javaparser.ast.Node;

final class RuleSupport {
    private RuleSupport() {
    }

    static SecurityFinding finding(String id, String title, String severity, String cwe,
                                   String description, String file, String source, Node node) {
        var range = node.getRange().orElseThrow();
        var lines = source.split("\\R", -1);
        var lineIndex = Math.max(0, Math.min(lines.length - 1, range.begin.line - 1));
        var snippet = lines.length == 0 ? "" : lines[lineIndex].trim();
        return new SecurityFinding(id, title, severity, cwe, description, file,
                range.begin.line, range.begin.column, snippet);
    }
}
