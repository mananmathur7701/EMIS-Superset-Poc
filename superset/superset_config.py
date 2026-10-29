"""Superset settings for the EMIS census POC. Secrets come from environment variables."""

# copy lets us change a copy of Superset's default security settings.
import copy
# os reads environment variables.
import os

# Superset's default security-header and theme settings, which we extend below.
from superset.config import TALISMAN_CONFIG as DEFAULT_TALISMAN_CONFIG
from superset.config import THEME_DEFAULT as SUPERSET_THEME_DEFAULT

# Secret key Superset uses to sign its cookies.
SECRET_KEY = os.environ["SUPERSET_SECRET_KEY"]
# Where Superset keeps its own data (users, charts, dashboards): the "superset" Postgres database.
SQLALCHEMY_DATABASE_URI = os.environ["SUPERSET_METADATA_DB_URI"]

# The EMIS frontend address(es) that may show Superset inside an iframe, comma-separated.
FRONTEND_ORIGINS = os.environ.get("EMBED_HOST_ORIGINS", "http://localhost:3000").split(",")

FEATURE_FLAGS = {
    # Turn on dashboard embedding (needed by the embedded SDK).
    "EMBEDDED_SUPERSET": True,
    # Draw every chart as soon as the dashboard opens, instead of only when it scrolls into view.
    # Otherwise charts show a loader again each time they come back into view.
    "DASHBOARD_VIRTUALIZATION": False,
}

# The EMIS loading spinner: a grey ring with a turning blue arc. Superset shows it instead of its
# own logo animation everywhere (dashboard, charts, Explore), so loading looks like the EMIS app.
EMIS_SPINNER_SVG = (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 50 50">'
    '<circle cx="25" cy="25" r="20" fill="none" stroke="#E3E6EB" stroke-width="4"/>'
    '<circle cx="25" cy="25" r="20" fill="none" stroke="#1F5FBF" stroke-width="4" '
    'stroke-linecap="round" stroke-dasharray="40 86">'
    '<animateTransform attributeName="transform" type="rotate" from="0 25 25" to="360 25 25" '
    'dur="0.8s" repeatCount="indefinite"/></circle></svg>'
)

# Superset's look, matched to the EMIS 2.0 design (colours, font and corners from its tokens).
# Start from a copy of Superset's default theme ...
THEME_DEFAULT = copy.deepcopy(SUPERSET_THEME_DEFAULT)
# ... and change only these settings.
THEME_DEFAULT["token"].update({
    # Window title.
    "brandAppName": "EMIS Census Analytics",
    # Our spinner instead of Superset's.
    "brandSpinnerSvg": EMIS_SPINNER_SVG,
    # EMIS blue for buttons, links and highlights.
    "colorPrimary": "#1F5FBF",
    "colorLink": "#1F5FBF",
    # EMIS text colours.
    "colorText": "#1B2430",
    "colorTextSecondary": "#526071",
    # EMIS light grey page background and border colour.
    "colorBgLayout": "#F6F7F9",
    "colorBorder": "#E3E6EB",
    "colorBorderSecondary": "#E3E6EB",
    # EMIS font and corner rounding.
    "fontFamily": "Inter, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
    "borderRadius": 6,
})
# EMIS is light only, so switch Superset's dark theme off.
THEME_DARK = None

# Guests (embedded dashboard viewers) get the read-only "Gamma" permissions, limited further to
# the dashboard and rows named in their guest token.
GUEST_ROLE_NAME = "Gamma"
# Secret used to sign guest tokens.
GUEST_TOKEN_JWT_SECRET = os.environ["SUPERSET_GUEST_TOKEN_SECRET"]
# Guest tokens expire after 5 minutes; the SDK fetches a new one automatically.
GUEST_TOKEN_JWT_EXP_SECONDS = 300
# A fixed "audience" value in every guest token. The backend calls Superset by its Docker name
# while the browser uses localhost, so Superset's address-based default would never match.
GUEST_TOKEN_JWT_AUDIENCE = "emis-superset"

# Keep Superset's security headers switched on.
TALISMAN_ENABLED = True
# Start from a copy of Superset's default security headers.
TALISMAN_CONFIG = copy.deepcopy(DEFAULT_TALISMAN_CONFIG)
# Allow only Superset itself and the EMIS frontend to show Superset pages in an iframe.
TALISMAN_CONFIG["content_security_policy"]["frame-ancestors"] = ["'self'", *FRONTEND_ORIGINS]
# Drop the older X-Frame-Options header, which cannot allow a different address.
TALISMAN_CONFIG["frame_options"] = None

# The frontend and Superset are both on "localhost" (the same site), so the analyst's Superset
# login cookie still works inside the builder iframe.
SESSION_COOKIE_SAMESITE = "Lax"

# ---------------------------------------------------------------------------------------------
# Automatic sign-in for the CPU analyst's builder.
# The EMIS backend gives the analyst a link to /emis/sso?token=...&next=/explore/... The token is
# a JWT signed with EMIS_SSO_SECRET (the backend has the same secret) that works for one minute.
# This route checks it, signs the analyst in to Superset as her own user (created the first time),
# and opens the page in "next".
# ---------------------------------------------------------------------------------------------

# timedelta describes how long the Superset login lasts.
from datetime import timedelta

# Secret shared with the EMIS backend (superset.sso-secret there).
EMIS_SSO_SECRET = os.environ.get("EMIS_SSO_SECRET", "")
# A Superset login made through this route ends after 8 hours at the latest. Logging out of the
# EMIS app also opens Superset's /logout/, which ends it at once.
PERMANENT_SESSION_LIFETIME = timedelta(hours=8)


def emis_sso_login():
    """Checks the EMIS token, signs the analyst in to Superset and redirects to the requested page."""
    # These imports need a running Superset app, so they are made here.
    import jwt
    from flask import abort, redirect, request, session
    from flask_login import login_user
    from superset.extensions import security_manager

    # Without a shared secret the route is switched off.
    if not EMIS_SSO_SECRET:
        # Answer 404 Not Found.
        abort(404)
    # Check the token: right signature, not expired, and meant for this route.
    try:
        # Now we will decode and check it.
        claims = jwt.decode(request.args.get("token", ""), EMIS_SSO_SECRET,
                            algorithms=["HS256"], audience="superset-sso")
    # Wrong, expired or missing token.
    except jwt.PyJWTError:
        # Answer 403 Forbidden.
        abort(403, "This sign-in link is invalid or has expired. Open the builder again from EMIS.")

    # Look for the Superset user with the analyst's username.
    user = security_manager.find_user(username=claims["sub"])
    # The first time, create it with the Admin role (builds charts and dashboards).
    if user is None:
        # Now we will create the user (no password: it can only sign in through this route).
        user = security_manager.add_user(
            username=claims["sub"], first_name=claims.get("first_name") or claims["sub"],
            last_name=claims.get("last_name") or "-", email=claims["email"],
            role=security_manager.find_role("Admin"))
    # A switched-off Superset user may not sign in.
    if not user or not user.is_active:
        # Answer 403 Forbidden.
        abort(403, "This Superset user is not active.")

    # Make the login cookie follow PERMANENT_SESSION_LIFETIME.
    session.permanent = True
    # Now we will sign the user in to Superset.
    login_user(user, remember=False)
    # Record the login in Superset's user statistics.
    security_manager.update_user_auth_stat(user)

    # The page to open next; only a path on this Superset is allowed (never another site).
    next_page = request.args.get("next", "/")
    # Refuse anything that is not a plain path, such as "//evil.example" or a full address.
    if not next_page.startswith("/") or next_page.startswith("//") or "\\" in next_page:
        # Fall back to Superset's home page.
        next_page = "/"
    # Now we will send the browser to that page.
    return redirect(next_page)


def FLASK_APP_MUTATOR(app):  # noqa: N802 (Superset's setting name)
    """Superset calls this once at startup; it adds our sign-in route."""
    # Answer GET /emis/sso with emis_sso_login.
    app.add_url_rule("/emis/sso", "emis_sso_login", emis_sso_login, methods=["GET"])
