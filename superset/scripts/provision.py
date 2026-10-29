#!/usr/bin/env python
"""Sets up Superset for the EMIS census POC. Safe to run again and again.

1. Connect Superset to the EMIS database and register the analytics.census_frozen view.
2. Copy each KPI's formula from the backend (GET /api/analytics/kpis) into Superset as a metric.
3. If the "Census KPIs" dashboard does not exist yet, build it with charts and filters.
4. Allow the EMIS frontend to embed that dashboard.
"""

# json turns Python dictionaries into JSON text.
import json
# os reads settings from environment variables.
import os
# time lets us pause between retries.
import time

# requests sends HTTP requests.
import requests

# Superset's address inside Docker.
SUPERSET_URL = os.environ.get("SUPERSET_URL", "http://superset:8088")
# The backend's address inside Docker.
BACKEND_URL = os.environ.get("BACKEND_URL", "http://backend:8080")
# Superset admin username.
ADMIN_USERNAME = os.environ["SUPERSET_ADMIN_USERNAME"]
# Superset admin password.
ADMIN_PASSWORD = os.environ["SUPERSET_ADMIN_PASSWORD"]
# How Superset connects to the EMIS database, using the read-only login.
EMIS_READER_URI = os.environ["EMIS_READER_URI"]
# Frontend address(es) allowed to embed the dashboard, comma-separated.
EMBED_ALLOWED_DOMAINS = os.environ.get("EMBED_HOST_ORIGINS", "http://localhost:3000").split(",")
# Short name of the dashboard.
DASHBOARD_SLUG = os.environ.get("SUPERSET_DASHBOARD_SLUG", "census-kpis")

# Name Superset shows for the EMIS database connection.
DATABASE_NAME = "EMIS census (read-only)"
# Title shown on the dashboard.
DASHBOARD_TITLE = "Census KPIs"

# Friendly column names shown in charts instead of raw column names.
COLUMN_LABELS = {
    # district_code is shown as "District".
    "district_code": "District",
    # academic_year is shown as "Academic year".
    "academic_year": "Academic year",
    # school_level is shown as "School level".
    "school_level": "School level",
    # school_type is shown as "School type".
    "school_type": "School type",
    # school_name is shown as "School".
    "school_name": "School",
    # total_pupils is shown as "Total pupils".
    "total_pupils": "Total pupils",
    # total_teachers is shown as "Total teachers".
    "total_teachers": "Total teachers",
    # trained_teachers is shown as "Trained teachers".
    "trained_teachers": "Trained teachers",
}

# The dashboard's filter bar, as (column, label) pairs.
FILTERS = [
    # Filter by academic year.
    ("academic_year", "Academic year"),
    # Filter by district.
    ("district_code", "District"),
    # Filter by school level.
    ("school_level", "School level"),
    # Filter by school type.
    ("school_type", "School type"),
]

# Metric fields Superset accepts back when we update a dataset.
METRIC_FIELDS = ["id", "metric_name", "verbose_name", "metric_type", "expression",
                 "description", "d3format", "currency", "extra", "warning_text"]
# Column fields Superset accepts back when we update a dataset.
COLUMN_FIELDS = ["id", "column_name", "type", "verbose_name", "description", "expression",
                 "filterable", "groupby", "is_active", "is_dttm", "python_date_format",
                 "extra", "advanced_data_type"]

# One HTTP session: it keeps the login token and cookies for every request.
session = requests.Session()


def wait_for(url, name):
    """Waits until a URL answers, so we do not start before Superset and the backend are up."""
    # Try up to 200 times, 3 seconds apart (10 minutes).
    for _ in range(200):
        # Connection errors just mean "not up yet", so catch them.
        try:
            # If the URL answers OK ...
            if requests.get(url, timeout=5).ok:
                # ... stop waiting.
                return
        # Ignore the connection error and try again.
        except requests.RequestException:
            pass
        # Now we will print that we are still waiting.
        print(f"Waiting for {name} ...", flush=True)
        # Pause 3 seconds.
        time.sleep(3)
    # Give up and stop the script with an error.
    raise SystemExit(f"{name} did not start at {url}")


def call(method, path, **options):
    """Sends one request to Superset and returns its JSON reply. Stops the script on an error."""
    # Now we will send the request.
    response = session.request(method, SUPERSET_URL + path, timeout=60, **options)
    # If Superset reports an error ...
    if response.status_code >= 400:
        # ... stop the script and show what went wrong.
        raise SystemExit(f"{method} {path} failed with {response.status_code}: {response.text[:800]}")
    # Return the JSON reply, or an empty dictionary if there is no body.
    return response.json() if response.content else {}


def find(resource, column, value):
    """Returns the first Superset item (database, dataset or chart) whose column equals value."""
    # Build Superset's search query, written in its "rison" format.
    query = f"(filters:!((col:{column},opr:eq,value:'{value}')))"
    # Now we will run the search.
    result = call("GET", f"/api/v1/{resource}/", params={"q": query})["result"]
    # Return the first match, or None if nothing matched.
    return result[0] if result else None


def keep_fields(item, allowed):
    """Returns a copy of item with only the allowed fields that have a value."""
    # Keep a field only if it is allowed and not empty.
    return {key: value for key, value in item.items() if key in allowed and value is not None}


def login():
    """Logs in as the Superset admin and prepares the session for changes."""
    # Superset's login details.
    credentials = {"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD, "provider": "db", "refresh": False}
    # Now we will log in and read the access token.
    token = call("POST", "/api/v1/security/login", json=credentials)["access_token"]
    # Send the token with every later request.
    session.headers["Authorization"] = f"Bearer {token}"
    # Superset also wants a CSRF token on requests that change things, so fetch one.
    csrf_token = call("GET", "/api/v1/security/csrf_token/")["result"]
    # Send the CSRF token with every later request.
    session.headers["X-CSRFToken"] = csrf_token
    # Say where requests come from, which Superset also checks.
    session.headers["Referer"] = SUPERSET_URL


def setup_database():
    """Connects Superset to the EMIS database and returns the connection's id."""
    # The connection details.
    payload = {"database_name": DATABASE_NAME, "sqlalchemy_uri": EMIS_READER_URI, "expose_in_sqllab": True}
    # Look for a connection made by an earlier run.
    existing = find("database", "database_name", DATABASE_NAME)
    # If there is one ...
    if existing:
        # ... update it, so a changed password is picked up ...
        call("PUT", f"/api/v1/database/{existing['id']}", json=payload)
        # ... and return its id.
        return existing["id"]
    # Otherwise create the connection and return the new id.
    return call("POST", "/api/v1/database/", json=payload)["id"]


def setup_dataset(database_id):
    """Registers the analytics.census_frozen view in Superset and returns the dataset's id."""
    # Look for the dataset from an earlier run.
    existing = find("dataset", "table_name", "census_frozen")
    # If there is one, use its id.
    if existing:
        # Remember its id.
        dataset_id = existing["id"]
    # Otherwise create it.
    else:
        # The dataset points at the view in the analytics schema.
        payload = {"database": database_id, "schema": "analytics", "table_name": "census_frozen"}
        # Now we will create it and remember the new id.
        dataset_id = call("POST", "/api/v1/dataset/", json=payload)["id"]
    # Re-read the view's columns, in case the view has changed.
    call("PUT", f"/api/v1/dataset/{dataset_id}/refresh")
    # Return the id.
    return dataset_id


def sync_metrics_and_labels(dataset_id, kpis):
    """Adds one Superset metric per KPI and gives the columns friendly labels."""
    # Now we will read the dataset's current metrics and columns.
    dataset = call("GET", f"/api/v1/dataset/{dataset_id}")["result"]

    # Keep the existing metrics (such as COUNT(*)), looked up by name.
    metrics = {m["metric_name"]: keep_fields(m, METRIC_FIELDS) for m in dataset["metrics"]}
    # Go through every KPI from the backend.
    for kpi in kpis:
        # Start from the existing metric with this name, or a new empty one.
        metric = metrics.setdefault(kpi["metricName"], {})
        # Metric name, e.g. "kpi_tch_01".
        metric["metric_name"] = kpi["metricName"]
        # Display name, e.g. "Trained teacher %".
        metric["verbose_name"] = kpi["name"]
        # The SQL formula, exactly as the backend built it.
        metric["expression"] = kpi["metricExpression"]
        # A description that includes the KPI code.
        metric["description"] = f"{kpi['code']}: {kpi['description']}"
        # Show numbers with one decimal place.
        metric["d3format"] = ",.1f"

    # Copy the columns with only the fields Superset accepts back.
    columns = [keep_fields(c, COLUMN_FIELDS) for c in dataset["columns"]]
    # Go through every column.
    for column in columns:
        # If we have a friendly label for it ...
        if column["column_name"] in COLUMN_LABELS:
            # ... use that label.
            column["verbose_name"] = COLUMN_LABELS[column["column_name"]]

    # Now we will save the metrics and columns back to Superset.
    call("PUT", f"/api/v1/dataset/{dataset_id}", json={"metrics": list(metrics.values()), "columns": columns})
    # Print what was synced.
    print("Synced KPI metrics:", ", ".join(k["metricName"] for k in kpis), flush=True)


def bar_chart(kpi, dataset_id, width):
    """Describes a bar chart of one KPI: one group of bars per district, one bar per year."""
    # The KPI's metric name.
    metric = kpi["metricName"]
    # Now we will return the chart description.
    return {
        # Chart title.
        "name": f"{kpi['name']} by district and year",
        # Superset's bar chart type.
        "viz_type": "echarts_timeseries_bar",
        # Width on the dashboard, out of 12 columns.
        "width": width,
        # Height on the dashboard (grid units).
        "height": 50,
        # The chart settings Superset stores and shows in Explore.
        "params": {
            # Which dataset to read.
            "datasource": f"{dataset_id}__table",
            # Chart type.
            "viz_type": "echarts_timeseries_bar",
            # One group of bars per district.
            "x_axis": "district_code",
            # One bar per academic year inside each group.
            "groupby": ["academic_year"],
            # The value to draw.
            "metrics": [metric],
            # No extra filters.
            "adhoc_filters": [],
            # Read at most 1000 rows.
            "row_limit": 1000,
            # Sort biggest first.
            "order_desc": True,
            # Upright bars.
            "orientation": "vertical",
            # Print each value on its bar.
            "show_value": True,
            # Show the year legend ...
            "show_legend": True,
            # ... at the top.
            "legendOrientation": "top",
            # Label under the x-axis.
            "x_axis_title": "District",
            # Space for that label.
            "x_axis_title_margin": 30,
            # One decimal place on the y-axis.
            "y_axis_format": ",.1f",
        },
        # The query Superset's frontend sends for these settings. Superset stores it with the
        # chart and refuses guest requests that ask for anything not in it.
        "query": {
            # District (sent as the chart's base axis) and year.
            "columns": [
                {"columnType": "BASE_AXIS", "sqlExpression": "district_code", "label": "district_code", "expressionType": "SQL"},
                "academic_year",
            ],
            # The KPI.
            "metrics": [metric],
            # Sorted by the KPI, biggest first.
            "orderby": [[metric, False]],
            # One bar series per year.
            "series_columns": ["academic_year"],
            # Read at most 1000 rows.
            "row_limit": 1000,
            # Sort biggest first.
            "order_desc": True,
        },
    }


def table_chart(name, groupby, kpis, dataset_id, height):
    """Describes a table with one row per group and one column per KPI."""
    # The metric names of all KPIs.
    metrics = [kpi["metricName"] for kpi in kpis]
    # Now we will return the chart description.
    return {
        # Chart title.
        "name": name,
        # Superset's table chart type.
        "viz_type": "table",
        # Full dashboard width.
        "width": 12,
        # Height on the dashboard (grid units).
        "height": height,
        # The chart settings Superset stores and shows in Explore.
        "params": {
            # Which dataset to read.
            "datasource": f"{dataset_id}__table",
            # Chart type.
            "viz_type": "table",
            # Add rows up per group (instead of listing raw rows).
            "query_mode": "aggregate",
            # The columns to group by.
            "groupby": groupby,
            # One column per KPI.
            "metrics": metrics,
            # No extra filters.
            "adhoc_filters": [],
            # Read at most 1000 rows.
            "row_limit": 1000,
            # Show 10 rows per page.
            "server_page_length": 10,
            # Sort biggest first.
            "order_desc": True,
            # Draw a small bar behind each number.
            "show_cell_bars": True,
        },
        # The query Superset's frontend sends for these settings (see bar_chart).
        "query": {
            # The group-by columns.
            "columns": groupby,
            # The KPIs.
            "metrics": metrics,
            # Sorted by the first KPI, biggest first.
            "orderby": [[metrics[0], False]],
            # Read at most 1000 rows.
            "row_limit": 1000,
            # Sort biggest first.
            "order_desc": True,
        },
    }


def save_chart(chart, dataset_id, dashboard_id):
    """Creates the chart (or updates one with the same name), adds it to the dashboard, returns its id."""
    # The chart's details.
    payload = {
        # Chart title.
        "slice_name": chart["name"],
        # Chart type.
        "viz_type": chart["viz_type"],
        # Which dataset it reads.
        "datasource_id": dataset_id,
        # The dataset is a table (or view).
        "datasource_type": "table",
        # The chart settings, as JSON text.
        "params": json.dumps(chart["params"]),
        # Put the chart on our dashboard.
        "dashboards": [dashboard_id],
    }
    # Look for a chart with the same name from an earlier run.
    existing = find("chart", "slice_name", chart["name"])
    # If there is one ...
    if existing:
        # ... remember its id ...
        chart_id = existing["id"]
        # ... and update it.
        call("PUT", f"/api/v1/chart/{chart_id}", json=payload)
    # Otherwise ...
    else:
        # ... create the chart and remember the new id.
        chart_id = call("POST", "/api/v1/chart/", json=payload)["id"]

    # The full query context Superset stores with the chart (it needs the chart's id inside).
    query_context = {
        # Which dataset.
        "datasource": {"id": dataset_id, "type": "table"},
        # Use the cache when possible.
        "force": False,
        # The query itself, with Superset's standard empty extras.
        "queries": [{"filters": [], "extras": {"having": "", "where": ""}, "applied_time_extras": {},
                     "annotation_layers": [], "url_params": {}, "custom_params": {},
                     "custom_form_data": {}, **chart["query"]}],
        # The chart settings plus the chart id.
        "form_data": {**chart["params"], "slice_id": chart_id},
        # Return the data as JSON.
        "result_format": "json",
        # Return full results.
        "result_type": "full",
    }
    # Now we will save the query context on the chart.
    call("PUT", f"/api/v1/chart/{chart_id}",
         json={"query_context": json.dumps(query_context), "query_context_generation": True})
    # Return the chart id.
    return chart_id


def build_layout(rows):
    """Builds the dashboard layout: rows of charts on Superset's 12-column grid."""
    # The fixed top of every Superset layout: root, grid and header.
    layout = {
        # Layout format version.
        "DASHBOARD_VERSION_KEY": "v2",
        # The root holds the grid.
        "ROOT_ID": {"type": "ROOT", "id": "ROOT_ID", "children": ["GRID_ID"]},
        # The grid holds the rows (added below).
        "GRID_ID": {"type": "GRID", "id": "GRID_ID", "children": [], "parents": ["ROOT_ID"]},
        # The dashboard header.
        "HEADER_ID": {"type": "HEADER", "id": "HEADER_ID", "meta": {"text": DASHBOARD_TITLE}},
    }
    # Go through each row, numbered from 1.
    for row_number, row in enumerate(rows, start=1):
        # The row's id, e.g. "ROW-1".
        row_id = f"ROW-{row_number}"
        # Add the row to the grid.
        layout["GRID_ID"]["children"].append(row_id)
        # Describe the row.
        layout[row_id] = {"type": "ROW", "id": row_id, "children": [], "parents": ["ROOT_ID", "GRID_ID"],
                          "meta": {"background": "BACKGROUND_TRANSPARENT"}}
        # Go through each chart in the row.
        for chart_id, chart in row:
            # The chart's layout id, e.g. "CHART-3".
            chart_key = f"CHART-{chart_id}"
            # Add the chart to the row.
            layout[row_id]["children"].append(chart_key)
            # Describe the chart's place and size.
            layout[chart_key] = {"type": "CHART", "id": chart_key, "children": [],
                                 "parents": ["ROOT_ID", "GRID_ID", row_id],
                                 "meta": {"chartId": chart_id, "sliceName": chart["name"],
                                          "width": chart["width"], "height": chart["height"]}}
    # Return the finished layout.
    return layout


def build_filter(column, label, dataset_id, chart_ids):
    """Describes one dropdown filter in the dashboard's filter bar."""
    # Now we will return the filter description.
    return {
        # A unique id, e.g. "NATIVE_FILTER-academic-year".
        "id": f"NATIVE_FILTER-{column.replace('_', '-')}",
        # Label shown above the dropdown.
        "name": label,
        # A "pick values" dropdown.
        "filterType": "filter_select",
        # A normal filter (not a divider).
        "type": "NATIVE_FILTER",
        # The dataset column it filters.
        "targets": [{"datasetId": dataset_id, "column": {"name": column}}],
        # Allow several values, sorted A to Z.
        "controlValues": {"multiSelect": True, "enableEmptyFilter": False, "defaultToFirstItem": False,
                          "searchAllOptions": False, "inverseSelection": False, "sortAscending": True},
        # No value is selected at first.
        "defaultDataMask": {"extraFormData": {}, "filterState": {}, "ownState": {}},
        # Not linked to another filter.
        "cascadeParentIds": [],
        # Applies to the whole dashboard ...
        "scope": {"rootPath": ["ROOT_ID"], "excluded": []},
        # ... which means every chart.
        "chartsInScope": chart_ids,
        # No tabs on this dashboard.
        "tabsInScope": [],
        # No description.
        "description": "",
    }


def setup_dashboard(dataset_id, kpis):
    """Builds the dashboard the first time; later runs leave it alone (analysts own it). Returns its id."""
    # Now we will ask Superset whether the dashboard already exists.
    response = session.get(f"{SUPERSET_URL}/api/v1/dashboard/{DASHBOARD_SLUG}", timeout=60)
    # If it exists ...
    if response.ok:
        # ... print that we keep it ...
        print(f"Dashboard '{DASHBOARD_SLUG}' already exists; leaving its layout alone", flush=True)
        # ... and return its id.
        return response.json()["result"]["id"]

    # Otherwise create an empty, published dashboard.
    dashboard_id = call("POST", "/api/v1/dashboard/",
                        json={"dashboard_title": DASHBOARD_TITLE, "slug": DASHBOARD_SLUG, "published": True})["id"]

    # Each bar chart's width: share the 12 grid columns between the KPIs (at least 4 each).
    bar_width = max(12 // len(kpis), 4)
    # The columns every KPI can be sliced by (the same for all KPIs).
    slice_columns = kpis[0]["sliceColumns"]
    # The charts, row by row, top to bottom.
    rows = [
        # Row 1: one bar chart per KPI.
        [bar_chart(kpi, dataset_id, bar_width) for kpi in kpis],
        # Row 2: a table of all KPIs by district, year, level and type.
        [table_chart("KPIs by district, year, level and type", slice_columns, kpis, dataset_id, 60)],
        # Row 3: a table of all KPIs per school.
        [table_chart("KPIs by school", ["academic_year", "district_code", "school_name"], kpis, dataset_id, 70)],
    ]
    # Now we will save every chart and pair it with its new id.
    saved_rows = [[(save_chart(chart, dataset_id, dashboard_id), chart) for chart in row] for row in rows]
    # Collect all chart ids (the filters apply to all of them).
    chart_ids = [chart_id for row in saved_rows for chart_id, _ in row]

    # The dashboard's extra settings: the filter bar, and clicking a chart filters the others.
    metadata = {
        # One filter per entry in FILTERS.
        "native_filter_configuration": [build_filter(column, label, dataset_id, chart_ids) for column, label in FILTERS],
        # Clicking a bar or row filters the other charts.
        "cross_filters_enabled": True,
    }
    # Now we will save the layout and the settings on the dashboard.
    call("PUT", f"/api/v1/dashboard/{dashboard_id}",
         json={"position_json": json.dumps(build_layout(saved_rows)), "json_metadata": json.dumps(metadata)})
    # Print what was built.
    print(f"Built dashboard '{DASHBOARD_SLUG}' with {len(chart_ids)} charts", flush=True)
    # Return the dashboard id.
    return dashboard_id


def main():
    """Runs every setup step in order."""
    # Wait until Superset is up.
    wait_for(f"{SUPERSET_URL}/health", "Superset")
    # Wait until the backend is up.
    wait_for(f"{BACKEND_URL}/actuator/health", "EMIS backend")

    # Now we will read the active KPIs from the backend.
    kpis = requests.get(f"{BACKEND_URL}/api/analytics/kpis", timeout=30).json()
    # Without KPIs there is nothing to chart, so stop.
    if not kpis:
        # Stop with an error.
        raise SystemExit("The backend returned no active KPIs; nothing to chart")

    # Log in to Superset.
    login()
    # Connect Superset to the EMIS database.
    database_id = setup_database()
    # Register the census_frozen view.
    dataset_id = setup_dataset(database_id)
    # Add the KPI metrics and column labels.
    sync_metrics_and_labels(dataset_id, kpis)
    # Build the dashboard (first run only).
    dashboard_id = setup_dashboard(dataset_id, kpis)
    # Allow the frontend to embed the dashboard; Superset answers with the embed id.
    embed = call("POST", f"/api/v1/dashboard/{dashboard_id}/embedded", json={"allowed_domains": EMBED_ALLOWED_DOMAINS})
    # Print a summary.
    print(f"Ready: dataset {dataset_id}, dashboard {dashboard_id}, embed id {embed['result']['uuid']}, "
          f"embeddable from {', '.join(EMBED_ALLOWED_DOMAINS)}", flush=True)


# Run main() when this file is started as a script.
if __name__ == "__main__":
    # Start.
    main()
