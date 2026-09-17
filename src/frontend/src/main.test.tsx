import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";
import { api, ApiError } from "./api";
import type { Analysis } from "./types";

vi.mock("./api", async (importOriginal) => {
  const original = await importOriginal<typeof import("./api")>();
  return {
    ...original,
    api: {
      session: vi.fn(),
      login: vi.fn(),
      logout: vi.fn(),
      create: vi.fn(),
      analysis: vi.fn(),
      systems: vi.fn(),
      history: vi.fn(),
    },
  };
});
const sample: Analysis = {
  analysisId: "analysis-1",
  status: "Completed",
  repositoryUrl: "https://github.com/acme/demo",
  reference: "main",
  language: "java",
  filesAnalyzed: 1,
  createdAt: "2026-09-06T12:00:00Z",
  findings: [
    {
      ruleId: "SAST-JAVA-001",
      title: "Credencial hardcoded",
      severity: "Critical",
      cwe: "CWE-798",
      description: "Credencial armazenada no código.",
      fileName: "Example.java",
      line: 4,
      column: 5,
      snippet: 'String password = "123456";',
    },
  ],
};
beforeEach(() => {
  vi.resetAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  history.replaceState(null, "", "/analyses/new");
  vi.mocked(api.session).mockResolvedValue({ email: "test@example.com" });
  vi.mocked(api.systems).mockResolvedValue({ systems: [], page: 0, size: 20, totalSystems: 0, totalAnalyses: 0, totalFindings: 0, totalCritical: 0, totalFiles: 0 });
});
afterEach(cleanup);
async function submit() {
  fireEvent.change(
    await screen.findByLabelText(/Repositório público do GitHub/),
    { target: { value: "https://github.com/acme/demo" } },
  );
  fireEvent.click(screen.getByRole("button", { name: /Iniciar análise/ }));
}

describe("fluxo autenticado da análise", () => {
  it("alterna e restaura o tema escuro sem persistir no carregamento inicial", async () => {
    const { unmount } = render(<App />);
    const shell = await screen.findByRole("banner");
    expect(shell.closest(".app-shell")).toHaveAttribute("data-theme", "light");
    const lightOption = screen.getByRole("button", { name: "Tema claro" });
    const darkOption = screen.getByRole("button", { name: "Tema escuro" });
    expect(lightOption).toHaveAttribute("aria-pressed", "true");
    expect(darkOption).toHaveAttribute("aria-pressed", "false");
    expect(localStorage.getItem("sast-theme")).toBeNull();
    fireEvent.click(darkOption);
    expect(shell.closest(".app-shell")).toHaveAttribute("data-theme", "dark");
    expect(lightOption).toHaveAttribute("aria-pressed", "false");
    expect(darkOption).toHaveAttribute("aria-pressed", "true");
    expect(localStorage.getItem("sast-theme")).toBe("dark");
    unmount();
    render(<App />);
    const restoredShell = await screen.findByRole("banner");
    expect(restoredShell.closest(".app-shell")).toHaveAttribute("data-theme", "dark");
    fireEvent.click(screen.getByRole("button", { name: "Tema claro" }));
    expect(restoredShell.closest(".app-shell")).toHaveAttribute("data-theme", "light");
    expect(localStorage.getItem("sast-theme")).toBe("light");
  });
  it("ignora uma preferência de tema inválida", async () => {
    localStorage.setItem("sast-theme", "sepia");
    render(<App />);
    expect((await screen.findByRole("banner")).closest(".app-shell")).toHaveAttribute("data-theme", "light");
  });
  it("redireciona a URL direta do dashboard e orienta pelo clique", async () => {
    history.replaceState(null, "", "/dashboard");
    render(<App />);
    expect(await screen.findByRole("heading", { name: "Nova análise" })).toBeInTheDocument();
    expect(location.pathname).toBe("/analyses/new");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const dashboardLink = screen.getByRole("link", { name: "Dashboard" });
    fireEvent.click(dashboardLink);
    expect(location.pathname).toBe("/analyses/new");
    expect(screen.getByRole("dialog")).toHaveAccessibleName("Cadastre um sistema primeiro");
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await waitFor(() => expect(document.activeElement).toBe(dashboardLink));
    fireEvent.click(dashboardLink);
    fireEvent.click(screen.getByRole("button", { name: "Cadastrar novo" }));
    expect(location.pathname).toBe("/analyses/new");
    await waitFor(() => expect(document.activeElement).toBe(screen.getByLabelText(/Repositório público do GitHub/)));
  });
  it("libera o dashboard após concluir uma análise", async () => {
    vi.mocked(api.create).mockResolvedValue(sample);
    render(<App />);
    await submit();
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(location.pathname).toBe("/dashboard");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("abre o dashboard individual pelo card do sistema", async () => {
    vi.mocked(api.systems).mockResolvedValue({
      systems: [{ owner: "acme", repositoryName: "demo", repositoryUrl: sample.repositoryUrl, latestCreatedAt: sample.createdAt, totalAnalyses: 2, latest: { analysisId: "analysis-1", reference: "main", createdAt: sample.createdAt, filesAnalyzed: 1, findings: 1 } }],
      page: 0, size: 20, totalSystems: 1, totalAnalyses: 2, totalFindings: 2, totalCritical: 2, totalFiles: 2,
    });
    vi.mocked(api.history).mockResolvedValue({ owner: "acme", repositoryName: "demo", page: 0, size: 20, total: 1, history: [{ analysisId: "analysis-1", reference: "main", createdAt: sample.createdAt, filesAnalyzed: 1, findings: 1 }] });
    vi.mocked(api.analysis).mockResolvedValue(sample);
    history.replaceState(null, "", "/dashboard");
    render(<App />);
    expect(await screen.findByRole("heading", { name: "Histórico de aplicações" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Abrir dashboard de demo" }));
    expect(location.pathname).toBe("/systems/acme/demo");
    expect(await screen.findByText("DASHBOARD DO SISTEMA")).toBeInTheDocument();
  });
  it("mantém o dashboard do sistema visível ao trocar uma execução do histórico", async () => {
    const older = { ...sample, analysisId: "analysis-2", reference: "release", createdAt: "2026-09-05T12:00:00Z", findings: [] };
    vi.mocked(api.history).mockResolvedValue({ owner: "acme", repositoryName: "demo", page: 0, size: 20, total: 2, history: [
      { analysisId: "analysis-1", reference: "main", createdAt: sample.createdAt, filesAnalyzed: 1, findings: 1 },
      { analysisId: "analysis-2", reference: "release", createdAt: older.createdAt, filesAnalyzed: 1, findings: 0 },
    ] });
    vi.mocked(api.analysis).mockImplementation(async (id) => id === "analysis-2" ? older : sample);
    history.replaceState(null, "", "/systems/acme/demo");
    render(<App />);
    expect(await screen.findByText("DASHBOARD DO SISTEMA")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /release/ }));
    expect(screen.getByText("DASHBOARD DO SISTEMA")).toBeInTheDocument();
    expect(await screen.findByText(/Nenhuma vulnerabilidade/)).toBeInTheDocument();
    expect(api.analysis).toHaveBeenCalledWith("analysis-2");
  });
  it("restaura o dashboard depois de recarregar a página", async () => {
    history.replaceState(null, "", "/dashboard");
    sessionStorage.setItem("sast-last-analysis", "analysis-1");
    vi.mocked(api.systems).mockResolvedValue({ systems: [{ owner: "acme", repositoryName: "demo", repositoryUrl: sample.repositoryUrl, latestCreatedAt: sample.createdAt, totalAnalyses: 1, latest: { analysisId: "analysis-1", reference: "main", createdAt: sample.createdAt, filesAnalyzed: 1, findings: 1 } }], page: 0, size: 20, totalSystems: 1, totalAnalyses: 1, totalFindings: 1, totalCritical: 1, totalFiles: 1 });
    vi.mocked(api.analysis).mockResolvedValue(sample);
    render(<App />);
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(api.systems).toHaveBeenCalled();
    expect(location.pathname).toBe("/dashboard");
  });
  it("protege rotas e realiza login sem armazenar senha", async () => {
    vi.mocked(api.session).mockRejectedValue(
      new ApiError(401, "Sessão expirada"),
    );
    vi.mocked(api.login).mockResolvedValue({ email: "test@example.com" });
    render(<App />);
    fireEvent.change(await screen.findByLabelText("E-mail"), {
      target: { value: "test@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Senha"), {
      target: { value: "test-password" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Entrar/ }));
    expect(
      await screen.findByRole("heading", { name: "Nova análise" }),
    ).toBeInTheDocument();
    expect(api.login).toHaveBeenCalledWith("test@example.com", "test-password");
    expect(location.pathname).toBe("/analyses/new");
    expect(localStorage.length).toBe(0);
  });
  it("apresenta falha de login genérica", async () => {
    vi.mocked(api.session).mockRejectedValue(new ApiError(401, ""));
    vi.mocked(api.login).mockRejectedValue(
      new ApiError(401, "E-mail ou senha inválidos"),
    );
    render(<App />);
    fireEvent.change(await screen.findByLabelText("E-mail"), {
      target: { value: "test@example.com" },
    });
    fireEvent.change(screen.getByLabelText("Senha"), {
      target: { value: "wrong" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Entrar/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "E-mail ou senha inválidos",
    );
  });
  it("envia referência, impede duplicação e exibe detalhes do resultado", async () => {
    vi.mocked(api.systems).mockResolvedValue({ systems: [{ owner: "acme", repositoryName: "demo", repositoryUrl: sample.repositoryUrl, latestCreatedAt: sample.createdAt, totalAnalyses: 1, latest: { analysisId: sample.analysisId, reference: sample.reference, createdAt: sample.createdAt, filesAnalyzed: 1, findings: 1 } }], page: 0, size: 20, totalSystems: 1, totalAnalyses: 1, totalFindings: 1, totalCritical: 1, totalFiles: 1 });
    let resolve!: (value: Analysis) => void;
    vi.mocked(api.create).mockReturnValue(
      new Promise((r) => {
        resolve = r;
      }),
    );
    render(<App />);
    fireEvent.change(await screen.findByLabelText(/Referência/), {
      target: { value: "main" },
    });
    await submit();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Análise em andamento",
    );
    expect(screen.getByRole("button", { name: "Analisando…" })).toBeDisabled();
    expect(screen.queryByText("Analisando os arquivos Java do repositório.")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Nova análise" })).toBeInTheDocument();
    expect(api.create).toHaveBeenCalledTimes(1);
    expect(api.create).toHaveBeenCalledWith(
      "https://github.com/acme/demo",
      "main",
    );
    resolve(sample);
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(location.pathname).toBe("/dashboard");
    expect(screen.getByRole("button", { name: "Abrir dashboard de demo" })).toBeInTheDocument();
  });
  it("trata sucesso sem achados", async () => {
    vi.mocked(api.systems).mockResolvedValue({ systems: [{ owner: "acme", repositoryName: "demo", repositoryUrl: sample.repositoryUrl, latestCreatedAt: sample.createdAt, totalAnalyses: 1, latest: { analysisId: sample.analysisId, reference: sample.reference, createdAt: sample.createdAt, filesAnalyzed: 1, findings: 0 } }], page: 0, size: 20, totalSystems: 1, totalAnalyses: 1, totalFindings: 0, totalCritical: 0, totalFiles: 1 });
    vi.mocked(api.create).mockResolvedValue({ ...sample, findings: [] });
    render(<App />);
    await submit();
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Abrir dashboard de demo" })).toBeInTheDocument();
    expect(api.create).toHaveBeenCalledWith("https://github.com/acme/demo", "");
  });
  it("mostra erro legível e permite nova tentativa", async () => {
    vi.mocked(api.create).mockRejectedValue(
      new ApiError(422, "Java inválido na linha 3"),
    );
    render(<App />);
    await submit();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Java inválido na linha 3",
    );
    expect(
      screen.getByRole("button", { name: /Iniciar análise/ }),
    ).toBeEnabled();
  });
  it("restaura análise pela URL e encerra sessão", async () => {
    history.replaceState(null, "", "/analyses/analysis-1");
    vi.mocked(api.analysis).mockResolvedValue(sample);
    vi.mocked(api.logout).mockResolvedValue();
    render(<App />);
    expect(
      await screen.findByRole("heading", { name: /Análise concluída/ }),
    ).toBeInTheDocument();
    expect(api.analysis).toHaveBeenCalledWith("analysis-1");
    fireEvent.click(screen.getByRole("button", { name: /Sair/ }));
    expect(await screen.findByLabelText("Senha")).toBeInTheDocument();
    expect(api.logout).toHaveBeenCalledOnce();
    expect(location.pathname).toBe("/login");
  });
  it("exibe o rastro de taint analysis quando o finding possui taintTrace", async () => {
    history.replaceState(null, "", "/analyses/analysis-1");
    vi.mocked(api.analysis).mockResolvedValue({
      ...sample,
      findings: [
        {
          ruleId: "TAINT-CMDI-001",
          title: "Command injection confirmado por Taint Analysis",
          severity: "Critical",
          cwe: "CWE-78",
          description: "Entrada HTTP não sanitizada alcança execução de comando.",
          fileName: "Controller.java",
          line: 4,
          column: 5,
          snippet: "Runtime.getRuntime().exec(full);",
          taintTrace: {
            engineVersion: "1.0.0",
            source: { kind: "http_param", line: 2, column: 20 },
            steps: [{ kind: "concatenation", line: 3, column: 20 }],
            sink: { kind: "sink", line: 4, column: 5 },
          },
        },
      ],
    });
    render(<App />);
    await screen.findByRole("heading", { name: /Análise concluída/ });
    fireEvent.click(screen.getByText(/Ver detalhes/));
    expect(await screen.findByText("Rastro de Taint Analysis")).toBeInTheDocument();
    expect(screen.getByText("Entrada HTTP")).toBeInTheDocument();
    expect(screen.getByText("Execução de comando")).toBeInTheDocument();
  });
  it("remove resultados e retorna ao login quando a sessão expira", async () => {
    vi.mocked(api.create).mockRejectedValue(
      new ApiError(401, "Sessão ausente ou expirada"),
    );
    render(<App />);
    await submit();
    expect(await screen.findByLabelText("Senha")).toBeInTheDocument();
    expect(
      screen.queryByText(sample.findings[0].snippet),
    ).not.toBeInTheDocument();
    await waitFor(() => expect(location.pathname).toBe("/login"));
  });
});
