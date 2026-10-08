#!/usr/bin/env python3
"""O-01: ensaio Compose isolado; saída contém somente evidência operacional derivada."""
import argparse
import base64
import concurrent.futures
import datetime
import json
import os
import platform
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ("db", "rabbitmq", "api", "worker", "web", "ollama")


def request(url, method="GET", body=None, token=None, basic=None):
    headers = {"Content-Type": "application/json", "User-Agent": "sast-o01-operation"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if basic:
        headers["Authorization"] = "Basic " + base64.b64encode(basic.encode()).decode()
    data = None if body is None else json.dumps(body).encode()
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data, headers, method=method), timeout=15) as response:
            payload = response.read(8 * 1024 * 1024 + 1)
            if len(payload) > 8 * 1024 * 1024:
                raise RuntimeError("Resposta HTTP excedeu o limite do ensaio")
            return response.status, json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        # Nunca incluir corpo de erro, credenciais ou fonte na saída.
        return error.code, None


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def wait_for(probe, timeout=120, label="condição operacional"):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            result = probe()
            if result:
                return result
        except (OSError, urllib.error.URLError, TimeoutError, RuntimeError):
            pass
        time.sleep(1)
    raise RuntimeError("Prazo excedido aguardando " + label)


def memory_bytes(text):
    amount, unit = re.fullmatch(r"([0-9.]+)\s*([A-Za-z]+)", text.strip()).groups()
    factors = {"B": 1, "KiB": 1024, "MiB": 1024**2, "GiB": 1024**3,
               "kB": 1000, "MB": 1000**2, "GB": 1000**3}
    return round(float(amount) * factors[unit])


class ComposeRun:
    def __init__(self, directory):
        self.project = "sast-o01-" + uuid.uuid4().hex[:12]
        self.environment = dict(os.environ)
        self.environment.update({
            "POSTGRES_DB": "sast", "POSTGRES_USER": "sast",
            "POSTGRES_PASSWORD": secrets.token_urlsafe(24),
            "RABBITMQ_USER": "sast", "RABBITMQ_PASSWORD": secrets.token_urlsafe(24),
            "SAST_JWT_SECRET": base64.b64encode(secrets.token_bytes(32)).decode(),
            "SAST_BOOTSTRAP_EMAIL": "bootstrap@operation.test",
            "SAST_BOOTSTRAP_PASSWORD": secrets.token_urlsafe(24), "GITHUB_TOKEN": "",
            "DB_PORT": "127.0.0.1:0", "API_PORT": "127.0.0.1:0", "WEB_PORT": "127.0.0.1:0",
            "SAST_COOKIE_SECURE": "false", "SAST_OLLAMA_BASE_URL": "http://ollama:11434",
            "SAST_OLLAMA_MODEL": "llama3.2:3b", "SAST_OLLAMA_MAX_CANDIDATES": "1",
            "SAST_OLLAMA_TOTAL_BUDGET_SECONDS": "2", "SAST_OLLAMA_SUGGESTION_MAX_METHODS": "1",
            "SAST_OLLAMA_SUGGESTION_BUDGET_SECONDS": "2",
            "SAST_GITHUB_MAX_ARCHIVE_BYTES": "104857600", "SAST_GITHUB_MAX_JAVA_FILES": "1000",
            "SAST_GITHUB_MAX_FILE_BYTES": "2097152", "SAST_GITHUB_MAX_EXTRACTED_BYTES": "524288000",
        })
        override = Path(directory) / "compose-operation.json"
        override.write_text(json.dumps({"services": {
            "rabbitmq": {"ports": ["127.0.0.1:0:15672"]},
        }}), encoding="utf-8")
        self.prefix = ["docker", "compose", "--env-file", "/dev/null", "--project-name", self.project,
                       "--file", str(ROOT / "compose.yaml"), "--file", str(override)]
        self.secrets = [self.environment[key] for key in
                        ("POSTGRES_PASSWORD", "RABBITMQ_PASSWORD", "SAST_JWT_SECRET", "SAST_BOOTSTRAP_PASSWORD")]

    def command(self, *args, input_text=None, timeout=600):
        result = subprocess.run(self.prefix + list(args), cwd=ROOT, env=self.environment,
                                input=input_text, capture_output=True, text=True, timeout=timeout)
        require(result.returncode == 0, "Comando Compose falhou: " + args[0])
        return result.stdout

    def url(self, service, port):
        address = self.command("port", service, str(port)).strip().splitlines()[0]
        return "http://127.0.0.1:" + address.rsplit(":", 1)[1]

    def sql(self, statement):
        result = self.command("exec", "-T", "db", "sh", "-c",
                              'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -v ON_ERROR_STOP=1',
                              input_text=statement)
        return result.strip()

    def queue(self):
        code, body = request(self.url("rabbitmq", 15672) + "/api/queues/%2F/sast.analysis.requested",
                             basic="sast:" + self.environment["RABBITMQ_PASSWORD"])
        require(code == 200, "Não foi possível consultar a fila")
        return {key: body.get(key, 0) for key in ("messages_ready", "messages_unacknowledged", "consumers")}

    def publish(self, analysis_id):
        uuid.UUID(analysis_id)
        code, body = request(self.url("rabbitmq", 15672) + "/api/exchanges/%2F/sast.analysis/publish", "POST",
                             {"properties": {"content_type": "text/plain"}, "routing_key": "analysis.requested",
                              "payload": analysis_id, "payload_encoding": "string"},
                             basic="sast:" + self.environment["RABBITMQ_PASSWORD"])
        require(code == 200 and body["routed"], "Entrega da duplicata não foi roteada")

    def memory(self):
        ids = self.command("ps", "-q", "api", "worker").split()
        result = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{json .}}", *ids],
                                capture_output=True, text=True, timeout=20)
        require(result.returncode == 0, "Não foi possível medir memória")
        measurements = {}
        for line in result.stdout.splitlines():
            value = json.loads(line)
            service = "worker" if "-worker-" in value["Name"] else "api"
            measurements[service] = memory_bytes(value["MemUsage"].split("/")[0])
        return measurements

    def diagnostics(self):
        states = self.command("ps", "--all", "--format", "{{.Service}} {{.State}} {{.Health}}")
        logs = self.command("logs", "--no-color", *SERVICES)
        database = self.sql("SELECT json_build_object('analyses', (SELECT count(*) FROM analyses), "
                            "'outbox_pending', count(*) FILTER (WHERE published_at IS NULL), "
                            "'outbox_attempts_max', coalesce(max(attempts),0)) FROM analysis_outbox;")
        return {"service_states": states.splitlines(), "database_counts": json.loads(database),
                "exception_classes": sorted(set(re.findall(r"\b[a-z][\w.]+Exception\b", logs))),
                "bootstrap_conflict_detected": "app_users_email" in logs or "already exists" in logs,
                "bootstrap_missing_detected": "Banco sem usuários" in logs}


def run_experiment(stack, repository, reference, timeout, report):
    print("O-01: iniciando seis serviços em projeto Compose isolado", flush=True)
    stack.command("up", "--build", "-d", "--wait", "--wait-timeout", "180", timeout=900)
    api = stack.url("api", 8080)
    web = stack.url("web", 80)
    require(request(api + "/health")[1]["status"] == "UP", "Health da API não está UP")
    require(request(web + "/api/auth/session")[0] == 401, "Proxy web não preservou autenticação")
    version = json.loads(stack.command("exec", "-T", "worker", "curl", "-fsS", "http://ollama:11434/api/version"))
    wait_for(lambda: stack.queue()["consumers"] == 2)
    password = secrets.token_urlsafe(24)
    stack.secrets.append(password)

    def account(label):
        data = {"email": label + "-" + uuid.uuid4().hex + "@operation.test", "password": password}
        require(request(api + "/api/auth/register", "POST", data)[0] == 201, "Cadastro falhou")
        code, login = request(api + "/api/auth/login", "POST", data)
        require(code == 200, "Login falhou")
        stack.secrets.append(login["accessToken"])
        return login["accessToken"]

    token, stranger = account("owner"), account("stranger")
    for invalid in ("http://github.com/acme/demo", "https://github.com.evil.test/acme/demo", "https://github.com/a/.."):
        require(request(api + "/api/analyses", "POST", {"repositoryUrl": invalid}, token)[0] == 400,
                "URL malformada não foi rejeitada")
    require(stack.sql("SELECT count(*) FROM analyses;") == "0", "URL inválida foi persistida")

    def submit():
        start = time.monotonic()
        code, body = request(api + "/api/analyses", "POST",
                             {"repositoryUrl": repository, "reference": reference}, token)
        require(code == 202, "Submissão não retornou 202")
        return {"id": str(uuid.UUID(body["analysisId"])), "started": start}

    report.update({"repository_url": repository, "commit_sha": reference, "ollama_version": version["version"],
              "services": list(SERVICES), "http_invalid_origins": 3, "health": "UP", "samples": 0,
              "memory_peak_observed_bytes": {}, "queue_peak_ready": 0, "active_attempts_peak": 0,
              "limits": {"archive_bytes": 104857600, "java_files": 1000, "file_bytes": 2097152,
                         "extracted_bytes": 524288000, "entries": 10000},
              "host": {"architecture": platform.machine(), "cpus": os.cpu_count()},
              "ai_budget": {"candidates": 1, "total_seconds": 2, "suggestion_methods": 1,
                            "suggestion_seconds": 2}})
    # Falhas provocadas somente neste projeto. A destruição no finally remove esta instância inteira.
    stack.command("stop", "worker", "ollama")
    stack.command("stop", "rabbitmq")
    outage = submit()
    print("O-01: verificando outbox com RabbitMQ interrompido", flush=True)
    wait_for(lambda: int(stack.sql("SELECT coalesce(max(attempts),0) FROM analysis_outbox;")) >= 1,
             timeout=120, label="retentativa de publicação registrada na outbox")
    require(stack.sql("SELECT count(*) FROM analysis_outbox WHERE published_at IS NULL;") == "1",
            "Outbox não preservou evento")
    report["outbox_attempts_during_outage"] = int(stack.sql("SELECT max(attempts) FROM analysis_outbox;"))
    stack.command("start", "rabbitmq")
    wait_for(lambda: request(stack.url("rabbitmq", 15672) + "/api/overview",
                            basic="sast:" + stack.environment["RABBITMQ_PASSWORD"])[0] == 200)
    stack.sql("UPDATE analysis_outbox SET available_at=now()-interval '1 second';")
    wait_for(lambda: stack.sql("SELECT count(*) FROM analysis_outbox WHERE published_at IS NULL;") == "0")
    # Duas submissões simultâneas; a fila fica retida para medir backlog e publicar duplicata.
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        futures = [executor.submit(submit) for _ in range(2)]
        pair = [future.result() for future in futures]
    wait_for(lambda: stack.queue()["messages_ready"] >= 3)
    stack.publish(pair[0]["id"])
    tasks = [outage, *pair]
    report["queue_peak_ready"] = stack.queue()["messages_ready"]
    # Simula a morte de um proprietário já confirmado: lease vencido e um evento publicado anteriormente.
    lease = submit()
    wait_for(lambda: stack.sql("SELECT count(*) FROM analysis_outbox WHERE analysis_id='" + lease["id"]
                              + "' AND published_at IS NULL;") == "0")
    stack.sql("UPDATE analyses SET lease_owner='worker-interrompido', lease_until=now()-interval '1 second', "
              + "attempt_count=1 WHERE id='" + lease["id"] + "';")
    # A mensagem da tentativa morta já teria recebido ACK. Retira-a da fila e devolve as demais.
    code, pending = request(stack.url("rabbitmq", 15672) + "/api/queues/%2F/sast.analysis.requested/get", "POST",
                            {"count": 100, "ackmode": "ack_requeue_false", "encoding": "auto"},
                            basic="sast:" + stack.environment["RABBITMQ_PASSWORD"])
    require(code == 200, "Não foi possível simular ACK da tentativa interrompida")
    require(any(item["payload"] == lease["id"] for item in pending), "Mensagem da lease não estava na fila")
    for item in pending:
        if item["payload"] != lease["id"]:
            stack.publish(item["payload"])
    tasks.append(lease)
    stack.command("start", "worker")
    deadline = time.monotonic() + timeout
    unsafe_snippets = []
    print("O-01: medindo processamento concorrente, recuperação e IA indisponível", flush=True)
    while tasks and time.monotonic() < deadline:
        queue = stack.queue()
        report["queue_peak_ready"] = max(report["queue_peak_ready"], queue["messages_ready"])
        active = int(stack.sql("SELECT count(*) FROM analyses WHERE status='PROCESSING' "
                               "AND lease_owner IS NOT NULL AND lease_until>now();"))
        report["active_attempts_peak"] = max(report["active_attempts_peak"], active)
        for service, amount in stack.memory().items():
            report["memory_peak_observed_bytes"][service] = max(amount, report["memory_peak_observed_bytes"].get(service, 0))
        report["samples"] += 1
        for task in tasks[:]:
            code, body = request(api + "/api/analyses/" + task["id"], token=token)
            require(code == 200, "Polling falhou")
            if body["status"] in ("COMPLETED", "FAILED"):
                if body["status"] == "FAILED":
                    report["failed_analysis"] = {"id": task["id"], "failure_stage": body["failureStage"]}
                    raise RuntimeError("Uma análise falhou; repetir após resolver origem/ambiente")
                require(body["semanticStatus"] == "DEGRADED", "IA indisponível não produziu DEGRADED")
                require(body["filesProcessed"] == body["filesTotal"] > 0, "Cobertura determinística incompleta")
                attempts = int(stack.sql("SELECT attempt_count FROM analyses WHERE id='" + task["id"] + "';"))
                require(attempts == (2 if task is lease else 1), "Duplicata ou lease produziram tentativas inesperadas")
                unsafe_snippets.extend(f["snippet"] for f in body["findings"] if len(f["snippet"]) >= 20)
                report.setdefault("analyses", []).append({"analysis_id": task["id"], "status": body["status"],
                    "semantic_status": body["semanticStatus"], "suggestion_status": body["suggestionStatus"],
                    "files_processed": body["filesProcessed"], "files_total": body["filesTotal"],
                    "findings": len(body["findings"]), "attempts": attempts,
                    "elapsed_seconds": round(time.monotonic() - task["started"], 3)})
                tasks.remove(task)
        time.sleep(1)
    require(not tasks, "Análises não concluíram dentro do prazo")
    require(len({item["findings"] for item in report["analyses"]}) == 1,
            "Duplicata ou retomada modificou o total determinístico do mesmo SHA")
    require(report["active_attempts_peak"] == 2, "Não foi observado processamento concorrente")
    target = pair[0]["id"]
    require(request(api + "/api/analyses/" + target, token=stranger)[0] == 404, "Isolamento JWT falhou")
    require(request(api + "/api/analyses/" + target)[0] == 401, "Consulta anônima foi aceita")
    require(request(api + "/api/analyses/tasks/" + target + "/ack", "POST", token=stranger)[0] == 204,
            "Confirmação estrangeira não respondeu conforme contrato")
    require(stack.sql("SELECT task_acknowledged FROM analyses WHERE id='" + target + "';") == "f",
            "Confirmação estrangeira alterou tarefa")
    require(request(api + "/api/analyses/tasks", token=stranger)[1] == [], "Tarefas vazaram entre usuários")
    history_path = "/api/analyses/systems/" + "/".join(urllib.parse.urlparse(repository).path.strip("/").split("/")) + "/history"
    require(request(api + history_path, token=stranger)[1]["history"] == [], "Histórico vazou entre usuários")
    logs = stack.command("logs", "--no-color", *SERVICES)
    require(not any(value in logs or json.dumps(value, ensure_ascii=False)[1:-1] in logs
                    for value in stack.secrets + unsafe_snippets), "Auditoria encontrou dado sensível em logs")
    report["logs_sensitive_values_detected"] = 0
    report["jwt_isolation"] = "passed"
    report["duplicates"] = "no_extra_attempt_or_findings"
    report["lease_recovery"] = "passed"
    stack.command("start", "ollama")
    require(json.loads(stack.command("exec", "-T", "worker", "curl", "-fsS",
                                     "http://ollama:11434/api/version"))["version"], "Ollama não recuperou")
    report["ollama_restored"] = True
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository-url", default="https://github.com/EduDalla/plataforma-sast")
    parser.add_argument("--reference", default="main")
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--output", type=Path, required=True, help="Arquivo JSON de evidência derivada")
    args = parser.parse_args()
    match = re.fullmatch(r"https://github.com/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)", args.repository_url)
    require(match is not None, "Informe URL HTTPS pública do GitHub")
    base = "https://api.github.com/repos/" + "/".join(match.groups())
    code, metadata = request(base)
    require(code == 200 and metadata["private"] is False, "Repositório deve ser público")
    code, commit = request(base + "/commits/" + urllib.parse.quote(args.reference, safe=""))
    require(code == 200, "Referência pública não resolvida")
    sha = commit["sha"]
    require(re.fullmatch(r"[0-9a-f]{40}", sha) is not None, "SHA inválido")
    require(not args.output.exists(), "Arquivo de evidência já existe; escolha outro destino")
    report = {"started_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "status": "FAILED"}
    with tempfile.TemporaryDirectory(prefix="sast-o01-") as directory:
        stack = ComposeRun(directory)
        try:
            run_experiment(stack, args.repository_url, sha, args.timeout, report)
            report["status"] = "PASSED"
        except (RuntimeError, OSError, subprocess.SubprocessError) as failure:
            # Não incluir objetos de subprocessos/HTTP (podem conter dados não confiáveis).
            report["failure_type"] = type(failure).__name__
            if isinstance(failure, RuntimeError):
                report["failure_reason"] = str(failure)
            try:
                report["diagnostics"] = stack.diagnostics()
            except (RuntimeError, OSError, subprocess.SubprocessError):
                report["diagnostics"] = {"available": False}
        finally:
            try:
                stack.command("down", "--volumes", "--remove-orphans", timeout=180)
                report["isolated_project_removed"] = True
            except (RuntimeError, OSError, subprocess.SubprocessError):
                report["isolated_project_removed"] = False
                report["cleanup_project"] = stack.project
                report["status"] = "FAILED"
        report["finished_at"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("x", encoding="utf-8") as target:
            target.write(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "PASSED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
