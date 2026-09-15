import { afterEach, expect, it, vi } from "vitest";
import { api } from "./api";
afterEach(() => vi.unstubAllGlobals());
it("envia o login e mantém o token na sessão", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ email: "test@example.com" })),
    );
  vi.stubGlobal("fetch", fetch);
  await api.login("test@example.com", "password");
  expect(fetch.mock.calls[0][1].credentials).toBe("same-origin");
  expect(fetch.mock.calls[0][1].headers.get("Content-Type")).toBe("application/json");
  await api.logout();
});
it("trata respostas não JSON e falhas de rede", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValueOnce(new Response("Bad gateway", { status: 502 }))
      .mockRejectedValueOnce(new TypeError("Failed to fetch")),
  );
  await expect(api.login("test@example.com", "password")).rejects.toMatchObject({
    status: 502,
    message: "Não foi possível concluir a solicitação.",
  });
  await expect(api.login("test@example.com", "password")).rejects.toMatchObject({ status: 0 });
});
