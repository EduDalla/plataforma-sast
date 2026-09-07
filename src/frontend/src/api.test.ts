import { afterEach, expect, it, vi } from "vitest";
import { api } from "./api";
afterEach(() => vi.unstubAllGlobals());
it("obtém CSRF para cada POST e usa cookies da mesma origem", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "first" }),
      ),
    )
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ email: "test@example.com" })),
    )
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "second" }),
      ),
    )
    .mockResolvedValueOnce(new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetch);
  await api.login("test@example.com", "password");
  await api.logout();
  expect(fetch.mock.calls[1][1]).toMatchObject({
    credentials: "same-origin",
    headers: { "X-CSRF-TOKEN": "first" },
  });
  expect(fetch.mock.calls[3][1]).toMatchObject({
    headers: { "X-CSRF-TOKEN": "second" },
  });
});
it("trata respostas não JSON e falhas de rede", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValueOnce(new Response("Bad gateway", { status: 502 }))
      .mockRejectedValueOnce(new TypeError("Failed to fetch")),
  );
  await expect(api.session()).rejects.toMatchObject({
    status: 502,
    message: "Não foi possível concluir a solicitação.",
  });
  await expect(api.session()).rejects.toMatchObject({ status: 0 });
});
