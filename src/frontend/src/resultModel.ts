import type { AiSuggestion, Finding, ResultPriority, ResultSummary } from "./types";

export interface UnifiedResultItem {
  key: string;
  source: "Regra" | "IA · Segurança" | "IA · Desempenho";
  priority: ResultPriority;
  fileName: string;
  line: number;
  column: number;
  title: string;
  finding?: Finding;
  suggestion?: AiSuggestion;
}

export const priorityLabels: Record<ResultPriority, string> = {
  Critical: "Crítica",
  High: "Alta",
  Medium: "Média",
  Low: "Baixa",
  Unclassified: "Sem classificação",
};

const priorityOrder: Record<ResultPriority, number> = {
  Critical: 0,
  High: 1,
  Medium: 2,
  Low: 3,
  Unclassified: 4,
};

export function unifiedResults(findings: Finding[] = [], suggestions: AiSuggestion[] = []): UnifiedResultItem[] {
  const items: UnifiedResultItem[] = findings.map((finding) => ({
    key: `rule:${finding.fileName}:${finding.ruleId}:${finding.line}:${finding.column}`,
    source: "Regra",
    priority: finding.severity,
    fileName: finding.fileName,
    line: finding.line,
    column: finding.column,
    title: finding.title,
    finding,
  }));
  suggestions.forEach((suggestion, index) => items.push({
    key: `ai:${suggestion.fileName}:${suggestion.line}:${suggestion.category}:${suggestion.title}:${index}`,
    source: suggestion.category === "PERFORMANCE" ? "IA · Desempenho" : "IA · Segurança",
    priority: suggestion.severity ?? "Unclassified",
    fileName: suggestion.fileName,
    line: suggestion.line,
    column: 0,
    title: suggestion.title,
    suggestion,
  }));
  return items.sort((a, b) => priorityOrder[a.priority] - priorityOrder[b.priority]
    || a.fileName.localeCompare(b.fileName)
    || a.line - b.line
    || a.title.localeCompare(b.title));
}

export function summarizeResults(items: UnifiedResultItem[]): ResultSummary {
  const critical = items.filter((item) => item.priority === "Critical").length;
  const high = items.filter((item) => item.priority === "High").length;
  const medium = items.filter((item) => item.priority === "Medium").length;
  const low = items.filter((item) => item.priority === "Low").length;
  const unclassified = items.filter((item) => item.priority === "Unclassified").length;
  return {
    total: items.length,
    critical,
    high,
    medium,
    low,
    unclassified,
    highestPriority: critical ? "Critical" : high ? "High" : medium ? "Medium" : low ? "Low"
      : unclassified ? "Unclassified" : null,
  };
}

export function priorityRank(priority: ResultPriority) {
  return priorityOrder[priority];
}
