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

    function formatDateTime(value) {
        const date = value instanceof Date ? value : new Date(value);
        if (Number.isNaN(date.getTime())) return display(value);

        const pattern = String(window.monitoringDateTimeFormat || "MM/dd/yyyy hh:mm:ss a");
        const parts = {
            yyyy: String(date.getFullYear()).padStart(4, "0"),
            yyy: String(date.getFullYear()).padStart(3, "0"),
            yy: String(date.getFullYear()).slice(-2),
            y: String(date.getFullYear()),
            MMMM: date.toLocaleString("en-US", { month: "long" }),
            MMM: date.toLocaleString("en-US", { month: "short" }),
            MM: String(date.getMonth() + 1).padStart(2, "0"),
            M: String(date.getMonth() + 1),
            dd: String(date.getDate()).padStart(2, "0"),
            d: String(date.getDate()),
            HH: String(date.getHours()).padStart(2, "0"),
            H: String(date.getHours()),
            hh: String((date.getHours() % 12) || 12).padStart(2, "0"),
            h: String((date.getHours() % 12) || 12),
            mm: String(date.getMinutes()).padStart(2, "0"),
            m: String(date.getMinutes()),
            ss: String(date.getSeconds()).padStart(2, "0"),
            s: String(date.getSeconds()),
            a: date.getHours() < 12 ? "AM" : "PM"
        };
        const tokens = /'[^']*(?:''[^']*)*'|yyyy|yyy|yy|y|MMMM|MMM|MM|M|dd|d|HH|H|hh|h|mm|m|ss|s|a|./g;
        return pattern.replace(tokens, token => {
            if (token.startsWith("'") && token.endsWith("'")) return token.slice(1, -1).replace(/''/g, "'");
            return Object.prototype.hasOwnProperty.call(parts, token) ? parts[token] : token;
        });
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
        if (normalized === "datetime") return formatDateTime(value);
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
        const ranks = { info: 1, healthy: 2, warning: 3, critical: 4 };
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
        const rank = { "": 0, "is-unknown": 0, "is-info": 1, "is-healthy": 2, "is-warning": 3, "is-critical": 4 };
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
            if (ordering === "COUNT_ASC") return a[1].length - b[1].length;
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
                htmlRows.push(renderRow(node.row, columns, config, state));
                return;
            }

            const collapsed = state.collapsed.has(node.id);
            const groupConfig = (config.details && config.details.groupBy) || config.groupBy || config;
            const showAggregates = groupConfig.showAggregates === true;
            const count = node.rows.length;
            const aggregateByField = new Map();

            if (showAggregates) {
                columns.filter(column => String(column.aggregation || "none").toLowerCase() !== "none")
                    .forEach(column => {
                        const operation = String(column.aggregation || "none").toLowerCase();
                        const values = node.rows.map(row => rowValue(row, column.field))
                            .filter(value => value !== null && value !== undefined && value !== "");
                        let result;

                        if (operation === "count") {
                            result = values.length;
                        } else {
                            const numbers = values.map(numeric).filter(value => value !== null);
                            if (!numbers.length) return;
                            if (operation === "sum") result = numbers.reduce((sum, value) => sum + value, 0);
                            else if (operation === "avg") result = numbers.reduce((sum, value) => sum + value, 0) / numbers.length;
                            else if (operation === "max") result = Math.max(...numbers);
                            else if (operation === "min") result = Math.min(...numbers);
                        }

                        if (result !== undefined) {
                            aggregateByField.set(column.field,
                                Number.isFinite(result) ? result.toLocaleString() : String(result));
                        }
                    });
            }

            const groupColumnIndex = columns.findIndex(column => column.field === node.field);
            const fallbackColumnIndex = groupColumnIndex >= 0 ? groupColumnIndex : 0;
            const cells = columns.map((column, index) => {
                const aggregate = aggregateByField.get(column.field);
                const groupLabel = index === fallbackColumnIndex
                    ? '<button type="button" class="summary-table-group-toggle" data-group-id="' + esc(node.id) +
                        '" aria-expanded="' + (!collapsed) + '"><i class="bi ' +
                        (collapsed ? "bi-chevron-right" : "bi-chevron-down") +
                        '"></i><strong>' + esc(node.field) + ': ' + esc(node.value) +
                        '</strong><span class="summary-table-group-count">' + count + ' rows</span></button>'
                    : "";

                if (index === fallbackColumnIndex) {
                    return '<td class="summary-table-group-value-cell">' +
                        (groupLabel || (aggregate !== undefined ? esc(aggregate) : "")) + '</td>';
                }
                if (aggregate !== undefined) {
                    return '<td class="summary-table-group-aggregate-cell">' + esc(aggregate) + '</td>';
                }
                return '<td></td>';
            }).join("");

            htmlRows.push('<tr class="summary-table-group-row"><td class="summary-table-select-cell"></td>' + cells + '</tr>');
            if (!collapsed) flattenNodes(node.children, config, state, columns, htmlRows);
        });
    }

    function renderRow(row, columns, config, state) {
        const css = rowClass(row, config);
        let rowId = state.rowIds.get(row);
        if (!rowId) {
            rowId = "row-" + (++state.nextRowId);
            state.rowIds.set(row, rowId);
        }
        state.visibleRowIds.push(rowId);
        return '<tr' + (css ? ' class="summary-table-row ' + css + '"' : ' class="summary-table-row"') + '>' +
            '<td class="summary-table-select-cell"><input type="checkbox" class="form-check-input summary-table-row-select" data-row-select-id="' + esc(rowId) + '" aria-label="Select row"' + (state.selectedRows.has(rowId) ? ' checked' : '') + '></td>' +
            columns.map(column => {
                const value = rowValue(row, column.field);
                const cellClass = conditionalRuleClass(row, config, "cell", column.field);
                const rawBoolean = value === true || value === false || (typeof value === "string" && /^(true|false)$/i.test(value.trim()));
                const statusValue = String(value == null ? "" : value).trim().toLowerCase();
                const isBoolean = rawBoolean || ["healthy", "health", "up", "active", "enabled", "ok", "pass", "passed", "success", "green", "1", "yes",
                    "unhealthy", "down", "inactive", "disabled", "error", "failed", "fail", "critical", "red", "0", "no"].includes(statusValue);
                const boolValue = rawBoolean ? (value === true || String(value).trim().toLowerCase() === "true")
                    : ["healthy", "health", "up", "active", "enabled", "ok", "pass", "passed", "success", "green", "1", "yes"].includes(statusValue);
                if (String(column.format || "").toLowerCase() === "toggle") {
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
        const searchConfig = details.search || state.config.search || {};
        const requestedSearchFields = Array.isArray(searchConfig.fields)
            ? searchConfig.fields.map(String)
            : state.columns.filter(column => column.searchable !== false).map(column => column.field);
        const summaryCountRows = state.search && searchConfig.enabled !== false
            ? state.detailRows.filter(row => requestedSearchFields.some(field =>
                String(display(rowValue(row, field))).toLowerCase().includes(state.search.toLowerCase())))
            : state.detailRows;
        const hasNewCards = Array.isArray(summary.cards);
        const hasLegacyCards = Array.isArray(details.summaryCards);
        const hasConfiguredCards = hasNewCards || hasLegacyCards;
        const configured = hasNewCards && (summary.cards.length > 0 || !hasLegacyCards)
            ? summary.cards : (hasLegacyCards ? details.summaryCards : (hasNewCards ? summary.cards : []));
        const cards = [];
        const totalRowsCard = summary.totalRowsCard && typeof summary.totalRowsCard === "object" ? summary.totalRowsCard : {};
        if (totalRowsCard.enabled === true) {
            cards.push({ label: totalRowsCard.label || "Total Rows", value: summaryCountRows.length, cls: "",
                filterField: null, filterValue: "", filterType: "grouped-all", filterOperation: "COUNT_ROWS",
                filterable: totalRowsCard.filterable === true,
                icon: totalRowsCard.icon && totalRowsCard.icon.enabled === true
                    ? String(totalRowsCard.icon.name || "list-check") : "" });
        }
        if (hasConfiguredCards) {
            const matches = (row, card) => !card.filterField || conditionalRuleMatches(row, {
                field: card.filterField,
                operator: card.filterOperator || "equals",
                value: card.filterValue
            });
            configured.forEach((card, index) => {
                const subset = state.detailRows.filter(row => matches(row, card));
                const field = String(card.field || "");
                const operation = String(card.operation || "COUNT_ROWS").toUpperCase();
                const values = field ? subset.map(row => rowValue(row, field)) : [];
                let value = 0;
                if (operation === "COUNT_ROWS") value = subset.length;
                else if (operation === "VALUE") value = values.length ? values[0] : null;
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
                const configuredFilterField = String(card.filterField || "");
                const filterType = configuredFilterField || operation === "VALUE" ? "custom-rule" : (field ? "custom-field" : "all");
                const defaultFilterable = Boolean(configuredFilterField || field || operation !== "COUNT_ROWS");
                const mapping = card.valueMapping && typeof card.valueMapping === "object" ? card.valueMapping : {};
                const mapped = value === true ? mapping.true : value === false ? mapping.false : value;
                const icon = card.icon && typeof card.icon === "object" && card.icon.enabled === true ? String(card.icon.name || "activity") : "";
                const label = card.label || (operation === "COUNT_ROWS" ? "Total Rows" : field || "Summary");
                const filterable = card.filterable === true || (card.filterable !== false && defaultFilterable);
                if (card.groupBy) {
                    const groups = new Map();
                    subset.forEach(row => {
                        const raw = rowValue(row, String(card.groupBy));
                        const key = raw === null || raw === undefined || String(raw).trim() === "" ? "" : String(raw);
                        if (!groups.has(key)) groups.set(key, []);
                        groups.get(key).push(row);
                    });
                    groups.forEach((groupRows, groupValue) => {
                        const groupValues = field ? groupRows.map(row => rowValue(row, field)) : [];
                        let result = groupRows.length;
                        if (operation === "VALUE") result = groupValues.length ? groupValues[0] : null;
                        else if (operation === "COUNT_NON_EMPTY") result = groupValues.filter(item => item !== null && item !== undefined && item !== "").length;
                        else if (operation === "COUNT_TRUE") result = groupValues.filter(item => item === true || String(item).toLowerCase() === "true").length;
                        else if (operation === "COUNT_FALSE") result = groupValues.filter(item => item === false || String(item).toLowerCase() === "false").length;
                        else if (["SUM","AVG","MAX","MIN"].includes(operation)) {
                            const nums = groupValues.map(numeric).filter(item => item !== null);
                            result = operation === "SUM" ? nums.reduce((sum,item)=>sum+item,0)
                                : operation === "AVG" ? (nums.length ? nums.reduce((sum,item)=>sum+item,0)/nums.length : null)
                                : operation === "MAX" ? (nums.length ? Math.max(...nums) : null)
                                : (nums.length ? Math.min(...nums) : null);
                        }
                        const mappedResult = result === true ? mapping.true : result === false ? mapping.false : result;
                        const mappedGroup = String(groupValue).toLowerCase() === "true" && mapping.true !== undefined ? mapping.true
                            : String(groupValue).toLowerCase() === "false" && mapping.false !== undefined ? mapping.false
                            : (groupValue === "" ? "(empty)" : display(groupValue));
                        cards.push({label:label + ": " + mappedGroup,
                            value:result === null ? "—" : formatValue(mappedResult === undefined ? result : mappedResult,card.format), cls:"",
                            filterField:String(card.groupBy),filterValue:groupValue,filterType:"grouped-value",
                            filterOperation:operation,filterable,icon});
                    });
                } else {
                    cards.push({ label, value: value === null ? "—" : formatValue(mapped === undefined ? value : mapped, card.format), cls: "",
                        filterField: configuredFilterField || field || null,
                        filterValue: operation === "VALUE" ? value : card.filterValue, filterOperator: card.filterOperator || "equals",
                        filterType, filterOperation: operation, filterable, icon });
                }
            });
            const groupedSummaryFields = (!hasNewCards || (summary.cards.length === 0 && hasLegacyCards)) &&
                Array.isArray(summary.groupByFields) ? summary.groupByFields.map(String) : [];
            if (groupedSummaryFields.length) {
                cards.unshift({ label: "All", value: summaryCountRows.length, cls: "",
                    filterField: null, filterValue: "", filterType: "grouped-all", filterOperation: "COUNT_ROWS", filterable: true });
            }
            groupedSummaryFields.forEach(field => {
                const counts = new Map();
                summaryCountRows.forEach(row => {
                    const raw = rowValue(row, field);
                    const key = raw === null || raw === undefined || String(raw).trim() === "" ? "" : String(raw);
                    counts.set(key, (counts.get(key) || 0) + 1);
                });
                const groupedColumn = state.columnMap.get(field) || {};
                const fieldLabel = groupedColumn.label || field;
                counts.forEach((count, value) => {
                    const displayValue = value === "" ? "(empty)" : formatValue(value, groupedColumn.format);
                    cards.push({ label: fieldLabel + ": " + displayValue, value: count, cls: "",
                        filterField: field, filterValue: value, filterType: "grouped-value", filterOperation: "COUNT_ROWS", filterable: true });
                });
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
                    cls: summaryClass(overall || 0, summary.totalCard.thresholds), filterField: null, filterType: "overall",
                    filterable: summary.totalCard.filterable !== false });
            }
            const excluded = new Set(Array.isArray(summary.excludedFields) ? summary.excludedFields.map(String) : []);
            numericFields.filter(field => !excluded.has(field)).forEach(field => {
                cards.push({ label: labels[field] || (state.columnMap.get(field) || {}).label || field,
                    value: totals[field], cls: summaryClass(totals[field], rules[field]), filterField: field,
                    filterType: "field", filterable: true });
            });
        }
        if (!cards.length) return "";
        return '<div class="server-health-summary summary-table-cards">' + cards.map(card => {
            const filterable = card.filterable !== false;
            const active = filterable && (card.filterType === "grouped-all"
                ? (!state.cardFilter || state.cardFilter.type === "grouped-all")
                : state.cardFilter && state.cardFilter.type === card.filterType &&
                    state.cardFilter.field === card.filterField &&
                    state.cardFilter.operation === (card.filterOperation || "") &&
                    (["grouped-value", "custom-rule"].includes(card.filterType)
                        ? String(state.cardFilter.value ?? "") === String(card.filterValue ?? "")
                        : true) &&
                    (card.filterType !== "custom-rule" ||
                        (state.cardFilter.operator || "equals") === (card.filterOperator || "equals")));
            const status = card.filterType === "overall" ? "total"
                : String(card.cls || "").includes("critical") ? "critical"
                : String(card.cls || "").includes("warning") ? "warning"
                : String(card.cls || "").includes("healthy") ? "healthy" : "unknown";
            const iconMarkup = card.icon ? '<i class="bi bi-' + esc(card.icon) + ' summary-table-card-icon" aria-hidden="true"></i>' : "";
            const content = iconMarkup + '<span class="summary-table-card-label">' + esc(card.label) + '</span>' +
                '<strong class="summary-table-card-value">' + esc(display(card.value)) + '</strong>';
            const cardClasses = 'server-health-summary-item server-health-summary-' + status + ' summary-table-card' +
                (filterable ? ' server-health-summary-filter summary-table-card-filter' : ' summary-table-card-static') +
                (active ? ' is-active' : '');
            if (!filterable) {
                return '<div class="' + cardClasses + '" aria-label="' + esc(card.label) + '">' + content + '</div>';
            }
            return '<button type="button" class="' + cardClasses +
                '" data-card-filter-type="' + esc(card.filterType) + '" data-card-filter-field="' + esc(card.filterField || '') +
                '" data-card-filter-value="' + esc(card.filterValue === undefined || card.filterValue === null ? "" : card.filterValue) +
                '" data-card-filter-operator="' + esc(card.filterOperator || '') +
                '" aria-label="' + esc(card.label) + '" data-card-filter-operation="' + esc(card.filterOperation || '') +
                '" aria-pressed="' + Boolean(active) + '">' + content + '</button>';
        }).join("") + '</div>';
    }

    function getFilteredSortedRows(state) {
        const details = state.config.details || {};
        const searchConfig = details.search || state.config.search || {};
        const sortConfig = details.sorting || state.config.sorting || {};
        let rows = state.detailRows.slice();
        if (state.cardFilter) {
            if (state.cardFilter.type === "grouped-all") {
                // The All card intentionally leaves the complete row set visible.
            } else if (state.cardFilter.type === "field" && state.cardFilter.field) {
                const field = state.cardFilter.field;
                rows = rows.filter(row => (numeric(rowValue(row, field)) || 0) > 0);
            } else if (state.cardFilter.type === "overall") {
                rows = rows.filter(row => state.numericFields.some(field => (numeric(rowValue(row, field)) || 0) > 0));
            } else if (state.cardFilter.type === "grouped-value" && state.cardFilter.field) {
                const field = state.cardFilter.field;
                const expected = String(state.cardFilter.value ?? "");
                rows = rows.filter(row => {
                    const actual = rowValue(row, field);
                    return String(actual === null || actual === undefined ? "" : actual) === expected;
                });
            } else if (state.cardFilter.type === "custom-rule" && state.cardFilter.field) {
                const rule = { field: state.cardFilter.field, operator: state.cardFilter.operator || "equals", value: state.cardFilter.value };
                rows = rows.filter(row => conditionalRuleMatches(row, rule));
            } else if (state.cardFilter.type === "custom-field" && state.cardFilter.field) {
                const field = state.cardFilter.field;
                const operation = String(state.cardFilter.operation || "").toUpperCase();
                rows = rows.filter(row => {
                    const value = rowValue(row, field);
                    if (operation === "COUNT_TRUE") return value === true || String(value).toLowerCase() === "true";
                    if (operation === "COUNT_FALSE") return value === false || String(value).toLowerCase() === "false";
                    if (operation === "COUNT_NON_EMPTY") return value !== null && value !== undefined && value !== "";
                    const number = numeric(value);
                    return number !== null && number > 0;
                });
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
        return rows;
    }

    function exportTableCsv(state) {
        const columns = state.columns.filter(column => column.visible !== false);
        const rows = getFilteredSortedRows(state);
        const quote = value => {
            const text = String(value === null || value === undefined ? "" : display(value));
            return '"' + text.replace(/"/g, '""') + '"';
        };
        const lines = [columns.map(column => quote(column.label || column.field)).join(",")];
        rows.forEach(row => {
            lines.push(columns.map(column => quote(rowValue(row, column.field))).join(","));
        });
        const blob = new Blob(["\uFEFF" + lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "summary-table-" + new Date().toISOString().slice(0, 10) + ".csv";
        link.style.display = "none";
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    }

    function renderContent(state) {
        const config = state.config;
        const details = config.details || {};
        const summaryLayout = String((config.summary || {}).layout || "").toUpperCase();
        const groupConfig = details.groupBy || config.groupBy || {};
        const grouping = groupConfig.enabled === true;
        const groupFields = grouping && Array.isArray(groupConfig.fields)
            ? groupConfig.fields.map(String).filter(field => field && (!state.columnMap.has(field) || state.columnMap.get(field).groupable !== false))
            : [];
        const searchConfig = details.search || config.search || {};
        const sortConfig = details.sorting || config.sorting || {};
        const pageConfig = details.pagination || config.pagination || {};
        let rows = getFilteredSortedRows(state);
        const columns = state.columns.filter(column => column.visible !== false);
        const visibleFields = new Set(columns.map(column => column.field));
        const toolbar = searchConfig.enabled === false ? "" : '<div class="summary-table-toolbar"><input class="form-control form-control-sm summary-table-search" type="search" placeholder="' + esc(searchConfig.placeholder || "Search table...") + '" value="' + esc(state.search) + '" aria-label="Search table"></div>';
        if (summaryLayout === "CARDS_ONLY" || details.enabled === false) return renderCards(state);
        if (!columns.length) return renderCards(state) + '<div class="summary-table-empty">No visible detail columns configured.</div>';
        state.visibleRowIds = [];
        const header = '<th scope="col" class="summary-table-select-cell"><input type="checkbox" class="form-check-input summary-table-select-all" aria-label="Select all rows on this page"></th>' + columns.map(column => {
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
            body = pageRows.map(row => renderRow(row, columns, config, state)).join("");
        }
        if (!body) body = '<tr><td colspan="' + (Math.max(1, columns.length) + 1) + '" class="summary-table-empty-cell">No matching rows.</td></tr>';
        const pager = pageConfig.enabled === false ? "" : '<div class="summary-table-pagination"><span class="small text-muted">Showing ' +
            (totalCount ? (state.page * pageSize + 1) : 0) + '–' + Math.min((state.page + 1) * pageSize, totalCount) + ' of ' + totalCount +
            (grouping && groupFields.length ? ' groups' : ' rows') + '</span><div class="d-flex align-items-center gap-2"><label class="small text-muted">' +
            (grouping && groupFields.length ? 'Groups' : 'Rows') + ' <select class="form-select form-select-sm summary-table-page-size" aria-label="' +
            (grouping && groupFields.length ? 'Groups' : 'Rows') + ' per page">' +
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
        const selectionCount = state.selectedRows.size;
        const selectionStatus = selectionCount ? '<span class="small text-muted summary-table-selection-count" aria-live="polite">' + selectionCount + ' selected</span>' : "";
        const exportButton = '<button type="button" class="btn btn-sm btn-outline-secondary summary-table-export" title="Export all filtered rows as CSV"><i class="bi bi-download me-1" aria-hidden="true"></i>Export CSV</button>';
        const tableControls = '<div class="summary-table-control-row">' + toolbar + groupSelector + exportButton + selectionStatus + '</div>';
        return (summaryLayout === "TABLE_ONLY" ? "" : renderCards(state)) + tableControls + '<div class="summary-table-scroll"><table class="table summary-table-grid mb-0"><thead><tr>' + header +
            '</tr></thead><tbody>' + body + '</tbody></table></div>' + pager;
    }

    function syncSelectionHeader(root, state) {
        const header = root.querySelector(".summary-table-select-all");
        if (!header) return;
        const visible = state.visibleRowIds || [];
        const selected = visible.filter(id => state.selectedRows.has(id)).length;
        header.checked = visible.length > 0 && selected === visible.length;
        header.indeterminate = selected > 0 && selected < visible.length;
        header.disabled = visible.length === 0;
    }

    function rerender(root, state) {
        root.innerHTML = renderContent(state);
        syncSelectionHeader(root, state);
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
            page: 0, collapsed: new Set(), cardFilter: null,
            rowIds: new WeakMap(), nextRowId: 0, selectedRows: new Set(), visibleRowIds: []
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
        if (event.target.matches(".summary-table-select-all, .summary-table-row-select")) {
            const found = stateFor(event.target);
            if (!found || !found.state) return;
            const state = found.state;
            if (event.target.matches(".summary-table-select-all")) {
                const shouldSelect = event.target.checked;
                (state.visibleRowIds || []).forEach(id => {
                    if (shouldSelect) state.selectedRows.add(id);
                    else state.selectedRows.delete(id);
                });
            } else {
                const rowId = event.target.getAttribute("data-row-select-id");
                if (event.target.checked) state.selectedRows.add(rowId);
                else state.selectedRows.delete(rowId);
            }
            rerender(found.root, state);
            return;
        }
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
        const cardButton = event.target.closest(".summary-table-card-filter");
        const groupClearButton = event.target.closest(".summary-table-group-clear");
        const prev = event.target.closest(".summary-table-prev");
        const next = event.target.closest(".summary-table-next");
        const exportButton = event.target.closest(".summary-table-export");
        if (!sortButton && !groupButton && !cardButton && !groupClearButton && !prev && !next && !exportButton) return;
        const found = stateFor(event.target);
        if (!found || !found.state) return;
        const state = found.state;
        if (exportButton) {
            exportTableCsv(state);
            return;
        }
        if (cardButton) {
            const type = cardButton.getAttribute("data-card-filter-type");
            const field = cardButton.getAttribute("data-card-filter-field") || null;
            const operation = cardButton.getAttribute("data-card-filter-operation") || "";
            const value = cardButton.getAttribute("data-card-filter-value") || "";
            if (type === "grouped-all") {
                state.cardFilter = null;
            } else if (state.cardFilter && state.cardFilter.type === type && state.cardFilter.field === field &&
                state.cardFilter.operation === operation &&
                (!["grouped-value", "custom-rule"].includes(type) || String(state.cardFilter.value ?? "") === value) &&
                (type !== "custom-rule" || (state.cardFilter.operator || "equals") === (cardButton.getAttribute("data-card-filter-operator") || "equals"))) {
                state.cardFilter = null;
            } else {
                state.cardFilter = { type, field, operation, value,
                    operator: cardButton.getAttribute("data-card-filter-operator") || "equals" };
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