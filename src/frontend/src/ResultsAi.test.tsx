import { cleanup, render, screen, within } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it } from "vitest";
import { Results } from "./components";
import type { Analysis } from "./types";

const base: Analysis = {
  analysisId: "analysis-1",
  status: "Completed",
  repositoryUrl: "https://github.com/acme/demo",
  reference: "main",
  language: "java",
  filesAnalyzed: 1,
  createdAt: "2026-09-24T12:00:00Z",
  semanticStatus: "COMPLETED",
  findings: [{
    ruleId: "SAST-JAVA-002",
    title: "Runtime.exec",
    severity: "Critical",
    cwe: "CWE-78",
    description: "Execução de comando",
    fileName: "Example.java",
    line: 4,
    column: 5,
    snippet: "Runtime.getRuntime().exec(input);",
    aiAssessment: {
      model: "llama3.2:3b",
      promptVersion: "1",
      confidence: 0.8,
      suggestedSeverity: "Medium",
      likelyFalsePositive: true,
      rationale: "Há validação anterior.",
      remediation: "Valide a entrada antes da execução.",
    },
  }],
};
afterEach(cleanup);

describe("resultado da análise semântica", () => {
  it("mostra a sugestão separada da severidade e das contagens determinísticas", () => {
    render(<Results data={base} />);
    expect(screen.getByText("Avaliação da IA concluída para todos os achados.")).toBeInTheDocument();
    const suggestion = screen.getByRole("region", { name: "Sugestão da IA" });
    expect(within(suggestion).getByText(/Severidade sugerida:/)).toHaveTextContent("Média");
    expect(within(suggestion).getByText(/Provável falso positivo:/)).toHaveTextContent("Sim");
    expect(screen.getByText("Runtime.exec").closest("details")).toHaveTextContent("Crítica");
    expect(screen.getByText("Críticas").parentElement).toHaveTextContent("1");
  });

  it("mantém achado sem avaliação visível quando o Ollama falha", () => {
    render(<Results data={{ ...base, semanticStatus: "DEGRADED", findings: [{ ...base.findings[0], aiAssessment: null }] }} />);
    expect(screen.getByText(/Avaliação da IA parcial ou indisponível/)).toBeInTheDocument();
    expect(screen.getByText("Runtime.exec")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Sugestão da IA" })).not.toBeInTheDocument();
  });
});
