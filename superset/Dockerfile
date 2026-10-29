# Start from the official Superset 6.1.0 image.
FROM apache/superset:6.1.0

# Switch to root to install software.
USER root
# Install the Postgres driver, which the official image does not include.
RUN uv pip install --python /app/.venv/bin/python --no-cache psycopg2-binary==2.9.13

# Add our Superset settings where Superset looks for them.
COPY --chown=superset:superset superset_config.py /app/pythonpath/superset_config.py
# Add our init and provisioning scripts, and make them runnable.
COPY --chown=superset:superset --chmod=755 scripts/ /app/emis/

# Switch back to Superset's normal user.
USER superset
