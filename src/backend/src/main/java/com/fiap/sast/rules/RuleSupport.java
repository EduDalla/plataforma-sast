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
        var line = lines[lineIndex];
        int start = Math.min(line.length(), range.begin.column - 1);
        int end = range.begin.line == range.end.line ? Math.min(line.length(), range.end.column) : line.length();
        var snippet = line.substring(start, Math.min(end, start + 500)).trim();
        return new SecurityFinding(id, title, severity, cwe, description, file,
                range.begin.line, range.begin.column, snippet);
    }
}
