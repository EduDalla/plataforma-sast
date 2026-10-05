export interface TraceStep {
  kind: string;
  line: number;
  column: number;
}
export interface TaintTrace {
  engineVersion: string;
  source: TraceStep;
  steps: TraceStep[];
  sink: TraceStep;
}
export interface AiAssessment {
  model: string;
  promptVersion: string;
  confidence: number;
  suggestedSeverity: "Critical" | "High" | "Medium" | "Low";
  likelyFalsePositive: boolean;
  rationale: string;
  remediation: string;
  risk?: string | null;
  evidence?: string[];
  falsePositiveReason?: string | null;
  limitations?: string | null;
  recommendations?: string[];
}
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
  taintTrace?: TaintTrace | null;
  aiAssessment?: AiAssessment | null;
}
export interface AiSuggestion {
  category: "SECURITY" | "PERFORMANCE";
  severity?: "Critical" | "High" | "Medium" | "Low" | null;
  title: string;
  fileName: string;
  line: number;
  evidence: string;
  rationale: string;
  confidence: number;
  recommendation: string;
  limitations: string;
  model: string;
  promptVersion: string;
}
export type ResultPriority = "Critical" | "High" | "Medium" | "Low" | "Unclassified";
export interface ResultSummary {
  total: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
  unclassified: number;
  highestPriority: ResultPriority | null;
}
export interface Analysis {
  analysisId: string;
  status: "PROCESSING" | "COMPLETED" | "FAILED" | "Completed";
  repositoryUrl: string;
  reference: string | null;
  language: "java";
  filesAnalyzed: number;
  createdAt: string;
  semanticStatus?: "PENDING" | "RUNNING" | "COMPLETED" | "DEGRADED" | "NOT_APPLICABLE";
  suggestionStatus?: "PENDING" | "RUNNING" | "COMPLETED" | "DEGRADED" | "NOT_APPLICABLE";
  suggestions?: AiSuggestion[];
  resultSummary?: ResultSummary;
  findings: Finding[];
  stage?: "QUEUED" | "DOWNLOADING" | "DETERMINISTIC" | "SEMANTIC" | "SUGGESTIONS" | "COMPLETED" | "FAILED";
  filesProcessed?: number;
  filesTotal?: number;
  failureStage?: string | null;
  failureMessage?: string | null;
  commitSha?: string | null;
}
export interface AnalysisTask {
  analysisId: string;
  status: "PROCESSING" | "COMPLETED" | "FAILED" | "Completed";
  stage?: Analysis["stage"];
  repositoryUrl: string;
  createdAt: string;
  semanticStatus?: Analysis["semanticStatus"];
  suggestionStatus?: Analysis["suggestionStatus"];
  resultSummary?: ResultSummary;
  acknowledged: boolean;
}
export interface HistoryEntry {
  analysisId: string;
  reference: string | null;
  createdAt: string;
  filesAnalyzed: number;
  findings: number;
  resultSummary?: ResultSummary;
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
  resultSummary?: ResultSummary;
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
