import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "./api";
import {
  AnalysisForm,
  Dashboard,
  ErrorMessage,
  Login,
  Processing,
  Results,
  Shield,
} from "./components";
import type { Analysis, Session } from "./types";

export function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);
  const [path, setPath] = useState(location.pathname);
  const [error, setError] = useState("");
  const [result, setResult] = useState<Analysis>();
  const [loading, setLoading] = useState(false);
  const pending = useRef(false);
  const generation = useRef(0);
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
        if (location.pathname === "/login" || location.pathname === "/")
          navigate("/dashboard", true);
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
  }, [navigate]);
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
  }, [path, session, result?.analysisId, navigate, failure]);
  async function login(email: string, password: string) {
    setError("");
    try {
      setSession(await api.login(email, password));
      navigate("/analyses/new", true);
    } catch (e) {
      failure(e);
    }
  }
  async function create(url: string, reference: string) {
    if (pending.current) return;
    pending.current = true;
    setLoading(true);
    setError("");
    setResult(undefined);
    const operation = ++generation.current;
    try {
      const data = await api.create(url, reference);
      if (generation.current === operation) {
        setResult(data);
        navigate("/analyses/" + data.analysisId);
      }
    } catch (e) {
      if (generation.current === operation) failure(e);
    } finally {
      pending.current = false;
      setLoading(false);
    }
  }
  async function logout() {
    if (loading) return;
    try {
      await api.logout();
      generation.current++;
      setSession(null);
      setResult(undefined);
      setError("");
      navigate("/login", true);
    } catch (e) {
      failure(e);
    }
  }
  function newAnalysis() {
    setError("");
    setResult(undefined);
    navigate("/analyses/new");
  }
  function dashboard() {
    setError("");
    navigate("/dashboard");
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
    <div className="app-shell">
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
          <a className={path === "/dashboard" ? "active" : ""} href="/dashboard" onClick={(event) => { event.preventDefault(); dashboard(); }}>Dashboard</a>
          <a className={path === "/analyses/new" ? "active" : ""} href="/analyses/new" onClick={(event) => { event.preventDefault(); newAnalysis(); }}>Nova análise</a>
        </nav>
        <div className="account">
          <span>{session.email}</span>
          <button className="text-button" onClick={logout} disabled={loading}>
            Sair <span aria-hidden="true">↗</span>
          </button>
        </div>
      </header>
      <main className="workspace">
        {loading ? (
          <Processing />
        ) : path === "/dashboard" ? (
          <Dashboard data={result} onNew={newAnalysis} onOpen={() => result && navigate(`/analyses/${result.analysisId}`)} />
        ) : path === "/analyses/new" ? (
          <AnalysisForm onSubmit={create} error={error} />
        ) : result ? (
          <>
            <ErrorMessage message={error} />
            <Results data={result} onNew={newAnalysis} />
          </>
        ) : (
          <section className="panel">
            <h1>Não foi possível abrir a análise</h1>
            <ErrorMessage message={error} />
            <button onClick={newAnalysis}>Nova análise</button>
          </section>
        )}
      </main>
      <footer className="app-footer">
        <span>SAST / Segurança de código</span>
        <span>Fundação & Parsers · CP1</span>
      </footer>
    </div>
  );
}
