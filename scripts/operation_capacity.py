#!/usr/bin/env python3
"""TASK-12: mede capacidade do Compose com várias réplicas de worker.

O script observa apenas metadados da API, PostgreSQL, RabbitMQ e docker stats.
O snapshot remoto é tratado pelo produto como entrada de análise; este script
não clona, compila, testa nem executa o repositório informado.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import statistics
import subprocess
import time
import urllib.parse
import uuid

from operation_smoke import ComposeRun, request, require, wait_for


def percentile(values, fraction):
    ordered = sorted(values)
    if not ordered:
        return None
    index = min(len(ordered) - 1, round((len(ordered) - 1) * fraction))
    return round(ordered[index], 3)


def resolve_sha(repository):
    parsed = urllib.parse.urlparse(repository)
    parts = parsed.path.strip("/").split("/")
    code, body = request("https://api.github.com/repos/" + "/".join(parts))
    require(code == 200 and body["private"] is False, "Repositório deve ser público")
    reference = body["default_branch"]
    code, commit = request("https://api.github.com/repos/" + "/".join(parts)
                           + "/commits/" + urllib.parse.quote(reference, safe=""))
    require(code == 200 and len(commit["sha"]) == 40, "SHA público não resolvido")
    return commit["sha"]


def run(stack, repository, reference, workers, amount, timeout, report):
    stack.command("up", "--build", "-d", "--wait", "--wait-timeout", "180",
                  "--scale", "worker=" + str(workers), timeout=900)
    api = stack.url("api", 8080)
    require(request(api + "/health")[1]["status"] == "UP", "Health da API não está UP")
    wait_for(lambda: stack.queue()["consumers"] == workers * 2,
             label="consumidores das réplicas de worker")
    password = "capacity-" + uuid.uuid4().hex
    account = {"email": "capacity-" + uuid.uuid4().hex + "@operation.test", "password": password}
    require(request(api + "/api/auth/register", "POST", account)[0] == 201, "Cadastro falhou")
    code, login = request(api + "/api/auth/login", "POST", account)
    require(code == 200, "Login falhou")
    token = login["accessToken"]
    submitted = []
    for _ in range(amount):
        started = time.monotonic()
        code, body = request(api + "/api/analyses", "POST",
                             {"repositoryUrl": repository, "reference": reference}, token)
        require(code == 202, "Submissão não retornou 202")
        submitted.append({"id": str(uuid.UUID(body["analysisId"])), "started": started})

    latencies = []
    queue_ready_peak = 0
    queue_unacked_peak = 0
    memory_peak = {}
    samples = 0
    deadline = time.monotonic() + timeout
    while submitted and time.monotonic() < deadline:
        queue = stack.queue()
        queue_ready_peak = max(queue_ready_peak, queue["messages_ready"])
        queue_unacked_peak = max(queue_unacked_peak, queue["messages_unacknowledged"])
        for service, amount_bytes in stack.memory().items():
            memory_peak[service] = max(memory_peak.get(service, 0), amount_bytes)
        samples += 1
        for task in submitted[:]:
            code, body = request(api + "/api/analyses/" + task["id"], token=token)
            require(code == 200, "Polling falhou")
            if body["status"] in ("COMPLETED", "FAILED"):
                require(body["status"] == "COMPLETED", "Uma análise falhou")
                require(body["filesProcessed"] == body["filesTotal"] > 0,
                        "Cobertura determinística incompleta")
                latency = time.monotonic() - task["started"]
                latencies.append(latency)
                submitted.remove(task)
        time.sleep(1)
    require(not submitted, "A carga não concluiu dentro do prazo")
    elapsed = max(latencies) if latencies else 0
    report.update({
        "status": "PASSED",
        "workers": workers,
        "worker_consumers": workers * 2,
        "load": {"analyses": amount, "repository_url": repository, "reference": reference},
        "throughput": {"analyses_per_second": round(amount / elapsed, 3) if elapsed else 0,
                       "wall_seconds": round(elapsed, 3)},
        "latency_seconds": {"min": round(min(latencies), 3), "mean": round(statistics.mean(latencies), 3),
                            "p50": percentile(latencies, .50), "p95": percentile(latencies, .95),
                            "p99": percentile(latencies, .99), "max": round(max(latencies), 3)},
        "queue": {"ready_peak": queue_ready_peak, "unacknowledged_peak": queue_unacked_peak},
        "memory_peak_observed_bytes": memory_peak,
        "samples": samples,
        "lease_recovery": "covered by BDD-OP-13 stale-worker fencing test; not inferred from throughput run",
    })


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository-url", default="https://github.com/EduDalla/plataforma-sast")
    parser.add_argument("--reference", default=None)
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--analyses", type=int, default=8)
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    require(1 <= args.workers <= 8, "workers deve estar entre 1 e 8")
    require(2 <= args.analyses <= 32, "analyses deve estar entre 2 e 32")
    require(not args.output.exists(), "Arquivo de evidência já existe; escolha outro destino")
    reference = args.reference or resolve_sha(args.repository_url)
    report = {"started_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "status": "FAILED"}
    with __import__("tempfile").TemporaryDirectory(prefix="sast-task12-") as directory:
        stack = ComposeRun(directory)
        try:
            run(stack, args.repository_url, reference, args.workers, args.analyses, args.timeout, report)
        except (RuntimeError, OSError, subprocess.SubprocessError) as failure:
            report["failure_type"] = type(failure).__name__
            report["failure_reason"] = str(failure) if isinstance(failure, RuntimeError) else "falha operacional"
            report["diagnostics"] = stack.diagnostics()
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
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "PASSED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
