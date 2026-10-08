#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${SAST_LOCAL_ENV_FILE:-${PROJECT_ROOT}/.env}"
BUILD_ROOT="$(mktemp -d -t sast-local-ci-XXXXXX)"

cleanup() {
  rm -rf -- "${BUILD_ROOT}"
}
trap cleanup EXIT

read_env_value() {
  local key="$1"
  if [[ ! -f "${ENV_FILE}" ]]; then
    return 0
  fi
  awk -v wanted="${key}" '
    index($0, wanted "=") == 1 {
      print substr($0, length(wanted) + 2)
      exit
    }
  ' "${ENV_FILE}"
}

required_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERRO: comando obrigatório ausente: $1" >&2
    exit 1
  }
}

required_command docker
required_command git
required_command mvn
required_command npm
required_command python3
required_command curl

cd "${PROJECT_ROOT}"

API_PORT_VALUE="${API_PORT:-$(read_env_value API_PORT)}"
API_PORT_VALUE="${API_PORT_VALUE:-8080}"
API_URL="${SAST_LOCAL_API_URL:-http://localhost:${API_PORT_VALUE}}"
GATE_EMAIL="${SAST_GATE_EMAIL:-${SAST_BOOTSTRAP_EMAIL:-$(read_env_value SAST_BOOTSTRAP_EMAIL)}}"
GATE_PASSWORD="${SAST_GATE_PASSWORD:-${SAST_BOOTSTRAP_PASSWORD:-$(read_env_value SAST_BOOTSTRAP_PASSWORD)}}"
COMMIT_SHA="${SAST_GATE_COMMIT_SHA:-$(git rev-parse HEAD)}"
REMOTE_URL="${SAST_GATE_REPOSITORY_URL:-$(git remote get-url origin)}"

if [[ "${REMOTE_URL}" =~ ^git@github\.com:(.+)/(.+)(\.git)?$ ]]; then
  OWNER="${BASH_REMATCH[1]}"
  REPOSITORY="${BASH_REMATCH[2]%.git}"
  REMOTE_URL="https://github.com/${OWNER}/${REPOSITORY}"
elif [[ "${REMOTE_URL}" =~ ^https://github\.com/(.+)/(.+)(\.git)?$ ]]; then
  OWNER="${BASH_REMATCH[1]}"
  REPOSITORY="${BASH_REMATCH[2]%.git}"
  REMOTE_URL="https://github.com/${OWNER}/${REPOSITORY}"
else
  echo "ERRO: o remote origin deve apontar para um repositório HTTPS ou SSH em github.com" >&2
  exit 1
fi

if [[ -z "${GATE_EMAIL}" || -z "${GATE_PASSWORD}" ]]; then
  echo "ERRO: configure SAST_GATE_EMAIL/SAST_GATE_PASSWORD ou SAST_BOOTSTRAP_EMAIL/SAST_BOOTSTRAP_PASSWORD no .env" >&2
  exit 1
fi

echo "== Demonstração local equivalente ao GitHub Actions =="
echo "Repositório: ${REMOTE_URL}"
echo "Commit: ${COMMIT_SHA}"
echo "API local: ${API_URL}"
echo "Credenciais: configuradas e ocultas"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "AVISO: o checkout possui alterações locais; os testes usam o checkout atual e o gate analisa o commit informado acima."
fi

echo
echo "[1/6] Validando Docker Compose"
docker compose config --quiet

echo
echo "[2/6] Verificando saúde da API local"
curl --silent --show-error --fail --max-time 10 "${API_URL}/health" >/dev/null

echo
echo "[3/6] Verificando backend"
MAVEN_OPTS="${MAVEN_OPTS:--Djdk.attach.allowAttachSelf=true}" \
  mvn --file src/backend/pom.xml verify \
  -Dsast.build.directory="${BUILD_ROOT}/sast-build"

echo
echo "[4/6] Instalando e construindo frontend"
npm --prefix src/frontend ci
npm --prefix src/frontend run build

echo
echo "[5/6] Executando testes do frontend e da política do gate"
npm --prefix src/frontend run test -- --run
python3 -m unittest discover -s scripts -p 'test_security_gate.py' -v

echo
echo "[6/6] Executando Security Gate contra a API local"
python3 scripts/security_gate.py \
  --api-url "${API_URL}" \
  --repository-url "${REMOTE_URL}" \
  --commit-sha "${COMMIT_SHA}" \
  --email "${GATE_EMAIL}" \
  --password "${GATE_PASSWORD}" \
  --policy .sast/security-gate.json

echo
echo "RESULTADO: demonstração local concluída com sucesso."
