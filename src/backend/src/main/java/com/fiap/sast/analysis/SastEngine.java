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

    /**
     * Inicializa o engine com parser e regras registradas pelo Spring.
     *
     * @param parser parser que constrói a AST sem executar o código recebido
     * @param rules regras determinísticas registradas na aplicação
     */
    public SastEngine(JavaSourceParser parser, List<SecurityRule> rules) {
        this.parser = parser;
        this.rules = rules;
    }

    /**
     * Cria o cache transitório compartilhado pelas etapas de uma tentativa do worker.
     *
     * @return cache que deve ser fechado ao terminar a tentativa
     */
    public AnalysisParseCache newParseCache() {
        return new AnalysisParseCache(parser);
    }

    /**
     * Aplica as regras determinísticas ao arquivo Java sem executar o código recebido.
     *
     * @param source conteúdo do arquivo Java
     * @param fileName caminho do arquivo no snapshot
     * @return findings ordenados e deduplicados pela identidade da ocorrência
     */
    public List<SecurityFinding> analyze(String source, String fileName) {
        return analyze(source, fileName, null);
    }

    /**
     * Aplica as regras usando a AST transitória da tentativa quando disponível.
     *
     * @param source conteúdo do arquivo Java
     * @param fileName caminho do arquivo no snapshot
     * @param cache cache da tentativa, ou {@code null} para parse isolado
     * @return findings ordenados e deduplicados pela identidade da ocorrência
     */
    public List<SecurityFinding> analyze(String source, String fileName, AnalysisParseCache cache) {
        final var ast = cache == null ? parse(source, fileName) : parse(cache, source, fileName);
        Map<FindingIdentity, SecurityFinding> findingsByIdentity = new LinkedHashMap<>();
        for (var rule : rules) {
            for (var finding : rule.analyze(ast, source, fileName)) {
                findingsByIdentity.putIfAbsent(FindingIdentity.from(finding), finding);
            }
        }
        return findingsByIdentity.values().stream()
                .sorted(Comparator.comparing(SecurityFinding::fileName)
                        .thenComparing(SecurityFinding::line)
                        .thenComparing(SecurityFinding::column)
                        .thenComparing(SecurityFinding::ruleId))
                .toList();
    }

    /**
     * Identidade da ocorrência detectada no AST.
     *
     * <p>A linha sozinha não identifica um finding: duas regras ou dois nós
     * diferentes podem começar na mesma linha. O trecho participa da chave
     * para distinguir nós que compartilham a mesma posição inicial, enquanto
     * a regra impede que uma regra substitua outra.</p>
     */
    private record FindingIdentity(String ruleId, String fileName, int line, int column, String snippet) {
        private static FindingIdentity from(SecurityFinding finding) {
            return new FindingIdentity(finding.ruleId(), finding.fileName(), finding.line(),
                    finding.column(), finding.snippet());
        }
    }

    private com.github.javaparser.ast.CompilationUnit parse(String source, String fileName) {
        try {
            return parser.parse(source);
        } catch (InvalidJavaSourceException exception) {
            throw new InvalidJavaSourceException(
                    exception.getMessage(), fileName, exception.line, exception.column);
        }
    }

    private com.github.javaparser.ast.CompilationUnit parse(AnalysisParseCache cache, String source, String fileName) {
        try {
            return cache.ast(fileName, source);
        } catch (InvalidJavaSourceException exception) {
            throw new InvalidJavaSourceException(
                    exception.getMessage(), fileName, exception.line, exception.column);
        }
    }
}
