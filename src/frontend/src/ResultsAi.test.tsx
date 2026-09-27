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
      risk: "A entrada ainda pode alcançar o processo.",
      evidence: ["Linha 4: argumento chega ao sink."],
      falsePositiveReason: "A validação não cobre todos os caminhos.",
      limitations: "Chamadas externas não foram resolvidas.",
      recommendations: ["Use uma allowlist.", "Evite shell interpretado."],
    },
  }],
};
afterEach(cleanup);

describe("resultado da análise semântica", () => {
  it("mostra o carregamento e mantém as áreas vazias durante o polling", () => {
    render(<Results data={{ ...base, status: "PROCESSING", findings: [], suggestions: [], semanticStatus: "RUNNING", suggestionStatus: "RUNNING" }} />);
    expect(screen.getByRole("status", { name: "Análise em andamento" })).toBeInTheDocument();
    expect(screen.getByText("Aguardando resultados")).toBeInTheDocument();
    expect(screen.getByText("Aguardando resultados das sugestões consultivas…")).toBeInTheDocument();
  });

  it("remove o indicador quando a análise termina ou falha", () => {
    const { rerender } = render(<Results data={{ ...base, status: "PROCESSING" }} />);
    expect(screen.getByRole("status", { name: "Análise em andamento" })).toBeInTheDocument();
    rerender(<Results data={{ ...base, status: "COMPLETED" }} />);
    expect(screen.queryByRole("status", { name: "Análise em andamento" })).not.toBeInTheDocument();
    rerender(<Results data={{ ...base, status: "FAILED", failureStage: "SEMANTIC", failureMessage: "Falha controlada" }} />);
    expect(screen.queryByRole("status", { name: "Análise em andamento" })).not.toBeInTheDocument();
  });

  it("mostra a sugestão separada da severidade e das contagens determinísticas", () => {
    render(<Results data={base} />);
    expect(screen.getByText("Avaliação da IA concluída para todos os achados.")).toBeInTheDocument();
    const suggestion = screen.getByRole("region", { name: "Sugestão da IA" });
    expect(within(suggestion).getByText(/Severidade sugerida:/)).toHaveTextContent("Média");
    expect(within(suggestion).getByText(/Provável falso positivo:/)).toHaveTextContent("Sim");
    expect(screen.getByText("Runtime.exec").closest("details")).toHaveTextContent("Crítica");
    expect(screen.getByText("Críticas").parentElement).toHaveTextContent("1");
    expect(within(suggestion).getByText("A entrada ainda pode alcançar o processo.")).toBeInTheDocument();
    expect(within(suggestion).getByText("Linha 4: argumento chega ao sink.")).toBeInTheDocument();
  });

  it("mantém achado sem avaliação visível quando o Ollama falha", () => {
    render(<Results data={{ ...base, semanticStatus: "DEGRADED", findings: [{ ...base.findings[0], aiAssessment: null }] }} />);
    expect(screen.getByText(/Avaliação da IA parcial ou indisponível/)).toBeInTheDocument();
    expect(screen.getByText("Runtime.exec")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Sugestão da IA" })).not.toBeInTheDocument();
  });

  it("mostra hipótese N+1 mesmo sem findings e mantém a contagem de vulnerabilidades em zero", () => {
    render(<Results data={{ ...base, findings: [], semanticStatus: "NOT_APPLICABLE",
      suggestionStatus: "COMPLETED", suggestions: [{
        category: "PERFORMANCE", severity: "Critical", title: "Possível N+1", fileName: "Orders.java", line: 3,
        evidence: "repository.findById(id)", rationale: "Consulta dentro do loop.", confidence: 0.8,
        recommendation: "Busque em lote.", limitations: "Confirme em execução.",
        model: "llama3.2:3b", promptVersion: "1",
      }] }} />);
    const section = screen.getByRole("region", { name: "Possíveis problemas sugeridos pela IA" });
    expect(within(section).getByText("Possível N+1")).toBeInTheDocument();
    expect(within(section).getAllByRole("heading", { name: /Crítica/ })[0]).toHaveTextContent("1");
    expect(screen.getByText("Vulnerabilidades").parentElement).toHaveTextContent("0");
    expect(screen.getByText("Nenhuma vulnerabilidade encontrada")).toBeInTheDocument();
  });
});
