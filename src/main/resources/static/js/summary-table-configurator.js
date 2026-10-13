(function () {
    "use strict";

    const byId = id => document.getElementById(id);
    const state = { sample: null, collections: [], rows: [], fields: [] };

    function escapeHtml(value) {
        return String(value === null || value === undefined ? "" : value)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function humanize(value) {
        return String(value).replace(/([a-z0-9])([A-Z])/g, "$1 $2")
            .replace(/[._-]+/g, " ").replace(/^./, c => c.toUpperCase());
    }

    function currentParserConfig() {
        const config = getConfig();
        const existing = config.responseParser && typeof config.responseParser === "object"
            ? config.responseParser : {};
        const type = byId("summaryTableResponseFormat").value;
        const parser = Object.assign({}, existing, { type });
        if (type === "DELIMITED") {
            const selectedDelimiter = byId("summaryTableDelimiter").value;
            parser.delimiter = selectedDelimiter === "custom" ? byId("summaryTableCustomDelimiter").value
                : (selectedDelimiter === "\\t" ? "\t" : selectedDelimiter);
            parser.commentPrefix = byId("summaryTableCommentPrefix").value;
            parser.headerRow = byId("summaryTableHasHeader").checked;
            parser.trim = true;
            parser.ignoreBlankLines = true;
        } else if (type === "KEY_VALUE") {
            const selectedSeparator = byId("summaryTableKeyValueSeparator").value;
            parser.separator = selectedSeparator === "custom" ? byId("summaryTableCustomSeparator").value
                : (selectedSeparator === "\\t" ? "\t" : selectedSeparator);
            parser.ignorePrefixes = byId("summaryTableIgnoredPrefixes").value
                .split(/\r?\n/).map(value => value.trim()).filter(Boolean);
        } else if (type === "REGEX") {
            parser.fields = readRegexFields();
        }
        return parser;
    }

    function parseSample(text) {
        const parser = currentParserConfig();
        const type = String(parser.type || "AUTO").toUpperCase();
        if (type === "AUTO" || type === "JSON") {
            const value = JSON.parse(text);
            if (value === null || typeof value !== "object") {
                throw new Error("The sample must be a JSON object or array.");
            }
            return value;
        }

        if (type === "PLAIN_TEXT") {
            const lines = text.split(/\\r?\\n/);
            const rows = lines.filter(line => line.trim() !== "").map((line, index) => ({
                lineNumber: index + 1,
                text: line
            }));
            if (!rows.length) throw new Error("No text lines were found in the sample.");
            return { rows };
        }

        if (type === "DELIMITED") {
            const delimiter = String(parser.delimiter === undefined ? "|" : parser.delimiter);
            if (!delimiter) throw new Error("Enter a non-empty delimiter.");
            const lines = text.split(/\r?\n/)
                .filter(line => line.trim() !== "" && !(parser.commentPrefix && line.trim().startsWith(parser.commentPrefix)))
                .map(line => line.split(delimiter).map(value => parser.trim === false ? value : value.trim()));
            if (!lines.length) throw new Error("No delimited rows were found.");
            const headers = parser.headerRow === false
                ? Array.from({ length: Math.max(...lines.map(line => line.length)) }, (_, index) => "column" + (index + 1))
                : lines[0];
            const firstData = parser.headerRow === false ? 0 : 1;
            const rows = lines.slice(firstData).map(line => {
                const row = {};
                headers.forEach((header, index) => { row[String(header || ("column" + (index + 1)))] = line[index] === undefined ? "" : line[index]; });
                return row;
            });
            if (!rows.length) throw new Error("No data rows were found. Check whether the sample contains a header row.");
            return { rows };
        }

        if (type === "KEY_VALUE") {
            const separator = String(parser.separator === undefined ? ":" : parser.separator);
            if (!separator) throw new Error("Enter a non-empty key-value separator.");
            const ignored = Array.isArray(parser.ignorePrefixes) ? parser.ignorePrefixes : [];
            const row = {};
            text.split(/\r?\n/).forEach(line => {
                if (!line.trim() || ignored.some(prefix => line.trim().startsWith(prefix))) return;
                const index = line.indexOf(separator);
                if (index <= 0) return;
                row[line.slice(0, index).trim()] = line.slice(index + separator.length).trim();
            });
            if (!Object.keys(row).length) throw new Error("No key-value entries were found.");
            return { rows: [row] };
        }

        if (type === "REGEX") {
            const fields = Array.isArray(parser.fields) ? parser.fields : [];
            if (!fields.length) throw new Error("Configure responseParser.fields in Advanced JSON Configuration before using regex extraction.");
            const row = {};
            fields.forEach(field => {
                if (!field || !field.key || !field.pattern) throw new Error("Each regex field requires a key and pattern.");
                const match = new RegExp(field.pattern, "m").exec(text);
                if (!match) {
                    if (field.required === true) throw new Error("Required regex field was not found: " + field.key);
                    return;
                }
                const group = Number.isInteger(field.group) ? field.group : 1;
                if (group < 0 || group >= match.length) throw new Error("Invalid capture group for field: " + field.key);
                let value = match[group];
                const valueType = String(field.type || "string").toLowerCase();
                if (["number", "decimal", "percentage"].includes(valueType)) {
                    const number = Number(String(value).replace(/,/g, "").replace(/%/g, "").trim());
                    if (!Number.isFinite(number)) throw new Error("Value for " + field.key + " is not numeric.");
                    value = number;
                } else if (["integer", "long"].includes(valueType)) {
                    const number = Number(String(value).replace(/,/g, "").trim());
                    if (!Number.isInteger(number)) throw new Error("Value for " + field.key + " is not an integer.");
                    value = number;
                } else if (valueType === "boolean") {
                    value = String(value).toLowerCase() === "true";
                }
                row[field.key] = value;
            });
            if (!Object.keys(row).length) throw new Error("No configured regex fields matched the sample.");
            return { rows: [row] };
        }

        throw new Error("Unsupported response format: " + type);
    }

    function currentWidgetType() {
        return String(byId("dashboardWidgetType").value || "").toUpperCase();
    }

    function getConfig() {
        const field = byId("dashboardWidgetFieldConfigJson");
        try {
            const value = JSON.parse(field.value || "{}");
            return value && typeof value === "object" && !Array.isArray(value) ? value : {};
        } catch (error) {
            return {};
        }
    }

    function setConfig(config) {
        byId("dashboardWidgetFieldConfigJson").value = JSON.stringify(config, null, 2);
        byId("dashboardWidgetFieldConfigJson").dispatchEvent(new Event("input", { bubbles: true }));
        renderPreview();
    }

    function findCollections(root) {
        const found = [];
        const visited = new Set();
        function visit(value, path, depth) {
            if (depth > 12 || value === null || typeof value !== "object" || visited.has(value)) return;
            visited.add(value);
            if (Array.isArray(value)) {
                const rows = value.filter(item => item && typeof item === "object" && !Array.isArray(item));
                if (rows.length) found.push({ path, rows, label: path || "(root array)" });
                // Do not expose array-index paths such as consumers.0.topics as reusable row paths.
                return;
            }
            Object.keys(value).forEach(key => {
                const nextPath = path ? path + "." + key : key;
                visit(value[key], nextPath, depth + 1);
            });
        }
        visit(root, "", 0);
        return found;
    }

    function selectedCollection() {
        const path = byId("summaryTableRowPath").value;
        return state.collections.find(item => item.path === path) || null;
    }

    function readRegexFields() {
        return Array.from(byId("summaryTableRegexFields").querySelectorAll("[data-regex-field]")).map(row => ({
            key: row.querySelector(".summary-regex-key").value.trim(),
            pattern: row.querySelector(".summary-regex-pattern").value,
            group: Number.parseInt(row.querySelector(".summary-regex-group").value, 10),
            type: row.querySelector(".summary-regex-type").value,
            required: row.querySelector(".summary-regex-required").checked
        }));
    }

    function renderRegexFields() {
        const config = getConfig();
        if (isSummary) renderSummaryCards(config);
        const parser = config.responseParser && typeof config.responseParser === "object" ? config.responseParser : {};
        const fields = Array.isArray(parser.fields) ? parser.fields : [];
        const host = byId("summaryTableRegexFields");
        host.innerHTML = fields.map((field, index) =>
            '<div class="border rounded p-2 mb-2" data-regex-field>' +
            '<div class="row g-2 align-items-end">' +
            '<div class="col-md-3"><label class="form-label small">Output field</label><input class="form-control form-control-sm summary-regex-key" maxlength="100" value="' + escapeHtml(field.key || "") + '"></div>' +
            '<div class="col-md-4"><label class="form-label small">Regular expression</label><input class="form-control form-control-sm font-monospace summary-regex-pattern" value="' + escapeHtml(field.pattern || "") + '"></div>' +
            '<div class="col-md-1"><label class="form-label small">Group</label><input class="form-control form-control-sm summary-regex-group" type="number" min="0" value="' + escapeHtml(field.group === undefined ? 1 : field.group) + '"></div>' +
            '<div class="col-md-2"><label class="form-label small">Value type</label><select class="form-select form-select-sm summary-regex-type">' +
            [["string","Text"],["number","Number"],["integer","Integer"],["percentage","Percentage"],["boolean","Boolean"]].map(option =>
                '<option value="' + option[0] + '"' + (String(field.type || "string") === option[0] ? " selected" : "") + '>' + option[1] + '</option>').join("") +
            '</select></div>' +
            '<div class="col-md-1"><div class="form-check"><input class="form-check-input summary-regex-required" type="checkbox"' + (field.required === true ? " checked" : "") + '><label class="form-check-label small">Required</label></div></div>' +
            '<div class="col-md-1"><button type="button" class="btn btn-outline-danger btn-sm summary-regex-remove" aria-label="Remove regex field ' + (index + 1) + '">×</button></div>' +
            '</div></div>'
        ).join("") || '<div class="text-muted small">Add one or more fields to extract values from the response.</div>';
    }

    function configuredColumns(config) {
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const configured = Array.isArray(details.columns) ? details.columns : (Array.isArray(config.columns) ? config.columns : []);
        return new Map(configured.filter(item => item && (item.field || item.key))
            .map(item => [String(item.field || item.key), item]));
    }

    function renderGroupByOptions(config, fields) {
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const groupBy = details.groupBy && typeof details.groupBy === "object" ? details.groupBy : {};
        const selected = Array.isArray(groupBy.fields) ? groupBy.fields.map(String) : [];
        const eligibleFields = fields.filter(item => item.groupable !== false);
        const select = byId("summaryTableGroupByFields");
        select.innerHTML = eligibleFields.map(item =>
            '<option value="' + escapeHtml(item.field) + '"' + (selected.includes(item.field) ? " selected" : "") + '>' +
            escapeHtml(item.label) + ' (' + escapeHtml(item.field) + ')</option>'
        ).join("");
        byId("summaryTableGroupByEnabled").checked = groupBy.enabled === true;
        byId("summaryTableGroupByInitialState").value = ["EXPANDED", "COLLAPSED"].includes(String(groupBy.initialState || "").toUpperCase())
            ? String(groupBy.initialState).toUpperCase() : "EXPANDED";
        byId("summaryTableGroupByOrder").value = ["VALUE_ASC", "VALUE_DESC", "COUNT_ASC", "COUNT_DESC"].includes(String(groupBy.order || "").toUpperCase())
            ? String(groupBy.order).toUpperCase() : "VALUE_ASC";
        byId("summaryTableGroupByAggregates").checked = groupBy.showAggregates !== false;
        syncGroupByControlState();
    }

    function syncGroupByControlState() {
        const enabled = byId("summaryTableGroupByEnabled").checked;
        byId("summaryTableGroupByFields").disabled = state.fields.length === 0 || !enabled;
        byId("summaryTableGroupByInitialState").disabled = !enabled;
        byId("summaryTableGroupByOrder").disabled = !enabled;
        byId("summaryTableGroupByAggregates").disabled = !enabled;
    }

    function readConditionalFormatting() {
        const host = byId("summaryTableConditionalRules");
        const rules = host ? Array.from(host.querySelectorAll("[data-conditional-rule]")).map(row => ({
            field: row.querySelector(".summary-conditional-field").value,
            operator: row.querySelector(".summary-conditional-operator").value,
            value: row.querySelector(".summary-conditional-value").value,
            severity: row.querySelector(".summary-conditional-severity").value,
            scope: row.querySelector(".summary-conditional-scope").value
        })).filter(rule => rule.field && rule.operator) : [];
        return { enabled: byId("summaryTableConditionalFormattingEnabled").checked, rules };
    }

    function renderConditionalRules(config) {
        const host = byId("summaryTableConditionalRules");
        if (!host) return;
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const formatting = details.conditionalFormatting && typeof details.conditionalFormatting === "object" ? details.conditionalFormatting : (config.conditionalFormatting || {});
        const rules = Array.isArray(formatting.rules) ? formatting.rules : [];
        byId("summaryTableConditionalFormattingEnabled").checked = formatting.enabled !== false;
        const fields = Array.from(new Set([...(state.fields || []), ...rules.map(rule => String(rule.field || "")).filter(Boolean)]));
        const operators = [["equals","Equals"],["not_equals","Not equals"],["contains","Contains"],["gt","Greater than"],["gte","Greater than or equal"],["lt","Less than"],["lte","Less than or equal"],["empty","Is empty"],["missing","Is missing"]];
        const severities = [["critical","Red — Critical"],["warning","Yellow — Warning"],["healthy","Green — Healthy / Success"],["info","Blue — Info"]];
        const scopes = [["row","Entire row"],["cell","Matching cell"]];
        host.innerHTML = rules.length ? rules.map((rule,index) => {
            const operator = String(rule.operator || "equals"), hideValue = ["empty","missing"].includes(operator);
            return '<div class="row g-2 align-items-end mb-2" data-conditional-rule>' +
                '<div class="col-md-3"><label class="form-label small">Field</label><select class="form-select form-select-sm summary-conditional-field"><option value="">Select field</option>' +
                fields.map(field => '<option value="' + escapeHtml(field) + '"' + (String(rule.field || "") === field ? " selected" : "") + '>' + escapeHtml(humanize(field)) + ' (' + escapeHtml(field) + ')' + (!(state.fields || []).includes(field) ? ' — not in sample' : '') + '</option>').join("") +
                '</select></div><div class="col-md-2"><label class="form-label small">Condition</label><select class="form-select form-select-sm summary-conditional-operator">' +
                operators.map(option => '<option value="' + option[0] + '"' + (operator === option[0] ? " selected" : "") + '>' + option[1] + '</option>').join("") +
                '</select></div><div class="col-md-2"><label class="form-label small">Value</label><input class="form-control form-control-sm summary-conditional-value" value="' + escapeHtml(rule.value == null ? "" : rule.value) + '" ' + (hideValue ? 'hidden ' : '') + 'placeholder="Value / threshold"></div>' +
                '<div class="col-md-2"><label class="form-label small">Highlight</label><select class="form-select form-select-sm summary-conditional-severity">' +
                severities.map(option => '<option value="' + option[0] + '"' + (String(rule.severity || "critical") === option[0] ? " selected" : "") + '>' + option[1] + '</option>').join("") +
                '</select></div><div class="col-md-2"><label class="form-label small">Apply to</label><select class="form-select form-select-sm summary-conditional-scope">' +
                scopes.map(option => '<option value="' + option[0] + '"' + (String(rule.scope || "row") === option[0] ? " selected" : "") + '>' + option[1] + '</option>').join("") +
                '</select></div><div class="col-md-1"><button type="button" class="btn btn-outline-danger btn-sm summary-conditional-remove" aria-label="Remove rule ' + (index + 1) + '" title="Remove rule"><i class="bi bi-trash"></i></button></div></div>';
        }).join("") : '<div class="text-muted small">No rules configured. Add a rule to highlight matching rows or cells.</div>';
        host.querySelectorAll(".summary-conditional-operator").forEach(select => {
            select.closest("[data-conditional-rule]").querySelector(".summary-conditional-value").hidden = ["empty","missing"].includes(select.value);
        });
        host.querySelectorAll("select, input").forEach(control => control.disabled = !byId("summaryTableConditionalFormattingEnabled").checked);
        byId("summaryTableAddConditionalRule").disabled = !byId("summaryTableConditionalFormattingEnabled").checked;
    }

    function conditionalPreviewMatches(row, rule) {
        const field = String(rule.field || "");
        const value = Object.prototype.hasOwnProperty.call(row, field) ? row[field] : field.split(".").reduce((current,key) => current == null ? undefined : current[key], row);
        const operator = String(rule.operator || "equals").toLowerCase();
        if (operator === "missing") return value === undefined;
        if (operator === "empty") return value === undefined || value === null || String(value).trim() === "";
        if (value === undefined || value === null) return false;
        const actual = String(value).trim(), expected = String(rule.value == null ? "" : rule.value).trim();
        if (["gt","gte","lt","lte"].includes(operator)) {
            const number = Number(actual.replace(/,/g,"").replace(/%/g,"")), threshold = Number(expected.replace(/,/g,"").replace(/%/g,""));
            if (!Number.isFinite(number) || !Number.isFinite(threshold)) return false;
            if (operator === "gt") return number > threshold;
            if (operator === "gte") return number >= threshold;
            if (operator === "lt") return number < threshold;
            return number <= threshold;
        }
        if (operator === "contains") return actual.toLowerCase().includes(expected.toLowerCase());
        if (operator === "not_equals") return actual.toLowerCase() !== expected.toLowerCase();
        return actual.toLowerCase() === expected.toLowerCase();
    }

    function conditionalPreviewClass(row, config, scope, field) {
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const formatting = details.conditionalFormatting && typeof details.conditionalFormatting === "object" ? details.conditionalFormatting : (config.conditionalFormatting || {});
        if (formatting.enabled === false || !Array.isArray(formatting.rules)) return "";
        const rank = { info: 1, healthy: 1, warning: 2, critical: 3 };
        let selected = "";
        formatting.rules.forEach(rule => {
            if (String(rule.scope || "row") !== scope || (scope === "cell" && String(rule.field || "") !== String(field || ""))) return;
            if (!conditionalPreviewMatches(row, rule)) return;
            const severity = String(rule.severity || "critical").toLowerCase();
            if ((rank[severity] || 0) > (rank[selected] || 0)) selected = severity;
        });
        return selected ? "is-" + selected : "";
    }

    function discover() {
        const message = byId("summaryTableDiscoveryMessage");
        try {
            const sample = parseSample(byId("summaryTableSampleJson").value);
            const collections = findCollections(sample);
            if (Array.isArray(sample) && sample.some(row => row && typeof row === "object" && !Array.isArray(row))
                && !collections.some(item => item.path === "")) {
                collections.unshift({ path: "", rows: sample.filter(row => row && typeof row === "object" && !Array.isArray(row)), label: "(root array)" });
            }
            if (!Array.isArray(sample) && sample && typeof sample === "object"
                && !collections.some(item => item.path === "")) {
                collections.unshift({ path: "", rows: [sample], label: "(root object)" });
            }
            if (!collections.length) {
                throw new Error("No object fields or array of row objects were found in the response.");
            }

            state.sample = sample;
            state.collections = collections;
            const config = getConfig();
            const rowPath = String(config.rowPath || "");
            const select = byId("summaryTableRowPath");
            select.innerHTML = collections.map(item =>
                '<option value="' + escapeHtml(item.path) + '">' + escapeHtml(item.label) + ' (' + item.rows.length + ' rows)</option>'
            ).join("");
            select.disabled = false;
            const parserType = String((config.responseParser || {}).type || "AUTO").toUpperCase();
            const preferRoot = ["STAT", "STATUS", "TEXT", "ELASTICSEARCH"].includes(currentWidgetType())
                && !["DELIMITED", "KEY_VALUE", "REGEX", "PLAIN_TEXT"].includes(parserType);
            const preferredPath = rowPath && collections.some(item => item.path === rowPath)
                ? rowPath
                : (preferRoot && collections.some(item => item.path === "") ? "" :
                    (collections.find(item => item.path !== "") || collections[0]).path);
            select.value = preferredPath;

            const details = config.details && typeof config.details === "object" ? config.details : {};
            const search = details.search || config.search || {};
            const sorting = details.sorting || config.sorting || {};
            const pagination = details.pagination || config.pagination || {};
            byId("summaryTableSearchEnabled").checked = search.enabled !== false;
            byId("summaryTableSortDirection").value = String(sorting.direction || "asc").toLowerCase() === "desc" ? "desc" : "asc";
            byId("summaryTablePageSize").value = ["5", "10", "25", "50"].includes(String(pagination.pageSize)) ? String(pagination.pageSize) : "10";

            renderFields();
            renderTypeMappingOptions();
            refreshConfigFromEditor();
            message.textContent = "Found " + collections.length + " collection(s). Select a collection and map the discovered fields.";
            message.classList.remove("text-danger");
            message.classList.add("text-muted");
        } catch (error) {
            state.sample = null;
            state.collections = [];
            state.rows = [];
            state.fields = [];
            byId("summaryTableFieldEditor").innerHTML = '<div class="text-muted small">No fields available.</div>';
            byId("summaryTablePreview").innerHTML = '<div class="text-muted small p-3">Preview is unavailable until the sample JSON is valid.</div>';
            byId("summaryTablePreviewCount").textContent = "";
            message.textContent = error.message || "Unable to read sample JSON.";
            message.classList.remove("text-muted");
            message.classList.add("text-danger");
        }
    }

    function renderFields() {
        const collection = selectedCollection();
        if (!collection) return;
        state.rows = collection.rows;
        const discoveredFields = Array.from(new Set(collection.rows.reduce((all, row) => all.concat(Object.keys(row)), [])));
        const config = getConfig();
        const existing = configuredColumns(config);
        // Keep saved mappings even when a field is absent from the current sample/latest job response.
        // Otherwise a partial or changing response silently drops display and aggregation settings.
        const allFields = [
            ...Array.from(existing.keys()),
            ...discoveredFields.filter(field => !existing.has(field))
        ];
        state.fields = allFields;
        const anyExisting = existing.size > 0;
        const fields = allFields.map(field => {
            const saved = existing.get(field);
            return {
                field,
                label: saved && saved.label ? saved.label : humanize(field),
                visible: saved ? saved.visible !== false : true,
                format: saved && saved.format ? String(saved.format).toLowerCase() : "",
                displayStyle: saved && saved.displayStyle ? String(saved.displayStyle).toLowerCase() : "text",
                aggregation: saved && saved.aggregation ? String(saved.aggregation).toLowerCase() : "none",
                sortable: saved ? saved.sortable !== false : true,
                searchable: saved ? saved.searchable !== false : true,
                groupable: saved ? saved.groupable !== false : true
            };
        });
        const formats = [
            ["", "Default"], ["number", "Number"], ["percentage", "Percentage"],
            ["duration", "Duration (seconds)"], ["datetime", "Date / time"], ["status", "Status"]
        ];
        const displayStyles = [["text", "Text"], ["checkbox", "Checkbox"], ["toggle", "Toggle"]];
        const aggregations = [["none", "None"], ["count", "Count"], ["sum", "Sum"], ["avg", "Average"], ["max", "Maximum"], ["min", "Minimum"]];
        byId("summaryTableFieldEditor").innerHTML = fields.length ? 
            '<div class="table-responsive border rounded"><table class="table table-sm align-middle mb-0">' +
            '<thead><tr><th>Order</th><th>Show</th><th>Source field</th><th>Display label</th><th>Format</th><th>Boolean display</th><th>Group aggregate</th><th>Groupable</th></tr></thead><tbody>' +
            fields.map((item, index) => '<tr data-field="' + escapeHtml(item.field) + '" data-sortable="' + item.sortable + '" data-searchable="' + item.searchable + '">' +
                '<td><div class="d-flex gap-1"><button type="button" class="btn btn-outline-secondary btn-sm summary-config-move-up" aria-label="Move ' + escapeHtml(item.field) + ' up"' + (index === 0 ? ' disabled' : '') + '>↑</button>' +
                '<button type="button" class="btn btn-outline-secondary btn-sm summary-config-move-down" aria-label="Move ' + escapeHtml(item.field) + ' down"' + (index === fields.length - 1 ? ' disabled' : '') + '>↓</button></div></td>' +
                '<td><input class="form-check-input summary-config-visible" type="checkbox" aria-label="Show ' + escapeHtml(item.field) + '"' + (item.visible ? ' checked' : '') + '></td>' +
                '<td><code>' + escapeHtml(item.field) + '</code></td>' +
                '<td><input class="form-control form-control-sm summary-config-label" type="text" maxlength="120" value="' + escapeHtml(item.label) + '" aria-label="Label for ' + escapeHtml(item.field) + '"></td>' +
                '<td><select class="form-select form-select-sm summary-config-format" aria-label="Format for ' + escapeHtml(item.field) + '">' +
                formats.map(option => '<option value="' + option[0] + '"' + (item.format === option[0] ? ' selected' : '') + '>' + option[1] + '</option>').join("") +
                '</select></td>' +
                '<td><select class="form-select form-select-sm summary-config-display-style" aria-label="Boolean display for ' + escapeHtml(item.field) + '">' +
                displayStyles.map(option => '<option value="' + option[0] + '"' + (item.displayStyle === option[0] ? ' selected' : '') + '>' + option[1] + '</option>').join("") +
                '</select></td>' +
                '<td><select class="form-select form-select-sm summary-config-aggregation" aria-label="Aggregation for ' + escapeHtml(item.field) + '">' +
                aggregations.map(option => '<option value="' + option[0] + '"' + (item.aggregation === option[0] ? ' selected' : '') + '>' + option[1] + '</option>').join("") +
                '</select></td>' +
                '<td><input class="form-check-input summary-config-groupable" type="checkbox" aria-label="Allow grouping by ' + escapeHtml(item.field) + '"' + (item.groupable ? ' checked' : '') + '></td></tr>').join("") +
            '</tbody></table></div><div class="form-text">Source fields are preserved separately from display labels. Uncheck fields you do not want shown as columns.</div>'
            : '<div class="text-muted small">No fields were found in this collection.</div>';

        const sort = byId("summaryTableDefaultSort");
        const previousSort = (config.details && config.details.sorting && config.details.sorting.defaultField)
            || (config.sorting && config.sorting.defaultField) || "";
        sort.innerHTML = '<option value="">No default sort</option>' + fields.map(item =>
            '<option value="' + escapeHtml(item.field) + '">' + escapeHtml(item.label) + ' (' + escapeHtml(item.field) + ')</option>'
        ).join("");
        sort.disabled = fields.length === 0;
        sort.value = allFields.includes(previousSort) ? previousSort : "";
        renderGroupByOptions(config, fields);
        renderConditionalRules(config);
        renderSummaryCards(config);
        renderTypeMappingOptions();
        renderPreview();
    }

    function readFieldEditor() {
        return Array.from(byId("summaryTableFieldEditor").querySelectorAll("tr[data-field]")).map(row => ({
            field: row.getAttribute("data-field"),
            label: row.querySelector(".summary-config-label").value.trim() || humanize(row.getAttribute("data-field")),
            visible: row.querySelector(".summary-config-visible").checked,
            groupable: row.querySelector(".summary-config-groupable").checked,
            sortable: row.getAttribute("data-sortable") !== "false",
            searchable: row.getAttribute("data-searchable") !== "false",
            format: row.querySelector(".summary-config-format").value,
            displayStyle: row.querySelector(".summary-config-display-style").value,
            aggregation: row.querySelector(".summary-config-aggregation").value
        }));
    }


    function isNumericSummaryField(field) {
        return !!field && (state.rows || []).some(row => {
            const value = rowValueForConfigurator(row, field);
            return value !== null && value !== undefined && value !== "" && typeof value !== "boolean" && Number.isFinite(Number(value));
        });
    }

    function rowValueForConfigurator(row, field) {
        if (row && Object.prototype.hasOwnProperty.call(row, field)) return row[field];
        return String(field || "").split(".").reduce((value, key) => value == null ? undefined : value[key], row);
    }

    function renderSummaryCards(config) {
        const host = byId("summaryTableSummaryCards");
        if (!host) return;
        const summary = config.summary && typeof config.summary === "object" ? config.summary : {};
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const hasSavedCards = Array.isArray(details.summaryCards) || Array.isArray(summary.cards);
        const cards = Array.isArray(details.summaryCards) ? details.summaryCards.slice() : (Array.isArray(summary.cards) ? summary.cards.slice() : []);
        if (!hasSavedCards && Array.isArray(state.rows) && state.rows.length) {
            cards.push({ label: "Total Rows", field: "", operation: "COUNT_ROWS", format: "" });
            (state.fields || []).filter(field => state.rows.some(row => row[field] !== null && row[field] !== undefined && row[field] !== "" && Number.isFinite(Number(row[field]))))
                .forEach(field => cards.push({ label: humanize(field) + " Total", field, operation: "SUM", format: "number" }));
        }
        const fields = Array.from(new Set([...(state.fields || []), ...cards.map(card => String(card.field || "")).filter(Boolean)]));
        const operations = [["COUNT_ROWS","Count rows"],["COUNT_NON_EMPTY","Count non-empty values"],["COUNT_TRUE","Count true"],["COUNT_FALSE","Count false"],["SUM","Sum"],["AVG","Average"],["MAX","Maximum"],["MIN","Minimum"]];
        const numericOnly = new Set(["SUM", "AVG", "MAX", "MIN"]);
        const isNumericField = field => !!field && (state.rows || []).some(row => {
            const value = rowValueForConfigurator(row, field);
            return value !== null && value !== undefined && value !== "" && typeof value !== "boolean" && Number.isFinite(Number(value));
        });
        const options = selected => '<option value="">No field / all rows</option>' + fields.map(field =>
            '<option value="' + escapeHtml(field) + '"' + (String(selected || "") === field ? " selected" : "") + '>' + escapeHtml(humanize(field)) + ' (' + escapeHtml(field) + ')</option>').join("");
        host.innerHTML = cards.map((card, index) => '<div class="border rounded p-2 mb-2" data-summary-card><div class="row g-2 align-items-end">' +
            '<div class="col-md-3"><label class="form-label small">Card label</label><input class="form-control form-control-sm summary-card-label" maxlength="100" value="' + escapeHtml(card.label || "") + '"></div>' +
            '<div class="col-md-3"><label class="form-label small">Field</label><select class="form-select form-select-sm summary-card-field">' + options(card.field) + '</select></div>' +
            '<div class="col-md-3"><label class="form-label small">Calculation</label><select class="form-select form-select-sm summary-card-operation">' +
            operations.map(op => '<option value="' + op[0] + '"' + (String(card.operation || "COUNT_ROWS") === op[0] ? " selected" : "") +
                (numericOnly.has(op[0]) && !isNumericField(card.field) ? " disabled" : "") + '>' + op[1] + '</option>').join("") + '</select></div>' +
            '<div class="col-md-2"><label class="form-label small">Format</label><select class="form-select form-select-sm summary-card-format">' +
            [["","Default"],["number","Number"],["percentage","Percentage"],["duration","Duration"]].map(op => '<option value="' + op[0] + '"' + (String(card.format || "") === op[0] ? " selected" : "") + '>' + op[1] + '</option>').join("") + '</select></div>' +
            '<div class="col-md-1"><button type="button" class="btn btn-outline-danger btn-sm summary-card-remove" aria-label="Remove summary card ' + (index + 1) + '">×</button></div>' +
            '<div class="col-md-4"><label class="form-label small">Filter field (optional)</label><select class="form-select form-select-sm summary-card-filter-field"><option value="">No filter</option>' +
            fields.map(field => '<option value="' + escapeHtml(field) + '"' + (String(card.filterField || "") === field ? " selected" : "") + '>' + escapeHtml(humanize(field)) + ' (' + escapeHtml(field) + ')</option>').join("") + '</select></div>' +
            '<div class="col-md-3"><label class="form-label small">Filter condition</label><select class="form-select form-select-sm summary-card-filter-operator">' +
            [["equals","Equals"],["not_equals","Not equals"],["contains","Contains"],["gt","Greater than"],["gte","At least"],["lt","Less than"],["lte","At most"]].map(op => '<option value="' + op[0] + '"' + (String(card.filterOperator || "equals") === op[0] ? " selected" : "") + '>' + op[1] + '</option>').join("") + '</select></div>' +
            '<div class="col-md-5"><label class="form-label small">Filter value</label><input class="form-control form-control-sm summary-card-filter-value" value="' + escapeHtml(card.filterValue == null ? "" : card.filterValue) + '" placeholder="e.g. CRITICAL"></div>' +
            '</div></div>').join("") || '<div class="text-muted small">No summary cards configured. Add cards to choose which metrics appear.</div>';
    }

    function readSummaryCards() {
        const host = byId("summaryTableSummaryCards");
        if (!host) return [];
        return Array.from(host.querySelectorAll("[data-summary-card]")).map(row => ({
            label: row.querySelector(".summary-card-label").value.trim() || "Summary",
            field: row.querySelector(".summary-card-field").value,
            operation: row.querySelector(".summary-card-operation").value,
            format: row.querySelector(".summary-card-format").value,
            filterField: row.querySelector(".summary-card-filter-field").value,
            filterOperator: row.querySelector(".summary-card-filter-operator").value,
            filterValue: row.querySelector(".summary-card-filter-value").value
        }));
    }

    function buildConfig() {
        const config = getConfig();
        config.renderer = "SUMMARY_TABLE";
        config.responseParser = currentParserConfig();
        const rowPath = byId("summaryTableRowPath").value;
        if (rowPath) config.rowPath = rowPath;
        else delete config.rowPath;

        config.details = config.details && typeof config.details === "object" ? config.details : {};
        config.details.columns = readFieldEditor();
        config.details.search = Object.assign({}, config.details.search || {}, { enabled: byId("summaryTableSearchEnabled").checked });
        const defaultField = byId("summaryTableDefaultSort").value;
        config.details.sorting = Object.assign({}, config.details.sorting || {}, {
            enabled: true, defaultField, direction: byId("summaryTableSortDirection").value
        });
        config.details.pagination = Object.assign({}, config.details.pagination || {}, {
            enabled: true, pageSize: Number(byId("summaryTablePageSize").value) || 10
        });
        config.details.groupBy = Object.assign({}, config.details.groupBy || {}, {
            enabled: byId("summaryTableGroupByEnabled").checked,
            fields: Array.from(byId("summaryTableGroupByFields").selectedOptions).map(option => option.value),
            initialState: byId("summaryTableGroupByInitialState").value,
            order: byId("summaryTableGroupByOrder").value,
            showAggregates: byId("summaryTableGroupByAggregates").checked
        });
        config.details.conditionalFormatting = readConditionalFormatting();
        config.details.summaryCards = readSummaryCards();
        config.summary = Object.assign({}, config.summary || {});
        config.summary.cards = config.details.summaryCards;
        if (config.summary.enabled === undefined) config.summary.enabled = true;
        return config;
    }

    function renderPreview() {
        const preview = byId("summaryTablePreview");
        const count = byId("summaryTablePreviewCount");
        if (!preview || !count) return;
        const collection = selectedCollection();
        if (!collection || !Array.isArray(collection.rows)) {
            count.textContent = "";
            preview.innerHTML = '<div class="text-muted small p-3">Load a sample response or the latest job output to preview the table.</div>';
            return;
        }
        const rows = collection.rows.filter(row => row && typeof row === "object");
        const editor = byId("summaryTableFieldEditor");
        const columns = editor ? readFieldEditor().filter(column => column && column.visible) : [];
        count.textContent = rows.length + " sample row(s)";
        if (!columns.length) {
            preview.innerHTML = '<div class="text-muted small p-3">Select at least one visible column to preview the table.</div>';
            return;
        }
        const previewRows = rows.slice(0, 10), previewConfig = getConfig();
        preview.innerHTML = '<table class="table table-sm table-striped align-middle mb-0"><thead><tr>' +
            columns.map(column => '<th>' + escapeHtml(column.label) + '</th>').join("") +
            '</tr></thead><tbody>' + previewRows.map(row => {
                const rowClass = conditionalPreviewClass(row, previewConfig, "row");
                return '<tr class="' + rowClass + '">' + columns.map(column => {
                    let value = row[column.field];
                    if (value !== null && typeof value === "object") value = JSON.stringify(value);
                    const bool = value === true || value === false || (typeof value === "string" && /^(true|false)$/i.test(String(value).trim()));
                    const boolValue = value === true || (typeof value === "string" && String(value).trim().toLowerCase() === "true");
                    const cellClass = conditionalPreviewClass(row, previewConfig, "cell", column.field);
                    if (bool && column.displayStyle === "checkbox") return '<td class="' + cellClass + '"><input class="form-check-input summary-table-boolean" type="checkbox" disabled ' + (boolValue ? 'checked' : '') + '></td>';
                    if (bool && column.displayStyle === "toggle") return '<td class="' + cellClass + '"><span class="summary-table-toggle ' + (boolValue ? 'is-on' : 'is-off') + '"><span></span></span></td>';
                    if (value === null || value === undefined) value = "";
                    if (column.format === "percentage" && value !== "") value = String(value) + "%";
                    if (column.format === "number" && value !== "" && Number.isFinite(Number(value))) value = Number(value).toLocaleString();
                    return '<td class="' + cellClass + '">' + escapeHtml(value) + '</td>';
                }).join("") + '</tr>';
            }).join("") + '</tbody></table>' +
            (rows.length > previewRows.length ? '<div class="small text-muted p-2">Preview limited to the first 10 rows.</div>' : "");
    }

    function refreshConfigFromEditor() {
        const type = currentWidgetType();
        if (type === "SUMMARY_TABLE") {
            if (state.sample && selectedCollection()) {
                setConfig(buildConfig());
                return;
            }
            const summaryConfig = getConfig();
            summaryConfig.responseParser = currentParserConfig();
            summaryConfig.details = summaryConfig.details && typeof summaryConfig.details === "object" ? summaryConfig.details : {};
            summaryConfig.details.search = Object.assign({}, summaryConfig.details.search || {}, {
                enabled: byId("summaryTableSearchEnabled").checked
            });
            summaryConfig.details.sorting = Object.assign({}, summaryConfig.details.sorting || {}, {
                enabled: true,
                direction: byId("summaryTableSortDirection").value,
                defaultField: byId("summaryTableDefaultSort").value
            });
            summaryConfig.details.pagination = Object.assign({}, summaryConfig.details.pagination || {}, {
                enabled: true,
                pageSize: Number(byId("summaryTablePageSize").value) || 10
            });
            summaryConfig.details.groupBy = Object.assign({}, summaryConfig.details.groupBy || {}, {
                enabled: byId("summaryTableGroupByEnabled").checked,
                fields: Array.from(byId("summaryTableGroupByFields").selectedOptions).map(option => option.value),
                initialState: byId("summaryTableGroupByInitialState").value,
                order: byId("summaryTableGroupByOrder").value,
                showAggregates: byId("summaryTableGroupByAggregates").checked
            });
            summaryConfig.details.conditionalFormatting = readConditionalFormatting();
            summaryConfig.details.summaryCards = readSummaryCards();
            summaryConfig.summary = Object.assign({}, summaryConfig.summary || {});
            summaryConfig.summary.cards = summaryConfig.details.summaryCards;
            setConfig(summaryConfig);
            return;
        }
        const config = getConfig();
        config.responseParser = currentParserConfig();
        if (type === "TABLE" && state.sample && selectedCollection()) {
            const columns = readFieldEditor();
            config.renderer = "TABLE";
            config.rowPath = byId("summaryTableRowPath").value || "";
            config.columns = columns;
            config.visibleFields = columns.filter(item => item.visible).map(item => item.field);
            config.labels = Object.fromEntries(columns.map(item => [item.field, item.label]));
            config.formats = Object.fromEntries(columns.filter(item => item.format).map(item => [item.field, item.format]));
            config.sorting = Object.assign({}, config.sorting || {}, {
                enabled: true,
                defaultField: byId("summaryTableDefaultSort").value,
                direction: byId("summaryTableSortDirection").value
            });
            config.search = Object.assign({}, config.search || {}, { enabled: byId("summaryTableSearchEnabled").checked });
            config.pagination = Object.assign({}, config.pagination || {}, {
                enabled: true, pageSize: Number(byId("summaryTablePageSize").value) || 10
            });
        } else {
            syncTypeMappingToConfig(config, type);
        }
        setConfig(config);
    }

    function selectedValues(id) {
        const select = byId(id);
        return select ? Array.from(select.selectedOptions).map(option => option.value) : [];
    }

    function setFieldOptions(id, fields, selected, noneLabel) {
        const select = byId(id);
        if (!select) return;
        const multiple = select.multiple;
        const selectedValues = (Array.isArray(selected) ? selected : [selected]).filter(Boolean).map(String);
        const selectedValuesSet = new Set(selectedValues);
        const availableFields = Array.from(new Set([...(fields || []).map(String), ...selectedValues]));
        const prefix = multiple ? "" : '<option value="">' + escapeHtml(noneLabel || "Select field") + '</option>';
        select.innerHTML = prefix + availableFields.map(field => {
            const missing = !(fields || []).map(String).includes(field);
            const label = humanize(field) + ' (' + field + ')' + (missing ? ' — not in sample' : '');
            return '<option value="' + escapeHtml(field) + '"' + (selectedValuesSet.has(field) ? " selected" : "") + '>' +
                escapeHtml(label) + '</option>';
        }).join("");
        if (!multiple && !selectedValues.length) select.value = "";
    }

    function renderTypeMappingOptions() {
        const type = currentWidgetType();
        const config = getConfig();
        const mapping = byId("widgetTypeMappingOptions");
        if (!mapping) return;
        const hasDedicatedMapping = ["CHART", "STAT", "STATUS", "TEXT", "SYSTEM_METRICS", "ELASTICSEARCH"].includes(type);
        mapping.hidden = !hasDedicatedMapping;
        byId("widgetTypeMappingTitle").textContent = ({
            CHART: "Chart field mapping",
            STAT: "Stat value mapping",
            STATUS: "Status field mapping",
            TEXT: "Text content mapping",
            SYSTEM_METRICS: "System metrics mapping",
            ELASTICSEARCH: "Elasticsearch mapping"
        })[type] || "Widget-specific mapping";
        mapping.querySelectorAll("[data-mapping-for]").forEach(element => {
            element.hidden = !element.getAttribute("data-mapping-for").split(/\s+/).includes(type);
        });
        const fields = state.fields || [];
        setFieldOptions("widgetCategoryField", fields, config.categoryField, "Select category field");
        setFieldOptions("widgetValueField", fields, config.valueField, "Select value field");
        setFieldOptions("widgetSeriesField", fields, config.seriesField, "No series field");
        setFieldOptions("widgetSingleValueField", fields, config.valueField, "Select value field");
        setFieldOptions("widgetStatusField", fields, config.statusField, "No status field");
        setFieldOptions("widgetTextFields", fields, config.textFields, "");
        setFieldOptions("widgetMetricFields", fields, config.fields, "");
        byId("widgetTextTemplate").value = config.textTemplate || "";
    }

    function syncTypeMappingToConfig(config, type) {
        const hasSample = state.sample && selectedCollection();
        config.responseParser = currentParserConfig();
        if (hasSample && ["TABLE", "SUMMARY_TABLE", "CHART", "SYSTEM_METRICS"].includes(type)) {
            config.rowPath = byId("summaryTableRowPath").value || "";
        }
        switch (type) {
            case "CHART":
                config.renderer = "CHART";
                config.categoryField = byId("widgetCategoryField").value;
                config.valueField = byId("widgetValueField").value;
                config.seriesField = byId("widgetSeriesField").value;
                break;
            case "STAT":
                config.renderer = "STAT";
                config.valueField = byId("widgetSingleValueField").value;
                config.visibleFields = config.valueField ? [config.valueField] : [];
                break;
            case "STATUS":
                config.renderer = "STATUS";
                config.valueField = byId("widgetSingleValueField").value;
                config.statusField = byId("widgetStatusField").value;
                config.visibleFields = [config.valueField, config.statusField].filter(Boolean);
                break;
            case "TEXT":
                config.renderer = "TEXT";
                config.textFields = selectedValues("widgetTextFields");
                config.textTemplate = byId("widgetTextTemplate").value;
                break;
            case "SYSTEM_METRICS":
                config.renderer = "SYSTEM_METRICS";
                config.fields = selectedValues("widgetMetricFields");
                config.labels = Object.assign({}, config.labels || {});
                break;
            case "ELASTICSEARCH":
                config.renderer = "ELASTICSEARCH";
                break;
            case "TABLE":
                config.renderer = "TABLE";
                break;
            default:
                break;
        }
    }

    function syncParserSettings() {
        const config = getConfig();
        config.responseParser = currentParserConfig();
        setConfig(config);
        if (byId("summaryTableSampleJson").value.trim()) discover();
    }

    function syncParserOptionVisibility() {
        const typeValue = byId("summaryTableResponseFormat").value;
        byId("summaryTableDelimitedOptions").hidden = typeValue !== "DELIMITED";
        byId("summaryTableKeyValueOptions").hidden = typeValue !== "KEY_VALUE";
        byId("summaryTableRegexOptions").hidden = typeValue !== "REGEX";
    }

    function defaultConfigForType(type) {
        switch (type) {
            case "SUMMARY_TABLE":
                return {
                    renderer: "SUMMARY_TABLE",
                    responseParser: { type: "AUTO" },
                    details: {
                        columns: [],
                        search: { enabled: true },
                        sorting: { enabled: true, defaultField: "", direction: "asc" },
                        pagination: { enabled: true, pageSize: 10 },
                        groupBy: { enabled: false, fields: [], initialState: "EXPANDED", order: "VALUE_ASC", showAggregates: true },
                        conditionalFormatting: { enabled: true, rules: [] },
                        summaryCards: []
                    },
                    summary: { enabled: true, cards: [] }
                };
            case "TABLE":
                return { renderer: "TABLE", responseParser: { type: "AUTO" }, rowPath: "", columns: [], visibleFields: [], labels: {}, sorting: { enabled: true }, search: { enabled: true }, pagination: { enabled: true, pageSize: 10 } };
            case "CHART":
                return { renderer: "CHART", responseParser: { type: "AUTO" }, rowPath: "", categoryField: "", valueField: "", seriesField: "" };
            case "STAT":
                return { renderer: "STAT", responseParser: { type: "AUTO" }, valueField: "", visibleFields: [] };
            case "STATUS":
                return { renderer: "STATUS", responseParser: { type: "AUTO" }, valueField: "", statusField: "", visibleFields: [] };
            case "TEXT":
                return { renderer: "TEXT", responseParser: { type: "AUTO" }, textFields: [], textTemplate: "" };
            case "SYSTEM_METRICS":
                return { renderer: "SYSTEM_METRICS", responseParser: { type: "AUTO" }, rowPath: "", fields: [], labels: {} };
            case "ELASTICSEARCH":
                return { renderer: "ELASTICSEARCH", responseParser: { type: "AUTO" } };
            default:
                return { responseParser: { type: "AUTO" } };
        }
    }

    function clearSampleState(message) {
        state.sample = null;
        state.collections = [];
        state.rows = [];
        state.fields = [];
        byId("summaryTableSampleJson").value = "";
        byId("summaryTableRowPath").innerHTML = '<option value="">Load sample data to discover rows</option>';
        byId("summaryTableRowPath").disabled = true;
        byId("summaryTableDefaultSort").innerHTML = '<option value="">No default sort</option>';
        byId("summaryTableDefaultSort").disabled = true;
        byId("summaryTableFieldEditor").innerHTML = '<div class="text-muted small">Load sample data and discover fields to configure columns.</div>';
        byId("summaryTablePreview").innerHTML = '<div class="text-muted small p-3">Discover fields to preview the selected columns.</div>';
        byId("summaryTablePreviewCount").textContent = "";
        byId("summaryTableDiscoveryMessage").textContent = message || "Paste a sample or load the latest job output.";
    }

    function showForSelectedType() {
        const type = currentWidgetType();
        const isSummary = type === "SUMMARY_TABLE";
        const isTable = type === "TABLE" || isSummary;
        const rowPathTypes = ["TABLE", "SUMMARY_TABLE", "CHART", "SYSTEM_METRICS"];
        byId("summaryTableConfigurator").hidden = !type;
        byId("dashboardWidgetAdvancedConfig").open = false;
        byId("summaryTableGroupBySection").hidden = !isSummary;
        byId("summaryTableConditionalFormattingSection").hidden = !isSummary;
        byId("summaryTableSummaryCardsSection").hidden = !isSummary;
        byId("summaryTableTableOptions").hidden = !isTable;
        byId("summaryTableFieldEditorGroup").hidden = !isTable;
        byId("summaryTableRowPathGroup").hidden = !rowPathTypes.includes(type);
        byId("widgetMappingHeading").textContent = type === "SUMMARY_TABLE" ? "Summary Table data and columns" : "Widget data and mapping";
        byId("widgetFieldMappingHeading").textContent = isSummary ? "Column mapping" : "Field mapping";
        const config = getConfig();
        const parser = config.responseParser && typeof config.responseParser === "object" ? config.responseParser : {};
        byId("summaryTableResponseFormat").value = ["AUTO", "JSON", "PLAIN_TEXT", "DELIMITED", "KEY_VALUE", "REGEX"].includes(String(parser.type || "AUTO").toUpperCase())
            ? String(parser.type || "AUTO").toUpperCase() : "AUTO";
        const delimiterValue = parser.delimiter || "|";
        byId("summaryTableDelimiter").value = ["|", ",", ";"].includes(delimiterValue)
            ? delimiterValue : (delimiterValue === "\t" ? "\\t" : "custom");
        byId("summaryTableCustomDelimiter").value = byId("summaryTableDelimiter").value === "custom" ? delimiterValue : "";
        byId("summaryTableCustomDelimiter").hidden = byId("summaryTableDelimiter").value !== "custom";
        byId("summaryTableCommentPrefix").value = parser.commentPrefix || "";
        byId("summaryTableHasHeader").checked = parser.headerRow !== false;
        const separatorValue = parser.separator || ":";
        byId("summaryTableKeyValueSeparator").value = [":", "="].includes(separatorValue)
            ? separatorValue : (separatorValue === "\t" ? "\\t" : "custom");
        byId("summaryTableCustomSeparator").value = byId("summaryTableKeyValueSeparator").value === "custom" ? separatorValue : "";
        byId("summaryTableCustomSeparator").hidden = byId("summaryTableKeyValueSeparator").value !== "custom";
        byId("summaryTableIgnoredPrefixes").value = Array.isArray(parser.ignorePrefixes) ? parser.ignorePrefixes.join("\n") : "";
        renderRegexFields();
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const groupBy = details.groupBy && typeof details.groupBy === "object" ? details.groupBy : {};
        byId("summaryTableGroupByEnabled").checked = groupBy.enabled === true;
        byId("summaryTableGroupByInitialState").value = String(groupBy.initialState || "EXPANDED").toUpperCase() === "COLLAPSED" ? "COLLAPSED" : "EXPANDED";
        byId("summaryTableGroupByOrder").value = ["VALUE_ASC", "VALUE_DESC", "COUNT_ASC", "COUNT_DESC"].includes(String(groupBy.order || "").toUpperCase())
            ? String(groupBy.order).toUpperCase() : "VALUE_ASC";
        byId("summaryTableGroupByAggregates").checked = groupBy.showAggregates !== false;
        const savedColumns = Array.from(configuredColumns(config).entries()).map(([field, column]) => ({
            field,
            label: column.label || humanize(field),
            groupable: column.groupable !== false
        }));
        (Array.isArray(groupBy.fields) ? groupBy.fields : []).forEach(field => {
            if (!savedColumns.some(item => item.field === String(field))) {
                savedColumns.push({ field: String(field), label: humanize(field), groupable: true });
            }
        });
        renderGroupByOptions(config, savedColumns);
        renderConditionalRules(config);
        syncGroupByControlState();
        syncParserOptionVisibility();
        renderTypeMappingOptions();
        if (state.sample) renderFields();
    }

    document.addEventListener("DOMContentLoaded", function () {
        const type = byId("dashboardWidgetType");
        const sample = byId("summaryTableSampleJson");
        const discoverButton = byId("summaryTableDiscoverFields");
        if (!type || !sample || !discoverButton) return;

        discoverButton.addEventListener("click", discover);
        byId("summaryTableLoadLatest").addEventListener("click", async function () {
            const jobId = byId("dashboardWidgetJobSourceId").value;
            const message = byId("summaryTableDiscoveryMessage");
            if (!jobId) {
                message.textContent = "Select a Monitoring Job before loading its latest output.";
                message.classList.remove("text-muted");
                message.classList.add("text-danger");
                return;
            }
            message.textContent = "Loading the latest saved result…";
            message.classList.remove("text-danger");
            message.classList.add("text-muted");
            try {
                const response = await fetch("/administration/dashboard/widgets/source-sample?jobId=" + encodeURIComponent(jobId), {
                    method: "GET",
                    headers: { "Accept": "application/json" },
                    credentials: "same-origin"
                });
                if (!response.ok) throw new Error("Unable to load the latest result (HTTP " + response.status + ").");
                const result = await response.json();
                if (!result || typeof result !== "object") {
                    message.textContent = "The latest job response was empty or invalid.";
                    return;
                }
                const candidates = [result.rawOutput, result.resultData, result.output, result.responseBody,
                    result.payload, result.data];
                let output = "";
                for (const candidate of candidates) {
                    if (typeof candidate === "string" && candidate.trim()) { output = candidate; break; }
                    if (candidate !== null && typeof candidate === "object") {
                        try { output = JSON.stringify(candidate); } catch (ignore) { output = ""; }
                        if (output) break;
                    }
                }
                if (!output.trim()) {
                    message.textContent = result.message || "No saved output is available. Run the job or paste a sample response.";
                    return;
                }
                sample.value = output;
                discover();
                if (result.createdAt) {
                    message.textContent = message.textContent + " Loaded saved output from " + result.createdAt + ".";
                }
            } catch (error) {
                message.textContent = error.message || "Unable to load the latest result.";
                message.classList.remove("text-muted");
                message.classList.add("text-danger");
            }
        });
        const responseFormat = byId("summaryTableResponseFormat");
        renderRegexFields();
        byId("summaryTableAddRegexField").addEventListener("click", function () {
            const fields = readRegexFields();
            fields.push({ key: "", pattern: "", group: 1, type: "string", required: false });
            const config = getConfig();
            config.responseParser = Object.assign({}, config.responseParser || {}, { type: "REGEX", fields });
            setConfig(config);
            renderRegexFields();
            responseFormat.value = "REGEX";
            syncParserOptionVisibility();
        });
        byId("summaryTableRegexFields").addEventListener("click", function (event) {
            if (!event.target.closest(".summary-regex-remove")) return;
            event.target.closest("[data-regex-field]").remove();
            syncParserSettings();
        });
        byId("summaryTableRegexFields").addEventListener("change", function () {
            syncParserSettings();
        });
        byId("summaryTableRegexFields").addEventListener("input", function () {
            syncParserSettings();
        });
        responseFormat.addEventListener("change", function () {
            syncParserOptionVisibility();
            syncParserSettings();
        });
        ["summaryTableDelimiter", "summaryTableHasHeader", "summaryTableKeyValueSeparator", "summaryTableIgnoredPrefixes",
            "summaryTableCustomDelimiter", "summaryTableCustomSeparator", "summaryTableCommentPrefix"].forEach(id => {
            byId(id).addEventListener("change", function () {
                byId("summaryTableCustomDelimiter").hidden = byId("summaryTableDelimiter").value !== "custom";
                byId("summaryTableCustomSeparator").hidden = byId("summaryTableKeyValueSeparator").value !== "custom";
                syncParserSettings();
            });
        });
        byId("summaryTableRowPath").addEventListener("change", function () {
            renderFields();
            renderTypeMappingOptions();
            refreshConfigFromEditor();
        });
        ["widgetCategoryField", "widgetValueField", "widgetSeriesField", "widgetSingleValueField",
            "widgetStatusField", "widgetTextFields", "widgetMetricFields"].forEach(id => {
            byId(id).addEventListener("change", refreshConfigFromEditor);
        });
        byId("widgetTextTemplate").addEventListener("input", refreshConfigFromEditor);
        byId("summaryTableFieldEditor").addEventListener("click", function (event) {
            const button = event.target.closest(".summary-config-move-up, .summary-config-move-down");
            if (!button) return;
            const row = button.closest("tr[data-field]");
            if (!row) return;
            if (button.classList.contains("summary-config-move-up") && row.previousElementSibling) {
                row.parentElement.insertBefore(row, row.previousElementSibling);
            } else if (button.classList.contains("summary-config-move-down") && row.nextElementSibling) {
                row.parentElement.insertBefore(row.nextElementSibling, row);
            }
            Array.from(row.parentElement.querySelectorAll("tr[data-field]")).forEach((item, index, rows) => {
                item.querySelector(".summary-config-move-up").disabled = index === 0;
                item.querySelector(".summary-config-move-down").disabled = index === rows.length - 1;
            });
            refreshConfigFromEditor();
        });
        byId("summaryTableFieldEditor").addEventListener("change", function (event) {
            refreshConfigFromEditor();
            if (event.target.classList.contains("summary-config-groupable")) {
                renderFields();
                refreshConfigFromEditor();
            }
        });
        byId("summaryTableFieldEditor").addEventListener("input", function (event) {
            if (event.target.classList.contains("summary-config-label")) refreshConfigFromEditor();
        });
        ["summaryTableDefaultSort", "summaryTableSortDirection", "summaryTablePageSize", "summaryTableSearchEnabled"].forEach(id => {
            byId(id).addEventListener("change", refreshConfigFromEditor);
        });
        let lastWidgetType = type.value;
        type.addEventListener("change", function () {
            const nextType = type.value;
            if (nextType !== lastWidgetType) {
                clearSampleState("Widget type changed. Previous mapping settings were cleared; configure the new widget type.");
                setConfig(defaultConfigForType(nextType));
                lastWidgetType = nextType;
            }
            showForSelectedType();
            const sourceType = byId("dashboardWidgetDataSourceType");
            const jobSelect = byId("dashboardWidgetJobSourceId");
            if (nextType === "SUMMARY_TABLE" && sourceType && sourceType.value === "MONITORING_JOB"
                && jobSelect && String(jobSelect.value || "").trim()) {
                byId("summaryTableLoadLatest").click();
            }
        });
        const jobSelectForLatest = byId("dashboardWidgetJobSourceId");
        const sourceTypeForLatest = byId("dashboardWidgetDataSourceType");
        if (jobSelectForLatest) jobSelectForLatest.addEventListener("change", function () {
            if (currentWidgetType() === "SUMMARY_TABLE" && sourceTypeForLatest
                && sourceTypeForLatest.value === "MONITORING_JOB" && String(jobSelectForLatest.value || "").trim()) {
                byId("summaryTableLoadLatest").click();
            }
        });
        if (sourceTypeForLatest) sourceTypeForLatest.addEventListener("change", function () {
            if (currentWidgetType() === "SUMMARY_TABLE" && sourceTypeForLatest.value === "MONITORING_JOB"
                && jobSelectForLatest && String(jobSelectForLatest.value || "").trim()) {
                byId("summaryTableLoadLatest").click();
            }
        });
        byId("dashboardWidgetFieldConfigJson").addEventListener("change", function () {
            const config = getConfig();
            showForSelectedType();
            if (state.sample && byId("summaryTableSampleJson").value.trim()) {
                discover();
            } else {
                const details = config.details && typeof config.details === "object" ? config.details : {};
                const search = details.search || config.search || {};
                const sorting = details.sorting || config.sorting || {};
                const pagination = details.pagination || config.pagination || {};
                byId("summaryTableSearchEnabled").checked = search.enabled !== false;
                byId("summaryTableSortDirection").value = String(sorting.direction || "asc").toLowerCase() === "desc" ? "desc" : "asc";
                byId("summaryTablePageSize").value = ["5", "10", "25", "50"].includes(String(pagination.pageSize)) ? String(pagination.pageSize) : "10";
            }
        });
        byId("summaryTableAddConditionalRule").addEventListener("click", function () {
            if (!byId("summaryTableConditionalFormattingEnabled").checked) return;
            const config = getConfig(), details = config.details && typeof config.details === "object" ? config.details : {};
            const formatting = details.conditionalFormatting && typeof details.conditionalFormatting === "object" ? details.conditionalFormatting : { enabled: true, rules: [] };
            const rules = Array.isArray(formatting.rules) ? formatting.rules.slice() : [];
            rules.push({ field: (state.fields || [])[0] || "", operator: "equals", value: "", severity: "critical", scope: "row" });
            details.conditionalFormatting = Object.assign({}, formatting, { enabled: true, rules });
            config.details = details;
            setConfig(config);
            renderConditionalRules(config);
        });
        byId("summaryTableConditionalRules").addEventListener("click", function (event) {
            const button = event.target.closest(".summary-conditional-remove");
            if (!button) return;
            button.closest("[data-conditional-rule]").remove();
            refreshConfigFromEditor();
        });
        byId("summaryTableConditionalRules").addEventListener("change", function (event) {
            if (event.target.classList.contains("summary-conditional-operator")) {
                event.target.closest("[data-conditional-rule]").querySelector(".summary-conditional-value").hidden = ["empty","missing"].includes(event.target.value);
            }
            refreshConfigFromEditor();
        });
        byId("summaryTableConditionalRules").addEventListener("input", function (event) {
            if (event.target.classList.contains("summary-conditional-value")) refreshConfigFromEditor();
        });
        byId("summaryTableAddSummaryCard").addEventListener("click", function () {
            const config = getConfig();
            config.summary = Object.assign({}, config.summary || {});
            const cards = readSummaryCards();
            cards.push({ label: "Total Rows", field: "", operation: "COUNT_ROWS", format: "" });
            config.summary.cards = cards;
            config.details = config.details && typeof config.details === "object" ? config.details : {};
            config.details.summaryCards = cards;
            setConfig(config);
            renderSummaryCards(config);
            refreshConfigFromEditor();
        });
        byId("summaryTableSummaryCards").addEventListener("click", function (event) {
            const button = event.target.closest(".summary-card-remove");
            if (!button) return;
            button.closest("[data-summary-card]").remove();
            refreshConfigFromEditor();
        });
        byId("summaryTableSummaryCards").addEventListener("change", function (event) {
            if (event.target.matches(".summary-card-field")) {
                const row = event.target.closest("[data-summary-card]");
                const operation = row.querySelector(".summary-card-operation");
                const field = event.target.value;
                if (!isNumericSummaryField(field) && ["SUM", "AVG", "MAX", "MIN"].includes(operation.value)) {
                    operation.value = field ? "COUNT_NON_EMPTY" : "COUNT_ROWS";
                }
                const config = getConfig();
                const currentCards = readSummaryCards();
                config.summary = Object.assign({}, config.summary || {});
                config.summary.cards = currentCards;
                config.details = config.details && typeof config.details === "object" ? config.details : {};
                config.details.summaryCards = currentCards;
                renderSummaryCards(config);
            }
            refreshConfigFromEditor();
        });
        byId("summaryTableSummaryCards").addEventListener("input", function (event) {
            if (event.target.matches("input")) refreshConfigFromEditor();
        });
        byId("summaryTableConditionalFormattingEnabled").addEventListener("change", function () {
            const config = getConfig();
            config.details = config.details && typeof config.details === "object" ? config.details : {};
            config.details.conditionalFormatting = readConditionalFormatting();
            setConfig(config);
            renderConditionalRules(config);
            refreshConfigFromEditor();
        });
        byId("summaryTableGroupByEnabled").addEventListener("change", function () {
            syncGroupByControlState();
            refreshConfigFromEditor();
        });
        ["summaryTableGroupByFields", "summaryTableGroupByInitialState", "summaryTableGroupByOrder", "summaryTableGroupByAggregates"].forEach(id => {
            byId(id).addEventListener("change", refreshConfigFromEditor);
        });
        const modal = byId("dashboardWidgetModal");
        if (modal) {
            modal.addEventListener("shown.bs.modal", function () {
                lastWidgetType = type.value;
                showForSelectedType();
                const sourceType = byId("dashboardWidgetDataSourceType");
                const jobSelect = byId("dashboardWidgetJobSourceId");
                const loadLatestButton = byId("summaryTableLoadLatest");
                if (type.value && sourceType && sourceType.value === "MONITORING_JOB"
                    && jobSelect && String(jobSelect.value || "").trim() && loadLatestButton) {
                    loadLatestButton.click();
                }
            });
            modal.addEventListener("hidden.bs.modal", function () {
                sample.value = "";
                state.sample = null;
                state.collections = [];
                state.rows = [];
                state.fields = [];
                byId("summaryTableDiscoveryMessage").textContent = "Paste valid JSON or text to begin.";
                byId("summaryTableFieldEditor").innerHTML = '<div class="text-muted small">Discovered fields will appear here.</div>';
                byId("summaryTablePreview").innerHTML = '<div class="text-muted small p-3">Discover fields to preview the table.</div>';
                byId("summaryTablePreviewCount").textContent = "";
                lastWidgetType = type.value;
            });
        }
        showForSelectedType();
    });
})();
