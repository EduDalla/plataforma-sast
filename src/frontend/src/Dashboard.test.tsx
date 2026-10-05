import { cleanup, render, screen, within } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it } from "vitest";
import { SystemDashboard } from "./components";
import type { Analysis } from "./types";

afterEach(cleanup);
const analysis: Analysis = {
  analysisId: "one", status: "COMPLETED", repositoryUrl: "https://github.com/acme/demo", reference: "main",
  language: "java", filesAnalyzed: 46, createdAt: "2026-09-06T12:00:00Z",
  findings: [{ ruleId: "rule", title: "Credencial", severity: "Critical", cwe: "CWE-798", description: "Revisar",
    fileName: "Service.java", line: 1, column: 1, snippet: "sample" }],
  suggestionStatus: "COMPLETED", suggestions: [{ category: "PERFORMANCE", severity: "High", title: "Possível consulta em laço",
    fileName: "Service.java", line: 12, evidence: "repository.findById(id)", rationale: "Consultas repetidas",
    confidence: 0.8, recommendation: "Avaliar busca em lote", limitations: "Confirmar com métricas", model: "local", promptVersion: "v1" }],
};
function show(data = analysis) {
  render(<SystemDashboard data={data} history={[]} totalHistory={3} hasMore={false}
    onSelect={() => {}} onBack={() => {}} onMore={() => {}} onOpen={() => {}} />);
}
function count(label: string, expected: string) {
  expect(within(screen.getByText(label).parentElement!).getByText(expected, { selector: "strong" })).toBeInTheDocument();
}
describe("dashboard unificado do sistema", () => {
  it("mostra tendência de alta, queda, estabilidade e não compara cobertura parcial", () => {
    const summary = (total: number) => ({ total, critical: total > 1 ? 2 : 1, high: 0, medium: 0, low: 0, unclassified: 0, highestPriority: "Critical" as const });
    render(<SystemDashboard data={analysis} history={[
      { analysisId: "r5", reference: "main", createdAt: "2026-09-10T12:00:00Z", filesAnalyzed: 46, findings: 2, resultSummary: summary(2), coverageStatus: "CONFIRMED" },
      { analysisId: "r4", reference: "main", createdAt: "2026-09-09T12:00:00Z", filesAnalyzed: 46, findings: 2, resultSummary: summary(2), coverageStatus: "CONFIRMED" },
      { analysisId: "r3", reference: "main", createdAt: "2026-09-08T12:00:00Z", filesAnalyzed: 46, findings: 3, resultSummary: summary(3), coverageStatus: "CONFIRMED" },
      { analysisId: "r2", reference: "main", createdAt: "2026-09-07T12:00:00Z", filesAnalyzed: 46, findings: 2, resultSummary: summary(2), coverageStatus: "CONFIRMED" },
      { analysisId: "r1", reference: "main", createdAt: "2026-09-06T12:00:00Z", filesAnalyzed: 46, findings: 2, resultSummary: summary(2), coverageStatus: "PARTIAL" },
    ]} totalHistory={5} hasMore={false} onSelect={() => {}} onBack={() => {}} onMore={() => {}} onOpen={() => {}} />);
    const table = screen.getByRole("table", { name: "Tendência de vulnerabilidades por execução" });
    expect(within(table).getByText("+1")).toBeInTheDocument();
    expect(within(table).getByText("-1")).toBeInTheDocument();
    expect(within(table).getByText("Estável")).toBeInTheDocument();
    expect(within(table).getByText("Não comparável")).toBeInTheDocument();
    expect(within(table).getAllByText("Confirmada")).toHaveLength(4);
    expect(within(table).getByText("Parcial")).toBeInTheDocument();
  });
  it("mostra itens mistos no total, prioridade e lista de revisão", () => {
    show();
    count("Análises", "3");
    count("Vulnerabilidades/Melhorias", "2");
    count("Críticas", "1");
    count("Arquivos analisados", "46");
    expect(screen.getByText(/Prioridade dos itens/)).toBeInTheDocument();
    expect(screen.getByText(/Itens de maior prioridade/)).toBeInTheDocument();
    expect(screen.getByText(/Desempenho/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Possível consulta em laço/ })).toBeInTheDocument();
  });
  it("usa categoria sem classificação para sugestão antiga e mantém zero findings na compatibilidade", () => {
    show({ ...analysis, findings: [], suggestions: [{ ...analysis.suggestions![0], severity: null }] });
    count("Vulnerabilidades/Melhorias", "1");
    expect(screen.getByText("Sem classificação")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Possível consulta em laço/ })).toBeInTheDocument();
  });
  it("não apresenta a ausência de itens durante processamento, falha ou resultado parcial", () => {
    show({ ...analysis, findings: [], suggestions: [], status: "PROCESSING", suggestionStatus: "RUNNING" });
    expect(screen.getByText("Verificações em processamento; os totais podem mudar.")).toBeInTheDocument();
    cleanup();
    show({ ...analysis, findings: [], suggestions: [], suggestionStatus: "DEGRADED" });
    expect(screen.getByText(/etapas parciais; isso não confirma ausência/)).toBeInTheDocument();
  });
  it.each([
    ["DEGRADED", /Cobertura parcial ou IA indisponível/], ["NOT_APPLICABLE", /Nenhum método candidato/],
    ["RUNNING", /Os números ainda são parciais/], [undefined, /Cobertura consultiva não informada/],
  ] as const)("explica a cobertura %s", (suggestionStatus, message) => {
    show({ ...analysis, suggestionStatus, suggestions: [] });
    expect(screen.getByText(message)).toBeInTheDocument();
  });
});
