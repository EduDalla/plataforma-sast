import type { Analysis, Session } from "./types";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { ...options, credentials: "same-origin" });
  } catch {
    throw new ApiError(
      0,
      "Não foi possível conectar ao sistema. Verifique sua conexão e tente novamente.",
    );
  }
  const body =
    response.status === 204
      ? undefined
      : await response.json().catch(() => undefined);
  if (!response.ok)
    throw new ApiError(
      response.status,
      body?.detail || body?.title || "Não foi possível concluir a solicitação.",
    );
  return body as T;
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  // Obtém um token atual inclusive após login, logout e expiração da sessão.
  const csrf = await request<{ headerName: string; token: string }>(
    "/api/auth/csrf",
  );
  return request<T>(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export const api = {
  session: () => request<Session>("/api/auth/session"),
  login: (email: string, password: string) =>
    post<Session>("/api/auth/login", { email, password }),
  logout: () => post<void>("/api/auth/logout"),
  create: (repositoryUrl: string, reference: string) =>
    post<Analysis>("/api/analyses", {
      repositoryUrl,
      reference: reference.trim() || null,
    }),
  analysis: (id: string) =>
    request<Analysis>("/api/analyses/" + encodeURIComponent(id)),
};
