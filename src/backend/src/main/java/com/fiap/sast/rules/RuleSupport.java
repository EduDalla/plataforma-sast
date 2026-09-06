package com.fiap.sast.rules;
import com.fiap.sast.analysis.SecurityFinding; import com.github.javaparser.ast.Node;
final class RuleSupport { static SecurityFinding finding(String id,String title,String severity,String cwe,String description,String file,String source,Node node){var r=node.getRange().orElseThrow(); var lines=source.split("\\R",-1); return new SecurityFinding(id,title,severity,cwe,description,file,r.begin.line,r.begin.column,lines[r.begin.line-1].trim());} }
