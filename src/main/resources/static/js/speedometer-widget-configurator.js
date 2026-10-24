(function () {
    "use strict";

    const $ = id => document.getElementById(id);
    const ids = {
        type: "dashboardWidgetType",
        json: "dashboardWidgetFieldConfigJson",
        job: "dashboardWidgetJobSourceId",
        panel: "dashboardWidgetSpeedometerConfig",
        sample: "dashboardWidgetSpeedometerSample",
        field: "dashboardWidgetSpeedometerValueField",
        select: "dashboardWidgetSpeedometerFieldSelect",
        minimum: "dashboardWidgetSpeedometerMinimum",
        maximum: "dashboardWidgetSpeedometerMaximum",
        unit: "dashboardWidgetSpeedometerUnit",
        decimals: "dashboardWidgetSpeedometerDecimals",
        label: "dashboardWidgetSpeedometerLabel",
        direction: "dashboardWidgetSpeedometerDirection",
        warning: "dashboardWidgetSpeedometerWarning",
        critical: "dashboardWidgetSpeedometerCritical",
        normalColor: "dashboardWidgetSpeedometerNormalColor",
        warningColor: "dashboardWidgetSpeedometerWarningColor",
        criticalColor: "dashboardWidgetSpeedometerCriticalColor",
        message: "dashboardWidgetSpeedometerMessage"
    };

    function readConfig() {
        try {
            const value = JSON.parse($(ids.json)?.value || "{}");
            return value && typeof value === "object" && !Array.isArray(value) ? value : {};
        } catch (error) {
            return {};
        }
    }

    function message(text, isError) {
        const target = $(ids.message);
        if (!target) return;
        target.textContent = text || "";
        target.className = "small align-self-center " + (isError ? "text-danger" : "text-muted");
    }

    function setValue(id, value, fallback) {
        const element = $(id);
        if (element) element.value = value === undefined || value === null ? fallback : String(value);
    }

    function loadFromJson() {
        const config = readConfig();
        setValue(ids.field, config.valueField, "");
        setValue(ids.minimum, config.minimum, 0);
        setValue(ids.maximum, config.maximum, 100);
        setValue(ids.unit, config.unit, "");
        setValue(ids.decimals, config.decimalPlaces, 0);
        setValue(ids.label, config.valueLabel, "");
        setValue(ids.direction, config.thresholdDirection, "HIGH");
        setValue(ids.warning, config.warningThreshold, 70);
        setValue(ids.critical, config.criticalThreshold, 90);
        setValue(ids.normalColor, config.normalColor, "");
        setValue(ids.warningColor, config.warningColor, "");
        setValue(ids.criticalColor, config.criticalColor, "");
        const selected = $(ids.select);
        if (selected) selected.value = config.valueField || "";
        updateVisibility();
    }

    function updateVisibility() {
        const active = String($(ids.type)?.value || "").toUpperCase() === "SPEEDOMETER";
        $(ids.panel)?.classList.toggle("d-none", !active);
    }

    function numericFields(root) {
        const found = new Map();
        const seen = new Set();
        function walk(value, path, depth) {
            if (depth > 12 || value === null || value === undefined) return;
            if (typeof value === "number" && Number.isFinite(value)) {
                if (path) found.set(path, path);
                return;
            }
            if (typeof value === "string") {
                const trimmed = value.trim();
                if (path && trimmed && /^[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:e[-+]?\\d+)?$/i.test(trimmed)) found.set(path, path);
                return;
            }
            if (typeof value !== "object" || seen.has(value)) return;
            seen.add(value);
            if (Array.isArray(value)) {
                if (value.length && value[0] && typeof value[0] === "object") walk(value[0], path ? path + ".0" : "0", depth + 1);
                return;
            }
            Object.keys(value).forEach(key => walk(value[key], path ? path + "." + key : key, depth + 1));
        }
        walk(root, "", 0);
        return Array.from(found.keys()).sort((a, b) => a.localeCompare(b));
    }

    function discover() {
        const sample = $(ids.sample)?.value || "";
        const select = $(ids.select);
        if (!select) return;
        let parsed;
        try {
            parsed = JSON.parse(sample);
        } catch (error) {
            message("Enter a valid JSON sample response first.", true);
            return;
        }
        const fields = numericFields(parsed);
        const previous = $(ids.field)?.value || select.value;
        select.replaceChildren(new Option("Select a numeric field", ""));
        fields.forEach(field => select.add(new Option(field, field)));
        if (previous && !fields.includes(previous)) select.add(new Option(previous + " (saved mapping)", previous));
        if (previous) select.value = previous;
        if (select.value) $(ids.field).value = select.value;
        message(fields.length ? "Discovered " + fields.length + " numeric field(s)." : "No numeric fields found in this sample.", !fields.length);
    }

    async function loadLatestSample() {
        const jobId = $(ids.job)?.value;
        if (!jobId) {
            message("Select a Monitoring Job before loading its latest output.", true);
            return;
        }
        message("Loading latest saved output…", false);
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
                if (candidate && typeof candidate === "object") { output = JSON.stringify(candidate, null, 2); break; }
            }
            if (!output.trim()) throw new Error(result?.message || "No saved output is available. Run the job or paste a sample response.");
            $(ids.sample).value = output;
            discover();
            message(result.createdAt ? "Loaded saved output from " + result.createdAt + "." : "Latest saved output loaded.", false);
        } catch (error) {
            message(error.message || "Unable to load latest output.", true);
        }
    }

    function syncToJson() {
        const config = readConfig();
        config.valueField = ($(ids.field)?.value || "").trim();
        config.minimum = Number($(ids.minimum)?.value);
        config.maximum = Number($(ids.maximum)?.value);
        config.unit = $(ids.unit)?.value || "";
        config.decimalPlaces = Number($(ids.decimals)?.value || 0);
        config.valueLabel = ($(ids.label)?.value || "").trim();
        config.thresholdDirection = $(ids.direction)?.value || "HIGH";
        config.warningThreshold = Number($(ids.warning)?.value);
        config.criticalThreshold = Number($(ids.critical)?.value);
        config.normalColor = ($(ids.normalColor)?.value || "").trim() || null;
        config.warningColor = ($(ids.warningColor)?.value || "").trim() || null;
        config.criticalColor = ($(ids.criticalColor)?.value || "").trim() || null;
        $(ids.json).value = JSON.stringify(config, null, 2);
    }

    function initialize() {
        if (!$(ids.json) || !$(ids.type) || !$(ids.panel)) return;
        loadFromJson();
        $(ids.type).addEventListener("change", updateVisibility);
        $(ids.select)?.addEventListener("change", function () {
            $(ids.field).value = this.value;
            syncToJson();
        });
        $(ids.field)?.addEventListener("input", function () {
            if ($(ids.select).value !== this.value) $(ids.select).value = "";
            syncToJson();
        });
        [ids.minimum, ids.maximum, ids.unit, ids.decimals, ids.label, ids.direction, ids.warning, ids.critical, ids.normalColor, ids.warningColor, ids.criticalColor]
            .forEach(id => $(id)?.addEventListener("change", syncToJson));
        $(ids.direction)?.addEventListener("change", function () {
            const warning = Number($(ids.warning).value);
            const critical = Number($(ids.critical).value);
            if (Number.isFinite(warning) && Number.isFinite(critical)
                    && ((this.value === "HIGH" && warning > critical)
                        || (this.value === "LOW" && warning < critical))) {
                $(ids.warning).value = critical;
                $(ids.critical).value = warning;
            }
            syncToJson();
        });
        $("dashboardWidgetSpeedometerDiscover")?.addEventListener("click", discover);
        $("dashboardWidgetSpeedometerLoadSample")?.addEventListener("click", loadLatestSample);
        $("dashboardWidgetModal")?.addEventListener("shown.bs.modal", loadFromJson);
        $(ids.job)?.addEventListener("change", function () {
            if (String($(ids.type).value || "").toUpperCase() === "SPEEDOMETER") {
                $(ids.sample).value = "";
                $(ids.select).replaceChildren(new Option("Discover fields or enter a path below", ""));
            }
        });
        $(ids.json).closest("form")?.addEventListener("submit", function (event) {
            if (String($(ids.type).value || "").toUpperCase() !== "SPEEDOMETER") return;
            const field = ($(ids.field).value || "").trim();
            const min = Number($(ids.minimum).value);
            const max = Number($(ids.maximum).value);
            const warning = Number($(ids.warning).value);
            const critical = Number($(ids.critical).value);
            if (!field) {
                event.preventDefault();
                message("Select or enter a numeric value field path.", true);
                $(ids.field).focus();
                return;
            }
            if (!Number.isFinite(min) || !Number.isFinite(max) || max <= min) {
                event.preventDefault();
                message("Maximum must be a number greater than minimum.", true);
                $(ids.maximum).focus();
                return;
            }
            if (!Number.isFinite(warning) || !Number.isFinite(critical)) {
                event.preventDefault();
                message("Warning and critical thresholds must be numeric.", true);
                return;
            }
            if (String($(ids.direction).value) === "HIGH" ? warning > critical : warning < critical) {
                event.preventDefault();
                message("For this threshold direction, warning must be less severe than critical.", true);
                $(ids.warning).focus();
                return;
            }
            syncToJson();
        });
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", initialize);
    else initialize();
})();
