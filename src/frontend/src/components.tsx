import { useState } from "react";
import type { FormEvent } from "react";
import type { Analysis } from "./types";

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
export function ErrorMessage({ message }: { message: string }) {
  return message ? (
    <div className="error" role="alert">
      {message}
    </div>
  ) : null;
}
export function Processing({ restoring = false }: { restoring?: boolean }) {
  return (
    <section className="panel processing" role="status" aria-live="polite">
      <span className="eyebrow">PLATAFORMA SAST</span>
      <div className="spinner" aria-hidden="true" />
      <h1>{restoring ? "Carregando seu espaço" : "Análise em andamento"}</h1>
      <p>
        {restoring
          ? "Só um instante…"
          : "Analisando os arquivos Java do repositório."}
      </p>
      {!restoring && (
        <>
          <span className="muted">Isso pode levar alguns segundos.</span>
          <button disabled>Analisando…</button>
        </>
      )}
    </section>
  );
}
export function Login({
  onLogin,
  error,
}: {
  onLogin: (email: string, password: string) => Promise<void>;
  error: string;
}) {
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    try {
      await onLogin(String(form.get("email")), String(form.get("password")));
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
          <h2>Bem-vindo de volta</h2>
          <p className="muted">Entre para analisar seus repositórios.</p>
          <ErrorMessage message={error} />
          <form onSubmit={submit}>
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
              <input
                autoComplete="current-password"
                name="password"
                type="password"
                required
                placeholder="Sua senha"
                disabled={busy}
              />
            </label>
            <button disabled={busy}>
              {busy ? "Entrando…" : "Entrar"}
              <span aria-hidden="true"> →</span>
            </button>
          </form>
          <p className="login-note">Seu espaço para um código mais seguro.</p>
        </div>
        <span className="login-bottom">PLATAFORMA DE ANÁLISE ESTÁTICA</span>
      </section>
    </div>
  );
}
export function AnalysisForm({
  onSubmit,
  error,
}: {
  onSubmit: (url: string, reference: string) => void;
  error: string;
}) {
  const [url, setUrl] = useState("");
  const [reference, setReference] = useState("");
  return (
    <>
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
                required
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://github.com/usuario/repositorio"
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
              <span>Somente repositórios públicos</span>
              <button type="submit">
                Iniciar análise <span aria-hidden="true">→</span>
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
const severityNames = {
  Critical: "Crítica",
  High: "Alta",
  Medium: "Média",
  Low: "Baixa",
};
export function Results({
  data,
  onNew,
}: {
  data: Analysis;
  onNew: () => void;
}) {
  const groups = new Map<string, Analysis["findings"]>();
  data.findings.forEach((f) =>
    groups.set(f.fileName, [...(groups.get(f.fileName) || []), f]),
  );
  return (
    <>
      <div className="result-heading">
        <div>
          <span className="eyebrow">RESULTADOS DA ANÁLISE</span>
          <h1>
            Análise concluída{" "}
            <span className="complete-mark" aria-label="Concluída">
              ✓
            </span>
          </h1>
          <p className="repository">
            {data.repositoryUrl.replace("https://github.com/", "")}
            <span className="ref">{data.reference || "Branch padrão"}</span>
          </p>
        </div>
        <button className="secondary" onClick={onNew}>
          Nova análise <span aria-hidden="true">↗</span>
        </button>
      </div>
      <div className="stats">
        <div>
          <span>Vulnerabilidades</span>
          <strong>{data.findings.length}</strong>
        </div>
        <div>
          <span>Críticas</span>
          <strong className="critical-text">
            {data.findings.filter((f) => f.severity === "Critical").length}
          </strong>
        </div>
        <div>
          <span>Altas</span>
          <strong className="high-text">
            {data.findings.filter((f) => f.severity === "High").length}
          </strong>
        </div>
        <div>
          <span>Arquivos analisados</span>
          <strong>
            {data.filesAnalyzed}
            <small> Java</small>
          </strong>
        </div>
      </div>
      {data.findings.length === 0 ? (
        <section className="panel empty">
          <span className="complete-mark">✓</span>
          <h2>Nenhuma vulnerabilidade encontrada</h2>
          <p>
            Não encontramos ocorrências das regras verificadas nesta análise.
          </p>
        </section>
      ) : (
        <section className="findings">
          <div className="findings-heading">
            <h2>Vulnerabilidades encontradas</h2>
            <span>{groups.size} arquivo(s) com achados</span>
          </div>
          {[...groups].map(([file, findings]) => (
            <div className="file-group" key={file}>
              <h3>
                <span aria-hidden="true">⌘</span> {file}
              </h3>
              {findings.map((f) => (
                <details
                  className="finding"
                  key={f.ruleId + ":" + f.line + ":" + f.column}
                >
                  <summary>
                    <div>
                      <span className={"badge " + f.severity.toLowerCase()}>
                        {severityNames[f.severity]}
                      </span>
                      <h4>{f.title}</h4>
                      <p>
                        {f.cwe} <span>·</span> Linha {f.line}, coluna {f.column}
                      </p>
                    </div>
                    <span className="expand">
                      Ver detalhes <span aria-hidden="true">⌄</span>
                    </span>
                  </summary>
                  <div className="finding-detail">
                    <h5>Descrição</h5>
                    <p>{f.description}</p>
                    <h5>Trecho do código</h5>
                    <pre>
                      <code>
                        <span className="line-number">{f.line}</span>
                        {f.snippet}
                      </code>
                    </pre>
                    <span className="muted">
                      Regra {f.ruleId} · {f.fileName}
                    </span>
                  </div>
                </details>
              ))}
            </div>
          ))}
        </section>
      )}
      <p className="result-footer">
        Concluída em {new Date(data.createdAt).toLocaleString("pt-BR")} ·
        Análise estática Java
      </p>
    </>
  );
}
