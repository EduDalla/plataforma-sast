package com.fiap.sast.rules;
import com.fiap.sast.analysis.SecurityFinding; import com.github.javaparser.ast.CompilationUnit; import java.util.List;
public interface SecurityRule { String ruleId(); List<SecurityFinding> analyze(CompilationUnit ast,String source,String file); }
