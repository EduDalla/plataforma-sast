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
it("envia o cadastro sem persistir a senha", async () => {
  const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ email: "new@example.com" }), { status: 201 }));
  vi.stubGlobal("fetch", fetch);
  await api.register("new@example.com", "strong-password");
  expect(fetch).toHaveBeenCalledWith("/api/auth/register", expect.objectContaining({ method: "POST" }));
  expect(fetch.mock.calls[0][1].body).toBe(JSON.stringify({ email: "new@example.com", password: "strong-password" }));
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

it("não exibe o identificador técnico da requisição na mensagem", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ detail: "Não foi possível analisar o repositório." }), {
        status: 500,
        headers: { "Content-Type": "application/json", "X-Request-Id": "0f5569c6-9ea5-4080-9df1-9e469e312750" },
      }),
    ),
  );

  await expect(api.login("test@example.com", "password")).rejects.toMatchObject({
    status: 500,
    message: "Não foi possível analisar o repositório.",
  });
});
