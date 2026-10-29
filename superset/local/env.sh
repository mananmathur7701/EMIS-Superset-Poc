#!/usr/bin/env bash
# Settings for running Superset on this machine (no Docker). The other scripts in this folder load
# this file first. Passwords come from the project's .env file.

# The project root: two folders up from this file.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# Make it available to the other scripts.
export PROJECT_ROOT
# Read the passwords from .env, if there is one (set -a exports every variable it defines).
if [ -f "$PROJECT_ROOT/.env" ]; then set -a; . "$PROJECT_ROOT/.env"; set +a; fi

# The Python environment that holds Superset (created by setup.sh).
export SUPERSET_VENV="$PROJECT_ROOT/superset/.venv"
# Superset reads our settings from this file.
export SUPERSET_CONFIG_PATH="$PROJECT_ROOT/superset/superset_config.py"
# The command line tool "superset" needs to know where the app is.
export FLASK_APP=superset

# Secret for Superset's cookies. Keep it stable: it also encrypts the database passwords Superset stores.
export SUPERSET_SECRET_KEY="${SUPERSET_SECRET_KEY:-dev-only-superset-secret-change-me-0123456789}"
# Secret for signing dashboard guest tokens ("visitor passes").
export SUPERSET_GUEST_TOKEN_SECRET="${SUPERSET_GUEST_TOKEN_SECRET:-dev-only-guest-token-secret-change-me}"
# Secret shared with the backend for the CPU analyst's automatic sign-in (same default as application.yml).
export EMIS_SSO_SECRET="${EMIS_SSO_SECRET:-dev-only-sso-secret-change-me-0123456789}"

# The one database (your local Postgres).
DB_HOST="${EMIS_DB_HOST:-127.0.0.1}"
# Its port.
DB_PORT="${EMIS_DB_PORT:-5432}"
# Its name.
DB_NAME="${EMIS_DB_NAME:-emis_superset_app}"
# Where Superset keeps its own tables: the "superset" schema, through the superset login.
export SUPERSET_METADATA_DB_URI="postgresql+psycopg2://superset:${SUPERSET_DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
# How Superset reads the census data: the read-only emis_reader login (used by provision.py).
export EMIS_READER_URI="postgresql+psycopg2://emis_reader:${EMIS_READER_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

# The frontend address allowed to show Superset in a frame (npm run dev always uses port 3000).
export EMBED_HOST_ORIGINS="${LOCAL_FRONTEND_URL:-http://localhost:3000}"
# The port Superset listens on.
export SUPERSET_PORT="${SUPERSET_PORT:-8088}"
# Superset's address, for provision.py.
export SUPERSET_URL="http://127.0.0.1:${SUPERSET_PORT}"
# The backend's address, for provision.py (IntelliJ / mvn spring-boot:run use port 8081).
export BACKEND_URL="${LOCAL_BACKEND_URL:-http://127.0.0.1:8081}"

# Superset's admin login.
export SUPERSET_ADMIN_USERNAME="${SUPERSET_ADMIN_USERNAME:-admin}"
# Its password.
export SUPERSET_ADMIN_PASSWORD="${SUPERSET_ADMIN_PASSWORD:-admin}"
# Its email.
export SUPERSET_ADMIN_EMAIL="${SUPERSET_ADMIN_EMAIL:-admin@emis.local}"
