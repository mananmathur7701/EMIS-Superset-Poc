#!/usr/bin/env bash
# One-time setup: installs Superset 6.1.0 in a Python environment inside this project, then
# prepares its tables and admin user. Safe to run again (it only adds what is missing).

# This script needs bash. If it was started with "sh script.sh" (on Ubuntu, sh is a smaller shell
# without features such as pipefail), start it again with bash.
if [ -z "${BASH_VERSION:-}" ]; then exec bash "$0" "$@"; fi

# Stop at the first failing command.
set -euo pipefail
# Load the settings.
. "$(dirname "${BASH_SOURCE[0]}")/env.sh"

# Create the Python environment the first time.
if [ ! -x "$SUPERSET_VENV/bin/python" ]; then
    # Say what is happening.
    echo "[setup] Creating the Python environment in $SUPERSET_VENV"
    # Create it with this machine's Python 3.
    python3 -m venv "$SUPERSET_VENV"
fi

# Say what is happening.
echo "[setup] Installing Superset 6.1.0 and the Postgres driver (a few minutes the first time)"
# Update pip itself first.
"$SUPERSET_VENV/bin/pip" install --quiet --upgrade pip
# Install Superset (the same version as before) with every package at the exact version the
# working Docker image used (requirements.txt, which also has the Postgres driver).
"$SUPERSET_VENV/bin/pip" install --quiet -r "$PROJECT_ROOT/superset/local/requirements.txt" "apache-superset==6.1.0"

# Say what is happening.
echo "[setup] Creating or upgrading Superset's own tables (schema superset)"
# Create or upgrade Superset's tables.
"$SUPERSET_VENV/bin/superset" db upgrade

# If the admin user already exists ...
if "$SUPERSET_VENV/bin/superset" fab list-users 2>/dev/null | grep -q "username:${SUPERSET_ADMIN_USERNAME} "; then
    # ... just say so.
    echo "[setup] Admin user '${SUPERSET_ADMIN_USERNAME}' already exists"
# Otherwise ...
else
    # ... create it.
    "$SUPERSET_VENV/bin/superset" fab create-admin --username "$SUPERSET_ADMIN_USERNAME" \
        --password "$SUPERSET_ADMIN_PASSWORD" --firstname Superset --lastname Admin --email "$SUPERSET_ADMIN_EMAIL"
fi

# Say what is happening.
echo "[setup] Creating Superset's built-in roles and permissions"
# Create the built-in roles (Admin, Gamma, ...).
"$SUPERSET_VENV/bin/superset" init

# Done.
echo "[setup] Done. Start Superset with superset/local/start.sh"
