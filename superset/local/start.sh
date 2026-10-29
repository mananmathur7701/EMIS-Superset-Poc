#!/usr/bin/env bash
# Starts Superset on http://localhost:8088 (and http://embed.localhost:8088 for embedded dashboards).
# Stop it with Ctrl-C.

# This script needs bash. If it was started with "sh script.sh" (on Ubuntu, sh is a smaller shell
# without features such as pipefail), start it again with bash.
if [ -z "${BASH_VERSION:-}" ]; then exec bash "$0" "$@"; fi

# Stop at the first failing command.
set -euo pipefail
# Load the settings.
. "$(dirname "${BASH_SOURCE[0]}")/env.sh"

# A second copy can't use the same port: if Superset already answers there, say so and stop.
if curl -s -m 2 "http://127.0.0.1:${SUPERSET_PORT}/health" | grep -q OK; then
    # Explain what to do instead.
    echo "[superset] Superset is already running on http://localhost:${SUPERSET_PORT} (stop it with Ctrl-C in its terminal tab first)"
    # Nothing more to do.
    exit 0
fi

# Say what is happening.
echo "[superset] Starting Superset on 127.0.0.1:${SUPERSET_PORT} with settings ${SUPERSET_CONFIG_PATH}"
# Run Superset's web server: 2 processes with 8 threads each, every request logged to this window.
exec "$SUPERSET_VENV/bin/gunicorn" --bind "127.0.0.1:${SUPERSET_PORT}" --workers 2 --worker-class gthread \
    --threads 8 --timeout 120 --access-logfile - --error-logfile - "superset.app:create_app()"
