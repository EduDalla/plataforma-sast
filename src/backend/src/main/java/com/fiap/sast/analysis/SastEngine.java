package com.fiap.sast.analysis;
import com.fiap.sast.parsing.InvalidJavaSourceException;
import com.fiap.sast.parsing.JavaSourceParser;
import com.fiap.sast.rules.SecurityRule;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SastEngine {
    private final JavaSourceParser parser;
    private final List<SecurityRule> rules;

    public SastEngine(JavaSourceParser parser, List<SecurityRule> rules) {
        this.parser = parser;
        this.rules = rules;
    }

    public List<SecurityFinding> analyze(String source, String fileName) {
        final var ast = parse(source, fileName);
        Map<FileLine, SecurityFinding> latestByLine = new LinkedHashMap<>();
        for (var rule : rules) {
            for (var finding : rule.analyze(ast, source, fileName)) {
                latestByLine.put(new FileLine(finding.fileName(), finding.line()), finding);
            }
        }
        return latestByLine.values().stream()
                .sorted(Comparator.comparing(SecurityFinding::fileName)
                        .thenComparing(SecurityFinding::line)
                        .thenComparing(SecurityFinding::column)
                        .thenComparing(SecurityFinding::ruleId))
                .toList();
    }

    private record FileLine(String fileName, int line) {}

    private com.github.javaparser.ast.CompilationUnit parse(String source, String fileName) {
        try {
            return parser.parse(source);
        } catch (InvalidJavaSourceException exception) {
            throw new InvalidJavaSourceException(
                    exception.getMessage(), fileName, exception.line, exception.column);
        }
    }
}
