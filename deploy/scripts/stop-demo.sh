#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

docker compose --env-file .env -p maoyan-app -f deploy/docker/compose.app.yml down
docker compose --env-file .env -p maoyan-infra -f deploy/docker/compose.infra.yml down
