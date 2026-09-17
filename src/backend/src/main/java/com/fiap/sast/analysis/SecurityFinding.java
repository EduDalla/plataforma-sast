package com.fiap.sast.analysis;
public record SecurityFinding(String ruleId,String title,String severity,String cwe,String description,String fileName,int line,int column,String snippet,TaintTrace taintTrace) {
    public SecurityFinding(String ruleId,String title,String severity,String cwe,String description,String fileName,int line,int column,String snippet) {
        this(ruleId,title,severity,cwe,description,fileName,line,column,snippet,null);
    }
}
