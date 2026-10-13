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

    function discover() {
        const message = byId("summaryTableDiscoveryMessage");
        try {
            const sample = parseSample(byId("summaryTableSampleJson").value);
            const collections = findCollections(sample);
            if (Array.isArray(sample) && sample.some(row => row && typeof row === "object" && !Array.isArray(row))
                && !collections.some(item => item.path === "")) {
                collections.unshift({ path: "", rows: sample.filter(row => row && typeof row === "object" && !Array.isArray(row)), label: "(root array)" });
            }
            if (!collections.length) {
                throw new Error("No array of JSON objects was found. Use a response containing an array of row objects for a Summary Table.");
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
            select.value = collections.some(item => item.path === rowPath) ? rowPath : collections[0].path;

            const details = config.details && typeof config.details === "object" ? config.details : {};
            const search = details.search || config.search || {};
            const sorting = details.sorting || config.sorting || {};
            const pagination = details.pagination || config.pagination || {};
            byId("summaryTableSearchEnabled").checked = search.enabled !== false;
            byId("summaryTableSortDirection").value = String(sorting.direction || "asc").toLowerCase() === "desc" ? "desc" : "asc";
            byId("summaryTablePageSize").value = ["5", "10", "25", "50"].includes(String(pagination.pageSize)) ? String(pagination.pageSize) : "10";

            renderFields();
            refreshConfigFromEditor();
            message.textContent = "Found " + collections.length + " collection(s). Select the row collection and columns.";
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
        const allFields = [
            ...Array.from(existing.keys()).filter(field => discoveredFields.includes(field)),
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
                format: saved && saved.format ? saved.format : "",
                sortable: saved ? saved.sortable !== false : true,
                searchable: saved ? saved.searchable !== false : true,
                groupable: saved ? saved.groupable !== false : true
            };
        });
        const formats = [
            ["", "Default"], ["number", "Number"], ["percentage", "Percentage"],
            ["duration", "Duration (seconds)"], ["datetime", "Date / time"], ["status", "Status"]
        ];
        byId("summaryTableFieldEditor").innerHTML = fields.length ? 
            '<div class="table-responsive border rounded"><table class="table table-sm align-middle mb-0">' +
            '<thead><tr><th>Order</th><th>Show</th><th>Source field</th><th>Display label</th><th>Format</th></tr></thead><tbody>' +
            fields.map((item, index) => '<tr data-field="' + escapeHtml(item.field) + '">' +
                '<td><div class="d-flex gap-1"><button type="button" class="btn btn-outline-secondary btn-sm summary-config-move-up" aria-label="Move ' + escapeHtml(item.field) + ' up"' + (index === 0 ? ' disabled' : '') + '>↑</button>' +
                '<button type="button" class="btn btn-outline-secondary btn-sm summary-config-move-down" aria-label="Move ' + escapeHtml(item.field) + ' down"' + (index === fields.length - 1 ? ' disabled' : '') + '>↓</button></div></td>' +
                '<td><input class="form-check-input summary-config-visible" type="checkbox" aria-label="Show ' + escapeHtml(item.field) + '"' + (item.visible ? ' checked' : '') + '></td>' +
                '<td><code>' + escapeHtml(item.field) + '</code></td>' +
                '<td><input class="form-control form-control-sm summary-config-label" type="text" maxlength="120" value="' + escapeHtml(item.label) + '" aria-label="Label for ' + escapeHtml(item.field) + '"></td>' +
                '<td><select class="form-select form-select-sm summary-config-format" aria-label="Format for ' + escapeHtml(item.field) + '">' +
                formats.map(option => '<option value="' + option[0] + '"' + (item.format === option[0] ? ' selected' : '') + '>' + option[1] + '</option>').join("") +
                '</select></td></tr>').join("") +
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
        renderPreview();
    }

    function readFieldEditor() {
        return Array.from(byId("summaryTableFieldEditor").querySelectorAll("tr[data-field]")).map(row => ({
            field: row.getAttribute("data-field"),
            label: row.querySelector(".summary-config-label").value.trim() || humanize(row.getAttribute("data-field")),
            visible: row.querySelector(".summary-config-visible").checked,
            sortable: true,
            searchable: true,
            groupable: true,
            format: row.querySelector(".summary-config-format").value
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
        config.summary = Object.assign({}, config.summary || {});
        if (config.summary.enabled === undefined) config.summary.enabled = true;
        return config;
    }

    function renderPreview() {
        const collection = selectedCollection();
        if (!collection) return;
        const rows = collection.rows;
        const columns = readFieldEditor().filter(column => column.visible);
        byId("summaryTablePreviewCount").textContent = rows.length + " sample row(s)";
        if (!columns.length) {
            byId("summaryTablePreview").innerHTML = '<div class="text-muted small p-3">Select at least one visible column to preview the table.</div>';
            return;
        }
        const previewRows = rows.slice(0, 10);
        byId("summaryTablePreview").innerHTML = '<table class="table table-sm table-striped align-middle mb-0"><thead><tr>' +
            columns.map(column => '<th>' + escapeHtml(column.label) + '</th>').join("") +
            '</tr></thead><tbody>' + previewRows.map(row => '<tr>' + columns.map(column => {
                let value = row[column.field];
                if (value !== null && typeof value === "object") value = JSON.stringify(value);
                if (value === null || value === undefined) value = "";
                if (column.format === "percentage" && value !== "") value = String(value) + "%";
                if (column.format === "number" && value !== "" && Number.isFinite(Number(value))) value = Number(value).toLocaleString();
                return '<td>' + escapeHtml(value) + '</td>';
            }).join("") + '</tr>').join("") + '</tbody></table>' +
            (rows.length > previewRows.length ? '<div class="small text-muted p-2">Preview limited to the first 10 rows.</div>' : "");
    }

    function refreshConfigFromEditor() {
        if (!state.sample || !selectedCollection()) return;
        setConfig(buildConfig());
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

    function showForSelectedType() {
        const isSummary = byId("dashboardWidgetType").value === "SUMMARY_TABLE";
        byId("summaryTableConfigurator").hidden = !isSummary;
        byId("dashboardWidgetAdvancedConfig").open = !isSummary;
        const config = getConfig();
        const parser = config.responseParser && typeof config.responseParser === "object" ? config.responseParser : {};
        byId("summaryTableResponseFormat").value = ["AUTO", "JSON", "DELIMITED", "KEY_VALUE", "REGEX"].includes(String(parser.type || "AUTO").toUpperCase())
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
        syncParserOptionVisibility();
        if (isSummary && state.sample) renderFields();
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
                const output = String(result.rawOutput || "").trim() ? result.rawOutput : (result.resultData || "");
                if (!String(output).trim()) {
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
            refreshConfigFromEditor();
        });
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
        byId("summaryTableFieldEditor").addEventListener("change", function () {
            refreshConfigFromEditor();
        });
        byId("summaryTableFieldEditor").addEventListener("input", function (event) {
            if (event.target.classList.contains("summary-config-label")) refreshConfigFromEditor();
        });
        ["summaryTableDefaultSort", "summaryTableSortDirection", "summaryTablePageSize", "summaryTableSearchEnabled"].forEach(id => {
            byId(id).addEventListener("change", refreshConfigFromEditor);
        });
        type.addEventListener("change", showForSelectedType);
        byId("dashboardWidgetFieldConfigJson").addEventListener("change", function () {
            if (state.sample) renderFields();
        });
        const modal = byId("dashboardWidgetModal");
        if (modal) {
            modal.addEventListener("shown.bs.modal", showForSelectedType);
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
            });
        }
        showForSelectedType();
    });
})();
