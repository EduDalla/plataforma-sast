import { afterEach, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { AnalysisForm, Login } from "./components";
import { setLocale } from "./i18n";

afterEach(() => setLocale("pt-BR"));

it("traduz cadastro e formulário de análise em inglês, inclusive atributos acessíveis", async () => {
  setLocale("en-US");
  const login = render(<Login onLogin={async () => {}} onRegister={async () => {}} error="" />);
  await waitFor(() => expect(screen.getByText("Welcome back")).toBeTruthy());
  expect(screen.getByPlaceholderText("Your password")).toBeTruthy();
  login.unmount();
  render(<AnalysisForm onSubmit={() => {}} error="" />);
  await waitFor(() => expect(screen.getByRole("heading", { name: "New analysis" })).toBeTruthy());
  expect(screen.getByText("Public GitHub repository")).toBeTruthy();
  expect(document.documentElement.lang).toBe("en-US");
});
