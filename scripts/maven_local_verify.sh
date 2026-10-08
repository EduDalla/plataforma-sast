#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_ROOT="$(mktemp -d -t cp1-cyber-maven-XXXXXX)"

cleanup() {
  rm -rf -- "${BUILD_ROOT}"
}
trap cleanup EXIT

if [[ ! -w "${BUILD_ROOT}" ]]; then
  echo "ERRO: o diretório temporário do Maven não está gravável pelo usuário atual." >&2
  exit 1
fi

cd "${PROJECT_ROOT}"

echo "Usando diretório temporário de build: ${BUILD_ROOT}"
echo "O conteúdo de src/backend/target não será alterado."

MAVEN_OPTS="${MAVEN_OPTS:--Djdk.attach.allowAttachSelf=true}" \
  mvn --file src/backend/pom.xml verify \
  -Dsast.build.directory="${BUILD_ROOT}"
