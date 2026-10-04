import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "./api";
import { AnalysisNotificationCenter } from "./AnalysisNotificationCenter";
import type { AnalysisNotification } from "./AnalysisNotificationCenter";
import {
  AnalysisForm,
  AnalysisFailureModal,
  Dashboard,
  DashboardPrompt,
  ErrorMessage,
  Login,
  Processing,
  Results,
  Shield,
  SystemDashboard,
} from "./components";
import type { Analysis, HistoryEntry, Session, SystemsPage } from "./types";
import { priorityLabels, summarizeResults, unifiedResults } from "./resultModel";

type Theme = "light" | "dark";
const THEME_STORAGE_KEY = "sast-theme";
const LAST_ANALYSIS_KEY = "sast-last-analysis";

function analysisStageTitle(stage?: Analysis["stage"] | string) {
  switch (stage) {
    case "DOWNLOADING": return "Acessando repositório";
    case "DETERMINISTIC": return "Analisando arquivos Java";
    case "SEMANTIC": return "Avaliando vulnerabilidades/melhorias identificadas";
    case "SUGGESTIONS": return "Buscando vulnerabilidades/melhorias adicionais";
    case "COMPLETED": return "Análise concluída";
    case "FAILED": return "Análise interrompida";
    default: return "Preparando análise";
  }
}

function analysisStageMessage(data: Analysis) {
  if (data.stage === "DETERMINISTIC" && data.filesTotal != null) {
    const total = data.filesTotal;
    const processed = data.filesProcessed ?? 0;
    const milestone = total > 0 ? Math.floor((processed / total) * 10) * 10 : 0;
    return `Arquivos Java processados: ${processed} de ${total} (${milestone}% concluído).`;
  }
  if (data.stage === "DOWNLOADING") return "Obtendo os arquivos públicos do repositório com segurança.";
  if (data.stage === "SEMANTIC") return "As vulnerabilidades e melhorias das regras já estão disponíveis enquanto a IA avalia os achados.";
  if (data.stage === "SUGGESTIONS") return "A IA está verificando métodos candidatos para vulnerabilidades e melhorias adicionais.";
  return "A análise está avançando. Os resultados aparecem nesta página.";
}

function semanticNotification(status: Analysis["semanticStatus"]): Pick<AnalysisNotification, "title" | "message" | "tone" | "final"> | null {
  if (status === "PENDING" || status === "RUNNING")
    return { title: "Avaliação de vulnerabilidades/melhorias", message: "A avaliação está em andamento; os itens das regras já podem ser consultados.", tone: "progress" };
  if (status === "COMPLETED")
    return { title: "Avaliação concluída", message: "Todos os achados candidatos foram avaliados.", tone: "success", final: true };
  if (status === "DEGRADED")
    return { title: "Avaliação parcial", message: "Alguns achados não receberam avaliação; os resultados das regras estão disponíveis.", tone: "warning", final: true };
  return null;
}

function suggestionNotification(status: Analysis["suggestionStatus"]): Pick<AnalysisNotification, "title" | "message" | "tone" | "final"> | null {
  if (status === "PENDING" || status === "RUNNING")
    return { title: "Verificação de vulnerabilidades/melhorias adicionais", message: "Verificando métodos candidatos. A lista será atualizada automaticamente.", tone: "progress" };
  if (status === "COMPLETED")
    return { title: "Verificação de vulnerabilidades/melhorias concluída", message: "A verificação dos métodos candidatos terminou.", tone: "success", final: true };
  if (status === "DEGRADED")
    return { title: "Verificação parcial de vulnerabilidades/melhorias", message: "Alguns métodos candidatos não foram avaliados.", tone: "warning", final: true };
  return null;
}

function readTheme(): Theme {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY) === "dark" ? "dark" : "light";
  } catch {
    return "light";
  }
}

function readLastAnalysisId(): string | null {
  try {
    return sessionStorage.getItem(LAST_ANALYSIS_KEY);
  } catch {
    return null;
  }
}

function storeLastAnalysisId(analysisId: string | null) {
  try {
    if (analysisId) sessionStorage.setItem(LAST_ANALYSIS_KEY, analysisId);
    else sessionStorage.removeItem(LAST_ANALYSIS_KEY);
  } catch {
    // A restauração é opcional quando o armazenamento não está disponível.
  }
}

export function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);
  const [path, setPath] = useState(location.pathname);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [result, setResult] = useState<Analysis>();
  const [systems, setSystems] = useState<SystemsPage>();
  const [systemHistory, setSystemHistory] = useState<HistoryEntry[]>([]);
  const [systemHistoryTotal, setSystemHistoryTotal] = useState(0);
  const [systemHistoryPage, setSystemHistoryPage] = useState(0);
  const [systemKey, setSystemKey] = useState<{ owner: string; repository: string }>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [systemUpdating, setSystemUpdating] = useState(false);
  const [theme, setTheme] = useState<Theme>(readTheme);
  const [dashboardPromptOpen, setDashboardPromptOpen] = useState(false);
  const [analysisFailureOpen, setAnalysisFailureOpen] = useState(false);
  const [analysisNotifications, setAnalysisNotifications] = useState<AnalysisNotification[]>([]);
  const [notificationAnnouncement, setNotificationAnnouncement] = useState("");
  const dashboardLinkRef = useRef<HTMLAnchorElement>(null);
  const pending = useRef(false);
  const generation = useRef(0);
  const observedAnalysis = useRef<{ analysisId: string; status: Analysis["status"]; stage?: Analysis["stage"]; semanticStatus?: Analysis["semanticStatus"]; suggestionStatus?: Analysis["suggestionStatus"] } | undefined>(undefined);
  const previousPath = useRef(path);
  const upsertNotification = useCallback((notification: AnalysisNotification) => {
    setAnalysisNotifications((current) => {
      const index = current.findIndex((item) => item.id === notification.id);
      if (index < 0) return [...current, notification];
      const next = [...current];
      next[index] = notification;
      return next;
    });
  }, []);
  const dismissNotification = useCallback((id: string) => {
    setAnalysisNotifications((current) => current.filter((item) => item.id !== id));
  }, []);
  const clearAnalysisNotifications = useCallback(() => {
    setAnalysisNotifications([]);
    setNotificationAnnouncement("");
  }, []);
  useEffect(() => {
    const wasAnalysis = /^\/analyses\/[^/]+$/.test(previousPath.current);
    const isAnalysis = /^\/analyses\/[^/]+$/.test(path);
    if (wasAnalysis && !isAnalysis) {
      clearAnalysisNotifications();
      observedAnalysis.current = undefined;
    }
    previousPath.current = path;
  }, [path, clearAnalysisNotifications]);
  const loadSystems = useCallback(async (page = 0) => {
    if (typeof api.systems !== "function") return undefined;
    const value = await api.systems(page);
    setSystems(value);
    return value;
  }, []);
  const navigate = useCallback((next: string, replace = false) => {
    if (replace) history.replaceState(null, "", next);
    else history.pushState(null, "", next);
    setPath(next);
  }, []);
  useEffect(() => {
    const pop = () => {
      generation.current++;
      setPath(location.pathname);
      setError("");
      setResult(undefined);
      setSystemHistory([]);
      setSystemHistoryTotal(0);
      setSystemHistoryPage(0);
      setSystemKey(undefined);
    };
    addEventListener("popstate", pop);
    return () => removeEventListener("popstate", pop);
  }, []);
  useEffect(() => {
    let active = true;
    api
      .session()
      .then((user) => {
        if (!active) return;
        setSession(user);
        if (typeof api.systems !== "function") {
          if (location.pathname === "/login" || location.pathname === "/") navigate("/analyses/new", true);
          return;
        }
        loadSystems().then((value) => {
          if (!active) return;
          if (location.pathname === "/login" || location.pathname === "/")
            navigate(value?.totalSystems ? "/dashboard" : "/analyses/new", true);
          else if (location.pathname === "/dashboard" && !value?.totalSystems)
            navigate("/analyses/new", true);
        }).catch(failure);
      })
      .catch((e) => {
        if (!active) return;
        if (!(e instanceof ApiError && e.status === 401)) setError(e.message);
        navigate(location.pathname === "/cadastro" ? "/cadastro" : "/login", true);
      })
      .finally(() => {
        if (active) setReady(true);
      });
    return () => {
      active = false;
    };
  }, [navigate, loadSystems]);
  const failure = useCallback(
    (e: unknown) => {
      setError(
        e instanceof Error
          ? e.message
          : "Não foi possível concluir a solicitação.",
      );
      if (e instanceof ApiError && e.status === 401) {
        generation.current++;
        setLoading(false);
        setResult(undefined);
        setSession(null);
        navigate("/login", true);
      }
    },
    [navigate],
  );
  useEffect(() => {
    if (!session) return;
    const systemMatch = path.match(/^\/systems\/([^/]+)\/([^/]+)$/);
    if (systemMatch) {
      const owner = decodeURIComponent(systemMatch[1]);
      const repository = decodeURIComponent(systemMatch[2]);
      if (systemKey?.owner === owner && systemKey.repository === repository && result) return;
      setSystemKey({ owner, repository });
      let active = true;
      setSystemUpdating(true);
      api.history(owner, repository).then((page) => {
        if (!active) return;
        setSystemHistory(page.history);
        setSystemHistoryTotal(page.total);
        setSystemHistoryPage(0);
        const selected = page.history[0];
        if (!selected) throw new Error("Este sistema ainda não possui análises.");
        return api.analysis(selected.analysisId);
      }).then((value) => {
        if (active && value) setResult(value);
      }).catch((e) => active && failure(e)).finally(() => active && setSystemUpdating(false));
      return () => { active = false; };
    }
    if (path === "/dashboard") {
      if (systems) return;
      if (typeof api.systems === "function") {
        let active = true;
        setLoading(true);
        loadSystems().catch((e) => active && failure(e)).finally(() => active && setLoading(false));
        return () => { active = false; };
      }
      if (result) return;
      const analysisId = readLastAnalysisId();
      if (!analysisId) {
        navigate("/analyses/new", true);
        return;
      }
      let active = true;
      setLoading(true);
      api
        .analysis(analysisId)
        .then((value) => {
          if (active) {
            setResult(value);
            setLoading(false);
          }
        })
        .catch((e) => {
          if (!active) return;
          storeLastAnalysisId(null);
          failure(e);
          if (!(e instanceof ApiError && e.status === 401))
            navigate("/analyses/new", true);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
      return () => {
        active = false;
      };
    }
    const match = path.match(/^\/analyses\/([^/]+)$/);
    if (!match || match[1] === "new") {
      if (path !== "/analyses/new") navigate("/analyses/new", true);
      return;
    }
    if (result?.analysisId === match[1]) return;
    let active = true;
    setLoading(true);
    api
      .analysis(match[1])
      .then((value) => {
        if (active) {
          setResult(value);
          storeLastAnalysisId(value.analysisId);
          setLoading(false);
        }
      })
      .catch((e) => {
        if (active) failure(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [path, session, systems, result?.analysisId, navigate, failure, loadSystems]);
  async function login(email: string, password: string) {
    setError("");
    setSuccess("");
    try {
      const value = await api.login(email, password);
      setSession(value);
      if (typeof api.systems !== "function") navigate("/analyses/new", true);
      else {
        const page = await loadSystems();
        navigate(page?.totalSystems ? "/dashboard" : "/analyses/new", true);
      }
    } catch (e) {
      failure(e);
    }
  }
  async function register(email: string, password: string) {
    setError("");
    try {
      await api.register(email, password);
      setSuccess("Cadastro realizado com sucesso. Faça login para continuar.");
      navigate("/login", true);
    } catch (e) {
      failure(e);
    }
  }
  async function create(url: string, reference: string) {
    if (pending.current) return;
    pending.current = true;
    setSubmitting(true);
    setError("");
    setResult(undefined);
    observedAnalysis.current = undefined;
    setNotificationAnnouncement("Preparando análise. Solicitando a análise do repositório.");
    upsertNotification({ id: "analysis-submission", title: "Preparando análise", message: "Solicitando a análise do repositório…", tone: "progress" });
    const operation = ++generation.current;
    try {
      const data = await api.create(url, reference);
      if (generation.current === operation) {
        dismissNotification("analysis-submission");
        setResult(data);
        storeLastAnalysisId(data.analysisId);
        if (data.status === "COMPLETED" || data.status === "Completed") {
          upsertNotification({ id: `analysis:${data.analysisId}:result`, title: "Análise concluída", message: "Os resultados já estão disponíveis.", tone: "success", final: true });
          setNotificationAnnouncement("Análise concluída. Os resultados já estão disponíveis.");
        } else if (data.status === "FAILED") {
          upsertNotification({ id: `analysis:${data.analysisId}:result`, title: "Análise interrompida", message: "Não foi possível concluir a análise. Consulte os detalhes na página.", tone: "error", final: true });
          setNotificationAnnouncement("Análise interrompida. Consulte os detalhes na página.");
        }
        navigate(`/analyses/${data.analysisId}`);
      }
    } catch (e) {
      if (generation.current === operation) {
        const message = e instanceof Error ? e.message : "Não foi possível concluir a solicitação.";
        upsertNotification({ id: "analysis-submission", title: "Não foi possível iniciar a análise", message, tone: "error", final: true });
        setNotificationAnnouncement(`Não foi possível iniciar a análise. ${message}`);
        failure(e);
      }
    } finally {
      pending.current = false;
      setSubmitting(false);
    }
  }

  useEffect(() => {
    if (!result) return;
    const previous = observedAnalysis.current;
    const sameAnalysis = previous?.analysisId === result.analysisId;
    const announcements: string[] = [];
    if (result.status === "PROCESSING") {
      const stage = result.stage || "QUEUED";
      if (!sameAnalysis || previous?.stage !== stage) {
        if (sameAnalysis && previous?.stage) {
          const previousId = `analysis:${result.analysisId}:stage:${previous.stage}`;
          upsertNotification({ id: previousId, title: analysisStageTitle(previous.stage), message: "Etapa concluída.", tone: "success", final: true });
        }
        upsertNotification({
          id: `analysis:${result.analysisId}:stage:${stage}`,
          title: analysisStageTitle(stage),
          message: analysisStageMessage(result),
          tone: "progress",
        });
        announcements.push(`${analysisStageTitle(stage)}. ${analysisStageMessage(result)}`);
      } else {
        upsertNotification({
          id: `analysis:${result.analysisId}:stage:${stage}`,
          title: analysisStageTitle(stage),
          message: analysisStageMessage(result),
          tone: "progress",
        });
      }
      const semantic = semanticNotification(result.semanticStatus);
      if (semantic) {
        upsertNotification({ id: `analysis:${result.analysisId}:semantic`, ...semantic });
        if (!sameAnalysis || previous?.semanticStatus !== result.semanticStatus)
          announcements.push(`${semantic.title}. ${semantic.message}`);
      }
      const suggestions = suggestionNotification(result.suggestionStatus);
      if (suggestions) {
        upsertNotification({ id: `analysis:${result.analysisId}:suggestions`, ...suggestions });
        if (!sameAnalysis || previous?.suggestionStatus !== result.suggestionStatus)
          announcements.push(`${suggestions.title}. ${suggestions.message}`);
      }
    } else if (sameAnalysis && previous.status === "PROCESSING") {
      const stageId = `analysis:${result.analysisId}:stage:${previous.stage || "QUEUED"}`;
      const unifiedSummary = result.resultSummary ?? summarizeResults(unifiedResults(result.findings, result.suggestions ?? []));
      const highest = unifiedSummary.highestPriority ? priorityLabels[unifiedSummary.highestPriority] : "Sem classificação";
      const completionMessage = unifiedSummary.total
        ? `${unifiedSummary.total} vulnerabilidade(s)/melhoria(s) identificada(s). Maior prioridade: ${highest}.`
        : "Nenhuma vulnerabilidade ou melhoria identificada nas verificações concluídas.";
      upsertNotification({
        id: stageId,
        title: result.status === "FAILED" ? "Análise interrompida" : "Análise concluída",
        message: result.status === "FAILED" ? "A análise foi interrompida. Consulte os detalhes e tente novamente." : completionMessage,
        tone: result.status === "FAILED" ? "error" : "success",
        final: true,
      });
      announcements.push(result.status === "FAILED" ? "Análise interrompida. Consulte os detalhes e tente novamente." : `Análise concluída. ${completionMessage}`);
      const semantic = semanticNotification(result.semanticStatus);
      if (result.status === "FAILED" && (result.semanticStatus === "PENDING" || result.semanticStatus === "RUNNING"))
        upsertNotification({ id: `analysis:${result.analysisId}:semantic`, title: "Avaliação interrompida", message: "A avaliação complementar foi encerrada junto com a análise.", tone: "error", final: true });
      else if (semantic && result.semanticStatus !== "PENDING" && result.semanticStatus !== "RUNNING")
        upsertNotification({ id: `analysis:${result.analysisId}:semantic`, ...semantic });
      const suggestions = suggestionNotification(result.suggestionStatus);
      if (result.status === "FAILED" && (result.suggestionStatus === "PENDING" || result.suggestionStatus === "RUNNING"))
        upsertNotification({ id: `analysis:${result.analysisId}:suggestions`, title: "Verificação de sugestões interrompida", message: "A etapa consultiva foi encerrada junto com a análise.", tone: "error", final: true });
      else if (suggestions && result.suggestionStatus !== "PENDING" && result.suggestionStatus !== "RUNNING")
        upsertNotification({ id: `analysis:${result.analysisId}:suggestions`, ...suggestions });
    }
    if (announcements.length > 0) setNotificationAnnouncement(announcements.join(" "));
    observedAnalysis.current = { analysisId: result.analysisId, status: result.status, stage: result.stage, semanticStatus: result.semanticStatus, suggestionStatus: result.suggestionStatus };
  }, [result, upsertNotification]);

  useEffect(() => {
    const match = path.match(/^\/analyses\/([^/]+)$/);
    if (!session || !match || match[1] === "new") return;
    let active = true;
    let timer: number | undefined;
    const poll = async () => {
      try {
        const value = await api.analysis(match[1]);
        if (!active) return;
        setResult(value);
        if (value.status === "PROCESSING") {
          timer = window.setTimeout(poll, 2000);
        }
      } catch (e) {
        if (active) failure(e);
      }
    };
    if (result?.analysisId === match[1] && result.status === "PROCESSING")
      timer = window.setTimeout(poll, 2000);
    return () => {
      active = false;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [path, session, result?.analysisId, result?.status, failure]);
  useEffect(() => {
    if (result?.status === "FAILED") setAnalysisFailureOpen(true);
  }, [result?.analysisId, result?.status]);
  async function logout() {
    if (loading) return;
    try {
      await api.logout();
      generation.current++;
      setSession(null);
      setResult(undefined);
      clearAnalysisNotifications();
      observedAnalysis.current = undefined;
      setSystems(undefined);
      storeLastAnalysisId(null);
      setError("");
      navigate("/login", true);
    } catch (e) {
      failure(e);
    }
  }
  function newAnalysis() {
    setError("");
    setResult(undefined);
    clearAnalysisNotifications();
    observedAnalysis.current = undefined;
    setSubmitting(false);
    setAnalysisFailureOpen(false);
    setDashboardPromptOpen(false);
    navigate("/analyses/new");
    requestAnimationFrame(() => document.getElementById("repository-url")?.focus());
  }
  function dashboard() {
    setError("");
    if (result || systems?.totalSystems) navigate("/dashboard");
    else setDashboardPromptOpen(true);
  }
  function openSystem(owner: string, repository: string) {
    setError("");
    setResult(undefined);
    setSystemHistory([]);
    setSystemHistoryTotal(0);
    setSystemHistoryPage(0);
    navigate(`/systems/${encodeURIComponent(owner)}/${encodeURIComponent(repository)}`);
  }
  function loadMoreSystemHistory() {
    if (!systemKey) return;
    const nextPage = systemHistoryPage + 1;
    setSystemUpdating(true);
    api.history(systemKey.owner, systemKey.repository, nextPage).then((page) => {
      setSystemHistory((current) => [...current, ...page.history]);
      setSystemHistoryPage(nextPage);
    }).catch(failure).finally(() => setSystemUpdating(false));
  }
  function changeSystemsPage(page: number) {
    setLoading(true);
    loadSystems(page).catch(failure).finally(() => setLoading(false));
  }
  function selectTheme(next: Theme) {
    setTheme(next);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // Preferência visual é opcional quando o armazenamento não está disponível.
    }
  }
  if (!ready)
    return (
      <main className="app-shell startup" data-theme={theme}>
        <Processing restoring />
      </main>
    );
  if (!session)
    return (
      <main>
        <Login
          onLogin={login}
          onRegister={register}
          initialRegistering={path === "/cadastro"}
          onToggleMode={() => navigate(path === "/cadastro" ? "/login" : "/cadastro", true)}
          success={success}
          error={error}
        />
      </main>
    );
  return (
    <div className="app-shell" data-theme={theme}>
      <header className="topbar">
        <a
          className="brand"
          href="/dashboard"
          onClick={(e) => {
            e.preventDefault();
            if (!loading) dashboard();
          }}
        >
          <Shield />
          <strong>SAST</strong>
          <span>Code security</span>
        </a>
        <nav className="topnav" aria-label="Navegação principal">
          <a ref={dashboardLinkRef} className={path === "/dashboard" ? "active" : ""} href="/dashboard" onClick={(event) => { event.preventDefault(); dashboard(); }}>Dashboard</a>
          <a className={path === "/analyses/new" ? "active" : ""} href="/analyses/new" onClick={(event) => { event.preventDefault(); newAnalysis(); }}>Nova análise</a>
        </nav>
        <div className="account">
          <span>{session.email}</span>
          <div className="theme-switcher" role="group" aria-label="Tema da interface">
            <button
              className="theme-option"
              type="button"
              aria-label="Tema claro"
              aria-pressed={theme === "light"}
              onClick={() => selectTheme("light")}
            >
              <span aria-hidden="true">☀</span>
              <span className="theme-option-label">Claro</span>
            </button>
            <button
              className="theme-option"
              type="button"
              aria-label="Tema escuro"
              aria-pressed={theme === "dark"}
              onClick={() => selectTheme("dark")}
            >
              <span aria-hidden="true">☾</span>
              <span className="theme-option-label">Escuro</span>
            </button>
          </div>
          <button className="text-button" onClick={logout} disabled={loading}>
            Sair <span aria-hidden="true">↗</span>
          </button>
        </div>
      </header>
      <AnalysisNotificationCenter notifications={analysisNotifications} announcement={notificationAnnouncement} onDismiss={dismissNotification} />
      <main className="workspace">
        {path === "/dashboard" ? (
          systems ? <Dashboard systems={systems} central onOpen={() => undefined} onOpenSystem={openSystem} onPage={changeSystemsPage} /> : <section className="panel system-page-loading" role="status">Carregando sistemas analisados…</section>
        ) : path.match(/^\/systems\/[^/]+\/[^/]+$/) && result ? (
          <SystemDashboard data={result} history={systemHistory} totalHistory={systemHistoryTotal} loading={systemUpdating} onSelect={(id) => { if (id === result.analysisId) return; setSystemUpdating(true); api.analysis(id).then(setResult).catch(failure).finally(() => setSystemUpdating(false)); }} onMore={loadMoreSystemHistory} hasMore={systemHistory.length < systemHistoryTotal} onBack={() => navigate("/dashboard")} onNavigate={(href) => navigate(href)} onOpen={() => navigate(`/analyses/${result.analysisId}`)} />
        ) : path.match(/^\/systems\/[^/]+\/[^/]+$/) ? (
          <section className="panel system-page-loading" role="status">Carregando dashboard do sistema…</section>
        ) : path === "/analyses/new" ? (
          <AnalysisForm onSubmit={create} error={error} busy={submitting} onNavigate={(href) => navigate(href)} />
        ) : result ? (
          <>
            <ErrorMessage message={error} />
            <Results data={result} onNavigate={(href) => navigate(href)} />
          </>
        ) : (
          <section className="panel">
            <h1>Não foi possível abrir a análise</h1>
            <ErrorMessage message={error} />
          </section>
        )}
      </main>
      {result?.status === "FAILED" && analysisFailureOpen && (
        <AnalysisFailureModal
          data={result}
          onClose={() => setAnalysisFailureOpen(false)}
          onNew={newAnalysis}
        />
      )}
      {dashboardPromptOpen && (
        <DashboardPrompt
          onClose={() => {
            setDashboardPromptOpen(false);
            requestAnimationFrame(() => dashboardLinkRef.current?.focus());
          }}
          onNew={newAnalysis}
        />
      )}
      <footer className="app-footer">
        <span>SAST / Segurança de código</span>
        <span>Fundação & Parsers · CP1</span>
      </footer>
    </div>
  );
}
