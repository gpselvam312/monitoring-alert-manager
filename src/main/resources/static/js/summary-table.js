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

    function formatValue(value, format) {
        if (value === null || value === undefined || value === "") return "";
        const normalized = String(format || "").toLowerCase();
        const number = numeric(value);
        if ((normalized === "number") && number !== null) return number.toLocaleString();
        if ((normalized === "percentage" || normalized === "percent") && number !== null) {
            return number.toLocaleString(undefined, { maximumFractionDigits: 2 }) + "%";
        }
        if (normalized === "duration" && number !== null) {
            const seconds = Math.max(0, Math.floor(number));
            const hours = Math.floor(seconds / 3600);
            const minutes = Math.floor((seconds % 3600) / 60);
            const remainingSeconds = seconds % 60;
            return [hours, minutes, remainingSeconds]
                .map(part => String(part).padStart(2, "0"))
                .join(":");
        }
        if (normalized === "datetime") {
            const date = new Date(value);
            if (!Number.isNaN(date.getTime())) return date.toLocaleString();
        }
        return display(value);
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
                format: column.format || "",
                displayStyle: column.displayStyle || "text",
                aggregation: column.aggregation || "none"
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

    function conditionalRuleMatches(row, rule) {
        const field = String(rule.field || "");
        const value = Object.prototype.hasOwnProperty.call(row, field) ? row[field] : field.split(".").reduce((current,key) => current == null ? undefined : current[key], row);
        const operator = String(rule.operator || "equals").toLowerCase();
        if (operator === "missing") return value === undefined;
        if (operator === "empty") return value === undefined || value === null || String(value).trim() === "";
        if (value === undefined || value === null) return false;
        const actual = String(value).trim(), expected = String(rule.value == null ? "" : rule.value).trim();
        if (["gt","gte","lt","lte"].includes(operator)) {
            const number = numeric(actual), threshold = numeric(expected);
            if (number === null || threshold === null) return false;
            if (operator === "gt") return number > threshold;
            if (operator === "gte") return number >= threshold;
            if (operator === "lt") return number < threshold;
            return number <= threshold;
        }
        if (operator === "contains") return actual.toLowerCase().includes(expected.toLowerCase());
        if (operator === "not_equals") return actual.toLowerCase() !== expected.toLowerCase();
        return actual.toLowerCase() === expected.toLowerCase();
    }

    function conditionalRuleClass(row, config, scope, field) {
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const formatting = details.conditionalFormatting && typeof details.conditionalFormatting === "object" ? details.conditionalFormatting : (config.conditionalFormatting || {});
        if (formatting.enabled === false || !Array.isArray(formatting.rules)) return "";
        const ranks = { info: 1, healthy: 1, warning: 2, critical: 3 };
        let selected = "";
        formatting.rules.forEach(rule => {
            if (!rule || String(rule.scope || "row").toLowerCase() !== scope) return;
            if (scope === "cell" && String(rule.field || "") !== String(field || "")) return;
            if (!conditionalRuleMatches(row, rule)) return;
            const severity = String(rule.severity || "critical").toLowerCase();
            if ((ranks[severity] || 0) > (ranks[selected] || 0)) selected = severity;
        });
        return selected ? "is-" + selected : "";
    }

    function rowClass(row, config) {
        const rowStatus = (config.details && config.details.rowStatus) || config.rowStatus || {};
        let baseClass = "";
        if (rowStatus.enabled !== false && rowStatus.field) baseClass = statusClass(rowValue(row, rowStatus.field), rowStatus);
        else {
            const thresholds = (config.details && config.details.thresholds && typeof config.details.thresholds === "object") ? config.details.thresholds : (config.thresholds && typeof config.thresholds === "object" ? config.thresholds : {});
            let hasWarning = false;
            for (const field of Object.keys(thresholds)) {
                const result = thresholdClass(rowValue(row, field), thresholds[field]);
                if (result === "is-critical") { baseClass = "is-critical"; break; }
                if (result === "is-warning") hasWarning = true;
            }
            if (!baseClass && hasWarning) baseClass = "is-warning";
        }
        const conditional = conditionalRuleClass(row, config, "row");
        const rank = { "": 0, "is-unknown": 0, "is-info": 1, "is-warning": 2, "is-critical": 3, "is-healthy": 0 };
        return (rank[conditional] || 0) > (rank[baseClass] || 0) ? conditional : baseClass;
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
                const aggregateColumns = columns.filter(column => String(column.aggregation || "none").toLowerCase() !== "none");
                aggregate = aggregateColumns.map(column => {
                    const operation = String(column.aggregation || "none").toLowerCase();
                    const values = node.rows.map(row => rowValue(row, column.field)).filter(value => value !== null && value !== undefined && value !== "");
                    let result;
                    if (operation === "count") result = values.length;
                    else {
                        const numbers = values.map(numeric).filter(value => value !== null);
                        if (!numbers.length) return "";
                        if (operation === "sum") result = numbers.reduce((sum, value) => sum + value, 0);
                        else if (operation === "avg") result = numbers.reduce((sum, value) => sum + value, 0) / numbers.length;
                        else if (operation === "max") result = Math.max(...numbers);
                        else if (operation === "min") result = Math.min(...numbers);
                    }
                    return esc(column.label) + " (" + esc(operation.toUpperCase()) + "): " + esc(result === undefined ? "—" : Number.isFinite(result) ? result.toLocaleString() : result);
                }).filter(Boolean).join(" · ");
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
                const cellClass = conditionalRuleClass(row, config, "cell", column.field);
                const isBoolean = value === true || value === false || (typeof value === "string" && /^(true|false)$/i.test(value.trim()));
                const boolValue = value === true || (typeof value === "string" && value.trim().toLowerCase() === "true");
                const displayStyle = String(column.displayStyle || "text").toLowerCase();
                if (isBoolean && displayStyle === "checkbox") {
                    return '<td' + (cellClass ? ' class="' + cellClass + '"' : '') + '><input class="form-check-input summary-table-boolean" type="checkbox" disabled aria-label="' + esc(column.label || column.field) + '" ' + (boolValue ? 'checked' : '') + '></td>';
                }
                if (isBoolean && displayStyle === "toggle") {
                    return '<td' + (cellClass ? ' class="' + cellClass + '"' : '') + '><span class="summary-table-toggle ' + (boolValue ? 'is-on' : 'is-off') + '" role="img" aria-label="' + esc(column.label || column.field) + ': ' + (boolValue ? 'true' : 'false') + '"><span></span></span></td>';
                }
                if (String(column.format).toLowerCase() === "status") {
                    const cls = statusClass(value, (config.details && config.details.rowStatus) || config.rowStatus || {});
                    return '<td' + (cellClass ? ' class="' + cellClass + '"' : '') + '><span class="summary-table-status ' + cls + '">' + esc(display(value)) + '</span></td>';
                }
                return '<td' + (cellClass ? ' class="' + cellClass + '"' : '') + '>' + esc(formatValue(value, column.format)) + '</td>';
            }).join("") + '</tr>';
    }

    function renderCards(state) {
        const summary = state.config.summary || {};
        if (summary.enabled === false) return "";
        const details = state.config.details && typeof state.config.details === "object" ? state.config.details : {};
        const hasConfiguredCards = Array.isArray(summary.cards) || Array.isArray(details.summaryCards);
        const configured = Array.isArray(summary.cards) ? summary.cards : (Array.isArray(details.summaryCards) ? details.summaryCards : []);
        const cards = [];
        if (hasConfiguredCards) {
            const matches = (row, card) => {
                if (!card.filterField) return true;
                const actual = rowValue(row, card.filterField);
                const actualText = actual == null ? "" : String(actual);
                const expected = card.filterValue == null ? "" : String(card.filterValue);
                const op = String(card.filterOperator || "equals").toLowerCase();
                const a = numeric(actual), b = numeric(expected);
                if (op === "contains") return actualText.toLowerCase().includes(expected.toLowerCase());
                if (["gt", "gte", "lt", "lte"].includes(op) && a !== null && b !== null)
                    return op === "gt" ? a > b : op === "gte" ? a >= b : op === "lt" ? a < b : a <= b;
                return op === "not_equals" ? actualText.toLowerCase() !== expected.toLowerCase()
                    : actualText.toLowerCase() === expected.toLowerCase();
            };
            configured.forEach((card, index) => {
                const subset = state.detailRows.filter(row => matches(row, card));
                const field = String(card.field || "");
                const operation = String(card.operation || "COUNT_ROWS").toUpperCase();
                const values = field ? subset.map(row => rowValue(row, field)) : [];
                let value = 0;
                if (operation === "COUNT_ROWS") value = subset.length;
                else if (operation === "COUNT_NON_EMPTY") value = values.filter(item => item !== null && item !== undefined && item !== "").length;
                else if (operation === "COUNT_TRUE") value = values.filter(item => item === true || String(item).toLowerCase() === "true").length;
                else if (operation === "COUNT_FALSE") value = values.filter(item => item === false || String(item).toLowerCase() === "false").length;
                else {
                    const numbers = values.map(numeric).filter(item => item !== null);
                    if (operation === "SUM") value = numbers.reduce((sum, item) => sum + item, 0);
                    else if (operation === "AVG") value = numbers.length ? numbers.reduce((sum, item) => sum + item, 0) / numbers.length : null;
                    else if (operation === "MAX") value = numbers.length ? Math.max(...numbers) : null;
                    else if (operation === "MIN") value = numbers.length ? Math.min(...numbers) : null;
                }
                cards.push({ label: card.label || (operation === "COUNT_ROWS" ? "Total Rows" : field || "Summary"),
                    value: value === null ? "—" : formatValue(value, card.format), cls: "", filterField: null, filterType: "custom-" + index });
            });
        } else {
            const numericFields = state.numericFields;
            const labels = state.config.labels && typeof state.config.labels === "object" ? state.config.labels : {};
            const rules = summary.thresholds && typeof summary.thresholds === "object" ? summary.thresholds : {};
            const sourceTotals = state.totalRow;
            const totals = {};
            const calculatedTotal = field => state.detailRows.reduce((sum, row) => sum + (numeric(rowValue(row, field)) || 0), 0);
            numericFields.forEach(field => {
                const authoritativeValue = sourceTotals ? numeric(rowValue(sourceTotals, field)) : null;
                totals[field] = authoritativeValue !== null ? authoritativeValue : calculatedTotal(field);
            });
            if (summary.totalCard && summary.totalCard.enabled === true) {
                const authoritativeValue = sourceTotals && summary.totalCard.field ? numeric(rowValue(sourceTotals, summary.totalCard.field)) : null;
                const overall = summary.totalCard.field ? (authoritativeValue !== null ? authoritativeValue : calculatedTotal(summary.totalCard.field))
                    : numericFields.reduce((sum, field) => sum + (totals[field] || 0), 0);
                cards.push({ label: summary.totalCard.label || "Overall Total", value: overall || 0,
                    cls: summaryClass(overall || 0, summary.totalCard.thresholds), filterField: null, filterType: "overall" });
            }
            const excluded = new Set(Array.isArray(summary.excludedFields) ? summary.excludedFields.map(String) : []);
            numericFields.filter(field => !excluded.has(field)).forEach(field => {
                cards.push({ label: labels[field] || (state.columnMap.get(field) || {}).label || field,
                    value: totals[field], cls: summaryClass(totals[field], rules[field]), filterField: field, filterType: "field" });
            });
        }
        if (!cards.length) return "";
        return '<div class="server-health-summary summary-table-cards">' + cards.map(card => {
            const active = state.cardFilter && state.cardFilter.type === card.filterType && state.cardFilter.field === card.filterField;
            const status = card.filterType === "overall" ? "total"
                : String(card.cls || "").includes("critical") ? "critical"
                : String(card.cls || "").includes("warning") ? "warning"
                : String(card.cls || "").includes("healthy") ? "healthy" : "unknown";
            return '<button type="button" class="server-health-summary-item server-health-summary-' + status +
                ' server-health-summary-filter summary-table-card' + (active ? ' is-active' : '') +
                '" data-card-filter-type="' + esc(card.filterType) + '" data-card-filter-field="' + esc(card.filterField || '') +
                '" aria-label="' + esc(card.label) + '" aria-pressed="' + Boolean(active) + '">' +
                '<span class="summary-table-card-label">' + esc(card.label) + '</span>' +
                '<strong class="summary-table-card-value">' + esc(display(card.value)) + '</strong></button>';
        }).join("") + '</div>';
    }

    function renderContent(state) {
        const config = state.config;
        const details = config.details || {};
        const groupConfig = details.groupBy || config.groupBy || {};
        const grouping = groupConfig.enabled === true;
        const groupFields = grouping && Array.isArray(groupConfig.fields)
            ? groupConfig.fields.map(String).filter(field => field && (!state.columnMap.has(field) || state.columnMap.get(field).groupable !== false))
            : [];
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
        const selectedGroupFields = groupFields;
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
        const tableControls = toolbar || groupSelector
            ? '<div class="summary-table-control-row">' + toolbar + groupSelector + '</div>' : "";
        return renderCards(state) + tableControls + '<div class="summary-table-scroll"><table class="table summary-table-grid mb-0"><thead><tr>' + header +
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
            !configuredExcluded.has(field) &&
            (detailRows.some(row => numeric(rowValue(row, field)) !== null) ||
                (totalRow && numeric(rowValue(totalRow, field)) !== null)));
        const state = {
            config, widget, result, columns, columnMap, detailRows, totalRow, numericFields,
            search: "", sortField: ((config.details || {}).sorting || {}).defaultField || "",
            sortDirection: String((((config.details || {}).sorting || {}).direction) || "asc").toLowerCase() === "desc" ? "desc" : "asc",
            page: 0, collapsed: new Set(), cardFilter: null
        };
        const groupConfig = (config.details || {}).groupBy || config.groupBy || {};
        if (groupConfig.enabled === true && groupConfig.initialState === "COLLAPSED" && Array.isArray(groupConfig.fields) && groupConfig.fields.length) {
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
            const groupToggle = found.root.querySelector(".summary-table-group-toggle-menu");
            if (groupToggle && window.bootstrap && window.bootstrap.Dropdown) {
                window.bootstrap.Dropdown.getOrCreateInstance(groupToggle).show();
            }
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