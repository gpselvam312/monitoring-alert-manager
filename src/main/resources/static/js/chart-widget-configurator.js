(function () {
    "use strict";

    const byId = id => document.getElementById(id);
    const fieldId = "dashboardWidgetFieldConfigJson";
    const typeId = "dashboardWidgetType";
    const chartTypeId = "dashboardWidgetChartType";
    let syncing = false;

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
            <div class="row g-3">
              <div class="col-md-6"><label class="form-label" for="chartMapRowPath">Data rows path</label><input class="form-control" id="chartMapRowPath" placeholder="data.metrics"><div class="form-text">Path to an array of records. Leave blank for a root array.</div></div>
              <div class="col-md-6"><label class="form-label" for="chartMapCategory">Category / label path</label><input class="form-control" id="chartMapCategory" placeholder="name"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapValue">Numeric value path</label><input class="form-control" id="chartMapValue" placeholder="value"></div>
              <div class="col-md-6"><label class="form-label" for="chartMapSeries">Group into series by (optional)</label><input class="form-control" id="chartMapSeries" placeholder="cluster"></div>
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
            chartMapRowPath: "rowPath", chartMapCategory: "categoryField",
            chartMapValue: "valueField", chartMapSeries: "seriesField",
            chartMapTitle: "title", chartMapLegend: "legendPosition",
            chartMapXAxis: "xAxisTitle", chartMapYAxis: "yAxisTitle",
            chartMapMin: "yAxisMin", chartMapMax: "yAxisMax"
        };
        Object.entries(ids).forEach(([id, key]) => {
            const control = byId(id);
            if (control) control.value = config[key] ?? (id === "chartMapLegend" ? "bottom" : "");
        });
        byId("chartMapSeriesJson").value = Array.isArray(config.series) ? JSON.stringify(config.series, null, 2) : "";\n        byId("chartMapNumberFormat").value = config.numberFormat || "auto";
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