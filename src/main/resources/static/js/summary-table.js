(function () {
    "use strict";

    const states = new Map();

    function esc(value) {
        return String(value === null || value === undefined ? "" : value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function path(source, key) {
        if (source === null || source === undefined || !key) return undefined;
        return String(key).replace(/^\$\.?/, "").split(".").reduce(function (value, part) {
            return value === null || value === undefined ? undefined : value[part];
        }, source);
    }

    function numeric(value) {
        if (typeof value === "number") return Number.isFinite(value) ? value : null;
        if (typeof value !== "string" || value.trim() === "") return null;
        const number = Number(value.replace(/,/g, ""));
        return Number.isFinite(number) ? number : null;
    }

    function display(value) {
        if (value === null || value === undefined) return "";
        if (typeof value === "number") return value.toLocaleString();
        return typeof value === "object" ? JSON.stringify(value) : String(value);
    }

    function getRows(result, config) {
        const payload = result && result.payload;
        const validRows = rows => Array.isArray(rows)
            ? rows.filter(row => row && typeof row === "object" && !Array.isArray(row))
            : null;

        // DashboardService wraps script output as result.payload.data, while the
        // script itself can return an envelope such as {"payload":{"hosts":[...]}}.
        // Try the configured rowPath against each supported envelope without
        // changing the mapping saved by the user.
        if (config.rowPath) {
            const candidates = [
                payload,
                payload && payload.data,
                payload && payload.payload,
                payload && payload.data && payload.data.payload,
                payload && payload.data && payload.data.data
            ];
            for (const candidate of candidates) {
                const rows = validRows(path(candidate, config.rowPath));
                if (rows) return rows;
            }
        }

        const directPayloadRows = validRows(payload);
        if (directPayloadRows) return directPayloadRows;
        const resultRows = validRows(result && result.rows);
        if (resultRows) return resultRows;

        // Fallback discovery supports unconfigured sources, including one or more
        // standard data/payload envelopes around an array of row objects.
        const queue = [payload];
        const visited = new Set();
        while (queue.length) {
            const current = queue.shift();
            if (!current || typeof current !== "object" || visited.has(current)) continue;
            visited.add(current);
            const rows = validRows(current);
            if (rows) return rows;
            if (Array.isArray(current)) continue;
            ["data", "payload", "rows", "results", "items", "hosts"].forEach(key => {
                if (current[key] && typeof current[key] === "object") queue.push(current[key]);
            });
        }
        return [];
    }

    function rowValue(row, field) {
        return Object.prototype.hasOwnProperty.call(row, field) ? row[field] : path(row, field);
    }

    function isTotalRow(row, totalConfig, columns) {
        if (totalConfig && totalConfig.mode === "AUTHORITATIVE_ROW" && totalConfig.identifyBy) {
            return String(rowValue(row, totalConfig.identifyBy)).toLowerCase() === String(totalConfig.value || "TOTAL").toLowerCase();
        }
        if (totalConfig && totalConfig.identifyBy) {
            return String(rowValue(row, totalConfig.identifyBy)).toLowerCase() === String(totalConfig.value || "TOTAL").toLowerCase();
        }
        const fields = Object.keys(row);
        return fields.some(field => numeric(rowValue(row, field)) === null
            && String(rowValue(row, field) || "").trim().toLowerCase() === "total");
    }

    function inferColumns(rows, configured) {
        if (Array.isArray(configured) && configured.length) {
            return configured.filter(column => column && (column.field || column.key)).map(column => ({
                field: String(column.field || column.key),
                label: column.label || String(column.field || column.key),
                visible: column.visible !== false,
                sortable: column.sortable !== false,
                groupable: column.groupable !== false,
                searchable: column.searchable !== false,
                format: column.format || ""
            }));
        }
        const keys = Array.from(new Set(rows.reduce((all, row) => all.concat(Object.keys(row)), [])));
        return keys.map(field => ({ field, label: field.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/^./, c => c.toUpperCase()),
            visible: true, sortable: true, groupable: true, searchable: true, format: "" }));
    }

    function statusClass(value, config) {
        const normalized = String(value === null || value === undefined ? "" : value).trim().toUpperCase();
        const mappings = config.mappings && typeof config.mappings === "object" ? config.mappings : {};
        const mapped = String(mappings[normalized] || normalized).toUpperCase();
        if (["SUCCESS", "HEALTHY", "OK", "GREEN", "PASS"].includes(mapped)) return "is-healthy";
        if (["WARNING", "WARN", "YELLOW", "DEGRADED"].includes(mapped)) return "is-warning";
        if (["ERROR", "CRITICAL", "FAILED", "FAILURE", "RED", "DOWN"].includes(mapped)) return "is-critical";
        return "is-unknown";
    }

    function thresholdClass(value, rules) {
        const number = numeric(value);
        if (number === null || !rules) return "";
        if (rules.criticalAbove !== undefined && number > Number(rules.criticalAbove)) return "is-critical";
        if (rules.warningAbove !== undefined && number > Number(rules.warningAbove)) return "is-warning";
        if (rules.criticalBelow !== undefined && number < Number(rules.criticalBelow)) return "is-critical";
        if (rules.warningBelow !== undefined && number < Number(rules.warningBelow)) return "is-warning";
        return "is-healthy";
    }

    function rowClass(row, config) {
        const rowStatus = (config.details && config.details.rowStatus) || config.rowStatus || {};
        if (rowStatus.enabled !== false && rowStatus.field) return statusClass(rowValue(row, rowStatus.field), rowStatus);
        const thresholds = (config.details && config.details.thresholds && typeof config.details.thresholds === "object")
            ? config.details.thresholds : (config.thresholds && typeof config.thresholds === "object" ? config.thresholds : {});
        for (const field of Object.keys(thresholds)) {
            const result = thresholdClass(rowValue(row, field), thresholds[field]);
            if (result === "is-critical" || result === "is-warning") return result;
        }
        return "";
    }

    function summaryClass(value, rules) {
        const result = thresholdClass(value, rules);
        return result || "";
    }

    function makeGroups(rows, fields, level, config, state, prefix) {
        if (level >= fields.length) return rows.map(row => ({ type: "row", row }));
        const field = fields[level];
        const grouped = new Map();
        rows.forEach(row => {
            const raw = rowValue(row, field);
            const key = raw === null || raw === undefined || raw === "" ? "(Empty)" : String(raw);
            if (!grouped.has(key)) grouped.set(key, []);
            grouped.get(key).push(row);
        });
        let entries = Array.from(grouped.entries());
        const ordering = config.groupBy && config.groupBy.order;
        entries.sort((a, b) => {
            if (ordering === "COUNT_DESC") return b[1].length - a[1].length;
            if (ordering === "VALUE_DESC") return String(b[0]).localeCompare(String(a[0]), undefined, { numeric: true });
            return String(a[0]).localeCompare(String(b[0]), undefined, { numeric: true });
        });
        return entries.map(entry => {
            const id = (prefix ? prefix + "|" : "") + field + "=" + entry[0];
            return { type: "group", id, field, value: entry[0], rows: entry[1], children: makeGroups(entry[1], fields, level + 1, config, state, id), level };
        });
    }

    function flattenNodes(nodes, config, state, columns, htmlRows) {
        nodes.forEach(node => {
            if (node.type === "row") {
                htmlRows.push(renderRow(node.row, columns, config));
                return;
            }
            const collapsed = state.collapsed.has(node.id);
            const groupConfig = (config.details && config.details.groupBy) || config.groupBy || config;
            const showAggregates = groupConfig.showAggregates === true;
            const count = node.rows.length;
            let aggregate = "";
            if (showAggregates) {
                const numericColumns = columns.filter(column => node.rows.some(row => numeric(rowValue(row, column.field)) !== null));
                aggregate = numericColumns.map(column => {
                    const total = node.rows.reduce((sum, row) => sum + (numeric(rowValue(row, column.field)) || 0), 0);
                    return esc(column.label) + ": " + esc(total.toLocaleString());
                }).join(" · ");
            }
            htmlRows.push('<tr class="summary-table-group-row"><td colspan="' + columns.length + '"><button type="button" class="summary-table-group-toggle" data-group-id="' + esc(node.id) + '" aria-expanded="' + (!collapsed) + '"><i class="bi ' + (collapsed ? "bi-chevron-right" : "bi-chevron-down") + '"></i><strong>' + esc(node.field) + ': ' + esc(node.value) + '</strong><span class="summary-table-group-count">' + count + ' rows</span>' + (aggregate ? '<span class="summary-table-group-aggregate">' + aggregate + '</span>' : '') + '</button></td></tr>');
            if (!collapsed) flattenNodes(node.children, config, state, columns, htmlRows);
        });
    }

    function renderRow(row, columns, config) {
        const css = rowClass(row, config);
        return '<tr' + (css ? ' class="summary-table-row ' + css + '"' : ' class="summary-table-row"') + '>' +
            columns.map(column => {
                const value = rowValue(row, column.field);
                if (String(column.format).toLowerCase() === "status") {
                    const cls = statusClass(value, (config.details && config.details.rowStatus) || config.rowStatus || {});
                    return '<td><span class="summary-table-status ' + cls + '">' + esc(display(value)) + '</span></td>';
                }
                return '<td>' + esc(display(value)) + '</td>';
            }).join("") + '</tr>';
    }

    function renderCards(state) {
        const summary = state.config.summary || {};
        if (summary.enabled === false) return "";
        const numericFields = state.numericFields;
        const labels = state.config.labels && typeof state.config.labels === "object" ? state.config.labels : {};
        const rules = summary.thresholds && typeof summary.thresholds === "object" ? summary.thresholds : {};
        const cards = [];
        const totalConfig = summary.totalRow || {};
        const sourceTotals = state.totalRow;
        const totals = {};
        numericFields.forEach(field => {
            totals[field] = sourceTotals
                ? numeric(rowValue(sourceTotals, field))
                : state.detailRows.reduce((sum, row) => sum + (numeric(rowValue(row, field)) || 0), 0);
            if (totals[field] === null || totals[field] === undefined) totals[field] = 0;
        });
        if (summary.totalCard && summary.totalCard.enabled === true) {
            let overall;
            if (summary.totalCard.field) {
                overall = sourceTotals ? numeric(rowValue(sourceTotals, summary.totalCard.field))
                    : state.detailRows.reduce((sum, row) => sum + (numeric(rowValue(row, summary.totalCard.field)) || 0), 0);
            } else {
                overall = numericFields.reduce((sum, field) => sum + (totals[field] || 0), 0);
            }
            cards.push({ label: summary.totalCard.label || "Overall Total", value: overall || 0, cls: summaryClass(overall || 0, summary.totalCard.thresholds), filterField: null, filterType: "overall" });
        }
        const excluded = new Set(Array.isArray(summary.excludedFields) ? summary.excludedFields.map(String) : []);
        numericFields.filter(field => !excluded.has(field)).forEach(field => {
            const label = labels[field] || (state.columnMap.get(field) || {}).label || field;
            cards.push({ label, value: totals[field], cls: summaryClass(totals[field], rules[field]), filterField: field, filterType: "field" });
        });
        if (!cards.length) return "";
        return '<div class="server-health-summary summary-table-cards">' + cards.map(card => {
            const active = state.cardFilter && state.cardFilter.type === card.filterType
                && state.cardFilter.field === card.filterField;
            const status = card.filterType === "overall" ? "total"
                : String(card.cls || "").includes("critical") ? "critical"
                : String(card.cls || "").includes("warning") ? "warning"
                : String(card.cls || "").includes("healthy") ? "healthy" : "unknown";
            const icon = status === "total" ? "bi-hdd-rack"
                : status === "healthy" ? "bi-check-circle"
                : status === "warning" ? "bi-exclamation-triangle"
                : status === "critical" ? "bi-x-circle" : "bi-diagram-3";
            return '<button type="button" class="server-health-summary-item server-health-summary-' + status +
                ' server-health-summary-filter summary-table-card' + (active ? ' is-active' : '') +
                '" data-card-filter-type="' + esc(card.filterType) +
                '" data-card-filter-field="' + esc(card.filterField || '') + '" aria-label="Filter rows by ' +
                esc(card.label) + '" aria-pressed="' + Boolean(active) + '">' +
                '<span><i class="bi ' + icon + '" aria-hidden="true"></i> ' + esc(card.label) + '</span>' +
                '<strong class="summary-table-card-value">' + esc(display(card.value)) +
                '</strong></button>';
        }).join("") + '</div>';
    }

    function renderContent(state) {
        const config = state.config;
        const details = config.details || {};
        const groupConfig = details.groupBy || config.groupBy || {};
        const grouping = groupConfig.enabled === true;
        const groupFields = Array.isArray(groupConfig.fields) ? groupConfig.fields.map(String).filter(field => field && (!state.columnMap.has(field) || state.columnMap.get(field).groupable !== false)) : [];
        const searchConfig = details.search || config.search || {};
        const sortConfig = details.sorting || config.sorting || {};
        const pageConfig = details.pagination || config.pagination || {};
        let rows = state.detailRows.slice();
        if (state.cardFilter) {
            if (state.cardFilter.type === "field" && state.cardFilter.field) {
                const field = state.cardFilter.field;
                rows = rows.filter(row => (numeric(rowValue(row, field)) || 0) > 0);
            } else if (state.cardFilter.type === "overall") {
                rows = rows.filter(row => state.numericFields.some(field => (numeric(rowValue(row, field)) || 0) > 0));
            }
        }
        if (state.search && searchConfig.enabled !== false) {
            const requested = Array.isArray(searchConfig.fields) ? searchConfig.fields.map(String) : state.columns.filter(column => column.searchable !== false).map(column => column.field);
            rows = rows.filter(row => {
                const fields = requested;
                return fields.some(field => String(display(rowValue(row, field))).toLowerCase().includes(state.search.toLowerCase()));
            });
        }
        if (state.sortField && sortConfig.enabled !== false) {
            const field = state.sortField;
            rows.sort((a, b) => {
                const av = rowValue(a, field), bv = rowValue(b, field);
                const an = numeric(av), bn = numeric(bv);
                let compare = an !== null && bn !== null ? an - bn : String(av ?? "").localeCompare(String(bv ?? ""), undefined, { numeric: true, sensitivity: "base" });
                return state.sortDirection === "desc" ? -compare : compare;
            });
        }
        const columns = state.columns.filter(column => column.visible !== false);
        const visibleFields = new Set(columns.map(column => column.field));
        const toolbar = searchConfig.enabled === false ? "" : '<div class="summary-table-toolbar"><input class="form-control form-control-sm summary-table-search" type="search" placeholder="' + esc(searchConfig.placeholder || "Search table...") + '" value="' + esc(state.search) + '" aria-label="Search table"></div>';
        if (!details.enabled && config.details && config.details.enabled === false) {
            return renderCards(state);
        }
        if (details.enabled === false) return renderCards(state);
        if (!columns.length) return renderCards(state) + '<div class="summary-table-empty">No visible detail columns configured.</div>';
        const header = columns.map(column => {
            const canSort = sortConfig.enabled !== false && column.sortable !== false;
            const active = state.sortField === column.field;
            return '<th scope="col">' + (canSort ? '<button type="button" class="summary-table-sort" data-sort-field="' + esc(column.field) + '">' : '') +
                esc(column.label) + (canSort ? '<i class="bi ' + (active ? (state.sortDirection === "asc" ? "bi-sort-up" : "bi-sort-down") : "bi-arrow-down-up") + '"></i></button>' : '') + '</th>';
        }).join("");
        let body = "";
        let pageCount = 1, totalCount = rows.length, pageRows = rows;
        const pageSize = Math.max(1, Math.min(500, Number(pageConfig.pageSize) || 10));
        if (grouping && groupFields.length) {
            const nodes = makeGroups(rows, groupFields, 0, groupConfig, state, "");
            pageCount = Math.max(1, Math.ceil(nodes.length / pageSize));
            state.page = Math.min(state.page, pageCount - 1);
            const visibleNodes = pageConfig.enabled === false ? nodes : nodes.slice(state.page * pageSize, (state.page + 1) * pageSize);
            const rendered = [];
            flattenNodes(visibleNodes, config, state, columns, rendered);
            body = rendered.join("");
            totalCount = nodes.length;
        } else {
            pageCount = Math.max(1, Math.ceil(rows.length / pageSize));
            state.page = Math.min(state.page, pageCount - 1);
            pageRows = pageConfig.enabled === false ? rows : rows.slice(state.page * pageSize, (state.page + 1) * pageSize);
            body = pageRows.map(row => renderRow(row, columns, config)).join("");
        }
        if (!body) body = '<tr><td colspan="' + Math.max(1, columns.length) + '" class="summary-table-empty-cell">No matching rows.</td></tr>';
        const pager = pageConfig.enabled === false ? "" : '<div class="summary-table-pagination"><span class="small text-muted">Showing ' +
            (totalCount ? (state.page * pageSize + 1) : 0) + '–' + Math.min((state.page + 1) * pageSize, totalCount) + ' of ' + totalCount +
            (grouping && groupFields.length ? ' groups' : ' rows') + '</span><div class="d-flex align-items-center gap-2"><label class="small text-muted">Rows <select class="form-select form-select-sm summary-table-page-size" aria-label="Rows per page">' +
            [5, 10, 20, 50, 100, pageSize].filter((v, i, a) => a.indexOf(v) === i).sort((a, b) => a - b).map(size => '<option value="' + size + '"' + (size === pageSize ? ' selected' : '') + '>' + size + '</option>').join("") +
            '</select></label><button type="button" class="btn btn-sm btn-outline-secondary summary-table-prev"' + (state.page <= 0 ? ' disabled' : '') + ' aria-label="Previous page"><i class="bi bi-chevron-left"></i></button><span class="small">' +
            (state.page + 1) + ' / ' + pageCount + '</span><button type="button" class="btn btn-sm btn-outline-secondary summary-table-next"' + (state.page >= pageCount - 1 ? ' disabled' : '') + ' aria-label="Next page"><i class="bi bi-chevron-right"></i></button></div></div>';
        const groupableColumns = state.columns.filter(column => column.groupable !== false);
        const selectedGroupFields = Array.isArray(groupConfig.fields) ? groupConfig.fields.map(String) : [];
        const groupLabel = selectedGroupFields.length
            ? selectedGroupFields.map(field => (state.columnMap.get(field) || {}).label || field).join(" → ")
            : "Group by";
        const groupSelector = groupableColumns.length ? '<div class="summary-table-controls"><div class="dropdown">' +
            '<button type="button" class="btn btn-sm btn-outline-secondary dropdown-toggle summary-table-group-toggle-menu" data-bs-toggle="dropdown" data-bs-auto-close="outside" aria-expanded="false">' +
            '<i class="bi bi-layers me-1" aria-hidden="true"></i>' + esc(groupLabel) + '</button>' +
            '<div class="dropdown-menu p-2 summary-table-group-menu">' +
            '<div class="small text-muted px-2 pb-2">Group rows by</div>' +
            groupableColumns.map(column => '<label class="dropdown-item d-flex align-items-center gap-2">' +
                '<input type="checkbox" class="form-check-input mt-0 summary-table-group-field" value="' + esc(column.field) + '"' +
                (selectedGroupFields.includes(column.field) ? ' checked' : '') + '>' +
                '<span>' + esc(column.label) + '</span></label>').join("") +
            '<div class="dropdown-divider"></div><button type="button" class="dropdown-item small summary-table-group-clear">' +
            '<i class="bi bi-x-circle me-1" aria-hidden="true"></i>Clear grouping</button></div></div></div>' : "";
        return renderCards(state) + toolbar + groupSelector + '<div class="summary-table-scroll"><table class="table summary-table-grid mb-0"><thead><tr>' + header +
            '</tr></thead><tbody>' + body + '</tbody></table></div>' + pager;
    }

    function rerender(root, state) {
        root.innerHTML = renderContent(state);
    }

    function render(result, widget) {
        const config = JSON.parse(JSON.stringify(widget && widget.fieldConfigJson ? safeParse(widget.fieldConfigJson) : {}));
        const columns = inferColumns(getRows(result || {}, config), config.details && config.details.columns || config.columns);
        const totalConfig = config.summary && config.summary.totalRow || {};
        const rawRows = getRows(result || {}, config);
        const detectedTotalRow = rawRows.find(row => isTotalRow(row, totalConfig, columns)) || null;
        const totalRow = String(totalConfig.mode || "").toUpperCase() === "AUTHORITATIVE_ROW" ? detectedTotalRow : null;
        const detailRows = rawRows.filter(row => !isTotalRow(row, totalConfig, columns));
        const columnMap = new Map(columns.map(column => [column.field, column]));
        const configuredExcluded = new Set(config.summary && Array.isArray(config.summary.excludedFields) ? config.summary.excludedFields.map(String) : []);
        const numericFields = columns.map(column => column.field).filter(field =>
            !configuredExcluded.has(field) && detailRows.some(row => numeric(rowValue(row, field)) !== null));
        const state = {
            config, widget, result, columns, columnMap, detailRows, totalRow, numericFields,
            search: "", sortField: ((config.details || {}).sorting || {}).defaultField || "",
            sortDirection: String((((config.details || {}).sorting || {}).direction) || "asc").toLowerCase() === "desc" ? "desc" : "asc",
            page: 0, collapsed: new Set(), cardFilter: null
        };
        const groupConfig = (config.details || {}).groupBy || config.groupBy || {};
        if (groupConfig.initialState === "COLLAPSED" && Array.isArray(groupConfig.fields) && groupConfig.fields.length) {
            const initialNodes = makeGroups(detailRows, groupConfig.fields.map(String), 0, groupConfig, state, "");
            const collapseAll = nodes => nodes.forEach(node => {
                if (node.type === "group") { state.collapsed.add(node.id); collapseAll(node.children); }
            });
            collapseAll(initialNodes);
        }
        const widgetId = String(widget && widget.id || "summary-table");
        states.set(widgetId, state);
        const cards = renderContent(state);
        return '<div class="summary-table-widget" data-summary-widget-id="' + esc(widgetId) + '">' + cards + '</div>';
    }

    function safeParse(value) {
        try { const parsed = JSON.parse(value); return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {}; }
        catch (_) { return {}; }
    }

    function closestRoot(target) {
        return target && target.closest ? target.closest(".summary-table-widget") : null;
    }

    function stateFor(target) {
        const root = closestRoot(target);
        return root ? { root, state: states.get(root.getAttribute("data-summary-widget-id")) } : null;
    }

    document.addEventListener("input", function (event) {
        if (!event.target.matches(".summary-table-search")) return;
        const found = stateFor(event.target);
        if (!found || !found.state) return;
        found.state.search = event.target.value;
        found.state.page = 0;
        rerender(found.root, found.state);
        const input = found.root.querySelector(".summary-table-search");
        if (input) { input.focus(); input.setSelectionRange(input.value.length, input.value.length); }
    });

    document.addEventListener("change", function (event) {
        if (event.target.matches(".summary-table-group-field")) {
            const found = stateFor(event.target);
            if (!found || !found.state) return;
            const selected = Array.from(found.root.querySelectorAll(".summary-table-group-field:checked")).map(input => input.value);
            const state = found.state;
            state.config.details = state.config.details || {};
            state.config.details.groupBy = state.config.details.groupBy || state.config.groupBy || {};
            state.config.details.groupBy.fields = selected;
            state.config.details.groupBy.enabled = selected.length > 0;
            state.config.details.groupBy.showAggregates = state.config.details.groupBy.showAggregates === true;
            state.page = 0;
            rerender(found.root, state);
            return;
        }
        if (!event.target.matches(".summary-table-page-size")) return;
        const found = stateFor(event.target);
        if (!found || !found.state) return;
        found.state.config.details = found.state.config.details || {};
        found.state.config.details.pagination = found.state.config.details.pagination || {};
        found.state.config.details.pagination.pageSize = Number(event.target.value) || 10;
        found.state.page = 0;
        rerender(found.root, found.state);
    });

    document.addEventListener("click", function (event) {
        const sortButton = event.target.closest(".summary-table-sort");
        const groupButton = event.target.closest(".summary-table-group-toggle");
        const cardButton = event.target.closest(".summary-table-card");
        const groupClearButton = event.target.closest(".summary-table-group-clear");
        const prev = event.target.closest(".summary-table-prev");
        const next = event.target.closest(".summary-table-next");
        if (!sortButton && !groupButton && !cardButton && !groupClearButton && !prev && !next) return;
        const found = stateFor(event.target);
        if (!found || !found.state) return;
        const state = found.state;
        if (cardButton) {
            const type = cardButton.getAttribute("data-card-filter-type");
            const field = cardButton.getAttribute("data-card-filter-field") || null;
            if (state.cardFilter && state.cardFilter.type === type && state.cardFilter.field === field) {
                state.cardFilter = null;
            } else {
                state.cardFilter = { type, field };
            }
            state.page = 0;
        } else if (sortButton) {
            const field = sortButton.getAttribute("data-sort-field");
            if (state.sortField === field) state.sortDirection = state.sortDirection === "asc" ? "desc" : "asc";
            else { state.sortField = field; state.sortDirection = "asc"; }
            state.page = 0;
        } else if (groupClearButton) {
            state.config.details = state.config.details || {};
            state.config.details.groupBy = state.config.details.groupBy || state.config.groupBy || {};
            state.config.details.groupBy.fields = [];
            state.config.details.groupBy.enabled = false;
            state.page = 0;
        } else if (groupButton) {
            const id = groupButton.getAttribute("data-group-id");
            if (state.collapsed.has(id)) state.collapsed.delete(id); else state.collapsed.add(id);
        } else if (prev) state.page = Math.max(0, state.page - 1);
        else if (next) state.page += 1;
        rerender(found.root, state);
    });

    window.SummaryTableWidget = { render };
})();