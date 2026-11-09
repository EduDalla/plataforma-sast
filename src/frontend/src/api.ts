import { demoEmail, project, sampleFindings, statusLabels, suggestions } from "./demo";
import { summarizeResults, unifiedResults } from "./resultModel";
import type { Analysis, AnalysisTask, HistoryEntry, Session, SystemsPage, VulnerabilityAudit, VulnerabilityResponse, VulnerabilityStatus } from "./types";

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
interface Run { id: string; start: number; ref: string; acknowledged: boolean }
interface State { signedIn: boolean; runs: Run[]; audits: Record<string, VulnerabilityAudit[]> }
const key = "fisast-demo-v1";
function restore(): State {
  try {
    const saved = JSON.parse(sessionStorage.getItem(key) || "null");
    if (saved?.runs?.length && saved.audits) return saved;
  } catch { /* O armazenamento da aba é opcional. */ }
  return { signedIn: false, audits: {}, runs: [1, 2].map(days => ({
    id: "demo-" + days, start: Date.now() - days * 86400000, ref: "main", acknowledged: true,
  })) };
}
const state = restore();
function save() {
  try { sessionStorage.setItem(key, JSON.stringify(state)); } catch { /* Os dados continuam em memória. */ }
}
function session(): Session {
  if (!state.signedIn) throw new ApiError(401, "Entre para acessar a demonstração.");
  return { email: demoEmail };
}
function runById(id: string) {
  const run = state.runs.find(run => run.id === id);
  if (!run) throw new ApiError(404, "Análise não encontrada.");
  return run;
}
const stages = ["QUEUED", "DOWNLOADING", "DETERMINISTIC", "SEMANTIC", "SUGGESTIONS", "COMPLETED"] as const;
function analysis(run: Run): Analysis {
  const step = Math.min(5, Math.max(0, Math.floor((Date.now() - run.start) / 2000)));
  const findings = step < 3 ? [] : sampleFindings.map(f => {
    const audit = state.audits[f.vulnerabilityCode!] ?? [];
    return { ...f, findingId: run.id + f.ruleId, vulnerabilityStatus: audit[audit.length - 1]?.newStatus ?? "OPEN",
      vulnerabilityVersion: audit.length, aiAssessment: step >= 4 ? f.aiAssessment : null };
  });
  const items = step === 5 ? suggestions : [];
  return { analysisId: run.id, status: step === 5 ? "COMPLETED" : "PROCESSING", stage: stages[step],
    repositoryUrl: project.repositoryUrl, language: "java", reference: run.ref, createdAt: new Date(run.start).toISOString(),
    filesAnalyzed: step >= 3 ? 3 : 0, filesTotal: 3, filesProcessed: step >= 3 ? 3 : 0,
    semanticStatus: step >= 4 ? "COMPLETED" : step === 3 ? "RUNNING" : "PENDING",
    suggestionStatus: step === 5 ? "COMPLETED" : step === 4 ? "RUNNING" : "PENDING",
    findings, suggestions: items, resultSummary: summarizeResults(unifiedResults(findings, items)) };
}
function historyEntry(a: Analysis): HistoryEntry {
  return { analysisId: a.analysisId, reference: a.reference, createdAt: a.createdAt, filesAnalyzed: a.filesAnalyzed,
    findings: a.findings.length, suggestions: a.suggestions?.length, resultSummary: a.resultSummary,
    coverageStatus: a.status === "COMPLETED" ? "CONFIRMED" : "PROCESSING", status: a.status, stage: a.stage,
    semanticStatus: a.semanticStatus, suggestionStatus: a.suggestionStatus };
}
function vulnerability(code: string): VulnerabilityResponse {
  const f = sampleFindings.find(f => f.vulnerabilityCode === code);
  if (!f) throw new ApiError(404, "Vulnerabilidade não encontrada.");
  const audit = state.audits[code] ?? [];
  const occurrences = state.runs.map(analysis).filter(a => a.findings.some(item => item.vulnerabilityCode === code))
    .map(a => ({ id: a.analysisId + code, analysisId: a.analysisId, findingId: a.analysisId + f.ruleId,
      detectedAt: a.createdAt, reference: a.reference }));
  const status = audit[audit.length - 1]?.newStatus ?? "OPEN";
  return { ...f, id: code, code, repositoryOwner: project.owner, repositoryName: project.repositoryName,
    status, statusLabel: statusLabels[status], version: audit.length, identityOrdinal: 1, audit, occurrences,
    firstIdentifiedAt: occurrences[occurrences.length - 1].detectedAt, lastDetectedAt: occurrences[0].detectedAt };
}
export const api = {
  config: async () => ({ locale: "pt-BR" }),
  session: async () => session(),
  login: async (email: string, password: string) => {
    if (email !== demoEmail || password !== "123456") throw new ApiError(401, "E-mail ou senha inválidos");
    state.signedIn = true; save();
    return session();
  },
  logout: async () => { state.signedIn = false; save(); },
  create: async (url: string, reference: string) => {
    session();
    if (url !== project.repositoryUrl) throw new ApiError(400, "Escolha o projeto de demonstração.");
    const run = { id: crypto.randomUUID(), start: Date.now(), ref: reference.trim() || "main", acknowledged: false };
    state.runs.unshift(run); save();
    return analysis(run);
  },
  analysis: async (id: string) => { session(); return analysis(runById(id)); },
  tasks: async (): Promise<AnalysisTask[]> => {
    session();
    return state.runs.filter(r => !r.acknowledged).map(r => ({ ...analysis(r), acknowledged: false }));
  },
  acknowledgeTask: async (id: string) => { session(); runById(id).acknowledged = true; save(); },
  systems: async (page = 0, size = 20): Promise<SystemsPage> => {
    session();
    const runs = state.runs.map(analysis);
    const done = runs.filter(a => a.status === "COMPLETED");
    const latest = historyEntry(runs[0]);
    const resultSummary = summarizeResults(done.flatMap(a => unifiedResults(a.findings, a.suggestions)));
    return { systems: page ? [] : [{ ...project, totalAnalyses: runs.length, latestCreatedAt: latest.createdAt, latest }],
      page, size, totalSystems: 1, totalAnalyses: runs.length, totalCritical: resultSummary.critical,
      totalFiles: done.reduce((n, a) => n + a.filesAnalyzed, 0), totalFindings: done.reduce((n, a) => n + a.findings.length, 0), resultSummary };
  },
  history: async (owner: string, repository: string, page = 0, size = 20) => {
    session();
    if (owner !== project.owner || repository !== project.repositoryName) throw new ApiError(404, "Projeto não encontrado.");
    return { owner, repositoryName: repository, page, size, total: state.runs.length,
      history: state.runs.slice(page * size, (page + 1) * size).map(r => historyEntry(analysis(r))) };
  },
  vulnerability: async (code: string) => { session(); return vulnerability(code); },
  updateVulnerabilityStatus: async (code: string, status: VulnerabilityStatus, observation: string, version?: number) => {
    session();
    const old = vulnerability(code);
    if (version !== undefined && version !== old.version) throw new ApiError(409, "Reabra o resultado para atualizar o status.");
    if (!observation.trim() || status === "VALIDATED" || status === old.status) throw new ApiError(400, "Informe uma alteração e uma justificativa.");
    state.audits[code] = [...old.audit, { id: crypto.randomUUID(), changedAt: new Date().toISOString(), actorEmail: demoEmail,
      previousStatus: old.status, previousStatusLabel: old.statusLabel, newStatus: status, newStatusLabel: statusLabels[status],
      observation: observation.trim() }];
    save();
    return vulnerability(code);
  },
};
