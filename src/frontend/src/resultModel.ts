import type { AiSuggestion, Finding, ResultPriority, ResultSummary } from "./types";

export interface UnifiedResultItem {
  key: string;
  source: "Regra" | "Segurança" | "Desempenho";
  priority: ResultPriority;
  fileName: string;
  line: number;
  column: number;
  title: string;
  finding?: Finding;
  suggestion?: AiSuggestion;
}

export interface CriticalFileRanking {
  fileName: string;
  total: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
  unclassified: number;
  highestSeverity: ResultPriority;
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

const compareText = (left: string, right: string) => left < right ? -1 : left > right ? 1 : 0;

/**
 * Ordena arquivos usando apenas findings determinísticos.
 * A severidade é comparada como vetor (Critical, High, Medium, Low e sem classificação),
 * depois vem a quantidade total e, por fim, o nome para manter o desempate estável.
 */
export function rankCriticalFiles(findings: Finding[] = []): CriticalFileRanking[] {
  const grouped = new Map<string, CriticalFileRanking>();
  findings.forEach((finding) => {
    const current = grouped.get(finding.fileName) ?? {
      fileName: finding.fileName,
      total: 0,
      critical: 0,
      high: 0,
      medium: 0,
      low: 0,
      unclassified: 0,
      highestSeverity: "Unclassified" as ResultPriority,
    };
    current.total += 1;
    if (finding.severity === "Critical") current.critical += 1;
    else if (finding.severity === "High") current.high += 1;
    else if (finding.severity === "Medium") current.medium += 1;
    else if (finding.severity === "Low") current.low += 1;
    else current.unclassified += 1;
    if (priorityOrder[finding.severity] < priorityOrder[current.highestSeverity]) current.highestSeverity = finding.severity;
    grouped.set(finding.fileName, current);
  });
  return [...grouped.values()].sort((left, right) =>
    right.critical - left.critical
    || right.high - left.high
    || right.medium - left.medium
    || right.low - left.low
    || right.unclassified - left.unclassified
    || right.total - left.total
    || compareText(left.fileName, right.fileName));
}

export function sortConsultiveSuggestions(suggestions: AiSuggestion[] = []): AiSuggestion[] {
  return suggestions.slice().sort((left, right) =>
    priorityOrder[left.severity ?? "Unclassified"] - priorityOrder[right.severity ?? "Unclassified"]
    || compareText(left.fileName, right.fileName)
    || left.line - right.line
    || compareText(left.title, right.title));
}

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
    source: suggestion.category === "PERFORMANCE" ? "Desempenho" : "Segurança",
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
