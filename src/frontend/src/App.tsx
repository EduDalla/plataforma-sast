import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "./api";
import {
  AnalysisForm,
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

type Theme = "light" | "dark";
const THEME_STORAGE_KEY = "sast-theme";
const LAST_ANALYSIS_KEY = "sast-last-analysis";

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
  const dashboardLinkRef = useRef<HTMLAnchorElement>(null);
  const pending = useRef(false);
  const generation = useRef(0);
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
        navigate("/login", true);
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
  async function create(url: string, reference: string) {
    if (pending.current) return;
    pending.current = true;
    setSubmitting(true);
    setError("");
    setResult(undefined);
    const operation = ++generation.current;
    try {
      const data = await api.create(url, reference);
      if (generation.current === operation) {
        setResult(data);
        storeLastAnalysisId(data.analysisId);
        if (typeof api.systems === "function") await loadSystems();
        navigate("/dashboard");
      }
    } catch (e) {
      if (generation.current === operation) failure(e);
    } finally {
      pending.current = false;
      setSubmitting(false);
    }
  }
  async function logout() {
    if (loading) return;
    try {
      await api.logout();
      generation.current++;
      setSession(null);
      setResult(undefined);
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
    setSubmitting(false);
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
      <main className="startup">
        <Processing restoring />
      </main>
    );
  if (!session)
    return (
      <main>
        <Login onLogin={login} error={error} />
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
      <main className="workspace">
        {path === "/dashboard" ? (
          systems ? <Dashboard systems={systems} central onOpen={() => undefined} onOpenSystem={openSystem} onPage={changeSystemsPage} /> : <section className="panel system-page-loading" role="status">Carregando sistemas analisados…</section>
        ) : path.match(/^\/systems\/[^/]+\/[^/]+$/) && result ? (
          <SystemDashboard data={result} history={systemHistory} totalHistory={systemHistoryTotal} loading={systemUpdating} onSelect={(id) => { if (id === result.analysisId) return; setSystemUpdating(true); api.analysis(id).then(setResult).catch(failure).finally(() => setSystemUpdating(false)); }} onMore={loadMoreSystemHistory} hasMore={systemHistory.length < systemHistoryTotal} onBack={() => navigate("/dashboard")} onOpen={() => navigate(`/analyses/${result.analysisId}`)} />
        ) : path.match(/^\/systems\/[^/]+\/[^/]+$/) ? (
          <section className="panel system-page-loading" role="status">Carregando dashboard do sistema…</section>
        ) : path === "/analyses/new" ? (
          <AnalysisForm onSubmit={create} error={error} busy={submitting} />
        ) : result ? (
          <>
            <ErrorMessage message={error} />
            <Results data={result} />
          </>
        ) : (
          <section className="panel">
            <h1>Não foi possível abrir a análise</h1>
            <ErrorMessage message={error} />
          </section>
        )}
      </main>
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
