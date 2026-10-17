(function () {
    "use strict";

    const byId = id => document.getElementById(id);
    const fieldId = "dashboardWidgetFieldConfigJson";
    const typeId = "dashboardWidgetType";
    const chartTypeId = "dashboardWidgetChartType";
    let syncing = false;
    const sampleState = { collections: [], numericFields: [] };

    function parseConfig() {
        try {
            const value = JSON.parse(byId(fieldId)?.value || "{}");
            return value && typeof value === "object" && !Array.isArray(value) ? value : {};
        } catch (e) {
            return {};
        }
    }

    function pathValue(config, path, fallback) {
        return path.split(".").reduce((value, key) => value == null ? undefined : value[key], config) ?? fallback;
    }

    function setPath(config, path, value) {
        const parts = path.split(".");
        let target = config;
        parts.slice(0, -1).forEach(part => {
            if (!target[part] || typeof target[part] !== "object" || Array.isArray(target[part])) target[part] = {};
            target = target[part];
        });
        if (value === "" || value === null || value === undefined) delete target[parts[parts.length - 1]];
        else target[parts[parts.length - 1]] = value;
    }

    function esc(value) {
        return String(value ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;")
            .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function ensurePanel() {
        const advanced = byId("dashboardWidgetAdvancedConfig");
        if (!advanced || byId("chartWidgetConfigurator")) return;
        const wrapper = advanced.closest(".col-12");
        if (!wrapper) return;
        const panel = document.createElement("div");
        panel.className = "col-12";
        panel.id = "chartWidgetConfigurator";
        panel.innerHTML = `
          <section class="border rounded p-3 mb-3">
            <div class="mb-3">
              <h6 class="mb-1">Chart configuration</h6>
              <p class="small text-muted mb-0">Map the response fields and tune the chart. These settings are saved with this widget.</p>
            </div>
            <div class="mb-3">
              <label class="form-label" for="chartMapSampleJson">Sample JSON response</label>
              <textarea class="form-control font-monospace" id="chartMapSampleJson" rows="5" spellcheck="false" placeholder="Paste a sample JSON response, or load the latest job output."></textarea>
              <div class="d-flex flex-wrap gap-2 mt-2">
                <button type="button" class="btn btn-outline-secondary btn-sm" id="chartMapLoadLatest">Load latest job output</button>
                <button type="button" class="btn btn-outline-primary btn-sm" id="chartMapDiscover">Discover fields</button>
                <span class="small text-muted align-self-center" id="chartMapSampleMessage" role="status" aria-live="polite">Paste JSON or load the latest output to begin.</span>
              </div>
          </div>
          <div class="row g-3">
              <div class="col-md-6"><label class="form-label" for="chartMapRowPath">Data rows path</label><select class="form-select" id="chartMapRowPath" disabled><option value="">Discover fields first</option></select><div class="form-text">Select the array containing chart records.</div></div>
              <div class="col-md-6"><label class="form-label" for="chartMapCategory">Category / label field</label><select class="form-select" id="chartMapCategory" disabled><option value="">Discover fields first</option></select></div>
              <div class="col-12">
                <label class="form-label mb-2">Discovered fields and chart series</label>
                <div class="table-responsive border rounded">
                  <table class="table table-sm table-striped align-middle mb-0">
                    <thead><tr><th style="min-width:80px"><div class="form-check mb-0"><input class="form-check-input" type="checkbox" id="chartMapEnableAll" aria-label="Enable or disable all chart fields"><label class="form-check-label" for="chartMapEnableAll">Enable</label></div></th><th style="min-width:180px">Source field</th><th style="min-width:180px">Series label</th><th style="min-width:120px">Line colour</th></tr></thead>
                    <tbody id="chartMapSeriesRows"><tr><td colspan="4" class="text-muted small p-3">Discover fields to configure chart series.</td></tr></tbody>
                  </table>
                </div>
                <div class="form-text">Every discovered numeric field appears here. Enable the fields to plot; change the display label or colour for each series. New fields are added automatically when discovered.</div>
              </div>
              <div class="col-md-6"><label class="form-label" for="chartMapTitle">Chart title</label><input class="form-control" id="chartMapTitle" placeholder="Monitoring metrics"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapLegend">Legend position</label><select class="form-select" id="chartMapLegend"><option value="top">Top</option><option value="bottom">Bottom</option><option value="left">Left</option><option value="right">Right</option><option value="none">Hidden</option></select></div>
              <div class="col-md-6"><label class="form-label" for="chartMapXAxis">X-axis title</label><input class="form-control" id="chartMapXAxis" placeholder="Category"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapYAxis">Y-axis title</label><input class="form-control" id="chartMapYAxis" placeholder="Value"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapMin">Y-axis minimum (optional)</label><input class="form-control" id="chartMapMin" type="number" step="any" placeholder="Auto"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapMax">Y-axis maximum (optional)</label><input class="form-control" id="chartMapMax" type="number" step="any" placeholder="Auto"></div>\n              <div class="col-md-6"><label class="form-label" for="chartMapNumberFormat">Number format</label><select class="form-select" id="chartMapNumberFormat"><option value="auto">Automatic</option><option value="number">Thousands separators</option><option value="compact">Compact (K/M)</option><option value="percent">Percentage</option></select></div>
              <div class="col-12"><div class="small" id="chartMapValidation" role="status" aria-live="polite"></div></div>
            </div>
          </section>`;
        wrapper.parentNode.insertBefore(panel, wrapper);
        panel.querySelectorAll("input,select,textarea").forEach(control => {
            control.addEventListener("input", syncToJson);
            control.addEventListener("change", syncToJson);
        });
        byId("chartMapDiscover").addEventListener("click", discoverSample);
        byId("chartMapLoadLatest").addEventListener("click", loadLatestSample);
        byId("chartMapRowPath").addEventListener("change", function () {
            populateFieldSelectors(parseConfig(), true);
            renderSeriesRows(parseConfig().series || []);
            syncToJson();
        });
        byId("chartMapSeriesRows").addEventListener("change", function () {
            updateEnableAllControl();
            syncToJson();
        });
        byId("chartMapSeriesRows").addEventListener("input", function () {
            updateEnableAllControl();
            syncToJson();
        });
        byId("chartMapEnableAll").addEventListener("change", function () {
            byId("chartMapSeriesRows").querySelectorAll(".chart-map-series-enabled").forEach(control => {
                control.checked = byId("chartMapEnableAll").checked;
            });
            syncToJson();
        });
    }

    function chartMessage(message, error) {
        const node = byId("chartMapSampleMessage");
        if (!node) return;
        node.textContent = message;
        node.classList.toggle("text-danger", !!error);
        node.classList.toggle("text-muted", !error);
    }

    function discoverSample() {
        try {
            const raw = byId("chartMapSampleJson").value.trim();
            if (!raw) throw new Error("Paste a sample JSON response or load the latest job output first.");
            const sample = JSON.parse(raw);
            if (!sample || typeof sample !== "object") throw new Error("The sample must be a JSON object or array.");
            const collections = [];
            function visit(value, path, depth) {
                if (depth > 12 || value === null || typeof value !== "object") return;
                if (Array.isArray(value)) {
                    const rows = value.filter(item => item && typeof item === "object" && !Array.isArray(item));
                    if (rows.length) collections.push({ path, rows, label: path || "(root array)" });
                    return;
                }
                Object.keys(value).forEach(key => visit(value[key], path ? path + "." + key : key, depth + 1));
            }
            if (Array.isArray(sample)) {
                const rows = sample.filter(item => item && typeof item === "object" && !Array.isArray(item));
                if (rows.length) collections.push({ path: "", rows, label: "(root array)" });
            } else collections.push({ path: "", rows: [sample], label: "(root object)" });
            visit(sample, "", 0);
            sampleState.collections = Array.from(new Map(collections.map(item => [item.path, item])).values());
            if (!sampleState.collections.length) throw new Error("No array of records or object fields were found.");
            const config = parseConfig();
            const rowSelect = byId("chartMapRowPath");
            rowSelect.innerHTML = sampleState.collections.map(item => '<option value="' + esc(item.path) + '">' + esc(item.label) + ' (' + item.rows.length + ' rows)</option>').join("");
            rowSelect.disabled = false;
            rowSelect.value = sampleState.collections.some(item => item.path === config.rowPath)
                ? (config.rowPath || "") : (sampleState.collections.find(item => item.path) || sampleState.collections[0]).path;
            populateFieldSelectors(config, false);
            renderSeriesRows(Array.isArray(config.series) ? config.series :
                (config.valueField ? [{ label: config.title || humanize(config.valueField), valueField: config.valueField }] : []));
            chartMessage("Discovered " + sampleState.collections.length + " row collection(s). Choose fields below.", false);
            syncToJson();
        } catch (error) {
            chartMessage(error.message || "Unable to discover sample fields.", true);
        }
    }

    function populateFieldSelectors(config, preserve) {
        const collection = sampleState.collections.find(item => item.path === byId("chartMapRowPath").value);
        if (!collection) return;
        const discovered = new Map();
        function visit(value, prefix, depth) {
            if (depth > 8 || value === null || value === undefined || Array.isArray(value)) return;
            if (typeof value === "object") {
                Object.keys(value).forEach(key => visit(value[key], prefix ? prefix + "." + key : key, depth + 1));
            } else if (prefix) {
                const item = discovered.get(prefix) || { path: prefix, numeric: false };
                if (typeof value === "number" || (typeof value === "string" && value.trim() && Number.isFinite(Number(value.replace(/,/g, "")))) ) item.numeric = true;
                discovered.set(prefix, item);
            }
        }
        collection.rows.slice(0, 100).forEach(row => visit(row, "", 0));
        const fields = Array.from(discovered.values()).sort((a, b) => a.path.localeCompare(b.path));
const configCategory = preserve ? byId("chartMapCategory").value : (config.categoryField || config.labelField || "");
        function fill(id, values, selected, emptyLabel) {
            const select = byId(id);
            select.innerHTML = '<option value="">' + emptyLabel + '</option>' + values.map(field =>
                '<option value="' + esc(field.path) + '"' + (field.path === selected ? ' selected' : '') + '>' +
                esc(field.path + (field.numeric ? " (number)" : "")) + '</option>').join("");
            select.disabled = values.length === 0;
            if (selected && !values.some(field => field.path === selected)) {
                select.insertAdjacentHTML("beforeend", '<option value="' + esc(selected) + '" selected>' + esc(selected + " (not in sample)") + '</option>');
                select.value = selected;
            }
        }
        fill("chartMapCategory", fields, configCategory, "Select category field");
        sampleState.numericFields = fields.filter(field => field.numeric);
        if (!byId("chartMapCategory").value && fields.length) byId("chartMapCategory").value = fields[0].path;
    }

    function humanize(value) {
        return String(value || "").replace(/([a-z0-9])([A-Z])/g, "$1 $2")
            .replace(/[._-]+/g, " ").replace(/^./, c => c.toUpperCase());
    }

    function readSeriesRows() {
        return Array.from(byId("chartMapSeriesRows").querySelectorAll("[data-chart-series-row]"))
            .map(row => {
                const valueField = row.getAttribute("data-field");
                const label = row.querySelector(".chart-map-series-label").value.trim() || humanize(valueField);
                const color = row.querySelector(".chart-map-series-color").value;
                const item = { label, valueField, enabled: row.querySelector(".chart-map-series-enabled").checked };
                if (color) item.color = color;
                return item;
            });
    }

    function updateEnableAllControl() {
        const master = byId("chartMapEnableAll");
        const controls = Array.from(byId("chartMapSeriesRows").querySelectorAll(".chart-map-series-enabled"));
        if (!master) return;
        master.checked = controls.length > 0 && controls.every(control => control.checked);
        master.indeterminate = controls.some(control => control.checked) && !master.checked;
        master.disabled = controls.length === 0;
    }

    function renderSeriesRows(series) {
        const host = byId("chartMapSeriesRows");
        if (!host) return;
        const savedSeries = Array.isArray(series) ? series : [];
        const savedByField = new Map(savedSeries.map(item => [String(item.valueField || item.field || ""), item]));
        const fields = sampleState.numericFields || [];
        const allFields = [
            ...fields,
            ...savedSeries.filter(item => {
                const field = String(item.valueField || item.field || "");
                return field && !fields.some(candidate => candidate.path === field);
            }).map(item => ({ path: String(item.valueField || item.field), numeric: true, savedOnly: true }))
        ];
        host.innerHTML = allFields.map(fieldInfo => {
            const field = String(fieldInfo.path);
            const saved = savedByField.get(field);
            const label = saved ? (saved.label || saved.name || humanize(field)) : humanize(field);
            const color = /^#[0-9a-f]{6}$/i.test(String(saved?.color || "")) ? saved.color : "#0d6efd";
            const enabled = saved ? saved.enabled !== false : true;
            return '<tr data-chart-series-row data-field="' + esc(field) + '">' +
                '<td><input class="form-check-input chart-map-series-enabled" type="checkbox" aria-label="Enable ' + esc(field) + '"' + (enabled ? ' checked' : '') + '></td>' +
                '<td><code>' + esc(field) + (fieldInfo.savedOnly ? ' <span class="text-muted">(not in sample)</span>' : '') + '</code></td>' +
                '<td><input class="form-control form-control-sm chart-map-series-label" maxlength="120" value="' + esc(label) + '" aria-label="Series label for ' + esc(field) + '"></td>' +
                '<td><input class="form-control form-control-sm chart-map-series-color" type="color" value="' + esc(color) + '" aria-label="Line colour for ' + esc(field) + '"></td>' +
                '</tr>';
        }).join("") || '<tr><td colspan="4" class="text-muted small p-3">No numeric fields were discovered in this collection.</td></tr>';
        const enableAll = byId("chartMapEnableAll");
        if (enableAll) {
            const controls = Array.from(host.querySelectorAll(".chart-map-series-enabled"));
            enableAll.checked = controls.length > 0 && controls.every(control => control.checked);
            enableAll.indeterminate = controls.some(control => control.checked) && !enableAll.checked;
            enableAll.disabled = controls.length === 0;
        }
    }

    async function loadLatestSample() {
        const jobId = byId("dashboardWidgetJobSourceId")?.value;
        if (!jobId) { chartMessage("Select a Monitoring Job before loading its latest output.", true); return; }
        chartMessage("Loading the latest saved result…", false);
        try {
            const response = await fetch("/administration/dashboard/widgets/source-sample?jobId=" + encodeURIComponent(jobId), {
                method: "GET", headers: { "Accept": "application/json" }, credentials: "same-origin"
            });
            if (!response.ok) throw new Error("Unable to load the latest result (HTTP " + response.status + ").");
            const result = await response.json();
            const candidates = [result?.rawOutput, result?.resultData, result?.output, result?.responseBody, result?.payload, result?.data];
            let output = "";
            for (const candidate of candidates) {
                if (typeof candidate === "string" && candidate.trim()) { output = candidate; break; }
                if (candidate && typeof candidate === "object") { output = JSON.stringify(candidate, null, 2); if (output) break; }
            }
            if (!output.trim()) throw new Error(result?.message || "No saved output is available. Run the job or paste a sample response.");
            byId("chartMapSampleJson").value = output;
            discoverSample();
            if (result.createdAt) chartMessage("Loaded saved output from " + result.createdAt + ".", false);
        } catch (error) { chartMessage(error.message || "Unable to load latest output.", true); }
    }

    function readUi(config) {
        if (sampleState.collections.length) {
            const mapping = {
                rowPath: byId("chartMapRowPath").value,
                categoryField: byId("chartMapCategory").value,
};
            Object.keys(mapping).forEach(key => {
                if (mapping[key]) config[key] = mapping[key];
                else delete config[key];
            });
        }
        const title = byId("chartMapTitle").value.trim();
        if (title) config.title = title; else delete config.title;
        config.legendPosition = byId("chartMapLegend").value;
        config.xAxisTitle = byId("chartMapXAxis").value.trim();
        config.yAxisTitle = byId("chartMapYAxis").value.trim();
        ["xAxisTitle", "yAxisTitle"].forEach(key => { if (!config[key]) delete config[key]; });
        const min = byId("chartMapMin").value.trim();
        const max = byId("chartMapMax").value.trim();
        if (min === "") delete config.yAxisMin; else config.yAxisMin = Number(min);
        if (max === "") delete config.yAxisMax; else config.yAxisMax = Number(max);
        if (min !== "" && !Number.isFinite(config.yAxisMin)) throw new Error("Y-axis minimum must be a valid number.");
        if (max !== "" && !Number.isFinite(config.yAxisMax)) throw new Error("Y-axis maximum must be a valid number.");
        if (min !== "" && max !== "" && config.yAxisMin > config.yAxisMax) throw new Error("Y-axis minimum must not exceed its maximum.");
        config.numberFormat = byId("chartMapNumberFormat").value || "auto";
        const series = readSeriesRows();
        if (series.some(item => !item.label || !item.valueField)) throw new Error("Each enabled chart series requires a numeric field and label.");
        config.series = series;
        delete config.valueField;
        delete config.seriesField;
        return config;
    }

    function syncToJson() {
        if (syncing) return;
        const message = byId("chartMapValidation");
        try {
            const config = readUi(parseConfig());
            syncing = true;
            byId(fieldId).value = JSON.stringify(config);
            byId(fieldId).dispatchEvent(new Event("input", { bubbles: true }));
            message.className = "small text-success";
            message.textContent = "Chart settings are valid.";
        } catch (error) {
            message.className = "small text-danger";
            message.textContent = error.message || "Invalid chart configuration.";
        } finally {
            syncing = false;
        }
    }

    function loadFromJson() {
        const config = parseConfig();
        const ids = {
            chartMapTitle: "title", chartMapLegend: "legendPosition",
            chartMapXAxis: "xAxisTitle", chartMapYAxis: "yAxisTitle",
            chartMapMin: "yAxisMin", chartMapMax: "yAxisMax"
        };
        Object.entries(ids).forEach(([id, key]) => {
            const control = byId(id);
            if (control) control.value = config[key] ?? (id === "chartMapLegend" ? "bottom" : "");
        });
byId("chartMapNumberFormat").value = config.numberFormat || "auto";
        if (sampleState.collections.length) {
            byId("chartMapRowPath").value = config.rowPath || "";
            populateFieldSelectors(config, false);
        }
        renderSeriesRows(Array.isArray(config.series) ? config.series :
            (config.valueField ? [{ label: config.title || humanize(config.valueField), valueField: config.valueField }] : []));
    }

    function updateVisibility() {
        const isChart = String(byId(typeId)?.value || "").toUpperCase() === "CHART";
        const panel = byId("chartWidgetConfigurator");
        if (panel) panel.classList.toggle("d-none", !isChart);
        const group = byId("dashboardWidgetChartTypeGroup");
        if (group) group.classList.toggle("d-none", !isChart);
        const genericMapping = byId("widgetTypeMappingOptions");
        if (genericMapping && isChart) genericMapping.hidden = true;
    }

    function initialize() {
        if (!byId(fieldId)) return;
        ensurePanel();
        if (!byId("chartWidgetConfigurator")) return;
        loadFromJson();
        updateVisibility();
        byId(typeId)?.addEventListener("change", updateVisibility);
        byId(chartTypeId)?.addEventListener("change", syncToJson);
        byId("dashboardWidgetModal")?.addEventListener("shown.bs.modal", function () {
            loadFromJson();
            updateVisibility();
            if (String(byId(typeId)?.value || "").toUpperCase() === "CHART" &&
                !byId("chartMapSampleJson").value.trim() && byId("dashboardWidgetJobSourceId")?.value) loadLatestSample();
        });
        byId("dashboardWidgetJobSourceId")?.addEventListener("change", function () {
            if (String(byId(typeId)?.value || "").toUpperCase() === "CHART") {
                byId("chartMapSampleJson").value = "";
                sampleState.collections = [];
                sampleState.numericFields = [];
                byId("chartMapSeriesRows").innerHTML = '<tr><td colspan="4" class="text-muted small p-3">Discover fields to configure chart series.</td></tr>';
                loadFromJson();
            }
        });
        const form = byId(fieldId).closest("form");
        form?.addEventListener("submit", function (event) {
            try {
                const config = readUi(parseConfig());
                byId(fieldId).value = JSON.stringify(config);
            } catch (error) {
                event.preventDefault();
                const message = byId("chartMapValidation");
                message.className = "small text-danger";
                message.textContent = error.message || "Correct the chart configuration before saving.";
            }
        });
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", initialize);
    else initialize();
})();