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
  it("restaura o dashboard depois de recarregar a página", async () => {
    history.replaceState(null, "", "/dashboard");
    sessionStorage.setItem("sast-last-analysis", "analysis-1");
    vi.mocked(api.analysis).mockResolvedValue(sample);
    render(<App />);
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(api.analysis).toHaveBeenCalledWith("analysis-1");
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
    expect(api.create).toHaveBeenCalledTimes(1);
    expect(api.create).toHaveBeenCalledWith(
      "https://github.com/acme/demo",
      "main",
    );
    resolve(sample);
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(location.pathname).toBe("/dashboard");
    fireEvent.click(screen.getByRole("button", { name: /Ver mais detalhes/ }));
    expect(await screen.findByRole("heading", { name: /Análise concluída/ })).toBeInTheDocument();
    expect(location.pathname).toBe("/analyses/analysis-1");
    fireEvent.click(screen.getByText("Ver detalhes"));
    expect(screen.getByText(sample.findings[0].description)).toBeVisible();
    expect(screen.getByText(sample.findings[0].snippet)).toBeVisible();
  });
  it("trata sucesso sem achados", async () => {
    vi.mocked(api.create).mockResolvedValue({ ...sample, findings: [] });
    render(<App />);
    await submit();
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(document.querySelector(".severity-card")).toHaveTextContent(
      "Nenhuma vulnerabilidade",
    );
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
