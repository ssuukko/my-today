#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  printf '%s\n' 'Copy .env.example to .env and set POSTGRES_PASSWORD, DB_PASSWORD and REGISTRATION_INVITE_TOKEN first.' >&2
  exit 1
fi
docker compose --env-file .env -f infra/compose.local.yml up -d --build --wait

