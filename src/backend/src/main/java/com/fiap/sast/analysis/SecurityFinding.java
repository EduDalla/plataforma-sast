package com.fiap.sast.analysis;
public record SecurityFinding(String ruleId, String title, String severity, String cwe, String description,
        String fileName, int line, int column, String snippet, TaintTrace taintTrace,
        String contextSignature, String expressionHash) {
    public SecurityFinding(String ruleId,String title,String severity,String cwe,String description,String fileName,int line,int column,String snippet) {
        this(ruleId,title,severity,cwe,description,fileName,line,column,snippet,null,null,null);
    }

    /** Mantém o construtor usado pelas regras com trace de taint.
     * @param ruleId identificador da regra
     * @param title título do finding
     * @param severity severidade determinística
     * @param cwe classificação CWE
     * @param description descrição do problema
     * @param fileName arquivo analisado
     * @param line linha do finding
     * @param column coluna do finding
     * @param snippet trecho necessário para o resultado
     * @param taintTrace trace da propagação, quando aplicável
     */
    public SecurityFinding(String ruleId, String title, String severity, String cwe, String description,
            String fileName, int line, int column, String snippet, TaintTrace taintTrace) {
        this(ruleId,title,severity,cwe,description,fileName,line,column,snippet,taintTrace,null,null);
    }
}
