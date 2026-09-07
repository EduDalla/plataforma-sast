import type { Analysis, LoginResponse, Session } from "./types";

let accessToken: string | null = null;

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
    const headers = new Headers(options.headers);
    if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
    response = await fetch(path, {
      ...options,
      headers,
      credentials: "same-origin",
    });
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
  if (!response.ok) {
    if (response.status === 401) accessToken = null;
    throw new ApiError(
      response.status,
      body?.detail || body?.title || "Não foi possível concluir a solicitação.",
    );
  }
  return body as T;
}

export const api = {
  session: async () => {
    if (!accessToken) throw new ApiError(401, "Sessão ausente ou expirada");
    return request<Session>("/api/auth/session");
  },
  login: async (email: string, password: string): Promise<Session> => {
    const response = await request<LoginResponse>(
      "/api/auth/login",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      },
    );
    accessToken = response.accessToken;
    return response;
  },
  logout: async () => {
    accessToken = null;
  },
  create: (repositoryUrl: string, reference: string) =>
    request<Analysis>("/api/analyses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        repositoryUrl,
        reference: reference.trim() || null,
      }),
    }),
  analysis: (id: string) =>
    request<Analysis>("/api/analyses/" + encodeURIComponent(id)),
};
