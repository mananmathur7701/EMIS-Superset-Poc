#!/usr/bin/env bash
# Connects Superset to the census data, copies the KPI formulas into it, builds the "Census KPIs"
# dashboard the first time, and allows embedding. Needs Superset and the backend running.

# This script needs bash. If it was started with "sh script.sh" (on Ubuntu, sh is a smaller shell
# without features such as pipefail), start it again with bash.
if [ -z "${BASH_VERSION:-}" ]; then exec bash "$0" "$@"; fi

# Stop at the first failing command.
set -euo pipefail
# Load the settings.
. "$(dirname "${BASH_SOURCE[0]}")/env.sh"

# Say what is happening.
echo "[provision] Superset ${SUPERSET_URL}, backend ${BACKEND_URL}, embedding allowed from ${EMBED_HOST_ORIGINS}"
# Run the provisioning script with Superset's Python (it has the "requests" library).
exec "$SUPERSET_VENV/bin/python" "$PROJECT_ROOT/superset/scripts/provision.py"
