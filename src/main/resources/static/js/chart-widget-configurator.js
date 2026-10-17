(function () {
    "use strict";

    const byId = id => document.getElementById(id);
    const fieldId = "dashboardWidgetFieldConfigJson";
    const typeId = "dashboardWidgetType";
    const chartTypeId = "dashboardWidgetChartType";
    let syncing = false;
    const sampleState = { collections: [] };

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
              <div class="col-md-6"><label class="form-label" for="chartMapValue">Numeric value field</label><select class="form-select" id="chartMapValue" disabled><option value="">Discover fields first</option></select></div>
              <div class="col-md-6"><label class="form-label" for="chartMapSeries">Group into series by (optional)</label><select class="form-select" id="chartMapSeries" disabled><option value="">No grouping</option></select></div>
              <div class="col-md-6"><label class="form-label" for="chartMapTitle">Chart title</label><input class="form-control" id="chartMapTitle" placeholder="Monitoring metrics"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapLegend">Legend position</label><select class="form-select" id="chartMapLegend"><option value="top">Top</option><option value="bottom">Bottom</option><option value="left">Left</option><option value="right">Right</option><option value="none">Hidden</option></select></div>
              <div class="col-md-6"><label class="form-label" for="chartMapXAxis">X-axis title</label><input class="form-control" id="chartMapXAxis" placeholder="Category"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapYAxis">Y-axis title</label><input class="form-control" id="chartMapYAxis" placeholder="Value"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapMin">Y-axis minimum (optional)</label><input class="form-control" id="chartMapMin" type="number" step="any" placeholder="Auto"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapMax">Y-axis maximum (optional)</label><input class="form-control" id="chartMapMax" type="number" step="any" placeholder="Auto"></div>\n              <div class="col-md-6"><label class="form-label" for="chartMapNumberFormat">Number format</label><select class="form-select" id="chartMapNumberFormat"><option value="auto">Automatic</option><option value="number">Thousands separators</option><option value="compact">Compact (K/M)</option><option value="percent">Percentage</option></select></div>
              <div class="col-12"><label class="form-label" for="chartMapSeriesJson">Explicit series (optional JSON array)</label><textarea class="form-control font-monospace" id="chartMapSeriesJson" rows="3" placeholder='[{"label":"Healthy","valueField":"healthy","color":"#198754"},{"label":"Warning","valueField":"warning","color":"#ffc107"}]'></textarea><div class="form-text">Use this for separate numeric fields per series. Each item supports label, valueField and optional color. When configured, seriesField is not used.</div></div>
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
        const configValue = preserve ? byId("chartMapValue").value : (config.valueField || "");
        const configCategory = preserve ? byId("chartMapCategory").value : (config.categoryField || config.labelField || "");
        const configSeries = preserve ? byId("chartMapSeries").value : (config.seriesField || "");
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
        fill("chartMapValue", fields.filter(field => field.numeric), configValue, "Select numeric value field");
        fill("chartMapSeries", fields, configSeries, "No grouping");
        if (!byId("chartMapCategory").value && fields.length) byId("chartMapCategory").value = fields[0].path;
        if (!byId("chartMapValue").value && fields.some(field => field.numeric)) byId("chartMapValue").value = fields.find(field => field.numeric).path;
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
        const mapping = {
            rowPath: byId("chartMapRowPath").value.trim(),
            categoryField: byId("chartMapCategory").value.trim(),
            valueField: byId("chartMapValue").value.trim(),
            seriesField: byId("chartMapSeries").value.trim()
        };
        Object.keys(mapping).forEach(key => {
            if (mapping[key]) config[key] = mapping[key];
            else delete config[key];
        });
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
        config.numberFormat = byId("chartMapNumberFormat").value || "auto";\n        const seriesText = byId("chartMapSeriesJson").value.trim();
        if (seriesText) {
            const parsed = JSON.parse(seriesText);
            if (!Array.isArray(parsed) || !parsed.every(item => item && typeof item.label === "string" && typeof item.valueField === "string")) {
                throw new Error("Explicit series must be an array of objects with label and valueField.");
            }
            config.series = parsed;
        } else delete config.series;
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
        byId("chartMapSeriesJson").value = Array.isArray(config.series) ? JSON.stringify(config.series, null, 2) : "";\n        byId("chartMapNumberFormat").value = config.numberFormat || "auto";
        if (sampleState.collections.length) {
            byId("chartMapRowPath").value = config.rowPath || "";
            populateFieldSelectors(config, false);
        }
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