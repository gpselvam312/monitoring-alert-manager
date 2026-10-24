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
            const lines = text.split(/\r?\n/);
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
        byId("dashboardWidgetFieldConfigJson").value = JSON.stringify(config);
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
        let configured = Array.isArray(details.columns) && details.columns.length ? details.columns
            : (Array.isArray(config.columns) ? config.columns : []);
        if (config.view && Array.isArray(config.view.metrics)) {
            const existingFields = new Set(configured.map(item => String(item.field || item.key || "")));
            config.view.metrics.filter(item => item && item.name).forEach(item => {
                const field = "metrics::" + String(item.name);
                if (!existingFields.has(field)) configured.push({
                    field, label: item.label || String(item.name),
                    visible: item.enabled !== false, format: item.format === "auto" ? "" : (item.format || ""),
                    sortable: true, searchable: true, groupable: false, aggregation: "none"
                });
            });
        }
        return new Map(configured.filter(item => item && (item.field || item.key))
            .map(item => [String(item.field || item.key), item]));
    }

    function fieldBooleanType(field) {
        if (!field || !Array.isArray(state.rows) || !state.rows.length) return null;
        const values = state.rows.map(row => {
            if (row && Object.prototype.hasOwnProperty.call(row, field)) return row[field];
            return String(field).split(".").reduce((value, key) => value == null ? undefined : value[key], row);
        }).filter(value => value !== null && value !== undefined && value !== "");
        if (!values.length) return null;
        return values.every(value => typeof value === "boolean" ||
            (typeof value === "string" && /^(true|false)$/i.test(value.trim())));
    }

    function syncSummaryCardBooleanFields() {
        const host = byId("summaryTableCardsEditor");
        if (!host) return;
        host.querySelectorAll("[data-summary-card-row]").forEach(row => {
            const field = row.querySelector(".summary-card-field").value;
            const groupBy = row.querySelector(".summary-card-group-by").value;
            const fieldType = fieldBooleanType(field), groupType = fieldBooleanType(groupBy);
            const mapping = row.querySelector(".summary-card-boolean-mapping");
            const hasLegacyMapping = row.querySelector(".summary-card-true-label").value !== "" ||
                row.querySelector(".summary-card-false-label").value !== "";
            if (mapping) mapping.hidden = !(fieldType === true || groupType === true ||
                (fieldType === null && groupType === null && hasLegacyMapping));
        });
    }

    function renderSummaryCardsEditor(config) {
        const host = byId("summaryTableCardsEditor"); if (!host) return;
        const summary = config.summary && typeof config.summary === "object" ? config.summary : {};
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const sourceCards = (Array.isArray(summary.cards) && (summary.cards.length || !Array.isArray(details.summaryCards)) ? summary.cards : (Array.isArray(details.summaryCards) ? details.summaryCards : [])).map(card => Object.assign({}, card));
        const legacyTotal = sourceCards.find(card => String(card.operation || "").toUpperCase() === "COUNT_ROWS" && !String(card.field || ""));
        const totalRows = summary.totalRowsCard && typeof summary.totalRowsCard === "object" ? summary.totalRowsCard : {};
        const cards = sourceCards.filter(card => !(String(card.operation || "").toUpperCase() === "COUNT_ROWS" && !String(card.field || "")));
        const fields = Array.from(new Set([...(state.fields || []), ...cards.map(card => String(card.field || "")).filter(Boolean)]));
        const options = selected => '<option value="">All rows</option>' + fields.map(field => '<option value="' + escapeHtml(field) + '"' + (String(selected || "") === field ? ' selected' : '') + '>' + escapeHtml(humanize(field)) + ' (' + escapeHtml(field) + ')</option>').join("");
        const ops = [["COUNT_ROWS","Count rows"],["VALUE","Field value"],["COUNT_NON_EMPTY","Count non-empty"],["COUNT_TRUE","Count true"],["COUNT_FALSE","Count false"],["SUM","Sum"],["AVG","Average"],["MAX","Maximum"],["MIN","Minimum"]];
        const iconPresets = [["activity","Activity"],["check-circle","Success"],["exclamation-triangle","Warning"],["x-circle","Critical"],["info-circle","Information"],["speedometer2","Gauge"],["server","Server"],["hdd-stack","Storage"],["cpu","CPU"],["memory","Memory"],["people","People"],["person-check","Person check"],["clock-history","History"],["lightning-charge","Performance"],["database","Database"],["cloud-check","Cloud"],["shield-check","Security"],["bell","Alerts"],["graph-up","Trend"],["list-check","List"],["box-seam","Box"],["diagram-3","Topology"],["arrow-repeat","Refresh"]];
        host.innerHTML = cards.map((card,i) => {
            const map = card.valueMapping && typeof card.valueMapping === "object" ? card.valueMapping : {}, icon = card.icon && typeof card.icon === "object" ? card.icon : {};
            return '<div class="border rounded p-3 mb-3" data-summary-card-row><div class="d-flex justify-content-between mb-2"><strong>Card ' + (i+1) + '</strong><button type="button" class="btn btn-sm btn-outline-danger summary-card-remove">Remove</button></div><div class="row g-2">' +
            '<div class="col-md-4"><label class="form-label small">Field</label><select class="form-select form-select-sm summary-card-field">' + options(card.field) + '</select></div>' +
            '<div class="col-md-4"><label class="form-label small">Label</label><input class="form-control form-control-sm summary-card-label" maxlength="120" value="' + escapeHtml(card.label || '') + '"></div>' +
            '<div class="col-md-4"><label class="form-label small">Calculation</label><select class="form-select form-select-sm summary-card-operation">' + ops.map(o => '<option value="' + o[0] + '"' + (String(card.operation || "COUNT_ROWS").toUpperCase()===o[0]?' selected':'') + '>' + o[1] + '</option>').join("") + '</select></div>' +
            '<div class="col-md-4"><label class="form-label small">Group by (optional)</label><select class="form-select form-select-sm summary-card-group-by">' + options(card.groupBy) + '</select></div>' +
            '<div class="col-md-4"><label class="form-label small">On click</label><select class="form-select form-select-sm summary-card-click"><option value="FILTER_SELF"' + (card.filterable === false?'':' selected') + '>Filter this widget</option><option value="NONE"' + (card.filterable === false?' selected':'') + '>No action</option></select></div>' +
            '<div class="col-md-4"><label class="form-label small">Format</label><select class="form-select form-select-sm summary-card-format">' + [["","Default"],["number","Number"],["percentage","Percentage"],["duration","Duration"],["datetime","Date / time"]].map(o=>'<option value="'+o[0]+'"'+(String(card.format||"")===o[0]?' selected':'')+'>'+o[1]+'</option>').join("") + '</select></div>' +
            '<div class="col-md-3"><div class="form-check mt-2"><input class="form-check-input summary-card-icon-enabled" type="checkbox"' + (icon.enabled===true?' checked':'') + '><label class="form-check-label small">Show icon</label></div></div>' +
            '<div class="col-md-4"><label class="form-label small">Bootstrap icon</label><select class="form-select form-select-sm summary-card-icon-preset">' + iconPresets.map(o => '<option value="' + o[0] + '"' + (String(icon.name || "activity") === o[0] ? " selected" : "") + '>' + o[1] + '</option>').join("") + '<option value="custom"' + (iconPresets.some(o => o[0] === String(icon.name || "")) ? "" : " selected") + '>Custom…</option></select></div>' +
            '<div class="col-md-5"><label class="form-label small">Custom icon name</label><input class="form-control form-control-sm summary-card-icon-name" value="' + escapeHtml(icon.name && !iconPresets.some(o => o[0] === String(icon.name)) ? icon.name : "") + '" placeholder="e.g. boxes"></div>' +
            '<div class="col-12 summary-card-boolean-mapping" hidden><div class="row g-2"><div class="col-md-6"><label class="form-label small">True label</label><input class="form-control form-control-sm summary-card-true-label" value="' + escapeHtml(map.true===undefined?'':map.true) + '" placeholder="Active"></div>' +
            '<div class="col-md-6"><label class="form-label small">False label</label><input class="form-control form-control-sm summary-card-false-label" value="' + escapeHtml(map.false===undefined?'':map.false) + '" placeholder="Inactive"></div></div></div></div></div></div>';
        }).join("") || '<div class="text-muted small">No cards configured. Select Add card to create one.</div>';
        const layout = String(summary.layout || (details.enabled === false ? "CARDS_ONLY" : "BOTH")).toUpperCase();
        byId("summaryTableLayout").value = ["BOTH","CARDS_ONLY","TABLE_ONLY"].includes(layout) ? layout : "BOTH";
        byId("summaryTableShowTotalRowsCard").checked = totalRows.enabled === true || (!summary.totalRowsCard && Boolean(legacyTotal));
        byId("summaryTableTotalRowsLabel").value = totalRows.label || (legacyTotal && legacyTotal.label) || "Total Rows";
        byId("summaryTableTotalRowsClick").value = totalRows.filterable === true ? "FILTER_SELF" : "NONE";
        const totalIcon = totalRows.icon && typeof totalRows.icon === "object" ? totalRows.icon : {};
        const totalIconName = String(totalIcon.name || "list-check");
        byId("summaryTableTotalRowsIconEnabled").checked = totalIcon.enabled === true;
        byId("summaryTableTotalRowsIconPreset").value = iconPresets.some(option => option[0] === totalIconName) ? totalIconName : "custom";
        byId("summaryTableTotalRowsIconName").value = iconPresets.some(option => option[0] === totalIconName) ? "" : totalIconName;
        syncSummaryCardBooleanFields();
    }
    function readSummaryCardsEditor() {
        return Array.from(byId("summaryTableCardsEditor").querySelectorAll("[data-summary-card-row]")).map(row => {
            const field = row.querySelector(".summary-card-field").value, groupBy = row.querySelector(".summary-card-group-by").value;
            const mapping = {}, t = row.querySelector(".summary-card-true-label").value, f = row.querySelector(".summary-card-false-label").value;
            const fieldType = fieldBooleanType(field), groupType = fieldBooleanType(groupBy);
            if (fieldType === true || groupType === true || (fieldType === null && groupType === null && (t !== "" || f !== ""))) {
                if (t !== "") mapping.true = t;
                if (f !== "") mapping.false = f;
            }
            const card = {field, label:row.querySelector(".summary-card-label").value.trim() || (field ? humanize(field) : "Total Rows"),
                operation:row.querySelector(".summary-card-operation").value, format:row.querySelector(".summary-card-format").value,
                filterable:row.querySelector(".summary-card-click").value === "FILTER_SELF",
                icon:{enabled:row.querySelector(".summary-card-icon-enabled").checked,name:row.querySelector(".summary-card-icon-name").value.trim() || (row.querySelector(".summary-card-icon-preset").value === "custom" ? "activity" : row.querySelector(".summary-card-icon-preset").value)}, valueMapping:mapping};
            if (groupBy) card.groupBy = groupBy; return card;
        });
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
            const systemMetricsCollection = currentWidgetType() === "SYSTEM_METRICS"
                ? collections.find(item => /(^|\.)machines$/.test(item.path))
                : null;
            // A blank saved rowPath used to select the synthetic root-object row, which
            // exposed wrapper fields such as "machines" instead of one row per machine.
            const preferredPath = systemMetricsCollection
                ? systemMetricsCollection.path
                : (rowPath && collections.some(item => item.path === rowPath)
                    ? rowPath
                    : (preferRoot && collections.some(item => item.path === "") ? "" :
                        (collections.find(item => item.path !== "") || collections[0]).path));
            select.value = preferredPath;

            const details = config.details && typeof config.details === "object" ? config.details : {};
            const search = details.search || config.search || {};
            const sorting = details.sorting || config.sorting || {};
            const pagination = details.pagination || config.pagination || {};
            byId("summaryTableSearchEnabled").checked = search.enabled !== false;
            byId("summaryTableExportCsvEnabled").checked = !details.exportCsv || details.exportCsv.enabled !== false;
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

    function syncColumnVisibilitySelectAll() {
        const host = byId("summaryTableFieldEditor");
        const selectAll = host.querySelector(".summary-config-visible-all");
        if (!selectAll) return;
        const checkboxes = Array.from(host.querySelectorAll("tbody .summary-config-visible"));
        const checkedCount = checkboxes.filter(checkbox => checkbox.checked).length;
        selectAll.checked = checkboxes.length > 0 && checkedCount === checkboxes.length;
        selectAll.indeterminate = checkedCount > 0 && checkedCount < checkboxes.length;
        selectAll.disabled = checkboxes.length === 0;
    }

    function syncGroupableSelectAll() {
        const host = byId("summaryTableFieldEditor");
        const selectAll = host.querySelector(".summary-config-groupable-all");
        if (!selectAll) return;
        const checkboxes = Array.from(host.querySelectorAll("tbody .summary-config-groupable"));
        const checkedCount = checkboxes.filter(checkbox => checkbox.checked).length;
        selectAll.checked = checkboxes.length > 0 && checkedCount === checkboxes.length;
        selectAll.indeterminate = checkedCount > 0 && checkedCount < checkboxes.length;
        selectAll.disabled = checkboxes.length === 0;
    }

    function renderFields() {
        const collection = selectedCollection();
        const config = getConfig();
        const existing = configuredColumns(config);
        if (!collection && !existing.size && !(state.fields || []).length) return;
        state.rows = collection ? collection.rows : [];
        const discoveredFields = collection
            ? (currentWidgetType() === "SYSTEM_METRICS" ? discoverSystemMetricFields(collection.rows)
                : Array.from(new Set(collection.rows.reduce((all, row) => all.concat(Object.keys(row)), []))))
            : [];
        // Keep saved mappings even when a field is absent from the current sample/latest job response.
        // Otherwise a partial or changing response silently drops display and aggregation settings.
        const isSystemMetrics = currentWidgetType() === "SYSTEM_METRICS";
        const allFields = isSystemMetrics
            ? [
                // Keep the user's saved mapping order for every field that still exists,
                // preserve dynamic metrics that are temporarily absent, then append new fields
                // in the order they are discovered. This keeps the preview/runtime aligned
                // with the mapping table instead of resetting columns on every discovery.
                ...Array.from(existing.keys()).filter(field =>
                    discoveredFields.includes(field) || String(field).startsWith("metrics::")),
                ...discoveredFields.filter(field => !existing.has(field))
            ]
            : [
                ...Array.from(existing.keys()),
                ...discoveredFields.filter(field => !existing.has(field))
            ];
        state.fields = allFields;
        const anyExisting = existing.size > 0;
        const fields = allFields.map(field => {
            const saved = existing.get(field);
            return {
                field,
                label: saved && saved.label ? saved.label
                    : (String(field).startsWith("metrics::") ? String(field).slice("metrics::".length) : humanize(field)),
                visible: saved ? saved.visible !== false : true,
                format: saved && saved.format !== undefined ? String(saved.format).toLowerCase()
                    : (currentWidgetType() === "SYSTEM_METRICS" ? (systemMetricDefaultFormat(field, collection ? collection.rows : []) || "") : ""),
                aggregation: saved && saved.aggregation ? String(saved.aggregation).toLowerCase() : "none",
                summaryCard: false,
                sortable: saved ? saved.sortable !== false : true,
                searchable: saved ? saved.searchable !== false : true,
                groupable: saved ? saved.groupable !== false : true
            };
        });
        const summary = config.summary && typeof config.summary === "object" ? config.summary : {};
        const details = config.details && typeof config.details === "object" ? config.details : {};
        const oldCards = Array.isArray(summary.cards) && (summary.cards.length > 0 || !Array.isArray(details.summaryCards))
            ? summary.cards : (Array.isArray(details.summaryCards) ? details.summaryCards : (Array.isArray(summary.cards) ? summary.cards : null));
        const selectedCardFields = new Set((oldCards || []).map(card => String(card.field || "")).filter(Boolean));
        const groupedSummaryFields = new Set((Array.isArray(summary.groupByFields) ? summary.groupByFields : []).map(String));
        const cardsByField = new Map((oldCards || []).filter(card => card && card.field)
            .map(card => [String(card.field), card]));
        fields.forEach(item => {
            const savedCard = cardsByField.get(item.field);
            const operation = String(savedCard && savedCard.operation || "").toUpperCase();
            item.summaryFilterable = savedCard ? savedCard.filterable !== false : true;
            const inferredAggregation = ({
                COUNT_NON_EMPTY: "count", COUNT_TRUE: "count", COUNT_FALSE: "count",
                SUM: "sum", AVG: "avg", MAX: "max", MIN: "min"
            })[operation];
            if (item.aggregation === "none" && inferredAggregation) item.aggregation = inferredAggregation;
            item.summaryCard = selectedCardFields.has(item.field) && item.aggregation !== "none";
            item.groupSummary = groupedSummaryFields.has(item.field);
        });
        const systemMetrics = currentWidgetType() === "SYSTEM_METRICS";
        const formats = systemMetrics
            ? [["", "Automatic"], ["meter", "Health meter"], ["number", "Numeric value"], ["text", "Text"], ["duration", "Duration"], ["status", "Status"]]
            : [["", "Default"], ["number", "Number"], ["percentage", "Percentage"], ["toggle", "Toggle"],
                ["duration", "Duration (seconds)"], ["datetime", "Date / time"], ["status", "Status"]];
        const aggregations = [["none", "None"], ["count", "Count"], ["sum", "Sum"], ["avg", "Average"], ["max", "Maximum"], ["min", "Minimum"]];
        byId("summaryTableFieldEditor").innerHTML = fields.length ?
            '<div class="table-responsive border rounded"><table class="table table-sm table-striped align-middle mb-0 summary-config-mapping-table">' +
            '<thead><tr><th>Order</th><th><div class="form-check mb-0"><input class="form-check-input summary-config-visible-all" type="checkbox" aria-label="Show or hide all fields"><label class="form-check-label">Show</label></div></th><th>Source field</th><th>Display label</th><th>Format</th>' +
            (systemMetrics ? '' : '<th>Aggregate</th><th><div class="form-check mb-0"><input class="form-check-input summary-config-groupable-all" type="checkbox" aria-label="Allow grouping by all columns"><label class="form-check-label">Groupable</label></div></th>') +
            '</tr></thead><tbody>' +
            fields.map((item, index) => '<tr data-field="' + escapeHtml(item.field) + '" data-sortable="' + item.sortable + '" data-searchable="' + item.searchable + '">' +
                '<td><div class="d-flex gap-1"><button type="button" class="btn btn-outline-secondary btn-sm summary-config-move-up" aria-label="Move ' + escapeHtml(item.field) + ' up"' + (index === 0 ? ' disabled' : '') + '>↑</button>' +
                '<button type="button" class="btn btn-outline-secondary btn-sm summary-config-move-down" aria-label="Move ' + escapeHtml(item.field) + ' down"' + (index === fields.length - 1 ? ' disabled' : '') + '>↓</button></div></td>' +
                '<td><input class="form-check-input summary-config-visible" type="checkbox" aria-label="Show ' + escapeHtml(item.field) + '"' + (item.visible ? ' checked' : '') + '></td>' +
                '<td><code>' + escapeHtml(displaySourceField(item.field)) + '</code></td>' +
                '<td><input class="form-control form-control-sm summary-config-label" type="text" maxlength="120" value="' + escapeHtml(item.label) + '" aria-label="Label for ' + escapeHtml(item.field) + '"></td>' +
                '<td><select class="form-select form-select-sm summary-config-format" aria-label="Format for ' + escapeHtml(item.field) + '">' +
                formats.map(option => '<option value="' + option[0] + '"' + (item.format === option[0] ? ' selected' : '') + '>' + option[1] + '</option>').join("") +
                '</select></td>' +
                (systemMetrics ? '' : '<td><select class="form-select form-select-sm summary-config-aggregation" aria-label="Aggregation for ' + escapeHtml(item.field) + '">' +
                aggregations.map(option => '<option value="' + option[0] + '"' + (item.aggregation === option[0] ? ' selected' : '') + '>' + option[1] + '</option>').join("") +
                '</select></td><td><input class="form-check-input summary-config-groupable" type="checkbox" aria-label="Allow grouping by ' + escapeHtml(item.field) + '"' + (item.groupable ? ' checked' : '') + '></td>') +
                '</tr>').join("") +
            '</tbody></table></div><div class="form-text">' + (systemMetrics
                ? 'Configure machine identity, overall status, and each discovered metric in one mapping table. New metric names appear automatically when discovered.'
                : 'Source fields are preserved separately from display labels. Uncheck fields you do not want shown as columns.') + '</div>'
            : '<div class="text-muted small">No fields were found in this collection.</div>'; 
        syncColumnVisibilitySelectAll();
        syncGroupableSelectAll();

        const sort = byId("summaryTableDefaultSort");
        const previousSort = (config.details && config.details.sorting && config.details.sorting.defaultField)
            || (config.sorting && config.sorting.defaultField) || "";
        sort.innerHTML = '<option value="">No default sort</option>' + fields.map(item =>
            '<option value="' + escapeHtml(item.field) + '">' + escapeHtml(item.label) + ' (' + escapeHtml(item.field) + ')</option>'
        ).join("");
        sort.disabled = fields.length === 0;
        sort.value = allFields.includes(previousSort) ? previousSort : "";
        renderSummaryCardsEditor(config);
        renderGroupByOptions(config, fields);
        renderConditionalRules(config);
        renderTypeMappingOptions();
        renderPreview();
    }

    function readFieldEditor() {
        const systemMetrics = currentWidgetType() === "SYSTEM_METRICS";
        return Array.from(byId("summaryTableFieldEditor").querySelectorAll("tr[data-field]")).map(row => ({
            field: row.getAttribute("data-field"),
            label: row.querySelector(".summary-config-label").value.trim() || humanize(row.getAttribute("data-field")),
            visible: row.querySelector(".summary-config-visible").checked,
            groupable: systemMetrics ? false : row.querySelector(".summary-config-groupable").checked,
            sortable: row.getAttribute("data-sortable") !== "false",
            searchable: row.getAttribute("data-searchable") !== "false",
            format: row.querySelector(".summary-config-format").value,
            aggregation: systemMetrics ? "none" : row.querySelector(".summary-config-aggregation").value,
        }));
    }


    function isNumericSummaryField(field) {
        return !!field && (state.rows || []).some(row => {
            const value = rowValueForConfigurator(row, field);
            return value !== null && value !== undefined && value !== "" && typeof value !== "boolean" && Number.isFinite(Number(value));
        });
    }

    function rowValueForConfigurator(row, field) {
        const source = String(field || "");
        if (source.startsWith("metrics::")) {
            const name = source.slice("metrics::".length);
            const metrics = row && row.metrics;
            if (Array.isArray(metrics)) {
                const metric = metrics.find(item => item && String(item.name !== undefined ? item.name : item.key) === name);
                return metric && typeof metric === "object" ? metric.value : undefined;
            }
            if (metrics && typeof metrics === "object") {
                const metric = metrics[name];
                return metric && typeof metric === "object" ? metric.value : metric;
            }
            return undefined;
        }
        if (row && Object.prototype.hasOwnProperty.call(row, field)) return row[field];
        return source.split(".").reduce((value, key) => value == null ? undefined : value[key], row);
    }

    function displaySourceField(field) {
        return String(field || "").startsWith("metrics::")
            ? "metrics[" + String(field).slice("metrics::".length) + "].value" : field;
    }

    function discoverSystemMetricFields(rows) {
        const fields = [], metricNames = [];
        const addField = field => {
            if (field && !fields.includes(field)) fields.push(field);
        };
        const visitObject = (value, prefix, depth) => {
            if (!value || typeof value !== "object" || Array.isArray(value) || depth > 6) return;
            Object.keys(value).forEach(key => {
                // Metrics use their own name/value mapping and must not appear as a JSON object field.
                if (!prefix && key === "metrics") return;
                const field = prefix ? prefix + "." + key : key;
                addField(field);
                if (value[key] && typeof value[key] === "object" && !Array.isArray(value[key])) {
                    visitObject(value[key], field, depth + 1);
                }
            });
        };
        (rows || []).forEach(row => {
            if (!row || typeof row !== "object") return;
            visitObject(row, "", 0);
            const metrics = row.metrics;
            if (Array.isArray(metrics)) metrics.forEach(metric => {
                const name = metric && (metric.name !== undefined && String(metric.name).trim()
                    ? metric.name : (metric.key !== undefined && String(metric.key).trim() ? metric.key : metric.label));
                if (name !== null && name !== undefined && String(name).trim() && !metricNames.includes(String(name).trim())) metricNames.push(String(name).trim());
            });
            else if (metrics && typeof metrics === "object") Object.keys(metrics).forEach(name => {
                if (!metricNames.includes(name)) metricNames.push(name);
            });
        });
        return fields.concat(metricNames.map(name => "metrics::" + name));
    }

    function systemMetricDefaultFormat(field, rows) {
        if (!String(field || "").startsWith("metrics::")) return "";
        const name = String(field).slice("metrics::".length);
        for (const row of rows || []) {
            const metrics = row && row.metrics;
            let metric;
            if (Array.isArray(metrics)) metric = metrics.find(item => item && String(item.name !== undefined ? item.name : item.key) === name);
            else if (metrics && typeof metrics === "object") metric = metrics[name];
            if (metric && typeof metric === "object") {
                const type = String(metric.type || "").toLowerCase();
                if (["percentage", "percent"].includes(type) || metric.unit === "%") return "meter";
                if (type === "duration") return "duration";
                if (["number", "capacity"].includes(type) || typeof metric.value === "number") return "number";
            } else if (typeof metric === "number") return "number";
        }
        return "";
    }

    function buildConfig() {
        const config = getConfig();
        config.renderer = "SUMMARY_TABLE";
        config.responseParser = currentParserConfig();
        const rowPath = byId("summaryTableRowPath").value;
        if (rowPath) config.rowPath = rowPath;
        else delete config.rowPath;

        config.details = config.details && typeof config.details === "object" ? config.details : {};
        const editedColumns = readFieldEditor();
        config.details.columns = editedColumns.map(({ summaryCard, summaryFilterable, groupSummary, ...column }) => column);
        config.details.search = Object.assign({}, config.details.search || {}, { enabled: byId("summaryTableSearchEnabled").checked });
        config.details.exportCsv = Object.assign({}, config.details.exportCsv || {}, { enabled: byId("summaryTableExportCsvEnabled").checked });
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
        delete config.details.summaryCards;
        config.summary = Object.assign({}, config.summary || {});
        config.summary.groupByFields = Array.isArray(config.summary.groupByFields) ? config.summary.groupByFields : [];
        config.summary.cards = readSummaryCardsEditor();
        config.summary.totalRowsCard = {
            enabled: byId("summaryTableShowTotalRowsCard").checked,
            label: byId("summaryTableTotalRowsLabel").value.trim() || "Total Rows",
            filterable: byId("summaryTableTotalRowsClick").value === "FILTER_SELF",
        icon: { enabled: byId("summaryTableTotalRowsIconEnabled").checked,
            name: byId("summaryTableTotalRowsIconName").value.trim() || (byId("summaryTableTotalRowsIconPreset").value === "custom" ? "list-check" : byId("summaryTableTotalRowsIconPreset").value) }
        };
        config.summary.layout = byId("summaryTableLayout").value;
        config.details.enabled = config.summary.layout !== "CARDS_ONLY";
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
                    let value = rowValueForConfigurator(row, column.field);
                    if (value !== null && typeof value === "object") value = JSON.stringify(value);
                    const rawBoolean = value === true || value === false || (typeof value === "string" && /^(true|false)$/i.test(String(value).trim()));
                    const statusValue = String(value == null ? "" : value).trim().toLowerCase();
                    const isBoolean = rawBoolean || ["healthy", "health", "up", "active", "enabled", "ok", "pass", "passed", "success", "green", "1", "yes",
                        "unhealthy", "down", "inactive", "disabled", "error", "failed", "fail", "critical", "red", "0", "no"].includes(statusValue);
                    const boolValue = rawBoolean ? (value === true || String(value).trim().toLowerCase() === "true")
                        : ["healthy", "health", "up", "active", "enabled", "ok", "pass", "passed", "success", "green", "1", "yes"].includes(statusValue);
                    const cellClass = conditionalPreviewClass(row, previewConfig, "cell", column.field);
                    if (String(column.format || "").toLowerCase() === "toggle") return '<td class="' + cellClass + '"><span class="summary-table-toggle ' + (boolValue ? 'is-on' : 'is-off') + '"><span></span></span></td>';
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
            summaryConfig.details.exportCsv = Object.assign({}, summaryConfig.details.exportCsv || {}, {
                enabled: byId("summaryTableExportCsvEnabled").checked
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
            delete summaryConfig.details.summaryCards;
            summaryConfig.summary = Object.assign({}, summaryConfig.summary || {});
            summaryConfig.summary.cards = readSummaryCardsEditor();
            summaryConfig.summary.totalRowsCard = {
                enabled: byId("summaryTableShowTotalRowsCard").checked,
                label: byId("summaryTableTotalRowsLabel").value.trim() || "Total Rows",
                filterable: byId("summaryTableTotalRowsClick").value === "FILTER_SELF",
            icon: { enabled: byId("summaryTableTotalRowsIconEnabled").checked,
                name: byId("summaryTableTotalRowsIconName").value.trim() || (byId("summaryTableTotalRowsIconPreset").value === "custom" ? "list-check" : byId("summaryTableTotalRowsIconPreset").value) }
            };
            summaryConfig.summary.layout = byId("summaryTableLayout").value;
            summaryConfig.details.enabled = summaryConfig.summary.layout !== "CARDS_ONLY";
            setConfig(summaryConfig);
            return;
        }
        const config = getConfig();
        config.responseParser = currentParserConfig();
        if (type === "SYSTEM_METRICS" && state.sample && selectedCollection()) {
            const columns = readFieldEditor();
            config.renderer = "SYSTEM_METRICS";
            config.rowPath = byId("summaryTableRowPath").value || "";
            config.details = config.details && typeof config.details === "object" ? config.details : {};
            config.details.columns = columns.map(({ summaryCard, summaryFilterable, groupSummary, ...column }) => column);
            config.details.pagination = Object.assign({}, config.details.pagination || {}, { enabled: true, pageSize: 12 });
            config.summary = Object.assign({}, config.summary || {});
            config.summary.cards = readSummaryCardsEditor();
            config.summary.totalRowsCard = {
                enabled: byId("summaryTableShowTotalRowsCard").checked,
                label: byId("summaryTableTotalRowsLabel").value.trim() || "Total Rows",
                filterable: byId("summaryTableTotalRowsClick").value === "FILTER_SELF",
                icon: {
                    enabled: byId("summaryTableTotalRowsIconEnabled").checked,
                    name: byId("summaryTableTotalRowsIconName").value.trim() || (byId("summaryTableTotalRowsIconPreset").value === "custom" ? "list-check" : byId("summaryTableTotalRowsIconPreset").value)
                }
            };
            delete config.fields;
            delete config.labels;
            delete config.view;
            setConfig(config);
            return;
        }
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
        // Older Stat/Status mappings may have visibleFields but no valueField
        // because the generic chart submit handler previously removed valueField.
        // Restore the primary selection from the saved visibility list when reopening.
        const configuredPrimaryField = config.valueField ||
            (["STAT", "STATUS"].includes(type) && Array.isArray(config.visibleFields)
                ? config.visibleFields[0] || "" : "");
        setFieldOptions("widgetSingleValueField", fields, configuredPrimaryField, "Select value field");
        setFieldOptions("widgetStatusField", fields, config.statusField, "No status field");
        setFieldOptions("widgetMetricStatusField", fields,
            config.statusField || (config.fields && !Array.isArray(config.fields) ? config.fields.status : ""),
            "Auto-detect status field");
        const metricFields = config.fields && typeof config.fields === "object" && !Array.isArray(config.fields)
            ? config.fields : {};
        // Only machine identity and health status need semantic selectors. All metric fields,
        // labels, formats, visibility and order use the shared discovered-field mapping table.
        setFieldOptions("systemMetricHostnameField", fields, metricFields.hostname || "", "Select hostname field");
        const view = config.view && typeof config.view === "object" ? config.view : {};
        byId("systemMetricShowSummaryCards").checked = view.showSummaryCards !== false;
        setFieldOptions("widgetTextFields", fields, config.textFields, "");
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
            case "STAT": {
                config.renderer = "STAT";
                config.valueField = byId("widgetSingleValueField").value;
                const columns = readFieldEditor();
                config.columns = columns;
                const visibleColumns = columns.filter(column => column.visible);
                config.visibleFields = Array.from(new Set([
                    config.valueField,
                    ...visibleColumns.map(column => column.field)
                ].filter(Boolean)));
                config.labels = Object.assign({}, config.labels || {},
                    Object.fromEntries(columns.map(column => [column.field, column.label || humanize(column.field)])));
                config.formats = Object.assign({}, config.formats || {},
                    Object.fromEntries(columns.filter(column => column.format).map(column => [column.field, column.format])));
                break;
            }
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
            case "SYSTEM_METRICS": {
                config.renderer = "SYSTEM_METRICS";
                config.details = config.details && typeof config.details === "object" ? config.details : {};
                const columns = readFieldEditor().map(({ aggregation, groupable, sortable, searchable, ...column }) => column);
                config.details.columns = columns;
                config.fields = config.fields && typeof config.fields === "object" && !Array.isArray(config.fields) ? config.fields : {};
                const hostnameField = byId("systemMetricHostnameField").value;
                const statusField = byId("widgetMetricStatusField").value;
                if (hostnameField) config.fields.hostname = hostnameField;
                else delete config.fields.hostname;
                if (statusField) config.statusField = statusField;
                else delete config.statusField;
                // The shared mapping table is the source of truth for every displayed metric.
                // Arbitrary flat fields and newly discovered metrics require no code changes.
                config.view = Object.assign({}, config.view || {}, {
                    showSummaryCards: byId("systemMetricShowSummaryCards").checked,
                    metrics: columns.filter(column => String(column.field).startsWith("metrics::")).map((column, index) => ({
                        name: String(column.field).slice("metrics::".length),
                        enabled: column.visible !== false,
                        label: column.label || String(column.field).slice("metrics::".length),
                        format: column.format || "auto",
                        order: index
                    }))
                });
                break;
            }
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
                    },
                    summary: { enabled: true, cards: [], totalRowsCard: { enabled: false, label: "Total Rows", filterable: false } }
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
                return { renderer: "SYSTEM_METRICS", responseParser: { type: "AUTO" }, rowPath: "", fields: {}, statusField: "", labels: {} };
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
        const isFieldMapping = isTable || ["STAT", "SYSTEM_METRICS"].includes(type);
        const rowPathTypes = ["TABLE", "SUMMARY_TABLE", "CHART", "SYSTEM_METRICS"];
        byId("summaryTableConfigurator").hidden = !type;
        byId("dashboardWidgetAdvancedConfig").open = false;
        byId("summaryTableGroupBySection").hidden = !isSummary;
        byId("summaryTableCardsSection").hidden = !(isSummary || type === "SYSTEM_METRICS");
        byId("summaryTableMappingActions").hidden = !isSummary;
        byId("summaryTableConditionalFormattingSection").hidden = !isSummary;
        byId("summaryTableTableOptions").hidden = !isTable;
        byId("summaryTableExportCsvOption").hidden = !isSummary;
        byId("summaryTableLayoutControl").hidden = !isSummary;
        byId("summaryTableFieldEditorGroup").hidden = !isFieldMapping;
        byId("summaryTableRowPathGroup").hidden = !rowPathTypes.includes(type);
        byId("widgetMappingHeading").textContent = type === "SUMMARY_TABLE" ? "Summary Table data and columns" : "Widget data and mapping";
        byId("widgetFieldMappingHeading").textContent = isSummary ? "Column mapping" : type === "SYSTEM_METRICS" ? "Machine and metric mapping" : "Field mapping";
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
        byId("summaryTableExportCsvEnabled").checked = !details.exportCsv || details.exportCsv.enabled !== false;
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
        if (state.sample || type === "SYSTEM_METRICS") renderFields();
    }

    document.addEventListener("DOMContentLoaded", function () {
        const type = byId("dashboardWidgetType");
        const sample = byId("summaryTableSampleJson");
        const discoverButton = byId("summaryTableDiscoverFields");
        if (!type || !sample || !discoverButton) return;

        discoverButton.addEventListener("click", discover);
        // Restore the editor preview from the latest saved job output when reopening
        // a System Metrics widget. The sample JSON itself is not persisted.
        const widgetModal = byId("dashboardWidgetModal");
        if (widgetModal) {
            widgetModal.addEventListener("shown.bs.modal", function () {
                if (currentWidgetType() !== "SYSTEM_METRICS") return;
                if (sample.value.trim()) return;
                if (!byId("dashboardWidgetJobSourceId").value) return;
                byId("summaryTableLoadLatest").click();
            });
        }

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
            "widgetStatusField", "widgetMetricStatusField", "widgetTextFields",
            "systemMetricHostnameField", "systemMetricShowSummaryCards"].forEach(id => {
            byId(id).addEventListener("change", refreshConfigFromEditor);
        });
        byId("widgetTextTemplate").addEventListener("input", refreshConfigFromEditor);
        byId("summaryTableShowTotalRowsCard").addEventListener("change", refreshConfigFromEditor);
        ["summaryTableTotalRowsLabel", "summaryTableTotalRowsClick", "summaryTableTotalRowsIconEnabled", "summaryTableTotalRowsIconPreset", "summaryTableTotalRowsIconName"].forEach(id => {
            byId(id).addEventListener("change", function () {
                if (id === "summaryTableTotalRowsIconPreset" && byId(id).value !== "custom") byId("summaryTableTotalRowsIconName").value = "";
                refreshConfigFromEditor();
            });
            byId(id).addEventListener("input", function () {
                if (id === "summaryTableTotalRowsIconName" && byId(id).value.trim()) byId("summaryTableTotalRowsIconPreset").value = "custom";
                refreshConfigFromEditor();
            });
        });
        byId("summaryTableAddCard").addEventListener("click", function () {
            const config = getConfig(), summary = config.summary && typeof config.summary === "object" ? config.summary : {};
            const details = config.details && typeof config.details === "object" ? config.details : {};
            const cards = Array.isArray(summary.cards) ? summary.cards.slice() : (Array.isArray(details.summaryCards) ? details.summaryCards.slice() : []);
            cards.push({field:(state.fields || [])[0] || "",label:"",operation:"COUNT_ROWS",filterable:true,icon:{enabled:false,name:"activity"},valueMapping:{}});
            config.summary = Object.assign({},summary,{cards}); config.details = details; setConfig(config); renderSummaryCardsEditor(config);
        });
        byId("summaryTableCardsEditor").addEventListener("click", function (event) {
            const button = event.target.closest(".summary-card-remove"); if (!button) return;
            button.closest("[data-summary-card-row]").remove(); refreshConfigFromEditor();
        });
        ["change","input"].forEach(name => byId("summaryTableCardsEditor").addEventListener(name, function (event) {
            const row = event.target.closest("[data-summary-card-row]");
            if (row && event.target.matches(".summary-card-icon-preset") && event.target.value !== "custom") {
                row.querySelector(".summary-card-icon-name").value = "";
            } else if (row && event.target.matches(".summary-card-icon-name") && event.target.value.trim()) {
                row.querySelector(".summary-card-icon-preset").value = "custom";
            }
            if (event.target.matches(".summary-card-field, .summary-card-group-by")) syncSummaryCardBooleanFields();
            refreshConfigFromEditor();
        }));
        byId("summaryTableLayout").addEventListener("change", refreshConfigFromEditor);
        function clearSummaryTableMapping(resetToDefaults) {
            const message = resetToDefaults
                ? "Reset mapping to defaults? Column display settings, summary cards, grouping, and conditional formatting will be reset. The response parser and row path will be preserved."
                : "Clear the current mapping? Columns will be hidden, summary cards and grouping cleared, and conditional-formatting rules removed. The response parser and row path will be preserved.";
            if (!window.confirm(message)) return;
            const config = getConfig();
            config.renderer = "SUMMARY_TABLE";
            config.details = config.details && typeof config.details === "object" ? config.details : {};
            config.summary = config.summary && typeof config.summary === "object" ? config.summary : {};
            if (resetToDefaults) {
                delete config.details.columns;
                delete config.columns;
            } else {
                const mappingFields = Array.from(new Set([...configuredColumns(config).keys(), ...(state.fields || [])]));
                config.details.columns = mappingFields.map(field => ({
                    field, label: humanize(field), visible: false, groupable: true,
                    sortable: true, searchable: true, format: "", aggregation: "none"
                }));
            }
            config.details.sorting = Object.assign({}, config.details.sorting || {}, { defaultField: "", enabled: true, direction: "asc" });
            config.details.groupBy = Object.assign({}, config.details.groupBy || {}, {
                enabled: false, fields: [], initialState: "EXPANDED", order: "VALUE_ASC", showAggregates: true
            });
            config.details.conditionalFormatting = { enabled: true, rules: [] };
            delete config.details.summaryCards;
            config.summary.cards = [];
            config.summary.totalRowsCard = { enabled: false, label: "Total Rows", filterable: false };
            delete config.summary.groupByFields;
            config.summary.layout = "BOTH";
            config.summary.enabled = true;
            config.details.enabled = true;
            setConfig(config);
            showForSelectedType();
            renderFields();
            renderSummaryCardsEditor(config);
            byId("summaryTableDefaultSort").value = "";
            byId("summaryTableSortDirection").value = "asc";
            refreshConfigFromEditor();
        }
        byId("summaryTableClearMapping").addEventListener("click", function () { clearSummaryTableMapping(false); });
        byId("summaryTableResetMapping").addEventListener("click", function () { clearSummaryTableMapping(true); });
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
            if (event.target.matches(".summary-config-groupable-all")) {
                byId("summaryTableFieldEditor").querySelectorAll("tbody .summary-config-groupable")
                    .forEach(checkbox => { checkbox.checked = event.target.checked; });
                event.target.indeterminate = false;
                refreshConfigFromEditor();
                syncGroupableSelectAll();
                return;
            }
            if (event.target.matches(".summary-config-visible-all")) {
                byId("summaryTableFieldEditor").querySelectorAll("tbody .summary-config-visible")
                    .forEach(checkbox => { checkbox.checked = event.target.checked; });
                event.target.indeterminate = false;
                refreshConfigFromEditor();
                syncColumnVisibilitySelectAll();
                return;
            }
            const row = event.target.closest("tr[data-field]");
            if (event.target.classList.contains("summary-config-aggregation")) {
                const summaryCard = row && row.querySelector(".summary-config-summary-card");
                const filterable = row && row.querySelector(".summary-config-summary-filterable");
                if (summaryCard) {
                    summaryCard.disabled = event.target.value === "none";
                    if (summaryCard.disabled) summaryCard.checked = false;
                }
                if (filterable) {
                    filterable.disabled = event.target.value === "none" || !summaryCard || !summaryCard.checked;
                    if (filterable.disabled) filterable.checked = false;
                }
            }
            if (event.target.classList.contains("summary-config-summary-card")) {
                const filterable = row && row.querySelector(".summary-config-summary-filterable");
                if (filterable) {
                    filterable.disabled = !event.target.checked || event.target.disabled;
                    if (filterable.disabled) filterable.checked = false;
                    else filterable.checked = true;
                }
            }
            refreshConfigFromEditor();
            if (event.target.classList.contains("summary-config-visible")) syncColumnVisibilitySelectAll();
            if (event.target.classList.contains("summary-config-groupable")) {
                syncGroupableSelectAll();
                renderFields();
                refreshConfigFromEditor();
            }
        });
        byId("summaryTableFieldEditor").addEventListener("input", function (event) {
            if (event.target.classList.contains("summary-config-label")) refreshConfigFromEditor();
        });
        ["summaryTableDefaultSort", "summaryTableSortDirection", "summaryTablePageSize", "summaryTableSearchEnabled", "summaryTableExportCsvEnabled"].forEach(id => {
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
                if (currentWidgetType() === "SUMMARY_TABLE" && sourceType && sourceType.value === "MONITORING_JOB"
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

        // Persist the Stat/Status primary field at submit time as well as on editor
        // changes. This also repairs older saved mappings that only contain visibleFields.
        const widgetForm = byId("dashboardWidgetFieldConfigJson").closest("form");
        if (widgetForm) {
            widgetForm.addEventListener("submit", function () {
                const type = currentWidgetType();
                if (!["STAT", "STATUS"].includes(type)) return;

                const config = getConfig();
                const selectedValueField = byId("widgetSingleValueField").value ||
                    config.valueField ||
                    (Array.isArray(config.visibleFields) ? config.visibleFields[0] || "" : "");
                config.renderer = type;
                if (selectedValueField) config.valueField = selectedValueField;
                else delete config.valueField;

                if (type === "STAT") {
                    config.visibleFields = selectedValueField ? [selectedValueField] : [];
                } else {
                    config.statusField = byId("widgetStatusField").value || config.statusField || "";
                    config.visibleFields = [selectedValueField, config.statusField].filter(Boolean);
                }
                config.responseParser = currentParserConfig();
                byId("dashboardWidgetFieldConfigJson").value = JSON.stringify(config);
            });
        }

        showForSelectedType();
    });
})();
