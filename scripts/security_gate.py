#!/usr/bin/env python3
"""Executa o Security Gate sem executar o código do repositório analisado."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen


SHA_PATTERN = re.compile(r"^[0-9a-fA-F]{40}$")
REPOSITORY_PATTERN = re.compile(r"^https://github\.com/([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9_.-]{1,100})$")


class GateFailure(RuntimeError):
    """Falha segura que pode ser publicada sem conteúdo do alvo."""


@dataclass(frozen=True)
class GateConfig:
    """Configuração imutável usada durante uma execução do Security Gate."""

    api_url: str
    repository_url: str
    commit_sha: str
    email: str
    password: str
    timeout_seconds: int
    poll_seconds: int
    policy_path: Path


def request_json(url: str, method: str = "GET", token: str | None = None,
                payload: dict[str, Any] | None = None) -> tuple[int, dict[str, Any]]:
    """Executa uma requisição JSON autenticada contra a API do SAST.

    :param url: endpoint HTTPS ou URL base da requisição.
    :param method: método HTTP a ser utilizado.
    :param token: JWT Bearer opcional para rotas autenticadas.
    :param payload: corpo JSON opcional da requisição.
    :return: tupla com status HTTP e corpo JSON decodificado.
    :raises GateFailure: quando a API, a rede ou o JSON retornado falha.
    """
    body = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(url, data=body, headers=headers, method=method)
    try:
        with urlopen(request, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw or b"{}")
    except HTTPError as error:
        raise GateFailure(f"API respondeu HTTP {error.code} em uma etapa do gate") from error
    except (URLError, TimeoutError, json.JSONDecodeError) as error:
        raise GateFailure("não foi possível consultar a API do SAST") from error


def normalize_repository(value: str) -> tuple[str, str, str]:
    """Valida e normaliza uma URL HTTPS de repositório público do GitHub.

    :param value: URL candidata no formato ``https://github.com/OWNER/REPO``.
    :return: proprietário, nome do repositório e URL normalizada.
    :raises GateFailure: quando a URL não atende ao contrato de origem.
    """
    match = REPOSITORY_PATTERN.fullmatch(value.rstrip("/"))
    if not match:
        raise GateFailure("repository URL must be an HTTPS public GitHub repository")
    owner, repository = match.groups()
    return owner, repository, f"https://github.com/{owner}/{repository}"


def fingerprint(finding: dict[str, Any]) -> str:
    """Calcula a identidade estável de um finding determinístico.

    :param finding: objeto JSON com regra, arquivo, linha e coluna.
    :return: SHA-256 da identidade ``ruleId:fileName:line:column``.
    """
    identity = "\x1f".join(str(finding.get(key, "")) for key in
                            ("ruleId", "fileName", "line", "column"))
    return hashlib.sha256(identity.encode("utf-8")).hexdigest()


def login(config: GateConfig) -> str:
    """Autentica a identidade protegida usada pelo pipeline.

    :param config: configuração com URL da API e credenciais do CI.
    :return: JWT Bearer emitido pela API.
    :raises GateFailure: quando a autenticação não retorna um token válido.
    """
    status, body = request_json(config.api_url + "/api/auth/login", "POST",
                                 payload={"email": config.email, "password": config.password})
    token = body.get("accessToken")
    if status != 200 or not isinstance(token, str) or not token:
        raise GateFailure("SAST service authentication failed")
    return token


def create_analysis(config: GateConfig, token: str) -> str:
    """Solicita uma análise fixada no SHA do commit do PR.

    :param config: configuração com repositório e SHA alvo.
    :param token: JWT Bearer da identidade do pipeline.
    :return: UUID da análise aceita pela API.
    :raises GateFailure: quando a API não aceita a análise como assíncrona.
    """
    status, body = request_json(config.api_url + "/api/analyses", "POST", token,
                                {"repositoryUrl": config.repository_url,
                                 "reference": config.commit_sha})
    analysis_id = body.get("analysisId")
    if status != 202 or not isinstance(analysis_id, str):
        raise GateFailure("SAST analysis was not accepted")
    return analysis_id


def poll_analysis(config: GateConfig, token: str, analysis_id: str) -> dict[str, Any]:
    """Consulta uma análise até estado terminal ou expiração do timeout.

    :param config: configuração com timeout e intervalo de polling.
    :param token: JWT Bearer da identidade do pipeline.
    :param analysis_id: UUID retornado na criação da análise.
    :return: resultado JSON em estado ``COMPLETED`` ou ``FAILED``.
    :raises GateFailure: quando a análise expira ou a API fica indisponível.
    """
    deadline = time.monotonic() + config.timeout_seconds
    endpoint = config.api_url + "/api/analyses/" + quote(analysis_id, safe="")
    while time.monotonic() < deadline:
        _, result = request_json(endpoint, token=token)
        status = result.get("status")
        if status in {"COMPLETED", "FAILED"}:
            return result
        time.sleep(config.poll_seconds)
    raise GateFailure("SAST analysis timed out before a terminal state")


def previous_findings(config: GateConfig, token: str, current_id: str,
                      owner: str, repository: str) -> list[dict[str, Any]]:
    """Obtém os findings da execução concluída anterior do mesmo repositório.

    :param config: configuração da API do SAST.
    :param token: JWT Bearer da identidade do pipeline.
    :param current_id: UUID da análise atual, que deve ser ignorada no baseline.
    :param owner: proprietário do repositório no GitHub.
    :param repository: nome do repositório no GitHub.
    :return: findings da execução anterior ou lista vazia quando não há baseline.
    :raises GateFailure: quando a API não pode consultar o histórico.
    """
    path = f"/api/analyses/systems/{quote(owner, safe='')}/{quote(repository, safe='')}" \
           "/history?page=0&size=20"
    _, history = request_json(config.api_url + path, token=token)
    entries = history.get("history", [])
    for entry in entries:
        previous_id = entry.get("analysisId")
        if previous_id and previous_id != current_id:
            _, result = request_json(config.api_url + "/api/analyses/" + quote(previous_id, safe=""),
                                     token=token)
            if result.get("status") == "COMPLETED":
                return result.get("findings", [])
    return []


def evaluate(config: GateConfig, result: dict[str, Any], baseline: list[dict[str, Any]]) -> dict[str, Any]:
    """Aplica a política determinística ao resultado da análise.

    :param config: configuração com repositório e SHA esperados.
    :param result: resultado JSON da análise atual.
    :param baseline: findings da execução concluída anterior.
    :return: resumo seguro com decisão, contagens e localizações mínimas.
    :raises GateFailure: quando origem, SHA, estado ou cobertura são inválidos.
    """
    _, _, expected_repository = normalize_repository(config.repository_url)
    if result.get("repositoryUrl") != expected_repository:
        raise GateFailure("analysis repository does not match the requested repository")
    if result.get("commitSha", "").lower() != config.commit_sha.lower():
        raise GateFailure("analysis commit SHA does not match the requested commit")
    if result.get("status") != "COMPLETED" or result.get("stage") != "COMPLETED":
        raise GateFailure("analysis did not complete deterministically")
    if result.get("filesTotal", 0) <= 0 or result.get("filesProcessed") != result.get("filesTotal"):
        raise GateFailure("deterministic analysis coverage is incomplete")

    baseline_ids = {fingerprint(item) for item in baseline}
    findings = result.get("findings", [])
    new_critical = [item for item in findings
                    if item.get("severity") == "Critical" and fingerprint(item) not in baseline_ids]
    high = sum(1 for item in findings if item.get("severity") == "High")
    return {
        "status": "FAIL" if new_critical else "PASS",
        "analysisId": result.get("analysisId"),
        "commitSha": result.get("commitSha"),
        "findings": len(findings),
        "baselineFindings": len(baseline),
        "newCritical": len(new_critical),
        "high": high,
        "semanticStatus": result.get("semanticStatus"),
        "suggestionStatus": result.get("suggestionStatus"),
        "locations": [
            {"ruleId": item.get("ruleId"), "fileName": item.get("fileName"),
             "line": item.get("line"), "column": item.get("column")}
            for item in new_critical
        ],
    }


def run(config: GateConfig) -> int:
    """Executa autenticação, análise, polling, baseline e decisão do gate.

    :param config: configuração completa da execução do Security Gate.
    :return: zero para aprovação ou um para bloqueio por Critical novo.
    :raises GateFailure: quando a análise ou a política não pode ser validada.
    """
    policy = json.loads(config.policy_path.read_text(encoding="utf-8"))
    if policy.get("blocking", {}).get("newCriticalDeterministic") is not True:
        raise GateFailure("gate policy does not enable deterministic Critical blocking")
    owner, repository, _ = normalize_repository(config.repository_url)
    token = login(config)
    analysis_id = create_analysis(config, token)
    result = poll_analysis(config, token, analysis_id)
    if result.get("status") == "FAILED":
        raise GateFailure("analysis finished in FAILED state")
    baseline = previous_findings(config, token, analysis_id, owner, repository)
    summary = evaluate(config, result, baseline)
    print(json.dumps(summary, ensure_ascii=False, separators=(",", ":")))
    return 1 if summary["status"] == "FAIL" else 0


def parse_args() -> GateConfig:
    """Lê argumentos de linha de comando e monta a configuração do gate.

    :return: configuração validada para a execução do Security Gate.
    :raises SystemExit: quando um argumento obrigatório é inválido ou ausente.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-url", required=True)
    parser.add_argument("--repository-url", required=True)
    parser.add_argument("--commit-sha", required=True)
    parser.add_argument("--email", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--timeout-seconds", type=int, default=900)
    parser.add_argument("--poll-seconds", type=int, default=10)
    parser.add_argument("--policy", default=".sast/security-gate.json")
    args = parser.parse_args()
    if not SHA_PATTERN.fullmatch(args.commit_sha):
        parser.error("--commit-sha must be a 40-character hexadecimal SHA")
    return GateConfig(args.api_url.rstrip("/"), args.repository_url.rstrip("/"), args.commit_sha,
                      args.email, args.password, args.timeout_seconds, args.poll_seconds,
                      Path(args.policy))


if __name__ == "__main__":
    try:
        sys.exit(run(parse_args()))
    except (GateFailure, OSError, json.JSONDecodeError) as error:
        print(json.dumps({"status": "FAIL", "reason": str(error)}, ensure_ascii=False), file=sys.stderr)
        sys.exit(1)
