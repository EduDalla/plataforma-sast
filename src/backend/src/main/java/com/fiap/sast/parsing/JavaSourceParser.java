package com.fiap.sast.parsing;
import com.github.javaparser.ast.CompilationUnit;
public interface JavaSourceParser { CompilationUnit parse(String source); }
