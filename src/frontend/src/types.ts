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
export interface HistoryEntry {
  analysisId: string;
  reference: string | null;
  createdAt: string;
  filesAnalyzed: number;
  findings: number;
}
export interface SystemCard {
  owner: string;
  repositoryName: string;
  repositoryUrl: string;
  latestCreatedAt: string;
  totalAnalyses: number;
  latest: HistoryEntry;
}
export interface SystemsPage {
  systems: SystemCard[];
  page: number;
  size: number;
  totalSystems: number;
  totalAnalyses: number;
  totalFindings: number;
  totalCritical: number;
  totalFiles: number;
}
export interface HistoryPage {
  owner: string;
  repositoryName: string;
  history: HistoryEntry[];
  page: number;
  size: number;
  total: number;
}
export interface Session {
  email: string;
  accessToken?: string;
  tokenType?: "Bearer";
  expiresIn?: number;
}

export interface LoginResponse extends Session {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}
