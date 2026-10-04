import { useEffect, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import Prism from "prismjs";
import "prismjs/components/prism-java";
import { api } from "./api";
import type { Analysis, HistoryEntry, SystemsPage } from "./types";
import { priorityLabels, summarizeResults, unifiedResults } from "./resultModel";

function renderJavaTokens(tokens: (Prism.Token | string)[]): ReactNode[] {
  return tokens.map((token, index) => {
    if (typeof token === "string") return token;
    const content = Array.isArray(token.content)
      ? renderJavaTokens(token.content as (Prism.Token | string)[])
      : String(token.content);
    return <span className={`token ${token.type}`} key={`${token.type}-${index}`}>{content}</span>;
  });
}

export function JavaCodeBlock({ code, line }: { code: string; line?: number }) {
  const grammar = Prism.languages.java;
  const tokens = grammar ? Prism.tokenize(code, grammar) : [code];
  return (
    <div className="code-block" aria-label="Código Java">
      <div className="code-block-header"><span>JAVA</span><span>Trecho analisado</span></div>
      <pre className="java-code"><code className="language-java">{line != null && <span className="line-number">{line}</span>}{renderJavaTokens(tokens as (Prism.Token | string)[])}</code></pre>
    </div>
  );
}

export function Shield() {
  return (
    <svg viewBox="0 0 32 36" fill="none" aria-hidden="true">
      <path
        d="M16 3 28 8v10c0 7-5 12-12 15C9 30 4 25 4 18V8L16 3Z"
        stroke="currentColor"
        strokeWidth="2.5"
      />
      <path
        d="m10 17 4 4 8-9"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
function PasswordVisibilityIcon({ visible }: { visible: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      {visible ? (
        <>
          <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
          <circle cx="12" cy="12" r="2.5" />
        </>
      ) : (
        <>
          <path d="m3 3 18 18" />
          <path d="M10.6 6.2A10.4 10.4 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-3.1 3.7M6.2 6.7C3.8 8.3 2.5 12 2.5 12S6 18 12 18c1.1 0 2.1-.2 3-.5" />
          <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
        </>
      )}
    </svg>
  );
}
export function ErrorMessage({ message }: { message: string }) {
  return message ? (
    <div className="error" role="alert">
      {message}
    </div>
  ) : null;
}

type BreadcrumbItem = {
  label: string;
  href?: string;
};

export function Breadcrumb({
  items,
  onNavigate,
}: {
  items: BreadcrumbItem[];
  onNavigate?: (href: string) => void;
}) {
  return (
    <nav className="breadcrumb" aria-label="Breadcrumb">
      <ol>
        {items.map((item, index) => {
          const current = index === items.length - 1;
          return (
            <li key={`${item.label}-${index}`}>
              {current || !item.href ? (
                <span aria-current={current ? "page" : undefined}>{item.label}</span>
              ) : (
                <a
                  href={item.href}
                  aria-label={`Voltar para ${item.label}`}
                  onClick={(event) => {
                    if (!onNavigate) return;
                    event.preventDefault();
                    onNavigate(item.href!);
                  }}
                >
                  {item.label}
                </a>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
export function Processing({ restoring = false }: { restoring?: boolean }) {
  if (restoring) {
    return (
      <section className="panel processing startup-processing" role="status" aria-label="Carregando aplicação" aria-live="polite">
        <div className="spinner" aria-hidden="true" />
      </section>
    );
  }
  return (
    <section className="panel processing" role="status" aria-live="polite">
      <span className="eyebrow">PLATAFORMA SAST</span>
      <div className="spinner" aria-hidden="true" />
      <h1>Análise em andamento</h1>
      <p>Analisando os arquivos Java do repositório.</p>
      <span className="muted">Isso pode levar alguns segundos.</span>
      <button disabled>Analisando…</button>
    </section>
  );
}
export function Login({
  onLogin,
  onRegister,
  initialRegistering = false,
  onToggleMode,
  success,
  error,
}: {
  onLogin: (email: string, password: string) => Promise<void>;
  onRegister: (email: string, password: string) => Promise<void>;
  initialRegistering?: boolean;
  onToggleMode?: () => void;
  success?: string;
  error: string;
}) {
  const [busy, setBusy] = useState(false);
  const [registering, setRegistering] = useState(initialRegistering);
  const [formError, setFormError] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirmation, setShowPasswordConfirmation] = useState(false);
  useEffect(() => {
    setRegistering(initialRegistering);
    setShowPassword(false);
    setShowPasswordConfirmation(false);
  }, [initialRegistering]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const email = String(form.get("email"));
      const password = String(form.get("password"));
      if (registering && password !== String(form.get("passwordConfirmation"))) {
        setFormError("As senhas não conferem");
        return;
      }
      setFormError("");
      await (registering ? onRegister : onLogin)(email, password);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login-layout">
      <aside className="login-intro">
        <div className="intro-grid" aria-hidden="true" />
        <span className="eyebrow">SEGURANÇA COMEÇA NO CÓDIGO</span>
        <h1>
          Encontre riscos.
          <br />
          Construa com
          <br />
          <span>confiança.</span>
        </h1>
        <p>
          Uma visão clara das vulnerabilidades do seu projeto, antes de elas se
          tornarem um problema.
        </p>
        <div className="intro-foot">
          <span className="status-dot" /> Análise estática · Java
        </div>
      </aside>
      <section className="login-side">
        <div className="login-card">
          <div className="login-logo">
            <Shield />
            <strong>SAST</strong>
          </div>
          <h2>{registering ? "Crie sua conta" : "Bem-vindo de volta"}</h2>
          <p className="muted">{registering ? "Cadastre-se para analisar seus repositórios." : "Entre para analisar seus repositórios."}</p>
          {!registering && success && <div className="success" role="status">{success}</div>}
          <ErrorMessage message={error || formError} />
          <form key={registering ? "register" : "login"} onSubmit={submit}>
            <label>
              E-mail
              <input
                autoComplete="username"
                name="email"
                type="email"
                required
                placeholder="seu@email.com"
                disabled={busy}
              />
            </label>
            <label>
              Senha
              <span className="password-field">
                <input
                  autoComplete={registering ? "new-password" : "current-password"}
                  name="password"
                  type={registering && showPassword ? "text" : "password"}
                  required
                  placeholder="Sua senha"
                  disabled={busy}
                />
                {registering && <button
                  className="password-toggle"
                  type="button"
                  aria-label={showPassword ? "Ocultar senha" : "Mostrar senha"}
                  onClick={() => setShowPassword((visible) => !visible)}
                  disabled={busy}
                >
                  <PasswordVisibilityIcon visible={showPassword} />
                </button>}
              </span>
            </label>
            {registering && <label>
              Confirmar senha
              <span className="password-field">
                <input
                  autoComplete="new-password"
                  name="passwordConfirmation"
                  type={showPasswordConfirmation ? "text" : "password"}
                  required
                  minLength={12}
                  placeholder="Repita sua senha"
                  disabled={busy}
                />
                <button
                  className="password-toggle"
                  type="button"
                  aria-label={showPasswordConfirmation ? "Ocultar confirmação de senha" : "Mostrar confirmação de senha"}
                  onClick={() => setShowPasswordConfirmation((visible) => !visible)}
                  disabled={busy}
                >
                  <PasswordVisibilityIcon visible={showPasswordConfirmation} />
                </button>
              </span>
            </label>}
            <button disabled={busy}>
              {busy ? (registering ? "Cadastrando…" : "Entrando…") : (registering ? "Criar conta" : "Entrar")}
              <span aria-hidden="true"> →</span>
            </button>
          </form>
          <p className="login-note">
            {registering ? "Já possui uma conta? " : "Ainda não possui uma conta? "}
            <a
              className="auth-switch"
              href={registering ? "/login" : "/cadastro"}
              onClick={(event) => {
                if (!onToggleMode) return;
                event.preventDefault();
                setFormError("");
                onToggleMode();
              }}
            >
              {registering ? "Entrar" : "Criar cadastro"}
            </a>
          </p>
        </div>
        <span className="login-bottom">PLATAFORMA DE ANÁLISE ESTÁTICA</span>
      </section>
    </div>
  );
}
export function AnalysisForm({
  onSubmit,
  error,
  busy = false,
  onNavigate,
}: {
  onSubmit: (url: string, reference: string) => void;
  error: string;
  busy?: boolean;
  onNavigate?: (href: string) => void;
}) {
  const [url, setUrl] = useState("");
  const [reference, setReference] = useState("");
  return (
    <>
      <Breadcrumb items={[{ label: "Dashboard", href: "/dashboard" }, { label: "Nova análise" }]} onNavigate={onNavigate} />
      <div className="page-heading">
        <span className="eyebrow">SEU PRÓXIMO PASSO</span>
        <h1>Nova análise</h1>
        <p>Conheça os riscos de segurança presentes no seu código.</p>
      </div>
      <div className="analysis-layout">
        <section className="panel form-panel">
          <div className="section-number">
            01 <span>Configure a análise</span>
          </div>
          <ErrorMessage message={error} />
          <form
            onSubmit={(e) => {
              e.preventDefault();
              onSubmit(url.trim(), reference.trim());
            }}
          >
            <label>
              Repositório público do GitHub
              <input
                id="repository-url"
                required
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://github.com/usuario/repositorio"
                disabled={busy}
              />
              <small>Informe a URL do repositório que deseja analisar.</small>
            </label>
            <div className="form-row">
              <label>
                Referência <span className="optional">opcional</span>
                <input
                  value={reference}
                  onChange={(e) => setReference(e.target.value)}
                  placeholder="Branch, tag ou SHA"
                  disabled={busy}
                />
                <small>Em branco, usamos o branch padrão.</small>
              </label>
              <label>
                Linguagem
                <div className="readonly-value">
                  Java <span>.java</span>
                </div>
              </label>
            </div>
            <div className="form-action">
              <span role={busy ? "status" : undefined} aria-live="polite">
                {busy ? "Análise em andamento. Você pode continuar nesta página." : "Somente repositórios públicos"}
              </span>
              <button type="submit" disabled={busy}>
                {busy ? "Analisando…" : "Iniciar análise"} <span className="action-arrow" aria-hidden="true">→</span>
              </button>
            </div>
          </form>
        </section>
        <aside className="scope-card">
          <Shield />
          <h2>O que verificamos</h2>
          <p>Três verificações essenciais para começar.</p>
          <ul>
            <li>
              <span>01</span>
              <div>
                Credenciais no código<small>CWE-798</small>
              </div>
            </li>
            <li>
              <span>02</span>
              <div>
                Execução de comandos<small>CWE-78</small>
              </div>
            </li>
            <li>
              <span>03</span>
              <div>
                Desserialização insegura<small>CWE-502</small>
              </div>
            </li>
          </ul>
          <p className="scope-note">
            Seu código é inspecionado sem ser executado. Apenas os resultados e
            trechos dos achados são salvos.
          </p>
        </aside>
      </div>
    </>
  );
}

function failureStageLabel(stage?: Analysis["stage"] | string | null) {
  switch (stage) {
    case "DOWNLOADING":
      return "acessar o repositório";
    case "DETERMINISTIC":
      return "analisar os arquivos Java";
    case "SEMANTIC":
    case "SUGGESTIONS":
      return "preparar os resultados complementares";
    default:
      return "concluir a análise";
  }
}

export function userFriendlyFailureMessage(data: Analysis) {
  switch (data.failureStage || data.stage) {
    case "DOWNLOADING":
      return "Não foi possível acessar o repositório. Verifique se a URL está correta e se o repositório é público.";
    case "DETERMINISTIC":
      return "Não foi possível analisar os arquivos Java deste repositório. Tente novamente em instantes.";
    case "SEMANTIC":
    case "SUGGESTIONS":
      return "A análise foi interrompida ao preparar as informações complementares. Tente novamente em instantes.";
    default:
      return "Ocorreu um erro inesperado durante a análise. Tente novamente em instantes.";
  }
}

export function AnalysisFailureModal({
  data,
  onClose,
  onNew,
}: {
  data: Analysis;
  onClose: () => void;
  onNew: () => void;
}) {
  const actionRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    actionRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    addEventListener("keydown", onKeyDown);
    return () => removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section
        className="analysis-failure-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="analysis-failure-title"
        aria-describedby="analysis-failure-description"
      >
        <span className="analysis-failure-icon" aria-hidden="true">!</span>
        <span className="eyebrow">ANÁLISE NÃO CONCLUÍDA</span>
        <h2 id="analysis-failure-title">Ocorreu um erro ao analisar</h2>
        <p id="analysis-failure-description">
          Não foi possível {failureStageLabel(data.failureStage || data.stage)} neste momento.
        </p>
        <p className="analysis-failure-help">{userFriendlyFailureMessage(data)}</p>
        <div className="modal-actions">
          <button ref={actionRef} type="button" onClick={onNew}>Tentar nova análise</button>
          <button className="secondary" type="button" onClick={onClose}>Ver detalhes</button>
        </div>
      </section>
    </div>
  );
}

export function DashboardPrompt({
  onClose,
  onNew,
}: {
  onClose: () => void;
  onNew: () => void;
}) {
  const actionRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    actionRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    addEventListener("keydown", onKeyDown);
    return () => removeEventListener("keydown", onKeyDown);
  }, [onClose]);
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section
        className="dashboard-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dashboard-modal-title"
      >
        <button className="modal-close" type="button" aria-label="Fechar" onClick={onClose}>×</button>
        <span className="modal-icon" aria-hidden="true"><Shield /></span>
        <h2 id="dashboard-modal-title">Cadastre um sistema primeiro</h2>
        <p>Conclua uma análise de repositório para visualizar o Dashboard e identificar vulnerabilidades.</p>
        <div className="modal-actions">
          <button ref={actionRef} type="button" onClick={onNew}>Cadastrar novo</button>
          <button className="secondary" type="button" onClick={onClose}>Fechar</button>
        </div>
      </section>
    </div>
  );
}

export function Dashboard({
  data,
  onOpen,
  systems,
  central = false,
  totalHistory = 1,
  onOpenSystem,
  onPage,
}: {
  data?: Analysis;
  onOpen: () => void;
  systems?: SystemsPage;
  onOpenAnalysis?: (id: string) => void;
  central?: boolean;
  totalHistory?: number;
  onOpenSystem?: (owner: string, repository: string) => void;
  onPage?: (page: number) => void;
}) {
  if (central && systems) {
    return <section className="dashboard-page dashboard-central">
      <Breadcrumb items={[{ label: "Dashboard" }]} />
      <div className="dashboard-title"><div><span className="eyebrow">SEUS SISTEMAS</span><h1>Dashboard</h1><p>Selecione um sistema para acompanhar suas análises.</p></div></div>
      <div className="dashboard-stats"><div><span>Sistemas</span><strong>{systems.totalSystems}</strong><small>repositórios</small></div><div><span>Análises</span><strong>{systems.totalAnalyses}</strong><small>execuções consideradas</small></div><div><span>Vulnerabilidades/Melhorias</span><strong>{systems.resultSummary?.total ?? systems.totalFindings}</strong><small>em todas as execuções</small></div><div><span>Críticas</span><strong className="dashboard-red">{systems.resultSummary?.critical ?? systems.totalCritical}</strong><small>prioridade imediata</small></div></div>
      <SystemCards data={systems} onOpenSystem={onOpenSystem} onPage={onPage} />
    </section>;
  }
  const findings = data?.findings || [];
  const suggestions = data?.suggestions ?? [];
  const items = unifiedResults(findings, suggestions);
  const summary = systems?.resultSummary ?? data?.resultSummary ?? summarizeResults(items);
  const files = [...new Set(items.map((item) => item.fileName))]
    .map((fileName) => ({
      fileName,
      count: items.filter((item) => item.fileName === fileName).length,
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 5);
  const suggestionCoverage = data?.suggestionStatus === "COMPLETED"
    ? "Métodos candidatos avaliados. A varredura consultiva não cobre todo o repositório."
    : data?.suggestionStatus === "DEGRADED"
    ? "Cobertura parcial ou IA indisponível. Pode haver melhorias ainda não identificadas."
    : data?.suggestionStatus === "NOT_APPLICABLE"
    ? "Nenhum método candidato às sugestões foi selecionado. Isso não significa ausência de melhorias."
    : data?.suggestionStatus === "PENDING" || data?.suggestionStatus === "RUNNING"
    ? "Sugestões consultivas em processamento. Os números ainda são parciais."
    : "Cobertura consultiva não informada nesta execução.";
  const chartTotal = summary.total;
  const dashboardEmptyMessage = data?.status === "FAILED"
    ? "A análise falhou; os dados parciais não confirmam ausência de itens."
    : data?.status === "PROCESSING" || data?.suggestionStatus === "PENDING" || data?.suggestionStatus === "RUNNING"
    ? "Verificações em processamento; os totais podem mudar."
    : data?.suggestionStatus === "DEGRADED"
    ? "Nenhum item disponível nas etapas parciais; isso não confirma ausência de problemas."
    : "Nenhuma vulnerabilidade ou melhoria encontrada nesta execução.";
  const firstEnd = chartTotal ? summary.critical / chartTotal * 100 : 0;
  const secondEnd = chartTotal ? (summary.critical + summary.high) / chartTotal * 100 : 0;
  const thirdEnd = chartTotal ? (summary.critical + summary.high + summary.medium) / chartTotal * 100 : 0;
  const topItems = items.slice(0, 4);

  return (
    <section className="dashboard-page">
      <div className="dashboard-title">
        <div>
          <span className="eyebrow">VISÃO GERAL</span>
          <h1>Dashboard</h1>
          <p>Acompanhe a segurança dos seus repositórios em um só lugar.</p>
        </div>
      </div>
      <div className="dashboard-stats">
        <div><span>Análises</span><strong>{systems?.totalAnalyses ?? (data ? totalHistory : 0)}</strong><small>no histórico</small></div>
        <div><span>Vulnerabilidades/Melhorias</span><strong>{summary.total}</strong><small>itens nas verificações</small></div>
        <div><span>Críticas</span><strong className="dashboard-red">{summary.critical}</strong><small>prioridade imediata</small></div>
        <div><span>Arquivos analisados</span><strong>{systems?.totalFiles ?? data?.filesAnalyzed ?? 0}</strong><small>arquivos Java</small></div>
      </div>
      <p className="dashboard-scope">Os indicadores de itens correspondem à execução selecionada. A prioridade usa a severidade de cada item.</p>
      <p className="dashboard-scope" role="status">{suggestionCoverage}</p>
      <div className="dashboard-grid">
        <article className="dashboard-card severity-card">
          <h2><span className="chart-icon">◔</span> Prioridade dos itens</h2>
          {chartTotal ? <div className="severity-chart-row"><div className="severity-donut" style={{ background: `conic-gradient(#f04444 0 ${firstEnd}%, #ff761c ${firstEnd}% ${secondEnd}%, #ffcc19 ${secondEnd}% ${thirdEnd}%, #3d82f4 ${thirdEnd}% ${summary.unclassified ? (summary.critical + summary.high + summary.medium + summary.low) / chartTotal * 100 : 100}%, #94a3b8 ${summary.unclassified ? (summary.critical + summary.high + summary.medium + summary.low) / chartTotal * 100 : 100}% 100%)` }}><span>{chartTotal}</span></div><div className="severity-legend"><span><i className="legend-critical"/>Crítica <b>{summary.critical}</b></span><span><i className="legend-high"/>Alta <b>{summary.high}</b></span><span><i className="legend-medium"/>Média <b>{summary.medium}</b></span><span><i className="legend-low"/>Baixa <b>{summary.low}</b></span><span>Sem classificação <b>{summary.unclassified}</b></span></div></div> : <div className="dashboard-empty"><span>{data?.status === "FAILED" ? "!" : data?.status === "PROCESSING" || data?.suggestionStatus === "RUNNING" ? "…" : "✓"}</span><p>{dashboardEmptyMessage}</p></div>}
        </article>
        <article className="dashboard-card detected-card">
          <h2>Itens de maior prioridade</h2>
          {topItems.length ? <ul>{topItems.map((item) => <li key={item.key}>
            <i className={item.priority.toLowerCase()} />
            <div><button className="dashboard-result-link" type="button" onClick={onOpen}><strong>{priorityLabels[item.priority]} · {item.title}</strong></button>
              <p>{item.source} · {item.fileName}:{item.line}</p></div>
          </li>)}</ul> : <div className="dashboard-list-empty">Nenhum item disponível para ordenar por prioridade.</div>}
        </article>
        <article className="dashboard-card files-card"><h2><span className="chart-icon">☷</span> Arquivos com mais itens</h2>{files.length ? <ul>{files.map((file) => <li key={file.fileName}><span>{file.fileName}</span><b>{file.count} {file.count === 1 ? "item" : "itens"}</b></li>)}</ul> : <div className="dashboard-list-empty">Nenhum arquivo com itens.</div>}</article>
      </div>
      {data && <button className="dashboard-last" onClick={onOpen}>Ver mais detalhes <span aria-hidden="true">↗</span></button>}
    </section>
  );
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function repositoryBreadcrumb(url: string) {
  const path = url.replace(/^https:\/\/github\.com\//, "").replace(/\/+$/, "");
  const [owner, repository] = path.split("/");
  if (!owner || !repository) return undefined;
  return {
    label: `${owner}/${repository}`,
    href: `/systems/${encodeURIComponent(owner)}/${encodeURIComponent(repository)}`,
  };
}

function SystemCards({ data, onOpenSystem, onPage }: { data: SystemsPage; onOpenSystem?: (owner: string, repository: string) => void; onPage?: (page: number) => void }) {
  if (!data.systems.length) return <div className="systems-empty">Nenhum sistema analisado ainda.</div>;
  return <section className="systems-section" aria-labelledby="systems-heading">
    <div className="systems-heading"><div><span className="eyebrow">SEUS SISTEMAS</span><h2 id="systems-heading">Histórico de aplicações</h2></div><span>{data.totalSystems} sistema{data.totalSystems === 1 ? "" : "s"}</span></div>
    <div className="systems-grid">{data.systems.map((system) => {
      const key = `${system.owner}/${system.repositoryName}`;
      return <button className="system-card" key={key} type="button" onClick={() => onOpenSystem?.(system.owner, system.repositoryName)} aria-label={`Abrir dashboard de ${system.repositoryName}`}>
        <div className="system-card-top"><div><span className="system-kicker">SISTEMA</span><h3>{system.repositoryName}</h3><p>{system.owner}/{system.repositoryName}</p></div><span className="system-count">{system.totalAnalyses} análise{system.totalAnalyses === 1 ? "" : "s"}</span></div>
        <div className="system-latest"><span>Última análise</span><strong>{dateLabel(system.latest.createdAt)}</strong><small>{system.latest.reference || "Branch padrão"} · {system.latest.resultSummary?.total ?? system.latest.findings} vulnerabilidade(s)/melhoria(s)</small><small>Maior prioridade: {system.latest.resultSummary?.highestPriority ? priorityLabels[system.latest.resultSummary.highestPriority] : "Sem prioridade"}</small></div>
        <span className="system-open">Abrir dashboard <span aria-hidden="true">→</span></span>
      </button>;
    })}</div>
    {data.totalSystems > data.systems.length && <div className="systems-pagination"><button type="button" disabled={data.page === 0} onClick={() => onPage?.(data.page - 1)}>← Anteriores</button><span>Página {data.page + 1} de {Math.ceil(data.totalSystems / data.size)}</span><button type="button" disabled={(data.page + 1) * data.size >= data.totalSystems} onClick={() => onPage?.(data.page + 1)}>Próximos →</button></div>}
  </section>;
}

export function SystemDashboard({
  data,
  history,
  onSelect,
  onBack,
  onMore,
  hasMore,
  totalHistory,
  loading = false,
  onOpen,
  onNavigate,
}: {
  data: Analysis;
  history: HistoryEntry[];
  onSelect: (id: string) => void;
  onBack: () => void;
  onMore: () => void;
  hasMore: boolean;
  totalHistory: number;
  loading?: boolean;
  onOpen: () => void;
  onNavigate?: (href: string) => void;
}) {
  return <>
    <div className="system-dashboard-heading">
      <Breadcrumb items={[{ label: "Dashboard", href: "/dashboard" }, { label: data.repositoryUrl.replace("https://github.com/", "") }]} onNavigate={onNavigate || (() => onBack())} />
      <span className="eyebrow">DASHBOARD DO SISTEMA</span>
      <h1>{data.repositoryUrl.replace("https://github.com/", "")}</h1>
      <p>Visão da execução selecionada: <strong>{dateLabel(data.createdAt)}</strong> · {data.reference || "Branch padrão"}</p>
      <div className="system-history" aria-label="Histórico de análises">
        <div className="system-history-heading">
          <span>Histórico de execuções</span>
          <small>{totalHistory} execução{totalHistory === 1 ? "" : "ões"} · mais recente à direita</small>
        </div>
        <div className="system-history-list">
          {history.map((entry) => <button key={entry.analysisId} type="button" className={entry.analysisId === data.analysisId ? "selected" : ""} onClick={() => onSelect(entry.analysisId)} disabled={loading || entry.analysisId === data.analysisId} aria-current={entry.analysisId === data.analysisId ? "page" : undefined}>{dateLabel(entry.createdAt)} · {entry.reference || "padrão"} · {entry.resultSummary?.total ?? entry.findings} item(ns) · {entry.resultSummary?.highestPriority ? priorityLabels[entry.resultSummary.highestPriority] : "Sem prioridade"}</button>)}
          {hasMore && <button type="button" onClick={onMore} disabled={loading}>{loading ? "Carregando…" : "Carregar anteriores"}</button>}
        </div>
      </div>
      {loading && <p className="system-status" role="status" aria-live="polite">Atualizando os dados da análise selecionada…</p>}
    </div>
    <Dashboard data={data} onOpen={onOpen} totalHistory={totalHistory} />
  </>;
}

const severityNames = {
  Critical: "Crítica",
  High: "Alta",
  Medium: "Média",
  Low: "Baixa",
};
const traceStepLabels: Record<string, string> = {
  http_param: "Entrada HTTP",
  assignment: "Atribuição",
  concatenation: "Concatenação de string",
  conditional: "Expressão condicional",
  sink: "Execução de comando",
};

function traceStepLabel(kind: string) {
  return traceStepLabels[kind] || kind;
}
function TaintTraceView({ trace }: { trace: NonNullable<Analysis["findings"][number]["taintTrace"]> }) {
  const steps = [trace.source, ...trace.steps, trace.sink];
  return (
    <div className="taint-trace">
      <h5>Rastro de Taint Analysis</h5>
      <ol>
        {steps.map((step, index) => (
          <li key={index} className={step === trace.source ? "taint-source" : step === trace.sink ? "taint-sink" : undefined}>
            <span className="taint-kind">{traceStepLabel(step.kind)}</span>
            <span className="taint-position">Linha {step.line}, coluna {step.column}</span>
          </li>
        ))}
      </ol>
      <span className="muted">Engine de taint versão {trace.engineVersion}</span>
    </div>
  );
}
export function Results({
  data,
  onNavigate,
}: {
  data: Analysis;
  onNavigate?: (href: string) => void;
}) {
  const items = unifiedResults(data.findings, data.suggestions ?? []);
  const summary = data.resultSummary ?? summarizeResults(items);
  const groups = new Map<string, typeof items>();
  items.forEach((item) => groups.set(item.fileName, [...(groups.get(item.fileName) || []), item]));
  const orderedGroups = [...groups].sort((a, b) =>
    Math.min(...a[1].map((item) => ["Critical", "High", "Medium", "Low", "Unclassified"].indexOf(item.priority)))
      - Math.min(...b[1].map((item) => ["Critical", "High", "Medium", "Low", "Unclassified"].indexOf(item.priority)))
    || a[0].localeCompare(b[0]));
  const systemBreadcrumb = repositoryBreadcrumb(data.repositoryUrl);
  const partial = data.semanticStatus === "DEGRADED" || data.suggestionStatus === "DEGRADED";
  const running = data.status === "PROCESSING" || data.semanticStatus === "RUNNING" || data.suggestionStatus === "RUNNING";
  const severityNames: Record<string, string> = { Critical: "Crítica", High: "Alta", Medium: "Média", Low: "Baixa" };
  return <>
    <Breadcrumb items={[{ label: "Dashboard", href: "/dashboard" }, ...(systemBreadcrumb ? [systemBreadcrumb] : []), { label: "Análise" }]} onNavigate={onNavigate} />
    <div className="result-heading"><div>
      <span className="eyebrow">RESULTADOS DA ANÁLISE</span>
      <h1>{data.status === "PROCESSING" ? "Análise em andamento" : data.status === "FAILED" ? "Análise interrompida" : "Análise concluída"} {data.status !== "PROCESSING" && <span className="complete-mark" aria-label={data.status === "FAILED" ? "Falhou" : "Concluída"}>{data.status === "FAILED" ? "!" : "✓"}</span>}</h1>
      <p className="repository">{data.repositoryUrl.replace("https://github.com/", "")}<span className="ref">{data.reference || "Branch padrão"}</span></p>
    </div></div>
    {data.status === "FAILED" && <p className="semantic-status" role="alert">{userFriendlyFailureMessage(data)}</p>}
    {partial && data.status !== "FAILED" && <p className="semantic-status" role="status">Resultado parcial: uma etapa de IA foi concluída com cobertura limitada. Os itens exibidos não representam cobertura completa.</p>}
    <div className="stats">
      <div><span>Vulnerabilidades/Melhorias</span><strong>{summary.total}</strong></div>
      <div><span>Críticas</span><strong className="critical-text">{summary.critical}</strong></div>
      <div><span>Altas</span><strong className="high-text">{summary.high}</strong></div>
      <div><span>Arquivos analisados</span><strong>{data.status === "PROCESSING" ? `${data.filesProcessed ?? 0}/${data.filesTotal || "…"}` : data.filesAnalyzed}<small> Java</small></strong></div>
    </div>
    {items.length === 0 ? <section className="panel empty">
      {running ? <span className="empty-loading" role="status" aria-label="Carregando resultados"><span className="spinner" aria-hidden="true" /></span> : <span className="complete-mark">{data.status === "FAILED" ? "!" : "✓"}</span>}
      <h2>{data.status === "FAILED" ? "Análise sem conclusão" : running ? "Aguardando resultados" : partial ? "Nenhum item disponível nesta etapa parcial" : "Nenhuma vulnerabilidade ou melhoria encontrada"}</h2>
      <p>{data.status === "FAILED" ? userFriendlyFailureMessage(data) : running ? "As vulnerabilidades e melhorias serão exibidas aqui conforme as etapas terminarem." : partial ? "Uma etapa teve falha ou cobertura limitada; a ausência de itens não confirma que não existam problemas." : "As verificações concluídas não identificaram ocorrências nesta execução."}</p>
    </section> : <section className="findings" aria-label="Vulnerabilidades e melhorias">
      <div className="findings-heading"><h2>Vulnerabilidades/Melhorias</h2><span>{groups.size} arquivo(s) com ocorrências</span></div>
      {orderedGroups.map(([file, group]) => <div className="file-group" key={file}>
        <h3><span aria-hidden="true">⌘</span> {file}</h3>
        {group.map((item) => <details className="finding" key={item.key}>
          <summary><div><span className={`badge ${item.priority.toLowerCase()}`}>{priorityLabels[item.priority]}</span><span className="ai-kicker">{item.source}</span><h4>{item.title}</h4><p>{item.finding?.cwe ? `${item.finding.cwe} · ` : ""}Linha {item.line}{item.column ? `, coluna ${item.column}` : ""}</p></div><span className="expand">Ver detalhes <span aria-hidden="true">⌄</span></span></summary>
          <div className="finding-detail">
            {item.finding ? <>
              <h5>Descrição</h5><p>{item.finding.description}</p><JavaCodeBlock code={item.finding.snippet} line={item.finding.line} />
              {item.finding.taintTrace && <TaintTraceView trace={item.finding.taintTrace} />}
              {item.finding.aiAssessment && <section className="ai-assessment" aria-label="Avaliação consultiva da IA"><div className="ai-assessment-heading"><div><span className="ai-kicker">ANÁLISE SEMÂNTICA</span><h5>Avaliação da IA</h5></div><span className="ai-consultive">Consultiva</span></div><div className="ai-assessment-summary"><div><span>Severidade sugerida:<strong className={`ai-severity ai-severity-${item.finding.aiAssessment.suggestedSeverity.toLowerCase()}`}>{severityNames[item.finding.aiAssessment.suggestedSeverity]}</strong></span></div><div><span>Provável falso positivo:<strong>{item.finding.aiAssessment.likelyFalsePositive ? "Sim" : "Não"}</strong></span></div><div><span>Confiança do modelo:<strong>{Math.round(item.finding.aiAssessment.confidence * 100)}%</strong></span></div></div><div className="ai-assessment-copy">{item.finding.aiAssessment.risk && <div><h6>Risco contextual</h6><p>{item.finding.aiAssessment.risk}</p></div>}<div><h6>Justificativa</h6><p>{item.finding.aiAssessment.rationale}</p></div>{item.finding.aiAssessment.evidence?.length ? <div><h6>Evidências</h6><ul>{item.finding.aiAssessment.evidence.map((evidence, index) => <li key={index}>{evidence}</li>)}</ul></div> : null}{item.finding.aiAssessment.falsePositiveReason && <div><h6>Motivo da avaliação de falso positivo</h6><p>{item.finding.aiAssessment.falsePositiveReason}</p></div>}<div><h6>Remediação sugerida</h6><p>{item.finding.aiAssessment.remediation}</p></div>{item.finding.aiAssessment.recommendations?.length ? <div><h6>Ações recomendadas</h6><ul>{item.finding.aiAssessment.recommendations.map((recommendation, index) => <li key={index}>{recommendation}</li>)}</ul></div> : null}{item.finding.aiAssessment.limitations && <div><h6>Limitações</h6><p>{item.finding.aiAssessment.limitations}</p></div>}</div><small className="ai-assessment-meta">Modelo {item.finding.aiAssessment.model} · A severidade exibida permanece a da regra.</small></section>}
              <span className="muted">Regra {item.finding.ruleId} · {item.finding.fileName}</span>
            </> : item.suggestion && <><JavaCodeBlock code={item.suggestion.evidence} line={item.suggestion.line} /><h5>Justificativa</h5><p>{item.suggestion.rationale}</p><h5>Recomendação</h5><p>{item.suggestion.recommendation}</p><h5>Confiança</h5><p>{Math.round(item.suggestion.confidence * 100)}%</p><h5>Limitações</h5><p>{item.suggestion.limitations}</p><small className="ai-assessment-meta">Modelo {item.suggestion.model} · Sugestão consultiva</small></>}
          </div>
        </details>)}
      </div>)}
    </section>}
    <p className="result-footer">Concluída em {new Date(data.createdAt).toLocaleString("pt-BR")} · Análise estática Java</p>
  </>;
}
