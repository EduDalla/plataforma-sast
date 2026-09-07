export interface Finding {
  ruleId: string;
  title: string;
  severity: "Critical" | "High" | "Medium" | "Low";
  cwe: string;
  description: string;
  fileName: string;
  line: number;
  column: number;
  snippet: string;
}
export interface Analysis {
  analysisId: string;
  status: "Completed";
  repositoryUrl: string;
  reference: string | null;
  language: "java";
  filesAnalyzed: number;
  createdAt: string;
  findings: Finding[];
}
export interface Session {
  email: string;
}
