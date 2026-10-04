import { act, cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AnalysisNotificationCenter } from "./AnalysisNotificationCenter";
import type { AnalysisNotification } from "./AnalysisNotificationCenter";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("central de notificações da análise", () => {
  it("exibe três notificações e libera espaço para a quarta quando a primeira termina", async () => {
    vi.useFakeTimers();
    function Example() {
      const [notifications, setNotifications] = useState<AnalysisNotification[]>([
        { id: "one", title: "Primeira", message: "Concluída", tone: "success", final: true },
        { id: "two", title: "Segunda", message: "Em andamento", tone: "progress" },
        { id: "three", title: "Terceira", message: "Em andamento", tone: "progress" },
        { id: "four", title: "Quarta", message: "Aguardando espaço", tone: "progress" },
      ]);
      return <AnalysisNotificationCenter notifications={notifications} announcement="" onDismiss={(id) => setNotifications((items) => items.filter((item) => item.id !== id))} />;
    }

    render(<Example />);
    expect(screen.getByText("Primeira")).toBeInTheDocument();
    expect(screen.getByText("Terceira")).toBeInTheDocument();
    expect(screen.queryByText("Quarta")).not.toBeInTheDocument();

    await act(async () => { await vi.advanceTimersByTimeAsync(2500); });
    expect(screen.getByText("Primeira").closest("li")).toHaveClass("is-exiting");
    await act(async () => { await vi.advanceTimersByTimeAsync(350); });

    expect(screen.queryByText("Primeira")).not.toBeInTheDocument();
    expect(screen.getByText("Quarta")).toBeInTheDocument();
  });
});
