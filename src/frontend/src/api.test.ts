import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { project } from "./demo";

beforeEach(() => { sessionStorage.clear(); vi.resetModules(); vi.useFakeTimers(); vi.stubGlobal("fetch", vi.fn()); });
afterEach(() => { expect(fetch).not.toHaveBeenCalled(); vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });

it("protege os dados, restaura a sessão na aba e encerra o acesso no logout", async () => {
  const { api } = await import("./api");
  await expect(api.systems()).rejects.toMatchObject({ status: 401 });
  await expect(api.login("test@gmail.com", "errada")).rejects.toMatchObject({ status: 401 });
  await expect(api.login("other@gmail.com", "123456")).rejects.toMatchObject({ status: 401 });
  await api.login("test@gmail.com", "123456");
  expect(JSON.stringify(sessionStorage)).not.toContain("123456");
  vi.resetModules();
  const restored = (await import("./api")).api;
  expect(await restored.session()).toEqual({ email: "test@gmail.com" });
  await restored.logout();
  await expect(restored.analysis("demo-1")).rejects.toMatchObject({ status: 401 });
});

it("simula o histórico, as etapas, a tarefa e a auditoria sem rede", async () => {
  let { api } = await import("./api");
  await api.login("test@gmail.com", "123456");
  expect(await api.systems()).toMatchObject({ totalSystems: 1, totalAnalyses: 2, totalFindings: 6, resultSummary: { total: 8 } });
  await expect(api.create("https://github.com/other/repo", "")).rejects.toMatchObject({ status: 400 });
  const run = await api.create(project.repositoryUrl, "teste");
  expect(run.stage).toBe("QUEUED");
  vi.resetModules();
  api = (await import("./api")).api;
  for (const stage of ["DOWNLOADING", "DETERMINISTIC", "SEMANTIC", "SUGGESTIONS", "COMPLETED"]) {
    vi.advanceTimersByTime(2000);
    expect((await api.tasks())[0].stage).toBe(stage);
  }
  const result = await api.analysis(run.analysisId);
  expect(result.findings.map(f => f.cwe)).toEqual(["CWE-798", "CWE-78", "CWE-502"]);
  expect(result.resultSummary?.total).toBe(4);
  expect((await api.history("acme", "projeto-teste")).history[0].analysisId).toBe(run.analysisId);
  await api.acknowledgeTask(run.analysisId);
  expect(await api.tasks()).toEqual([]);
  const code = result.findings[0].vulnerabilityCode!;
  await api.updateVulnerabilityStatus(code, "FIXED", "Corrigido no exemplo", 0);
  vi.resetModules();
  api = (await import("./api")).api;
  expect((await api.vulnerability(code)).audit[0]).toMatchObject({ actorEmail: "test@gmail.com", previousStatus: "OPEN", newStatus: "FIXED" });
  await expect(api.updateVulnerabilityStatus(code, "IN_PROGRESS", "Reabrir", 0)).rejects.toMatchObject({ status: 409 });
  expect((await api.analysis("demo-1")).findings[0].vulnerabilityStatus).toBe("FIXED");
});

it("funciona em memória se a aba bloquear o armazenamento", async () => {
  vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error(); });
  vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error(); });
  const { api } = await import("./api");
  await api.login("test@gmail.com", "123456");
  expect((await api.systems()).totalSystems).toBe(1);
});
