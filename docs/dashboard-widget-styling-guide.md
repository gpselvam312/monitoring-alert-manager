# Dashboard Widget Styling and Configuration Guide

## 1. Purpose

The dashboard supports several widget types with a shared outer layout and specialized content renderers. This guide describes the existing conventions for widget selection, icons, styling, status indicators, responsive behavior, and configuration.

The objective is to maintain a consistent, theme-aware dashboard without removing layouts that are useful for particular monitoring scenarios.

## Text / Info widget configuration

The `TEXT` widget supports the existing result-backed `textFields` and `textTemplate` configuration, plus guided presentation settings:

- `displayMode`: `INFORMATION` or `SECTION_HEADER`
- `title` and `subtitle`: optional presentation text
- `content`: text displayed beneath the title; newlines are preserved
- `border`: `true` (default for configured presentation) or `false`
- `associatedWidgetId`: optional target widget ID for a section header; the header is rendered immediately before that widget

Example static information widget (no monitoring job is required):

```json
{
  "displayMode": "INFORMATION",
  "title": "Deployment notes",
  "subtitle": "Before starting a deployment",
  "content": "Confirm the change window.\nNotify the support team.",
  "border": false
}
```

Example heading-style widget:

```json
{
  "displayMode": "SECTION_HEADER",
  "title": "API Health",
  "subtitle": "Current environment checks",
  "border": true,
  "associatedWidgetId": "42"
}
```

Dynamic placeholders such as `{{data.serviceName}}` can be used in `content` when the widget has a monitoring-job data source. Static content works without a data source. In the guided editor, select an enabled widget on the same dashboard tab to associate it with the section header. The header is moved directly before that widget and spans the dashboard width. If the target is later disabled or deleted, the header remains visible as a standalone section heading. Existing `textFields` and `textTemplate` configurations remain supported for compatibility.

## 2. Choosing a widget

| Type | Intended use |
|---|---|
| `STAT` | Single KPI value and supporting metrics |
| `STATUS` | Health/status value, message, and metrics |
| `TEXT` | Text output or selected fields from JSON |
| `TABLE` | Tabular results with configurable columns |
| `CHART` | Time-series or categorical visualizations supported by the chart configuration |
| `SYSTEM_METRICS` | Server health summaries and individual server cards |
| `ELASTICSEARCH` | Elasticsearch cluster overview, node metrics, and alerts |

`SYSTEM_METRICS` and `ELASTICSEARCH` have dedicated renderers and are rendered at full dashboard width.

### Specialized `TABLE` renderers

- `SERVER_HEALTH`: renders server health cards.
- `KAFKA_CONSUMER_HEALTH`: renders Kafka cluster and consumer details, including consumer lag and expandable topic/partition information.

Example configuration for a specialized table renderer:

```json
{
  "renderer": "KAFKA_CONSUMER_HEALTH",
  "rowPath": "data.consumerGroups"
}
```

This is an illustrative fragment; the Kafka renderer also requires the field mappings documented for that renderer.

## 3. Shared visual conventions

### Widget container and header

Use the shared dashboard container, header, title, description, and action-button styles. Do not recreate these elements inside a specialized renderer.

### Theme-aware colors

Prefer existing CSS variables over hardcoded colors:

- `--theme-primary` for primary accents and emphasis.
- `--text-primary` and `--text-secondary` for text hierarchy.
- `--card-bg`, `--app-bg`, and `--border-color` for surfaces and borders.
- `--success`, `--warning`, `--danger`, and `--info` for semantic states.

Theme-specific styles should remain readable in both light and dark modes.

### Icons

Use the existing Bootstrap Icons library with `bi` classes. Reuse an established icon where possible, and choose a recognizable icon when a new metric requires one.

The shared widget-header defaults currently use:

| Widget | Default icon |
|---|---|
| STAT | `bi-speedometer2` |
| STATUS | `bi-heart-pulse` |
| TEXT | `bi-card-text` |
| TABLE | `bi-table` |
| CHART | `bi-bar-chart` |
| SYSTEM_METRICS | `bi-hdd-rack` |
| ELASTICSEARCH | `bi-database-check` |

An explicitly configured widget icon takes precedence over the default.

### Icon sizing

Choose sizes appropriate to the component:

- Widget header icons should remain visually balanced with the title.
- Summary-card icons should be prominent enough to identify the metric.
- Inline metric icons should not compete with the metric value.
- Status icons should be paired with readable text, rather than conveying the state through color alone.

These are design guidelines, not a requirement to use one identical size everywhere.

## 4. Status and severity conventions

Use the existing semantic status colors consistently:

- **Healthy / UP:** success.
- **Warning:** warning.
- **Critical / DOWN / failure:** danger.
- **Unknown / unavailable:** neutral or secondary.

Preserve the meaning of the source data. Missing or invalid metrics must not be displayed as healthy merely because a value is unavailable.

Cluster-level status and node-level status should remain distinguishable. Alerts should include enough text to explain the affected component and condition.

## 5. Summary cards and interactions

Summary cards can serve two different purposes:

- **Informational cards:** display counts or measurements without changing the content.
- **Filter cards:** update the displayed list when selected.

Only filter cards should indicate selection and behave like interactive controls. Interactive cards should provide visible hover and keyboard-focus feedback. Informational cards should not imply that they are clickable.

Keep card labels, icons, and values aligned, and allow long names or values to wrap without overflowing their containers.

## 6. Tables and large result sets

Use the standard `TABLE` renderer for general tabular data. Configure columns and their labels, formatting, visibility, and sorting where needed.

When appropriate, enable search, sorting, and pagination for large result sets. Display row counts and clear pagination controls. Use readable numeric alignment and consistent row separators or zebra striping.

Specialized renderers may have their own controls, but their colors, typography, borders, and spacing should follow the same theme conventions.

## 7. Responsive behavior

- Use grid layouts that adapt to available width.
- Reduce the number of cards per row on smaller screens.
- Prevent long hostnames, consumer-group names, and other identifiers from overflowing.
- Keep tables usable on narrow screens, using horizontal scrolling when necessary.
- Ensure summary values and controls remain visible at mobile sizes.

## 8. CSS maintenance

- Prefer existing shared dashboard styles and theme variables.
- Keep specialized selectors scoped to their component.
- Avoid duplicate declarations for the same selector unless an intentional override is documented.
- Keep responsive overrides close to, or clearly associated with, the relevant component styles.
- Do not introduce hardcoded light-theme assumptions into specialized widgets.
- Preserve the distinction between interactive and informational elements.

## 9. Checklist for a new or updated widget

- [ ] Select an existing widget type or justify a new dedicated renderer.
- [ ] Reuse the shared dashboard container and header.
- [ ] Use Bootstrap Icons and an appropriate default icon.
- [ ] Use theme variables for text, backgrounds, borders, and status colors.
- [ ] Keep status text understandable without relying on color alone.
- [ ] Check missing, invalid, and empty data.
- [ ] Verify responsive behavior for desktop, tablet, and mobile.
- [ ] Confirm that interactive cards and controls have correct focus and hover behavior.
- [ ] Check for duplicate or conflicting CSS declarations.
- [ ] Test both light and dark themes.

## 10. Related documentation

- `docs/elasticsearch-health-widget.md` — Elasticsearch collector setup, thresholds, and widget configuration.
- `src/main/resources/static/js/dashboard.js` — widget selection and rendering logic.
- `src/main/resources/static/css/app.css` — shared dashboard and specialized widget styles.
