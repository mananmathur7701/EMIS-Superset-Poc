#!/usr/bin/env bash
# Prepares Superset's own database and admin user. Runs once per start, before Superset.

# Stop at the first failing command.
set -euo pipefail

# Create or upgrade Superset's own tables.
superset db upgrade

# If the admin user already exists ...
if superset fab list-users 2>/dev/null | grep -q "username:${SUPERSET_ADMIN_USERNAME} "; then
    # ... just say so.
    echo "Admin user '${SUPERSET_ADMIN_USERNAME}' already exists"
# Otherwise ...
else
    # ... create the admin user with the details from docker-compose.yml.
    superset fab create-admin \
        --username "${SUPERSET_ADMIN_USERNAME}" \
        --password "${SUPERSET_ADMIN_PASSWORD}" \
        --firstname Superset \
        --lastname Admin \
        --email "${SUPERSET_ADMIN_EMAIL}"
fi

# Create Superset's built-in roles and permissions (Admin, Gamma, ...).
superset init
