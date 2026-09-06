package com.fiap.sast.analysis;
import com.fiap.sast.parsing.InvalidJavaSourceException;
import com.fiap.sast.parsing.JavaSourceParser;
import com.fiap.sast.rules.SecurityRule;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

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
        return rules.stream()
                .flatMap(rule -> rule.analyze(ast, source, fileName).stream())
                .sorted(Comparator.comparing(SecurityFinding::fileName)
                        .thenComparing(SecurityFinding::line)
                        .thenComparing(SecurityFinding::column)
                        .thenComparing(SecurityFinding::ruleId))
                .toList();
    }

    private com.github.javaparser.ast.CompilationUnit parse(String source, String fileName) {
        try {
            return parser.parse(source);
        } catch (InvalidJavaSourceException exception) {
            throw new InvalidJavaSourceException(
                    exception.getMessage(), fileName, exception.line, exception.column);
        }
    }
}
