package com.fiap.sast.analysis;
public record SecurityFinding(String ruleId,String title,String severity,String cwe,String description,String fileName,int line,int column,String snippet) {}
