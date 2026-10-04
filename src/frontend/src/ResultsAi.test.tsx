import { cleanup, render, screen, within } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { afterEach, describe, expect, it } from "vitest";
import { Results } from "./components";
import type { Analysis } from "./types";

const base: Analysis = {
  analysisId: "analysis-1", status: "COMPLETED", repositoryUrl: "https://github.com/acme/demo",
  reference: "main", language: "java", filesAnalyzed: 1, createdAt: "2026-09-24T12:00:00Z",
  semanticStatus: "COMPLETED", suggestionStatus: "COMPLETED",
  findings: [{ ruleId: "SAST-JAVA-002", title: "Runtime.exec", severity: "Critical", cwe: "CWE-78",
    description: "Execução de comando", fileName: "Example.java", line: 4, column: 5,
    snippet: "Runtime.getRuntime().exec(input);", aiAssessment: { model: "llama3.2:3b", promptVersion: "1",
      confidence: 0.8, suggestedSeverity: "Medium", likelyFalsePositive: true, rationale: "Há validação anterior.",
      remediation: "Valide a entrada.", evidence: ["A entrada chega ao sink."], limitations: "Chamadas externas não resolvidas." } }],
};
afterEach(cleanup);

describe("lista unificada de vulnerabilidades e melhorias", () => {
  it("mantém o carregamento durante o polling", () => {
    render(<Results data={{ ...base, status: "PROCESSING", findings: [], suggestions: [], semanticStatus: "RUNNING", suggestionStatus: "RUNNING" }} />);
    expect(screen.getByRole("heading", { name: "Análise em andamento" })).toBeInTheDocument();
    expect(screen.getByText("Aguardando resultados")).toBeInTheDocument();
  });
  it("mantém a avaliação da IA dentro do finding e não troca sua prioridade", () => {
    render(<Results data={base} />);
    const item = screen.getByText("Runtime.exec").closest("details")!;
    expect(item).toHaveTextContent("Crítica");
    expect(item).toHaveTextContent("Regra");
    expect(within(item).getByRole("region", { name: "Avaliação consultiva da IA" })).toHaveTextContent("Severidade sugerida:Média");
    expect(item).toHaveTextContent("A entrada chega ao sink.");
    expect(document.querySelector(".stats")!).toHaveTextContent("Vulnerabilidades/Melhorias1");
  });
  it("contabiliza sugestão independente no mesmo arquivo sem fundi-la com finding", () => {
    const data: Analysis = { ...base, suggestions: [{ category: "PERFORMANCE", severity: "High", title: "Possível N+1",
      fileName: "Example.java", line: 4, evidence: "repository.findById(id)", rationale: "Consulta no loop.",
      confidence: 0.8, recommendation: "Buscar em lote.", limitations: "Validar por métricas.", model: "llama3.2:3b", promptVersion: "1" }] };
    render(<Results data={data} />);
    expect(document.querySelectorAll("details.finding")).toHaveLength(2);
    expect(screen.getByText("Possível N+1").closest("details")).toHaveTextContent("Alta");
    expect(document.querySelector(".stats")!).toHaveTextContent("Vulnerabilidades/Melhorias2");
    expect(screen.getByText("Evidência").parentElement).toHaveTextContent("repository.findById(id)");
  });
  it("marca sugestões antigas sem severidade como sem classificação", () => {
    const data: Analysis = { ...base, findings: [], suggestions: [{ category: "SECURITY", severity: null, title: "Revisar validação",
      fileName: "Example.java", line: 4, evidence: "entrada", rationale: "Revisar fluxo.", confidence: 0.6,
      recommendation: "Validar dados.", limitations: "Análise parcial.", model: "llama3.2:3b", promptVersion: "1" }] };
    render(<Results data={data} />);
    expect(screen.getByText("Sem classificação")).toBeInTheDocument();
    expect(screen.queryByText("Nenhuma vulnerabilidade ou melhoria encontrada")).not.toBeInTheDocument();
  });
  it("diferencia execução parcial sem itens da ausência confirmada", () => {
    render(<Results data={{ ...base, findings: [], suggestions: [], suggestionStatus: "DEGRADED" }} />);
    expect(screen.getByText(/Resultado parcial/)).toBeInTheDocument();
    expect(screen.getByText("Nenhum item disponível nesta etapa parcial")).toBeInTheDocument();
  });
});
