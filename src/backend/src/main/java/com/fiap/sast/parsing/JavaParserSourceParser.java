package com.fiap.sast.parsing;
import com.github.javaparser.*; import com.github.javaparser.ast.CompilationUnit; import org.springframework.stereotype.Component;
@Component public class JavaParserSourceParser implements JavaSourceParser {
 private final JavaParser parser;
 public JavaParserSourceParser(){ var config=new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21); parser=new JavaParser(config); }
 public CompilationUnit parse(String source){ var result=parser.parse(source); if(result.getResult().isEmpty()){var p=result.getProblems().getFirst(); var pos=p.getLocation().flatMap(x->x.getBegin()).orElse(new com.github.javaparser.Position(1,1)); throw new InvalidJavaSourceException(p.getMessage(),pos.line,pos.column);} return result.getResult().orElseThrow(); }
}
