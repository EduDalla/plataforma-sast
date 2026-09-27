import { cleanup, render, screen, within } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it } from "vitest";
import { SystemDashboard } from "./components";
import type { Analysis } from "./types";

afterEach(cleanup);
const analysis: Analysis = {
  analysisId: "one", status: "COMPLETED", repositoryUrl: "https://github.com/acme/demo",
  reference: "main", language: "java", filesAnalyzed: 46, createdAt: "2026-09-06T12:00:00Z",
  findings: [], suggestionStatus: "COMPLETED", suggestions: [{
    category: "PERFORMANCE", severity: "High", title: "Possível consulta em laço",
    fileName: "Service.java", line: 12, evidence: "repository.findById(id)",
    rationale: "Consultas repetidas", confidence: 0.8, recommendation: "Avaliar busca em lote",
    limitations: "Confirmar com métricas", model: "local", promptVersion: "v1",
  }],
};
function show(data = analysis) {
  render(<SystemDashboard data={data} history={[]} totalHistory={3} hasMore={false}
    onSelect={() => {}} onBack={() => {}} onMore={() => {}} onOpen={() => {}} />);
}
function count(label: string, expected: string) {
  expect(within(screen.getByText(label).parentElement!).getByText(expected, { selector: "strong" })).toBeInTheDocument();
}
describe("visão geral do sistema", () => {
  it("mostra performance mesmo sem findings e mantém os contadores separados", () => {
    show();
    count("Análises", "3");
    count("Vulnerabilidades", "0");
    count("Arquivos analisados", "46");
    count("Melhorias de performance", "1");
    count("Sugestões de segurança", "0");
    count("Sugestões críticas ou altas", "1");
    count("Arquivos com pontos de atenção", "1");
    expect(screen.queryByText("Avaliar busca em lote")).not.toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Possíveis problemas sugeridos pela IA" })).not.toBeInTheDocument();
    expect(screen.getByText(/Zero achados não garante/)).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: "Tendência de falhas" })).not.toBeInTheDocument();
  });
  it("conta arquivos distintos e sugestões de segurança sem inflar findings", () => {
    show({ ...analysis, findings: [{ ruleId: "rule", title: "Achado", severity: "Critical", cwe: "CWE-798",
      description: "Revisar", fileName: "Service.java", line: 1, column: 1, snippet: "sample" }],
      suggestions: [...analysis.suggestions!, { ...analysis.suggestions![0], category: "SECURITY", severity: null, line: 20 }] });
    count("Vulnerabilidades", "1");
    count("Sugestões de segurança", "1");
    count("Arquivos com pontos de atenção", "1");
    count("Sugestões críticas ou altas", "1");
  });
  it.each([
    ["DEGRADED", /Cobertura parcial ou IA indisponível/],
    ["NOT_APPLICABLE", /Nenhum método candidato/],
    ["RUNNING", /Os números ainda são parciais/],
    [undefined, /Cobertura consultiva não informada/],
  ] as const)("explica a cobertura %s mesmo sem sugestões", (suggestionStatus, message) => {
    show({ ...analysis, suggestionStatus, suggestions: [] });
    expect(screen.getByText(message)).toBeInTheDocument();
    count("Melhorias de performance", "0");
  });
});
