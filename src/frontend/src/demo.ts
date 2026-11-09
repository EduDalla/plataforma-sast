import type { Finding, AiSuggestion, VulnerabilityStatus } from "./types";

export const project = {
  owner: "acme", repositoryName: "projeto-teste", repositoryUrl: "https://github.com/acme/projeto-teste",
};
export const demoEmail = "test@gmail.com";
export const statusLabels: Record<VulnerabilityStatus, string> = {
  OPEN: "Aberta", IN_PROGRESS: "Em correção", FIXED: "Corrigida", VALIDATED: "Validada",
  FALSE_POSITIVE: "Falso positivo", RISK_ACCEPTED: "Risco aceito",
};

const examples = [
  ["CWE-798", "Credencial no código", "Banco.java", 'String senha = "exemplo";', "Obtenha a credencial de um cofre de segredos."],
  ["CWE-78", "Comando com entrada externa", "Comando.java", "Runtime.getRuntime().exec(entrada);", "Restrinja os comandos permitidos e separe os argumentos."],
  ["CWE-502", "Desserialização insegura", "Importacao.java", "Object valor = stream.readObject();", "Prefira um formato de dados com esquema explícito."],
];
export const sampleFindings: Finding[] = examples.map(([cwe, title, fileName, snippet, remediation], i) => ({
  cwe, title, fileName, snippet, description: title + " no projeto de demonstração.",
  ruleId: "SAST-JAVA-00" + (i + 1), vulnerabilityCode: "VUL-00000" + (i + 1),
  line: 8 + i, column: 5, severity: i === 0 ? "Critical" : "High",
  aiAssessment: { model: "Simulação", promptVersion: "demo", confidence: 0.92,
    suggestedSeverity: i === 0 ? "Critical" : "High", likelyFalsePositive: false,
    rationale: "Exemplo de risco identificado no trecho apresentado.", remediation },
}));
export const suggestions: AiSuggestion[] = [{
  category: "PERFORMANCE", severity: "Low", title: "Reutilizar conexão",
  fileName: "Banco.java", line: 14, evidence: "abrirConexao();",
  rationale: "O exemplo abre uma conexão por item.", recommendation: "Reutilize a conexão durante o lote.",
  confidence: 0.85, limitations: "Avaliação fictícia.", model: "Simulação", promptVersion: "demo",
}];
