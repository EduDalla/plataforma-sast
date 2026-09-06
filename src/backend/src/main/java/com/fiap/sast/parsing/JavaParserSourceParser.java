package com.fiap.sast.parsing;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class JavaParserSourceParser implements JavaSourceParser {
    private final JavaParser parser;

    public JavaParserSourceParser() {
        var config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        parser = new JavaParser(config);
    }

    @Override
    public CompilationUnit parse(String source) {
        var result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            Problem problem = result.getProblems().stream().findFirst()
                    .orElseThrow(() -> new InvalidJavaSourceException(
                            "Código Java inválido", 1, 1));
            var position = problem.getLocation()
                    .flatMap(location -> location.getBegin().getRange())
                    .map(range -> range.begin)
                    .orElse(new com.github.javaparser.Position(1, 1));
            throw new InvalidJavaSourceException(
                    problem.getMessage(), position.line, position.column);
        }
        return result.getResult().orElseThrow();
    }
}
