(function ()
{
    "use strict";


    /*
     * ------------------------------------------------------------
     * CONFIGURATION
     * ------------------------------------------------------------
     */

    const apiUrl = "/api/dashboard";
    const applicationsApiUrl = "/api/dashboard/applications";
    const environmentsApiUrl = "/api/dashboard/environments";

    const APPLICATIONS_CACHE_KEY = "mam.dashboard.applications.v1";
    const ENVIRONMENTS_CACHE_PREFIX = "mam.dashboard.environments.v1:";

    function readSessionCache(key)
    {
        try
        {
            const value = sessionStorage.getItem(key);
            if (!value) return null;
            const parsed = JSON.parse(value);
            return Array.isArray(parsed) ? parsed : null;
        }
        catch (exception)
        {
            return null;
        }
    }

    function writeSessionCache(key, value)
    {
        try { sessionStorage.setItem(key, JSON.stringify(value)); }
        catch (exception) { /* Continue if browser storage is unavailable or full. */ }
    }



    /*
     * ------------------------------------------------------------
     * DOM ELEMENTS
     * ------------------------------------------------------------
     */

    const loadingElement =
        document.getElementById("dashboardLoading");

    const errorElement =
        document.getElementById("dashboardError");

    const emptyElement =
        document.getElementById("dashboardEmpty");

    const containerElement =
        document.getElementById("dashboardContainer");

    const tabsElement =
        document.getElementById("dashboardTabs");

    const contentElement =
        document.getElementById("dashboardContent");

    const errorMessageElement =
        document.getElementById("dashboardErrorMessage");

    const refreshButton =
        document.getElementById("dashboardRefreshButton");

    const retryButton =
        document.getElementById("dashboardRetryButton");

    const dateRangeContainer = document.getElementById("dashboardDateRange");
    const dateRangePreset = document.getElementById("dashboardDateRangePreset");
    const dateRangeFrom = document.getElementById("dashboardDateRangeFrom");
    const dateRangeTo = document.getElementById("dashboardDateRangeTo");
    const dateRangeFromGroup = document.getElementById("dashboardDateRangeFromGroup");
    const dateRangeToGroup = document.getElementById("dashboardDateRangeToGroup");
    const dateRangeApplyButton = document.getElementById("dashboardDateRangeApply");
    const dateRangeMessage = document.getElementById("dashboardDateRangeMessage");
    const applicationFilter = document.getElementById("dashboardApplicationFilter");
    const environmentFilter = document.getElementById("dashboardEnvironmentFilter");


    /*
     * ------------------------------------------------------------
     * STATE
     * ------------------------------------------------------------
     */

    let dashboardData = [];
    let activeTabId = null;
    const serverHealthState = new Map();
    const autoRefreshTimers = new Map();

    /*
     * Keep references to Chart.js instances.
     *
     * Key   = canvas id
     * Value = Chart.js instance
     */
    const dashboardCharts = new Map();
    let dateRangeEventsBound = false;
    let dateRangeInitialized = false;
    let pageIsUnloading = false;

    window.addEventListener("pagehide", function ()
    {
        pageIsUnloading = true;
        clearAutoRefreshTimers();
    });


    /*
     * ------------------------------------------------------------
     * STATE HANDLING
     * ------------------------------------------------------------
     */

    function showState(state)
    {
        loadingElement.classList.toggle(
            "d-none",
            state !== "loading"
        );

        errorElement.classList.toggle(
            "d-none",
            state !== "error"
        );

        emptyElement.classList.toggle(
            "d-none",
            state !== "empty"
        );

        containerElement.classList.toggle(
            "d-none",
            state !== "content"
        );
    }


    /*
     * ------------------------------------------------------------
     * HTML HELPERS
     * ------------------------------------------------------------
     */

    function escapeHtml(value)
    {
        if (value === null || value === undefined)
        {
            return "";
        }

        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }


    /*
     * ------------------------------------------------------------
     * STATUS
     * ------------------------------------------------------------
     */

    function getStatusClass(status)
    {
        switch (String(status || "").toUpperCase())
        {
            case "GREEN":
            case "HEALTHY":
            case "SUCCESS":
            case "UP":
                return "dashboard-status-green";

            case "YELLOW":
            case "WARNING":
                return "dashboard-status-yellow";

            case "RED":
            case "CRITICAL":
            case "FAILED":
            case "FAILURE":
            case "DOWN":
                return "dashboard-status-red";

            default:
                return "dashboard-status-gray";
        }
    }


    function getStatusLabel(status)
    {
        const normalized =
            String(status || "").toUpperCase();

        switch (normalized)
        {
            case "GREEN":
            case "HEALTHY":
            case "SUCCESS":
            case "UP":
                return "Healthy";

            case "YELLOW":
            case "WARNING":
                return "Warning";

            case "RED":
            case "CRITICAL":
            case "FAILED":
            case "FAILURE":
            case "DOWN":
                return "Critical";

            case "GRAY":
            default:
                return "Unknown";
        }
    }


    function getStatusIcon(status)
    {
        const normalized =
            String(status || "").toUpperCase();

        switch (normalized)
        {
            case "GREEN":
            case "HEALTHY":
            case "SUCCESS":
            case "UP":
                return "bi-check-circle-fill";

            case "YELLOW":
            case "WARNING":
                return "bi-exclamation-triangle-fill";

            case "RED":
            case "CRITICAL":
            case "FAILED":
            case "FAILURE":
            case "DOWN":
                return "bi-x-circle-fill";

            default:
                return "bi-question-circle-fill";
        }
    }


    /*
     * ------------------------------------------------------------
     * GENERAL HELPERS
     * ------------------------------------------------------------
     */

    function formatLastUpdated(value)
    {
        if (!value)
        {
            return "";
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime()))
        {
            return String(value);
        }

        return date.toLocaleString();
    }


    function getWidgetSizeClass(size)
    {
        switch (String(size || "").toUpperCase())
        {
            case "SMALL":
                return "col-12 col-md-6 col-xl-3";

            case "LARGE":
                return "col-12 col-xl-8";

            case "FULL":
                return "col-12";

            case "MEDIUM":
            default:
                return "col-12 col-md-6 col-xl-4";
        }
    }


	function getWidgetIcon(widget)
	{
	    if (widget.icon)
	    {
	        return widget.icon;
	    }

	    switch (
	        String(widget.widgetType || "").toUpperCase()
	    )
	    {
	        case "STAT":
	            return "bi-speedometer2";

	        case "TABLE":
	        case "SUMMARY_TABLE":
	            return "bi-table";

	        case "CHART":
	            return "bi-bar-chart";

	        case "STATUS":
	            return "bi-heart-pulse";

        case "SYSTEM_METRICS":
            return "bi-hdd-rack";

        case "ELASTICSEARCH":
            return "bi-database-check";

        case "IFRAME":
            return "bi-window";


	        case "TEXT":
	            return "bi-card-text";

	        default:
	            return "bi-grid";
	    }
	}


    /*
     * ------------------------------------------------------------
     * CHART.JS HELPERS
     * ------------------------------------------------------------
     */

    function getThemeChartColors()
    {
        const styles =
            getComputedStyle(document.body);

        return [
            styles.getPropertyValue("--theme-primary").trim(),
            styles.getPropertyValue("--success").trim(),
            styles.getPropertyValue("--warning").trim(),
            styles.getPropertyValue("--danger").trim(),
            styles.getPropertyValue("--info").trim(),
            styles.getPropertyValue("--text-secondary").trim()
        ];
    }


    function getChartTheme()
    {
        const styles =
            getComputedStyle(document.body);

        return {
            primary:
                styles.getPropertyValue("--theme-primary").trim(),

            textPrimary:
                styles.getPropertyValue("--text-primary").trim(),

            textSecondary:
                styles.getPropertyValue("--text-secondary").trim(),

            border:
                styles.getPropertyValue("--border-color").trim()
        };
    }


	function normalizeChartType(chartType)
	{
	    switch (String(chartType || "").toUpperCase())
	    {
	        case "LINE":
	            return "line";

	        case "BAR":
	            return "bar";

	        case "PIE":
	            return "pie";

	        case "DONUT":
	            return "doughnut";

	        default:
	            return "bar";
	    }
	}


    function getChartCanvasId(widgetId)
    {
        return "dashboard-chart-" + widgetId;
    }


    function toChartNumber(value)
    {
        if (value === null || value === undefined || String(value).trim() === "") return null;
        const number = typeof value === "number" ? value : Number(String(value).replace(/,/g, "").trim());
        return Number.isFinite(number) ? number : null;
    }


    /*
     * Build BAR / LINE datasets.
     *
     * WidgetDataPoint:
     *
     * {
     *     label,
     *     value,
     *     series
     * }
     */
    function buildChartDatasets(data)
    {
        const palette =
            getThemeChartColors();

        const labels = [];
        const seriesNames = [];
        const values = new Map();


        data.forEach(function (point)
        {
            if (!point)
            {
                return;
            }

            const label =
                point.label === null ||
                point.label === undefined
                    ? ""
                    : String(point.label);


            const series =
                point.series === null ||
                point.series === undefined ||
                String(point.series).trim() === ""
                    ? "Value"
                    : String(point.series);


            if (!labels.includes(label))
            {
                labels.push(label);
            }


            if (!seriesNames.includes(series))
            {
                seriesNames.push(series);
            }


            const key =
                series + "\u0000" + label;


            values.set(
                key,
                toChartNumber(point.value)
            );
        });


        const datasets = seriesNames.map(function (series, index) {
            const seriesPoint = data.find(point => point && String(point.series ?? "Value") === series);
            const color = seriesPoint && seriesPoint.color ? seriesPoint.color : palette[index % palette.length];
            return {
                label: series,
                data: labels.map(function (label) {
                    const key = series + "\u0000" + label;
                    return values.has(key) ? values.get(key) : null;
                }),
                backgroundColor: color, borderColor: color, borderWidth: 2,
                borderRadius: 4, tension: 0.3, pointRadius: 3,
                pointHoverRadius: 5, pointBackgroundColor: color, fill: false
            };
        });


        return {
            labels: labels,
            datasets: datasets
        };
    }


    /*
     * Build PIE data.
     */
    function buildPieChartData(data, config)
    {
        const labels = [];
        const values = [];
        const colors = [];
        const themeColors = getThemeChartColors();
        const colorScheme = String(config && config.pieColorScheme || "categorical").toLowerCase();
        const configuredSeries = Array.isArray(config && config.series) ? config.series : [];
        const valueSeries = configuredSeries.find(function (series) {
            return series && series.valueField === (config && (config.pieValueField || config.valueField));
        });
        const singleColor = valueSeries && valueSeries.color ? valueSeries.color :
            (themeColors[0] || getChartTheme().primary || "#0d6efd");

        (data || []).forEach(function (point, index)
        {
            if (!point) return;
            labels.push(point.label === null || point.label === undefined ? "" : String(point.label));
            values.push(toChartNumber(point.value));
            // Use a category palette for pie slices; numeric series colours are for line/bar series.
            // The golden-angle hue spacing keeps adjacent categories visually distinct and stable.
            colors.push(colorScheme === "single"
                ? singleColor
                : "hsl(" + Math.round((index * 137.508 + 18) % 360) + ", 68%, 52%)");
        });

        const theme = getChartTheme();
        return {
            labels: labels,
            datasets: [{
                label: "Value",
                data: values,
                backgroundColor: colors,
                // Use thin white separators between slices instead of heavy slice outlines.
                borderColor: "#ffffff",
                borderWidth: 1,
                hoverBorderColor: "#ffffff",
                hoverBorderWidth: 1,
                hoverOffset: 8,
                spacing: 0,
                borderAlign: "inner"
            }]
        };
    }


    function createChartOptions(chartType, config)
    {
        const theme = getChartTheme();
        config = config || {};
        const legendPosition = String(config.legendPosition || "bottom").toLowerCase();
        const numberFormat = String(config.numberFormat || "auto").toLowerCase();
        const formatNumber = function (value) {
            const number = Number(value);
            if (!Number.isFinite(number)) return value;
            if (numberFormat === "percent") return number.toLocaleString(undefined, { maximumFractionDigits: 2 }) + "%";
            if (numberFormat === "compact") return new Intl.NumberFormat(undefined, { notation: "compact", maximumFractionDigits: 1 }).format(number);
            if (numberFormat === "number") return number.toLocaleString();
            return String(value);
        };
        const options = {
            responsive: true, maintainAspectRatio: false, animation: { duration: 250 },
            plugins: {
                legend: {
                    display: legendPosition !== "none",
                    position: ["top", "bottom", "left", "right"].includes(legendPosition) ? legendPosition : "bottom",
                    labels: { color: theme.textPrimary, usePointStyle: true, padding: 16 }
                },
                tooltip: {
                    enabled: true,
                    displayColors: true,
                    padding: 12,
                    cornerRadius: 8,
                    callbacks: {
                        label: function (context) {
                            const rawValue = context.parsed && typeof context.parsed.y !== "undefined"
                                ? context.parsed.y : context.raw;
                            if (chartType === "pie" || chartType === "doughnut") {
                                const values = context.dataset && Array.isArray(context.dataset.data) ? context.dataset.data : [];
                                const total = values.reduce(function (sum, value) {
                                    const number = toChartNumber(value);
                                    return sum + (number === null ? 0 : number);
                                }, 0);
                                const numeric = toChartNumber(rawValue);
                                const percentage = numeric !== null && total > 0
                                    ? " (" + (numeric / total * 100).toLocaleString(undefined, { maximumFractionDigits: 1 }) + "%)"
                                    : "";
                                return formatNumber(rawValue) + percentage;
                            }
                            const label = context.dataset && context.dataset.label ? context.dataset.label + ": " : "";
                            return label + formatNumber(rawValue);
                        }
                    }
                }
            }
        };
        if (config.title) options.plugins.title = {
            display: true, text: String(config.title), color: theme.textPrimary,
            padding: { top: 4, bottom: 12 }
        };
        if (chartType === "bar" || chartType === "line") {
            options.interaction = { mode: "index", intersect: false };
            options.scales = {
                x: {
                    title: { display: !!config.xAxisTitle, text: config.xAxisTitle || "", color: theme.textSecondary },
                    ticks: { color: theme.textSecondary }, grid: { color: theme.border }
                },
                y: {
                    beginAtZero: config.yAxisMin === undefined,
                    min: config.yAxisMin === undefined ? undefined : Number(config.yAxisMin),
                    max: config.yAxisMax === undefined ? undefined : Number(config.yAxisMax),
                    title: { display: !!config.yAxisTitle, text: config.yAxisTitle || "", color: theme.textSecondary },
                    ticks: { color: theme.textSecondary, callback: function (value) { return formatNumber(value); } },
                    grid: { color: theme.border }
                }
            };
        }
        return options;
    }


    /*
     * Destroy all existing Chart.js instances.
     *
     * This is important when Refresh is clicked.
     */
    function destroyDashboardCharts()
    {
        dashboardCharts.forEach(function (chart)
        {
            try
            {
                chart.destroy();
            }
            catch (exception)
            {
                console.warn(
                    "Unable to destroy dashboard chart",
                    exception
                );
            }
        });

        dashboardCharts.clear();
    }


    /*
     * Initialize charts after their canvas elements
     * have been added to the DOM.
     */
	function initializeDashboardCharts()
	{
	    if (typeof Chart === "undefined")
	    {
	        console.error(
	            "Chart.js is not loaded."
	        );
	        return;
	    }

	    dashboardData.forEach(function (tab)
	    {
	        if (
	            !tab ||
	            !Array.isArray(tab.widgets)
	        )
	        {
	            return;
	        }

	        tab.widgets.forEach(function (widgetResponse)
	        {
	            if (
	                !widgetResponse ||
	                !widgetResponse.widget
	            )
	            {
	                return;
	            }

	            const widget =
	                widgetResponse.widget;

	            const result =
	                widgetResponse.result || {};

	            const widgetType =
	                String(widget.widgetType || "")
	                    .toUpperCase();
						
				if (widgetType !== "CHART")
				{
				    return;
				}

	            const data = getConfiguredChartData(result, widget);

	            if (data.length === 0)
	            {
	                return;
	            }

	            const canvasId =
	                getChartCanvasId(widget.id);

	            const canvas =
	                document.getElementById(canvasId);

	            if (!canvas)
	            {
	                return;
	            }

	            const chartType =
	                normalizeChartType(
	                    widget.chartType
	                );

	            let chartData;

	            if (chartType === "pie" || chartType === "doughnut")
	            {
	                chartData =
	                    buildPieChartData(data, parseWidgetFieldConfig(widget));
	            }
	            else
	            {
	                chartData =
	                    buildChartDatasets(data);
	            }

	            const config = parseWidgetFieldConfig(widget);
            const chart = new Chart(canvas, {
                type: chartType,
                data: chartData,
                options: createChartOptions(chartType, config)
            });

            dashboardCharts.set(
	                canvasId,
	                chart
	            );
	        });
	    });
	}
	
	function generateRandomChartColors(count)
	{
	    const colors = [];

	    for (let i = 0; i < count; i++)
	    {
	        const hue =
	            Math.floor(
	                Math.random() * 360
	            );

	        const saturation =
	            55 +
	            Math.floor(
	                Math.random() * 20
	            );

	        const lightness =
	            45 +
	            Math.floor(
	                Math.random() * 15
	            );

	        colors.push(
	            `hsl(${hue}, ${saturation}%, ${lightness}%)`
	        );
	    }

	    return colors;
	}


    /*
     * Chart.js calculates the width incorrectly when a chart
     * is initially inside a display:none tab.
     *
     * Resize it when the tab becomes visible.
     */
    function resizeDashboardCharts(tabId)
    {
        dashboardCharts.forEach(function (chart)
        {
            if (!chart.canvas)
            {
                return;
            }


            const pane =
                chart.canvas.closest(
                    ".dashboard-tab-pane"
                );


            if (!pane)
            {
                return;
            }


            if (
                String(pane.dataset.tabId) ===
                String(tabId)
            )
            {
                try
                {
                    chart.resize();
                }
                catch (exception)
                {
                    console.warn(
                        "Unable to resize dashboard chart",
                        exception
                    );
                }
            }
        });
    }


    /*
     * ------------------------------------------------------------
     * TABLE
     * ------------------------------------------------------------
     */

	
    function parseWidgetFieldConfig(widget)
    {
        if (!widget || !widget.fieldConfigJson) return {};
        try
        {
            const config = JSON.parse(widget.fieldConfigJson);
            return config && typeof config === "object" && !Array.isArray(config) ? config : {};
        }
        catch (exception)
        {
            console.warn("Invalid widget field configuration", exception);
            return {};
        }
    }

    function localDateString(date)
    {
        return date.getFullYear() + "-" +
            String(date.getMonth() + 1).padStart(2, "0") + "-" +
            String(date.getDate()).padStart(2, "0");
    }

    function updateDateRangePreset()
    {
        if (!dateRangePreset || !dateRangeFrom || !dateRangeTo) return;

        const custom = dateRangePreset.value === "custom";
        dateRangeFromGroup.classList.toggle("d-none", !custom);
        dateRangeToGroup.classList.toggle("d-none", !custom);
        if (custom) return;

        const today = new Date();
        const from = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        if (dateRangePreset.value === "7days") from.setDate(from.getDate() - 6);
        if (dateRangePreset.value === "30days") from.setDate(from.getDate() - 29);

        dateRangeFrom.value = localDateString(from);
        dateRangeTo.value = localDateString(today);
    }

    function widgetSupportsDateRange(widget)
    {
        const config = parseWidgetFieldConfig(widget);
        return Boolean(config.dateRange && config.dateRange.enabled === true);
    }

    function configureDateRangeControls()
    {
        if (!dateRangeContainer || !dateRangePreset) return;

        const enabled = dashboardData.some(function (tab)
        {
            return Array.isArray(tab.widgets) && tab.widgets.some(function (response)
            {
                return widgetSupportsDateRange(response && response.widget);
            });
        });

        dateRangeContainer.classList.toggle("d-none", !enabled);
        if (!enabled) return;

        if (!dateRangeInitialized)
        {
            dateRangePreset.value = "today";
            updateDateRangePreset();
            dateRangeInitialized = true;
        }

        if (!dateRangeEventsBound)
        {
            dateRangePreset.addEventListener("change", updateDateRangePreset);
            dateRangeApplyButton.addEventListener("click", applyDashboardDateRange);
            dateRangeEventsBound = true;
        }
    }

    async function applyDashboardDateRange()
    {
        if (!dateRangeFrom.value || !dateRangeTo.value)
        {
            dateRangeMessage.textContent = "Select both a start date and an end date.";
            return;
        }

        const startDate = dateRangeFrom.value;
        const endDate = dateRangeTo.value;
        if (startDate > endDate)
        {
            dateRangeMessage.textContent = "Start date must be on or before end date.";
            return;
        }

        const activeButton = tabsElement.querySelector(".dashboard-tab.active");
        const activeTabId = activeButton ? activeButton.dataset.tabId : null;
        const activeTab = dashboardData.find(function (tab)
        {
            return String(tab.id) === String(activeTabId);
        });

        if (!activeTab)
        {
            dateRangeMessage.textContent = "Select an environment tab first.";
            return;
        }

        const widgets = Array.isArray(activeTab.widgets) ? activeTab.widgets : [];
        const uniqueJobs = new Map();
        widgets.forEach(function (response)
        {
            const widget = response && response.widget;
            if (widget && widgetSupportsDateRange(widget)
                    && widget.dataSourceType === "MONITORING_JOB"
                    && widget.dataSourceId !== null && widget.dataSourceId !== undefined)
            {
                uniqueJobs.set(String(widget.dataSourceId), response);
            }
        });

        if (uniqueJobs.size === 0)
        {
            dateRangeMessage.textContent = "No date-range-enabled API widgets are configured for this environment.";
            return;
        }

        dateRangeApplyButton.disabled = true;
        dateRangeMessage.textContent = "Fetching results for " + startDate + " through " + endDate + "...";

        try
        {
            await Promise.all(Array.from(uniqueJobs.values()).map(async function (response)
            {
                const widgetId = response.widget.id;
                const url = "/api/dashboard/widgets/" + encodeURIComponent(widgetId) +
                    "/run?startDate=" + encodeURIComponent(startDate) +
                    "&endDate=" + encodeURIComponent(endDate);
                const result = await fetch(url, {
                    method: "POST",
                    headers: { "Accept": "application/json" },
                    cache: "no-store"
                });

                if (!result.ok)
                {
                    let message = "Unable to fetch date-range results.";
                    try
                    {
                        const errorBody = await result.json();
                        if (errorBody.message) message = errorBody.message;
                    }
                    catch (ignored)
                    {
                        // Keep the generic message.
                    }
                    throw new Error(message);
                }
            }));

            await loadDashboard(activeTabId);
            dateRangeMessage.textContent = "Results updated for " + startDate + " through " + endDate + ".";
        }
        catch (error)
        {
            dateRangeMessage.textContent = error && error.message
                ? error.message
                : "Unable to fetch date-range results.";
        }
        finally
        {
            dateRangeApplyButton.disabled = false;
        }
    }


    function getJsonPath(source, path)
    {
        if (source === null || source === undefined || !path) return undefined;
        const normalizedPath = String(path).replace(/^\$\.?/, "");
        if (!normalizedPath) return source;
        return normalizedPath.split(".").reduce(function (current, part)
        {
            if (current === null || current === undefined) return undefined;
            if (Array.isArray(current) && /^\d+$/.test(part)) return current[Number(part)];
            return current[part];
        }, source);
    }

    function isObjectRow(value)
    {
        return value !== null && typeof value === "object" && !Array.isArray(value);
    }

    function parseJsonPayload(value)
    {
        if (typeof value !== "string") return value;
        const text = value.trim();
        if (!text) return value;
        try { return JSON.parse(text); }
        catch (ignore) { return value; }
    }

    function getConfiguredTableRows(result, config)
    {
        result = result || {};
        // Monitoring outputs are not always normalized identically: some jobs return
        // payload as an object, while others persist the raw JSON as a string.
        let payload = parseJsonPayload(result.payload);
        if (payload === undefined || payload === null)
            payload = parseJsonPayload(result.data);

        if (config.rowPath)
        {
            let configuredRows = getJsonPath(payload, config.rowPath);
            if (!Array.isArray(configuredRows) && payload && typeof payload === "object" &&
                !Array.isArray(payload) && payload.data !== undefined)
            {
                configuredRows = getJsonPath(payload.data, config.rowPath);
            }
            if (Array.isArray(configuredRows)) return configuredRows.filter(isObjectRow);
            // Accept root arrays from PDSH scripts even if an older config specifies rowPath.
            if (Array.isArray(payload)) return payload.filter(isObjectRow);
        }
        if (Array.isArray(payload)) return payload.filter(isObjectRow);
        const existingRows = Array.isArray(result.rows) ? result.rows : [];
        if (existingRows.length > 0) return existingRows;
        if (!payload || typeof payload !== "object") return [];

        const data = Object.prototype.hasOwnProperty.call(payload, "data") ? payload.data : payload;
        if (Array.isArray(data)) return data.filter(isObjectRow);

        if (isObjectRow(data))
        {
            const arrays = Object.values(data).filter(function (value)
            {
                return Array.isArray(value) && value.some(isObjectRow);
            });
            if (arrays.length > 0) return arrays[0].filter(isObjectRow);
        }

        return [];
    }

    function renderSectionHeaderWidget(widget, config)
    {
        const title = typeof config.title === "string" ? config.title.trim() : "";
        const subtitle = typeof config.subtitle === "string" ? config.subtitle.trim() : "";
        const associatedWidgetId = config.associatedWidgetId == null ? "" : String(config.associatedWidgetId);
        const borderClass = config.border === true ? " dashboard-section-header-bordered" : "";
        const sizeClass = getWidgetSizeClass(widget.size);
        return '<div class="dashboard-widget-column ' + sizeClass + ' dashboard-section-header-column" data-widget-id="' +
            escapeHtml(widget.id) + '" data-associated-widget-id="' + escapeHtml(associatedWidgetId) + '">' +
            '<section class="dashboard-section-header' + borderClass + '" aria-label="' + escapeHtml(title || "Section") + '">' +
            '<h2 class="dashboard-section-header-title">' + escapeHtml(title || widget.name || "Section") + '</h2>' +
            (subtitle ? '<p class="dashboard-section-header-subtitle">' + escapeHtml(subtitle) + '</p>' : "") +
            '</section></div>';
    }

    function renderTextWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        const payload = result.payload || {};
        const labels = config.labels && typeof config.labels === "object" ? config.labels : {};

        if (typeof config.content === "string" && config.content.trim())
        {
            return '<div class="dashboard-text-content"><div class="dashboard-text-message">' +
                escapeHtml(config.content).replace(/\n/g, "<br>") +
                '</div></div>';
        }

        if (typeof config.textTemplate === "string" && config.textTemplate.trim())
        {
            const rendered = config.textTemplate.replace(/\{\{\s*([^}]+?)\s*\}\}/g, function (_, path)
            {
                const value = getJsonPath(payload, path.trim());
                if (value === null || value === undefined) return "";
                return typeof value === "object" ? JSON.stringify(value) : String(value);
            });
            return '<div class="dashboard-text-content">' +
                '<div class="dashboard-text-message">' + escapeHtml(rendered).replace(/\n/g, "<br>") +
                '</div></div>';
        }

        if (Array.isArray(config.textFields) && config.textFields.length > 0)
        {
            const fieldsHtml = config.textFields.map(function (path)
            {
                const value = getJsonPath(payload, path);
                const label = labels[path] || path;
                return '<div class="dashboard-status-metric">' +
                    '<span class="dashboard-status-metric-label">' + escapeHtml(label) + '</span>' +
                    '<span class="dashboard-status-metric-value">' +
                    escapeHtml(value === null || value === undefined
                        ? ""
                        : (typeof value === "object" ? JSON.stringify(value) : value)) +
                    '</span></div>';
            }).join("");

            return '<div class="dashboard-text-content dashboard-status-metrics">' + fieldsHtml + '</div>';
        }

        const message = result.message || "";
        return '<div class="dashboard-text-content">' +
            (message
                ? '<div class="dashboard-text-message">' + escapeHtml(message) + '</div>'
                : '<div class="dashboard-text-empty">No text available.</div>') +
            '</div>';
    }


    function getConfiguredChartData(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        const categoryField = config.categoryField || config.labelField;
        const explicitSeries = Array.isArray(config.series) ? config.series : [];
        const enabledSeries = explicitSeries.filter(function (series) { return series && series.enabled !== false; });
        const chartType = normalizeChartType(widget && widget.chartType);

        // Pie and Donut charts represent category aggregates, not one slice per row/series.
        // This prevents duplicate category slices when several records share the same label.
        if ((chartType === "pie" || chartType === "doughnut") && categoryField) {
            const rows = getConfiguredTableRows(result, config);
            if (rows.length) {
                const valueField = config.pieValueField || config.valueField ||
                    (enabledSeries[0] && enabledSeries[0].valueField) || "";
                const aggregation = String(config.pieAggregation || "sum").toLowerCase();
                const groups = new Map();

                rows.forEach(function (row) {
                    const rawLabel = getJsonPath(row, categoryField);
                    if (rawLabel === null || rawLabel === undefined || String(rawLabel).trim() === "") return;
                    const label = String(rawLabel);
                    if (!groups.has(label)) groups.set(label, { label: label, values: [], distinct: new Set(), count: 0 });
                    const group = groups.get(label);
                    group.count++;
                    if (!valueField) return;
                    const rawValue = getJsonPath(row, valueField);
                    if (rawValue === null || rawValue === undefined || String(rawValue).trim() === "") return;
                    if (aggregation === "countdistinct") {
                        group.distinct.add(typeof rawValue === "object" ? JSON.stringify(rawValue) : String(rawValue));
                        return;
                    }
                    if (aggregation === "count") return;
                    const numeric = toChartNumber(rawValue);
                    if (numeric !== null) group.values.push(numeric);
                });

                return Array.from(groups.values()).map(function (group) {
                    let value;
                    switch (aggregation) {
                        case "count": value = group.count; break;
                        case "countdistinct": value = group.distinct.size; break;
                        case "average":
                        case "avg": value = group.values.length
                            ? group.values.reduce((total, number) => total + number, 0) / group.values.length : null; break;
                        case "min": value = group.values.length ? Math.min.apply(null, group.values) : null; break;
                        case "max": value = group.values.length ? Math.max.apply(null, group.values) : null; break;
                        case "sum":
                        default: value = group.values.length
                            ? group.values.reduce((total, number) => total + number, 0) : null; break;
                    }
                    return value === null ? null : { label: group.label, value: value };
                }).filter(Boolean);
            }
        }

        const rows = categoryField && (config.valueField || enabledSeries.length)
            ? getConfiguredTableRows(result, config) : [];

        if (explicitSeries.length && !enabledSeries.length) return [];

        if (rows.length && enabledSeries.length) {
            const mapped = [];
            rows.forEach(function (row) {
                enabledSeries.forEach(function (series) {
                    const numeric = toChartNumber(getJsonPath(row, series.valueField));
                    if (numeric === null) return;
                    mapped.push({ label: getJsonPath(row, categoryField), value: numeric, series: series.label, color: series.color });
                });
            });
            return mapped.filter(point => point.label !== null && point.label !== undefined);
        }

        if (rows.length && categoryField && config.valueField) {
            return rows.map(function (row) {
                return {
                    label: getJsonPath(row, categoryField),
                    value: toChartNumber(getJsonPath(row, config.valueField)),
                    series: config.seriesField ? getJsonPath(row, config.seriesField) : undefined
                };
            }).filter(function (point) {
                return point.label !== null && point.label !== undefined && point.value !== null;
            });
        }

        // Preserve support for previously pre-mapped data, including JSON strings.
        const preMappedData = parseJsonPayload(result.data);
        if (Array.isArray(preMappedData)) {
            return preMappedData.map(function (point) {
                if (!point || point.label === null || point.label === undefined) return null;
                const numeric = toChartNumber(point.value);
                return numeric === null ? null : { label: point.label, value: numeric, series: point.series };
            }).filter(Boolean);
        }
        return [];
    }


    function formatTableValue(value, format)
    {
        if (value === null || value === undefined) return "";
        if (!format) return typeof value === "object" ? JSON.stringify(value) : value;

        const normalizedFormat = String(format).toLowerCase();
        if ((normalizedFormat === "percentage" || normalizedFormat === "percent") && Number.isFinite(Number(value)))
            return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 }) + "%";
        if (normalizedFormat === "number" && Number.isFinite(Number(value)))
            return Number(value).toLocaleString();
        if (normalizedFormat === "datetime") return formatLastUpdated(value);
        if (normalizedFormat === "duration" && Number.isFinite(Number(value)))
        {
            const seconds = Math.max(0, Math.floor(Number(value)));
            const hours = Math.floor(seconds / 3600);
            const minutes = Math.floor((seconds % 3600) / 60);
            const remainingSeconds = seconds % 60;
            return [hours, minutes, remainingSeconds]
                .map(function (part) { return String(part).padStart(2, "0"); })
                .join(":");
        }
        return typeof value === "object" ? JSON.stringify(value) : value;
    }

    function renderSystemMetricsCards(result, widget, config)
    {
        let payload = result && result.payload;
        // Monitoring outputs may be stored as serialized JSON or wrapped under data.
        // Normalize them before applying the configured rowPath.
        if (typeof payload === "string") {
            try { payload = JSON.parse(payload); } catch (ignore) { /* Keep the original value for the error/empty state. */ }
        }
        const payloadObject = payload && typeof payload === "object" ? payload : {};
        const normalizedResult = Object.assign({}, result || {}, { payload: payload });
        const failure = result && (result.success === false || result.ok === false)
            ? (result.error || result.message || "The monitoring script reported a collection failure.")
            : payloadObject.success === false || payloadObject.ok === false || payloadObject.error
                ? (payloadObject.error || payloadObject.message || "The monitoring script reported a collection failure.")
                : null;
        if (failure)
        {
            const message = typeof failure === "string" ? failure : JSON.stringify(failure);
            return '<div class="server-health-empty server-health-collection-error"><i class="bi bi-exclamation-triangle" aria-hidden="true"></i><span>System metrics collection failed: ' + escapeHtml(message) + '</span></div>';
        }

        // The System Metrics contract uses machines[] as its canonical row collection.
        // Prefer it over a stale saved rowPath (for example, an old root-object selection).
        const canonicalMachines = Array.isArray(payloadObject.machines)
            ? payloadObject.machines
            : payloadObject.data && Array.isArray(payloadObject.data.machines)
                ? payloadObject.data.machines
                : null;
        let rows = canonicalMachines
            ? canonicalMachines.filter(isObjectRow)
            : getConfiguredTableRows(normalizedResult, config);
        // Accept common response wrappers when no canonical machines[] array is present.
        if (!rows.length && payloadObject.data && typeof payloadObject.data === "object") {
            rows = getConfiguredTableRows({ payload: payloadObject.data }, config);
        }
        rows = rows.map(function (row) { return Object.assign({}, row); });
        if (!rows.length)
        {
            const jobName = widget.monitoringJobName
                || (result && (result.monitoringJobName || result.jobName
                    || (result.monitoringJob && result.monitoringJob.name)
                    || (result.job && result.job.name)))
                || widget.name
                || "Unknown";
            return '<div class="server-health-empty"><i class="bi bi-question-circle" aria-hidden="true"></i><span>Machine metrics are currently unavailable for monitoring job <strong>' + escapeHtml(jobName) + '</strong>. Please contact your system administrator if the issue persists.</span></div>';
        }

        const state = Object.assign({ status: "ALL", page: 0, groupBy: "", sortBy: "__health", sortDirection: "asc" }, serverHealthState.get(String(widget.id)) || {});
        const columns = config.details && Array.isArray(config.details.columns)
            ? config.details.columns.filter(function (column) { return column && column.field; })
            : [];
        const legacyView = config.view && typeof config.view === "object" ? config.view : {};
        if (legacyView.showSummaryCards === false) state.status = "ALL";

        const metricObject = function (row, field)
        {
            const source = String(field || "");
            if (!source.startsWith("metrics::")) return null;
            const name = source.slice("metrics::".length);
            const metrics = row && row.metrics;
            if (Array.isArray(metrics))
            {
                const metric = metrics.find(function (item) {
                    return item && String(item.name !== undefined ? item.name : item.key) === name;
                });
                return metric && typeof metric === "object" ? metric : null;
            }
            if (metrics && typeof metrics === "object")
            {
                const metric = metrics[name];
                return metric && typeof metric === "object" ? metric : null;
            }
            return null;
        };

        const valueOf = function (row, field)
        {
            const source = String(field || "");
            if (source.startsWith("metrics::"))
            {
                const metric = metricObject(row, source);
                return metric ? metric.value : undefined;
            }
            return getJsonPath(row, source);
        };

        const number = function (value)
        {
            if (typeof value === "number") return Number.isFinite(value) ? value : null;
            if (value === null || value === undefined || String(value).trim() === "") return null;
            const parsed = Number(value);
            return Number.isFinite(parsed) ? parsed : null;
        };

        const normalizeStatus = function (value)
        {
            const normalized = String(value === null || value === undefined ? "" : value).trim().toUpperCase();
            if (["HEALTHY", "HEALTH", "OK", "GREEN", "SUCCESS", "SUCCEEDED", "UP", "PASS", "PASSED"].includes(normalized)) return "HEALTHY";
            if (["WARNING", "WARN", "YELLOW", "DEGRADED"].includes(normalized)) return "WARNING";
            if (["CRITICAL", "ERROR", "FAILED", "FAILURE", "RED", "DOWN"].includes(normalized)) return "CRITICAL";
            return "UNKNOWN";
        };

        const configuredColumns = columns.length ? columns : [];
        const hostColumn = configuredColumns.find(function (column) {
            return /^(hostname|hostName|host|server|machine|name)$/i.test(String(column.field));
        });
        const statusColumn = configuredColumns.find(function (column) {
            return /^(status|healthStatus|health_status)$/i.test(String(column.field));
        }) || configuredColumns.find(function (column) {
            return String(column.format || "").toLowerCase() === "status"
                || /^(collection_status|collectionStatus)$/i.test(String(column.field));
        });

        const legacyPick = function (row, keys)
        {
            for (const key of keys)
            {
                const value = valueOf(row, key);
                if (value !== undefined && value !== null && value !== "") return value;
            }
            return null;
        };

        const uptimeText = function (seconds)
        {
            const v = number(seconds);
            if (v === null || v < 0) return "N/A";
            const d = Math.floor(v / 86400), h = Math.floor((v % 86400) / 3600), m = Math.floor((v % 3600) / 60);
            return d > 0 ? d + "d " + h + "h" : h > 0 ? h + "h " + m + "m" : m + "m";
        };

        const formatValue = function (value, format, unit)
        {
            if (value === null || value === undefined || value === "") return "N/A";
            const normalized = String(format || "").toLowerCase();
            if (normalized === "duration" && Number.isFinite(Number(value))) return uptimeText(Number(value));
            if (normalized === "number" && Number.isFinite(Number(value))) return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });
            if (normalized === "meter" && Number.isFinite(Number(value))) return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 }) + "%";
            if (normalized === "percentage" && Number.isFinite(Number(value))) return Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 }) + "%";
            let text = typeof value === "object" ? JSON.stringify(value) : String(value);
            if (unit && text !== "N/A" && !text.endsWith(String(unit))) text += (String(unit).startsWith(" ") ? "" : " ") + unit;
            return text;
        };

        const inferLegacyStatus = function (row)
        {
            const explicit = legacyPick(row, ["status", "healthStatus", "health_status"]);
            if (explicit !== null) return normalizeStatus(explicit);
            const cpu = number(legacyPick(row, ["cpu_used_percent", "cpuUsedPercent", "cpu_percent"]));
            const ram = number(legacyPick(row, ["ram_used_percent", "ramUsedPercent", "memory_used_percent"]));
            const load = number(legacyPick(row, ["load_1m", "load1m", "load_average_1m"]));
            const cores = Math.max(1, number(legacyPick(row, ["cpu_cores", "cpuCores"])) || 1);
            const disk = number(legacyPick(row, ["u01_used_percent", "u01_percent", "disk_u01_used_percent"]));
            const levels = [];
            if (cpu !== null) levels.push(cpu >= 85 ? 2 : cpu >= 70 ? 1 : 0);
            if (ram !== null) levels.push(ram >= 90 ? 2 : ram >= 75 ? 1 : 0);
            if (load !== null) levels.push(load / cores >= 1 ? 2 : load / cores >= 0.70 ? 1 : 0);
            if (disk !== null) levels.push(disk >= 95 ? 2 : disk >= 85 ? 1 : 0);
            if (!levels.length) return "UNKNOWN";
            const worst = Math.max.apply(null, levels);
            return worst === 2 ? "CRITICAL" : worst === 1 ? "WARNING" : "HEALTHY";
        };

        const items = rows.map(function (row)
        {
            const explicitStatus = legacyPick(row, ["status", "healthStatus", "health_status"]);
            const status = explicitStatus !== null
                ? normalizeStatus(explicitStatus)
                : (statusColumn ? normalizeStatus(valueOf(row, statusColumn.field)) : inferLegacyStatus(row));
            let pressure = 0;
            configuredColumns.forEach(function (column)
            {
                const format = String(column.format || "").toLowerCase();
                const value = number(valueOf(row, column.field));
                if (value !== null && (format === "meter" || format === "percentage")) pressure = Math.max(pressure, value / 100);
            });
            return { row: row, status: status, pressure: pressure };
        });

        // Grouping and sorting options come from mapped fields and metadata present in the response.
        // No machine inventory records are required.
        const availableFields = new Map();
        configuredColumns.forEach(function (column) {
            if (column && column.field) availableFields.set(String(column.field), String(column.label || column.field));
        });
        const addResponseFields = function (object, prefix, depth) {
            if (!object || typeof object !== "object" || Array.isArray(object) || depth > 2) return;
            Object.keys(object).forEach(function (key) {
                if (key === "metrics") return;
                const field = prefix ? prefix + "." + key : key;
                if (!availableFields.has(field)) {
                    const label = field.split(".").map(function (part) {
                        return part.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ")
                            .replace(/^./, function (character) { return character.toUpperCase(); });
                    }).join(" / ");
                    availableFields.set(field, label);
                }
                const value = object[key];
                if (value && typeof value === "object" && !Array.isArray(value)) addResponseFields(value, field, depth + 1);
            });
        };
        rows.forEach(function (row) { addResponseFields(row, "", 0); });
        const fieldLabel = function (field) {
            if (field === "__health") return "Health Status";
            if (field === "__hostname") return "Hostname";
            return availableFields.get(field) || field;
        };
        const fieldValue = function (item, field) {
            if (field === "__health") return item.status;
            if (field === "__hostname") {
                const host = hostColumn ? valueOf(item.row, hostColumn.field) : legacyPick(item.row, ["hostname", "hostName", "server", "name"]);
                return host === null || host === undefined || host === "" ? "Unknown host" : host;
            }
            return valueOf(item.row, field);
        };
        const healthRank = { CRITICAL: 0, WARNING: 1, UNKNOWN: 2, HEALTHY: 3 };
        const compareValues = function (left, right, direction) {
            const leftMissing = left === null || left === undefined || String(left).trim() === "";
            const rightMissing = right === null || right === undefined || String(right).trim() === "";
            if (leftMissing || rightMissing) return leftMissing === rightMissing ? 0 : (leftMissing ? 1 : -1);
            const leftNumber = number(left), rightNumber = number(right);
            let comparison;
            if (leftNumber !== null && rightNumber !== null) comparison = leftNumber - rightNumber;
            else comparison = String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: "base" });
            return direction === "desc" ? -comparison : comparison;
        };
        const sortItems = function (source) {
            const direction = state.sortDirection === "desc" ? "desc" : "asc";
            return source.slice().sort(function (a, b) {
                let comparison = 0;
                if (state.sortBy === "__health") {
                    comparison = (healthRank[a.status] - healthRank[b.status]) * (direction === "desc" ? -1 : 1);
                    if (comparison === 0) comparison = b.pressure - a.pressure;
                } else {
                    comparison = compareValues(fieldValue(a, state.sortBy), fieldValue(b, state.sortBy), direction);
                    if (comparison === 0) comparison = healthRank[a.status] - healthRank[b.status] || b.pressure - a.pressure;
                }
                if (comparison === 0) comparison = String(fieldValue(a, "__hostname")).localeCompare(String(fieldValue(b, "__hostname")), undefined, { numeric: true, sensitivity: "base" });
                return comparison;
            });
        };
        if (state.sortBy !== "__health" && state.sortBy !== "__hostname" && !availableFields.has(state.sortBy)) {
            state.sortBy = "__health";
            state.sortDirection = "asc";
        }
        if (state.groupBy && state.groupBy !== "__health" && !availableFields.has(state.groupBy)) state.groupBy = "";
        const sortedItems = sortItems(items);

        const counts = { total: items.length, CRITICAL: 0, WARNING: 0, UNKNOWN: 0, HEALTHY: 0 };
        items.forEach(function (item) { counts[item.status]++; });

        const filtered = state.status === "ALL"
            ? sortedItems
            : sortedItems.filter(function (item) { return item.status === state.status; });

        // Use the last measured page size when available. The post-render synchronizer
        // reads the real CSS grid column count, so browser zoom and responsive layout
        // determine how many cards fit on each page.
        const content = document.querySelector(".app-content");
        let gridWidth = content ? content.clientWidth : window.innerWidth;
        if (content) {
            const styles = window.getComputedStyle(content);
            gridWidth -= (parseFloat(styles.paddingLeft) || 0) + (parseFloat(styles.paddingRight) || 0);
        }
        const gridGap = 12;
        const cardMinWidth = 275;
        const estimatedColumns = Math.max(1, Math.floor((Math.max(cardMinWidth, gridWidth) + gridGap) / (cardMinWidth + gridGap)));
        const estimatedSize = estimatedColumns * 2;
        const size = Number(state.pageSize) > 0 ? Number(state.pageSize) : estimatedSize;
        state.pageSize = size;
        const pages = Math.max(1, Math.ceil(filtered.length / size));
        state.page = Math.min(Math.max(0, Number(state.page) || 0), pages - 1);
        serverHealthState.set(String(widget.id), state);
        const pageItems = filtered.slice(state.page * size, (state.page + 1) * size);

        const summaries = [
            ["Total", counts.total, "total", "bi-hdd-rack"], ["Healthy", counts.HEALTHY, "healthy", "bi-check-circle"],
            ["Warning", counts.WARNING, "warning", "bi-exclamation-triangle"], ["Critical", counts.CRITICAL, "critical", "bi-x-octagon"],
            ["Unknown", counts.UNKNOWN, "unknown", "bi-question-circle"]
        ];
        const summary = legacyView.showSummaryCards === false ? "" : '<div class="server-health-summary">' + summaries.map(function (x)
        {
            const statusFilter = x[2] === "total" ? "ALL" : x[2].toUpperCase();
            return '<button type="button" class="server-health-summary-item server-health-summary-' + x[2] + ' server-health-summary-filter' +
                (state.status === statusFilter ? ' is-active' : '') + '" data-widget-id="' + escapeHtml(widget.id) +
                '" data-status="' + statusFilter + '" aria-label="Filter servers by ' + x[0] +
                '" aria-pressed="' + (state.status === statusFilter) + '"><span><i class="bi ' + x[3] +
                '" aria-hidden="true"></i> ' + x[0] + '</span><strong class="server-health-' + x[2] +
                '">' + x[1] + '</strong></button>';
        }).join("") + '</div>';

        // Machine cards use a consistent health-first order. Grouping and sorting controls
        // are reserved for a future table view to keep the card dashboard uncluttered.
        state.groupBy = "";
        state.sortBy = "__health";
        state.sortDirection = "asc";
        const filters = '<div class="server-health-toolbar"><span class="small text-muted">Showing ' +
            (filtered.length ? state.page * size + 1 : 0) + "–" +
            Math.min((state.page + 1) * size, filtered.length) + " of " + filtered.length + ' machines</span></div>';

        const renderMappedFields = function (row)
        {
            const visible = configuredColumns.filter(function (column) {
                return column.visible !== false
                    && column.field !== (hostColumn && hostColumn.field)
                    && column.field !== (statusColumn && statusColumn.field);
            });

            if (!visible.length) return "";

            return visible.map(function (column)
            {
                const metric = metricObject(row, column.field);
                const value = metric ? metric.value : valueOf(row, column.field);
                const unit = metric && metric.unit !== undefined ? metric.unit : "";
                const rawFormat = String(column.format || "").toLowerCase();
                let format = rawFormat;
                if (!format || format === "auto")
                {
                    if (metric && (String(metric.type || "").toLowerCase() === "percentage" || metric.unit === "%")) format = "meter";
                    else if (metric && String(metric.type || "").toLowerCase() === "duration") format = "duration";
                    else if (typeof value === "number") format = "number";
                    else format = "text";
                }

                const status = metric ? normalizeStatus(metric.status) : "";
                const statusBadge = metric && metric.status
                    ? '<span class="server-health-status server-health-status-' + status.toLowerCase() + '">' + status + '</span>'
                    : "";
                const display = formatValue(value, format, unit);
                const meter = (format === "meter" || format === "percentage") && Number.isFinite(Number(value))
                    ? Math.max(0, Math.min(100, Number(value))) : null;

                return '<div class="server-health-metric' + (status ? ' server-health-metric-status-' + status.toLowerCase() : '') + '"><div><span><i class="bi bi-activity" aria-hidden="true"></i> ' +
                    escapeHtml(column.label || column.field) + '</span><strong>' + escapeHtml(display) + '</strong>' + statusBadge + '</div>' +
                    (meter === null ? "" : '<div class="server-health-meter server-health-metric-meter-' + (status ? status.toLowerCase() : "healthy") + '"><span style="width:' + meter + '%"></span></div>') +
                    '</div>';
            }).join("");
        };

        const renderCard = function (item)
        {
            const row = item.row;
            const hostValue = hostColumn ? valueOf(row, hostColumn.field) : legacyPick(row, ["hostname", "hostName", "server", "name"]);
            const host = hostValue === null || hostValue === undefined || hostValue === "" ? "Unknown host" : hostValue;
            const sev = item.status.toLowerCase();
            const statusIcon = { CRITICAL: "bi-x-octagon-fill", WARNING: "bi-exclamation-triangle-fill", UNKNOWN: "bi-question-circle-fill", HEALTHY: "bi-check-circle-fill" }[item.status];

            // Unknown machines often contain only a Collection Status metric, leaving every
            // regular metric as N/A. Show one actionable explanation instead of a wall of N/A.
            const collectionMetric = metricObject(row, "metrics::Collection Status");
            const collectionDetail = legacyPick(row, [
                "collection_error", "collectionError", "error_message", "errorMessage",
                "collection_status", "collectionStatus"
            ]) || (collectionMetric && collectionMetric.value);
            const normalizedDetail = String(collectionDetail === null || collectionDetail === undefined ? "" : collectionDetail).trim();
            const genericDetails = /^(|n\/?a|unknown|not available|not collected|unavailable)$/i.test(normalizedDetail);
            let unknownMessage = "Metrics could not be collected for this machine. Check connectivity, SSH credentials and permissions, then review the monitoring job execution logs.";
            if (!genericDetails && !/^(healthy|ok|success|succeeded)$/i.test(normalizedDetail)) {
                unknownMessage = "Metrics could not be collected: " + normalizedDetail;
            }
            const cardBody = item.status === "UNKNOWN"
                ? '<div class="server-health-collection-message" role="status"><i class="bi bi-exclamation-triangle" aria-hidden="true"></i><div><strong>Metrics unavailable</strong><p>' + escapeHtml(unknownMessage) + '</p></div></div>'
                : renderMappedFields(row);

            return '<article class="server-health-card server-health-card-' + sev + '"><header class="server-health-card-header"><div class="server-health-host"><i class="bi bi-hdd-network" aria-hidden="true"></i><strong title="' + escapeHtml(host) + '">' + escapeHtml(host) + '</strong></div><span class="server-health-status server-health-status-' + sev + '"><i class="bi ' + statusIcon + '" aria-hidden="true"></i> ' + item.status + '</span></header>' +
                cardBody +
                '</article>';
        };
        const cards = pageItems.map(renderCard).join("");

        const pager = '<div class="server-health-pagination"><button type="button" class="btn btn-sm btn-outline-secondary server-health-page" data-widget-id="' + escapeHtml(widget.id) + '" data-page="' + Math.max(0, state.page - 1) + '"' + (state.page === 0 ? " disabled" : "") + '><i class="bi bi-chevron-left me-1" aria-hidden="true"></i>Previous</button><span class="small text-muted">Page ' + (state.page + 1) + " of " + pages + '</span><button type="button" class="btn btn-sm btn-outline-secondary server-health-page" data-widget-id="' + escapeHtml(widget.id) + '" data-page="' + Math.min(pages - 1, state.page + 1) + '"' + (state.page >= pages - 1 ? " disabled" : "") + '>Next<i class="bi bi-chevron-right ms-1" aria-hidden="true"></i></button></div>';


        const summaryConfig = config.summary && typeof config.summary === "object" ? config.summary : {};
        const configuredSummaryCards = Array.isArray(summaryConfig.cards) ? summaryConfig.cards : [];
        const customSummary = configuredSummaryCards.filter(function (card) {
            return card && (card.field || String(card.operation || "").toUpperCase() === "COUNT_ROWS");
        }).map(function (card) {
            const field = String(card.field || "");
            const operation = String(card.operation || "COUNT_ROWS").toUpperCase();
            const values = field ? rows.map(function (row) { return valueOf(row, field); })
                .filter(function (value) { return value !== null && value !== undefined && value !== ""; }) : [];
            const numeric = values.map(number).filter(function (value) { return value !== null; });
            let value = 0;
            if (operation === "COUNT_ROWS") value = rows.length;
            else if (operation === "COUNT_NON_EMPTY") value = values.length;
            else if (operation === "COUNT_TRUE") value = values.filter(function (item) { return item === true || /^(true|yes|1|healthy|up|active|enabled|ok)$/i.test(String(item)); }).length;
            else if (operation === "COUNT_FALSE") value = values.filter(function (item) { return item === false || /^(false|no|0|unhealthy|down|inactive|disabled|error)$/i.test(String(item)); }).length;
            else if (operation === "SUM") value = numeric.reduce(function (sum, item) { return sum + item; }, 0);
            else if (operation === "AVG") value = numeric.length ? numeric.reduce(function (sum, item) { return sum + item; }, 0) / numeric.length : "N/A";
            else if (operation === "MAX") value = numeric.length ? Math.max.apply(null, numeric) : "N/A";
            else if (operation === "MIN") value = numeric.length ? Math.min.apply(null, numeric) : "N/A";
            else if (operation === "VALUE") value = values.length ? values[0] : "N/A";
            value = formatValue(value, String(card.format || "").toLowerCase(), "");
            const icon = card.icon && typeof card.icon === "object" && card.icon.enabled === true && card.icon.name
                ? '<i class="bi bi-' + escapeHtml(String(card.icon.name).replace(/^bi-/, "")) + '" aria-hidden="true"></i> ' : "";
            return '<div class="server-health-summary-item server-health-summary-custom"><span>' + icon + escapeHtml(card.label || (field ? field : "Total Rows")) +
                '</span><strong>' + escapeHtml(value) + '</strong></div>';
        }).join("");
        const customSummaryMarkup = customSummary ? '<div class="server-health-summary server-health-summary-configured">' + customSummary + '</div>' : "";

        return summary + customSummaryMarkup + filters + '<div class="server-health-grid" data-widget-id="' + escapeHtml(widget.id) + '">' + (cards || '<div class="server-health-empty"><i class="bi bi-search" aria-hidden="true"></i><span>No servers match this status filter.</span></div>') + '</div>' + pager;
    }
    // Backward-compatible renderer for existing TABLE widgets configured with renderer: SERVER_HEALTH.
    function renderServerHealthCards(result, widget, config)
    {
        const rows = getConfiguredTableRows(result, config).map(function (row) { return Object.assign({}, row); });
        const state = serverHealthState.get(String(widget.id)) || { status: "ALL", page: 0 };
        const pick = function (row, keys)
        {
            for (const key of keys) if (row[key] !== undefined && row[key] !== null && row[key] !== "") return row[key];
            return null;
        };
        const number = function (value) { const n = Number(value); return Number.isFinite(n) ? n : null; };
        const statusOf = function (row)
        {
            const collection = String(pick(row, ["collection_status", "collectionStatus", "status"]) || "").toUpperCase();
            if (["FAILED", "FAILURE", "FAIL", "ERROR", "DOWN", "UNREACHABLE", "UNAVAILABLE", "NOT_COLLECTED", "CRITICAL", "TIMEOUT"].includes(collection)) return "CRITICAL";
            const cpu = number(pick(row, ["cpu_used_percent", "cpuUsedPercent", "cpu_percent"]));
            const ram = number(pick(row, ["ram_used_percent", "ramUsedPercent", "memory_used_percent"]));
            const load = number(pick(row, ["load_1m", "load1m", "load_average_1m"]));
            const cores = Math.max(1, number(pick(row, ["cpu_cores", "cpuCores"])) || 1);
            const levels = [];
            if (cpu !== null) levels.push(cpu >= 85 ? 2 : cpu >= 70 ? 1 : 0);
            if (ram !== null) levels.push(ram >= 90 ? 2 : ram >= 75 ? 1 : 0);
            if (load !== null) levels.push(load / cores >= 1 ? 2 : load / cores >= 0.70 ? 1 : 0);
            if (!levels.length) return "CRITICAL";
            const worst = Math.max.apply(null, levels);
            return worst === 2 ? "CRITICAL" : worst === 1 ? "WARNING" : "HEALTHY";
        };
        const pressureOf = function (row)
        {
            const cpu = number(pick(row, ["cpu_used_percent", "cpuUsedPercent"])) || 0;
            const ram = number(pick(row, ["ram_used_percent", "ramUsedPercent"])) || 0;
            const load = number(pick(row, ["load_1m", "load1m", "load_average_1m"])) || 0;
            const cores = Math.max(1, number(pick(row, ["cpu_cores", "cpuCores"])) || 1);
            return Math.max(cpu / 85, ram / 90, load / cores);
        };
        const ranked = rows.map(function (row) { return { row: row, status: statusOf(row), pressure: pressureOf(row) }; })
            .sort(function (a, b)
            {
                const rank = { CRITICAL: 0, WARNING: 1, HEALTHY: 2 };
                return rank[a.status] - rank[b.status] || b.pressure - a.pressure;
            });
        const counts = { total: ranked.length, HEALTHY: 0, WARNING: 0, CRITICAL: 0 };
        ranked.forEach(function (item) { counts[item.status]++; });
        const filtered = state.status === "ALL" ? ranked : ranked.filter(function (item) { return item.status === state.status; });
        // Match pagination to the actual responsive grid's minimum card width.
        // Avoid measuring a different widget's existing grid or using a wider estimate.
        const content = document.querySelector(".app-content");
        let gridWidth = content ? content.clientWidth : window.innerWidth;
        if (content) {
            const styles = window.getComputedStyle(content);
            gridWidth -= (parseFloat(styles.paddingLeft) || 0) + (parseFloat(styles.paddingRight) || 0);
        }
        const gridGap = 12;
        const cardMinWidth = 275;
        const columnCount = Math.max(1, Math.floor((Math.max(cardMinWidth, gridWidth) + gridGap) / (cardMinWidth + gridGap)));
        const pageSize = columnCount * 2, pages = Math.max(1, Math.ceil(filtered.length / pageSize));
        state.page = Math.min(Math.max(0, state.page || 0), pages - 1);
        serverHealthState.set(String(widget.id), state);
        const pageRows = filtered.slice(state.page * pageSize, (state.page + 1) * pageSize);
        const summary = '<div class="server-health-summary">' +
            [["Total", counts.total, "total"], ["Healthy", counts.HEALTHY, "healthy"], ["Warning", counts.WARNING, "warning"], ["Critical", counts.CRITICAL, "critical"]]
            .map(function (item) { return '<div class="server-health-summary-item"><span>' + item[0] + '</span><strong class="server-health-' + item[2] + '">' + item[1] + '</strong></div>'; }).join("") + '</div>';
        const filters = '<div class="server-health-toolbar"><label class="small text-muted" for="server-health-filter-' + escapeHtml(widget.id) + '">Status</label>' +
            '<select class="form-select form-select-sm server-health-filter" id="server-health-filter-' + escapeHtml(widget.id) + '" data-widget-id="' + escapeHtml(widget.id) + '">' +
            [["ALL", "All"], ["CRITICAL", "Critical"], ["WARNING", "Warning"], ["HEALTHY", "Healthy"]].map(function (item)
            {
                return '<option value="' + item[0] + '"' + (state.status === item[0] ? " selected" : "") + ">" + item[1] + "</option>";
            }).join("") + '</select><span class="small text-muted">Showing ' + (filtered.length ? state.page * pageSize + 1 : 0) + "–" + Math.min((state.page + 1) * pageSize, filtered.length) + " of " + filtered.length + "</span></div>";
        const cards = pageRows.map(function (item)
        {
            const row = item.row, host = pick(row, ["hostname", "hostName", "server", "name"]) || "Unknown host";
            const cpu = number(pick(row, ["cpu_used_percent", "cpuUsedPercent", "cpu_percent"]));
            const ram = number(pick(row, ["ram_used_percent", "ramUsedPercent", "memory_used_percent"]));
            const load = pick(row, ["load_1m", "load1m", "load_average_1m"]);
            const used = pick(row, ["ram_used_mb", "ramUsedMb"]), total = pick(row, ["ram_total_mb", "ramTotalMb"]);
            const severity = item.status.toLowerCase();
            const metric = function (label, value, suffix)
            {
                const display = value === null || value === undefined ? "N/A" : escapeHtml(value) + (suffix || "");
                const width = Math.max(0, Math.min(100, number(value) || 0));
                return '<div class="server-health-metric"><div><span>' + label + '</span><strong>' + display + '</strong></div><div class="server-health-meter"><span class="server-health-meter-' + severity + '" style="width:' + width + '%"></span></div></div>';
            };
            return '<article class="server-health-card server-health-card-' + severity + '"><header><strong title="' + escapeHtml(host) + '">' + escapeHtml(host) + '</strong><span class="server-health-status server-health-status-' + severity + '">' + item.status + '</span></header>' +
                metric("CPU", cpu, "%") + metric("RAM", ram, "%") +
                '<div class="server-health-foot"><span>Load (1m): <strong>' + (load === null ? "N/A" : escapeHtml(load)) + '</strong></span>' +
                (used !== null && total !== null ? '<span>RAM: ' + escapeHtml(used) + " / " + escapeHtml(total) + " MB</span>" : "") + "</div></article>";
        }).join("");
        const pager = '<div class="server-health-pagination"><button type="button" class="btn btn-sm btn-outline-secondary server-health-page" data-widget-id="' + escapeHtml(widget.id) + '" data-page="' + Math.max(0, state.page - 1) + '"' + (state.page === 0 ? " disabled" : "") + ">Previous</button><span class=\"small text-muted\">Page " + (state.page + 1) + " of " + pages + '</span><button type="button" class="btn btn-sm btn-outline-secondary server-health-page" data-widget-id="' + escapeHtml(widget.id) + '" data-page="' + Math.min(pages - 1, state.page + 1) + '"' + (state.page >= pages - 1 ? " disabled" : "") + ">Next</button></div>";
        return summary + filters + '<div class="server-health-grid" data-widget-id="' + escapeHtml(widget.id) + '">' + (cards || '<div class="text-muted small p-3">No servers match this status filter.</div>') + "</div>" + pager;
    }

    function renderKafkaConsumerHealthWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        const payload = result.payload || result.data || {};
        let groups = config.rowPath ? getJsonPath(payload, config.rowPath) : undefined;
        if (!Array.isArray(groups) && config.rowPath)
            groups = getJsonPath(getJsonPath(payload, "data") || {}, config.rowPath);
        if (!Array.isArray(groups))
            return '<div class="kafka-health-empty">Kafka consumer data is unavailable. Check the widget rowPath mapping.</div>';

        const mappings = ["clusterPath","clusterNameField","clusterStatusField","clusterAvailableBrokersField",
            "clusterExpectedBrokersField","clusterVersionField","clusterTopicCountField","clusterOfflinePartitionsField",
            "groupNameField","groupStatusField","groupLagField","topicsField","topicNameField",
            "topicCurrentField","topicEndField","topicLagField","partitionsField","partitionIdField",
            "partitionCurrentField","partitionEndField","partitionLagField"];
        const missing = mappings.filter(function (key) { return typeof config[key] !== "string" || !config[key].trim(); });
        if (missing.length)
            return '<div class="kafka-health-empty">Configure Kafka field mappings: ' + escapeHtml(missing.join(", ")) + '.</div>';

        const valueAt = function (source, path) { return getJsonPath(source, path); };
        const statusOf = function (value)
        {
            const status = String(value || "UNKNOWN").toUpperCase();
            if (["HEALTHY","SUCCESS","GREEN","OK"].includes(status)) return "HEALTHY";
            if (["WARNING","YELLOW"].includes(status)) return "WARNING";
            if (["CRITICAL","FAILED","FAILURE","RED"].includes(status)) return "CRITICAL";
            return "UNKNOWN";
        };
        const labelOf = function (status)
        {
            return status === "HEALTHY" ? "Healthy" : status === "WARNING" ? "Warning" :
                status === "CRITICAL" ? "Critical" : "Unknown";
        };
        const cssOf = function (status)
        {
            return status === "HEALTHY" ? "kafka-status-healthy" : status === "WARNING" ? "kafka-status-warning" :
                status === "CRITICAL" ? "kafka-status-critical" : "kafka-status-unknown";
        };
        const labels = config.labels && typeof config.labels === "object" ? config.labels : {};
        const formats = config.formats && typeof config.formats === "object" ? config.formats : {};
        const numberOf = function (value, field)
        {
            if (value === null || value === undefined || value === "") return "—";
            const number = Number(value);
            if (!Number.isFinite(number)) return String(value);
            return formats[field] === "raw" ? String(value) : number.toLocaleString();
        };
        const defaultColumnKeys = ["consumerGroup", "topic", "currentOffset", "logEndOffset", "lag"];
        const requestedFields = Array.isArray(config.visibleFields) && config.visibleFields.length
            ? config.visibleFields.map(String).filter(function (field) { return defaultColumnKeys.includes(field); })
            : defaultColumnKeys.slice();
        const columnKeys = Array.from(new Set(requestedFields));
        const order = Array.isArray(config.order) ? config.order.map(String) : [];
        columnKeys.sort(function (left, right)
        {
            const leftIndex = order.indexOf(left);
            const rightIndex = order.indexOf(right);
            if (leftIndex < 0 && rightIndex < 0) return defaultColumnKeys.indexOf(left) - defaultColumnKeys.indexOf(right);
            if (leftIndex < 0) return 1;
            if (rightIndex < 0) return -1;
            return leftIndex - rightIndex;
        });
        if (!columnKeys.length) columnKeys.push("consumerGroup", "topic", "lag");
        const columnLabels = {
            consumerGroup: labels.consumerGroup || "Consumer Group",
            topic: labels.topic || "Topic",
            currentOffset: labels.currentOffset || "Current Offset",
            logEndOffset: labels.logEndOffset || "Log-End Offset",
            lag: labels.lag || "Lag"
        };
        const presentation = config.presentation && typeof config.presentation === "object" ? config.presentation : {};
        const presentationType = String(presentation.type || "expandable-table").toLowerCase();
        if (presentationType !== "expandable-table")
            return '<div class="kafka-health-empty">Unsupported Kafka presentation type: ' + escapeHtml(presentationType) + '.</div>';

        const mappedGroups = groups.filter(isObjectRow).map(function (group, index)
        {
            const rawName = valueAt(group, config.groupNameField);
            const rawLag = valueAt(group, config.groupLagField);
            const lag = rawLag === null || rawLag === undefined || rawLag === "" ? null : Number(rawLag);
            const rawTopics = valueAt(group, config.topicsField);
            return {
                id: String(rawName === null || rawName === undefined ? "unknown-" + index : rawName),
                name: rawName === null || rawName === undefined ? "Unknown group" : String(rawName),
                status: statusOf(valueAt(group, config.groupStatusField)),
                lag: Number.isFinite(lag) ? lag : null,
                topics: Array.isArray(rawTopics) ? rawTopics.filter(isObjectRow) : []
            };
        }).sort(function (a, b)
        {
            if (a.lag === null && b.lag === null) return a.name.localeCompare(b.name);
            if (a.lag === null) return 1;
            if (b.lag === null) return -1;
            return b.lag - a.lag;
        });

        const counts = {HEALTHY:0, WARNING:0, CRITICAL:0, UNKNOWN:0};
        mappedGroups.forEach(function (group) { counts[group.status]++; });
        const cluster = valueAt(payload, config.clusterPath) || valueAt(getJsonPath(payload, "data") || {}, config.clusterPath) || {};
        const clusterStatus = statusOf(valueAt(cluster, config.clusterStatusField));
        const clusterName = valueAt(cluster, config.clusterNameField);
        const availableBrokers = valueAt(cluster, config.clusterAvailableBrokersField);
        const expectedBrokers = valueAt(cluster, config.clusterExpectedBrokersField);
        const brokerLabel = expectedBrokers === null || expectedBrokers === undefined || expectedBrokers === ""
            ? numberOf(availableBrokers) + " brokers"
            : numberOf(availableBrokers) + " / " + numberOf(expectedBrokers) + " brokers";
        const widgetId = String(widget.id || "widget").replace(/[^a-zA-Z0-9_-]/g, "-");
        const offlinePartitions = Number(valueAt(cluster, config.clusterOfflinePartitionsField));
        const brokerCount = Number(availableBrokers);
        const expectedBrokerCount = Number(expectedBrokers);
        const allBrokersAvailable = Number.isFinite(brokerCount) && Number.isFinite(expectedBrokerCount) &&
            expectedBrokerCount > 0 && brokerCount >= expectedBrokerCount;
        const clusterHtml = '<section class="kafka-health-cluster"><div class="kafka-health-cluster-heading">' +
            '<div><strong>' + escapeHtml(clusterName || "Kafka cluster") + '</strong><span class="kafka-health-cluster-version">Kafka ' +
            escapeHtml(valueAt(cluster, config.clusterVersionField) || "version unknown") + '</span></div><span class="kafka-health-badge ' +
            cssOf(clusterStatus) + '">' + labelOf(clusterStatus) + '</span></div><div class="kafka-cluster-stat-grid">' +
            '<div class="kafka-cluster-stat kafka-cluster-stat-brokers' + (allBrokersAvailable ? ' kafka-cluster-stat-brokers-ok' : '') + '"><div class="kafka-cluster-stat-top"><span class="kafka-cluster-stat-icon"><i class="bi bi-hdd-network" aria-hidden="true"></i></span><span class="kafka-cluster-stat-label">Available Brokers</span></div><strong>' +
            escapeHtml(numberOf(availableBrokers)) + '<small> / ' + escapeHtml(expectedBrokers === null || expectedBrokers === undefined || expectedBrokers === "" ? "?" : numberOf(expectedBrokers)) +
            '</small></strong><span class="kafka-cluster-stat-footnote">Brokers available</span></div>' +
            '<div class="kafka-cluster-stat kafka-cluster-stat-version-card"><div class="kafka-cluster-stat-top"><span class="kafka-cluster-stat-icon"><i class="bi bi-layers" aria-hidden="true"></i></span><span class="kafka-cluster-stat-label">Kafka Version</span></div><strong class="kafka-cluster-stat-version">' +
            escapeHtml(valueAt(cluster, config.clusterVersionField) || "Unknown") + '</strong><span class="kafka-cluster-stat-footnote">Cluster software</span></div>' +
            '<div class="kafka-cluster-stat kafka-cluster-stat-topics"><div class="kafka-cluster-stat-top"><span class="kafka-cluster-stat-icon"><i class="bi bi-collection" aria-hidden="true"></i></span><span class="kafka-cluster-stat-label">Topics</span></div><strong>' +
            escapeHtml(numberOf(valueAt(cluster, config.clusterTopicCountField))) + '</strong><span class="kafka-cluster-stat-footnote">Configured topics</span></div>' +
            '<div class="kafka-cluster-stat kafka-cluster-stat-offline"><div class="kafka-cluster-stat-top"><span class="kafka-cluster-stat-icon"><i class="bi bi-exclamation-triangle" aria-hidden="true"></i></span><span class="kafka-cluster-stat-label">Offline Partitions</span></div><strong class="' +
            (offlinePartitions > 0 ? 'kafka-stat-critical' : 'kafka-stat-healthy') + '">' +
            escapeHtml(numberOf(valueAt(cluster, config.clusterOfflinePartitionsField))) + '</strong><span class="kafka-cluster-stat-footnote">Partitions unavailable</span></div>' +
            '</div></section>';
        let html = '<div class="kafka-health-widget" data-kafka-widget="' + escapeHtml(widgetId) +
            '" data-kafka-filter="ALL" data-kafka-page="1" data-kafka-page-size="10" data-kafka-sort-key="lag" data-kafka-sort="desc">' +
            clusterHtml + '<section class="kafka-consumer-section"><div class="kafka-consumer-section-heading"><div><span class="kafka-section-eyebrow">CONSUMER MONITORING</span><h3><i class="bi bi-activity" aria-hidden="true"></i> Consumer Group Lag</h3><p>Monitor consumer health and identify groups falling behind.</p></div><span class="kafka-consumer-total-badge">' + mappedGroups.length + ' groups</span></div>' +
            '<div class="kafka-health-summary" aria-label="Filter consumer groups by status">' +
            '<button type="button" class="kafka-health-count kafka-health-count-total is-active" data-kafka-filter-button="ALL"><span class="kafka-health-count-icon"><i class="bi bi-collection" aria-hidden="true"></i></span><span class="kafka-health-count-label">Total Groups</span><strong>' + mappedGroups.length + '</strong></button>' +
            '<button type="button" class="kafka-health-count kafka-status-healthy" data-kafka-filter-button="HEALTHY"><span class="kafka-health-count-icon"><i class="bi bi-check-circle" aria-hidden="true"></i></span><span class="kafka-health-count-label">Healthy</span><strong>' + counts.HEALTHY + '</strong></button>' +
            '<button type="button" class="kafka-health-count kafka-status-warning" data-kafka-filter-button="WARNING"><span class="kafka-health-count-icon"><i class="bi bi-exclamation-circle" aria-hidden="true"></i></span><span class="kafka-health-count-label">Warning</span><strong>' + counts.WARNING + '</strong></button>' +
            '<button type="button" class="kafka-health-count kafka-status-critical" data-kafka-filter-button="CRITICAL"><span class="kafka-health-count-icon"><i class="bi bi-x-octagon" aria-hidden="true"></i></span><span class="kafka-health-count-label">Critical</span><strong>' + counts.CRITICAL + '</strong></button>' +
            '<button type="button" class="kafka-health-count kafka-status-unknown" data-kafka-filter-button="UNKNOWN"><span class="kafka-health-count-icon"><i class="bi bi-question-circle" aria-hidden="true"></i></span><span class="kafka-health-count-label">Unknown</span><strong>' + counts.UNKNOWN + '</strong></button></div></section>' +
            '<div class="kafka-health-toolbar"><input type="search" class="form-control form-control-sm kafka-health-search" placeholder="Search consumer group or topic..." aria-label="Search consumer group or topic">' +
            '<span class="kafka-health-sort-hint">Select a column heading to sort</span></div>' +
            '<div class="table-responsive kafka-health-table-wrap"><table class="table table-sm dashboard-table kafka-health-table mb-0">' +
            '<thead><tr>' + columnKeys.map(function (key) {
                return '<th' + (["currentOffset", "logEndOffset", "lag"].includes(key) ? ' class="text-end"' : '') + '><button type="button" class="kafka-health-sort-header" data-kafka-sort-key="' +
                    escapeHtml(key) + '" aria-label="Sort by ' + escapeHtml(columnLabels[key]) + '">' + escapeHtml(columnLabels[key]) +
                    '<i class="bi bi-arrow-down-up kafka-health-sort-icon" aria-hidden="true"></i></button></th>';
            }).join("") + '<th class="kafka-health-expand-heading"></th></tr></thead>';

        let kafkaTopicRowStripeIndex = 0;
        mappedGroups.forEach(function (group, groupIndex)
        {
            const groupId = widgetId + "-g" + groupIndex;
            const groupStatusClass = cssOf(group.status);
            const topicSearch = group.topics.map(function (topic) { return String(valueAt(topic, config.topicNameField) || "").toLowerCase(); }).join(" ");
            const sumTopicField = function (field)
            {
                return group.topics.reduce(function (sum, topic)
                {
                    const value = Number(valueAt(topic, field));
                    return Number.isFinite(value) ? sum + value : sum;
                }, 0);
            };
            const groupSortValues = {
                consumergroup: group.name.toLowerCase(),
                topic: group.topics.length ? String(valueAt(group.topics[0], config.topicNameField) || "").toLowerCase() : "",
                currentoffset: sumTopicField(config.topicCurrentField),
                logendoffset: sumTopicField(config.topicEndField),
                lag: group.lag === null ? "" : group.lag
            };
            html += '<tbody class="kafka-health-group" data-group-id="' + escapeHtml(groupId) + '" data-group-name="' +
                escapeHtml(group.name.toLowerCase()) + '" data-group-status="' + group.status + '" data-group-lag="' +
                (group.lag === null ? "" : String(group.lag)) + '" data-group-topics="' + escapeHtml(topicSearch) + '"' +
                Object.keys(groupSortValues).map(function (key) { return ' data-group-sort-' + key + '="' + escapeHtml(groupSortValues[key]) + '"'; }).join("") + '>';

            if (!group.topics.length)
            {
                html += '<tr>' + columnKeys.map(function (key, columnIndex) {
                    if (key === "consumerGroup")
                        return '<td><span class="kafka-health-group-name">' + escapeHtml(group.name) + '</span><span class="kafka-health-badge ' +
                            groupStatusClass + '">' + labelOf(group.status) + '</span></td>';
                    if (columnIndex === 0)
                        return '<td class="text-muted">No topic offsets available</td>';
                    return '';
                }).join("") + '<td></td></tr>';
            }

            group.topics.forEach(function (topic, topicIndex)
            {
                const topicName = String(valueAt(topic, config.topicNameField) ?? "Unknown topic");
                const topicLag = valueAt(topic, config.topicLagField);
                const partitions = valueAt(topic, config.partitionsField);
                const partitionList = Array.isArray(partitions) ? partitions.filter(isObjectRow) : [];
                const expandId = groupId + "-t" + topicIndex;
                const topicCells = {
                    consumerGroup: '<td><span class="kafka-health-group-name">' + escapeHtml(group.name) + '</span><span class="kafka-health-badge ' +
                        groupStatusClass + '">' + labelOf(group.status) + '</span></td>',
                    topic: '<td>' + escapeHtml(topicName) + '</td>',
                    currentOffset: '<td class="text-end kafka-health-number">' + escapeHtml(numberOf(valueAt(topic, config.topicCurrentField), "currentOffset")) + '</td>',
                    logEndOffset: '<td class="text-end kafka-health-number">' + escapeHtml(numberOf(valueAt(topic, config.topicEndField), "logEndOffset")) + '</td>',
                    lag: '<td class="text-end kafka-health-number ' + groupStatusClass + '">' + escapeHtml(numberOf(topicLag, "lag")) + '</td>'
                };
                const topicStripeClass = kafkaTopicRowStripeIndex++ % 2 === 1 ? ' kafka-health-topic-row-alt' : '';
                html += '<tr class="kafka-health-topic-row' + topicStripeClass + '" data-topic-search="' + escapeHtml(topicName.toLowerCase()) + '">' +
                    columnKeys.map(function (key) { return topicCells[key] || ""; }).join("") +
                    '<td class="text-end"><button type="button" class="btn btn-sm btn-link kafka-health-expand" data-expand-id="' +
                    escapeHtml(expandId) + '" aria-expanded="false" aria-label="Expand partitions for ' + escapeHtml(topicName) +
                    '"><i class="bi bi-chevron-down"></i></button></td></tr>';

                if (partitionList.length)
                {
                    html += '<tr class="kafka-health-partition-row d-none" data-partition-parent="' + escapeHtml(expandId) +
                        '"><td colspan="' + (columnKeys.length + 1) + '"><div class="kafka-health-partitions">' +
                        '<div class="kafka-health-partition-heading"><div class="kafka-health-partition-title"><i class="bi bi-diagram-3" aria-hidden="true"></i><span>Partition-level offsets</span></div>' +
                        '<span class="kafka-health-partition-count">' + partitionList.length + ' partitions</span></div>' +
                        '<p class="kafka-health-partition-description">Offset and lag details for each partition in this topic.</p>' +
                        '<div class="kafka-health-partition-table-wrap"><table class="table table-sm kafka-partition-table mb-0"><thead><tr><th>' + escapeHtml(labels.partition || "Partition") +
                        '</th><th class="text-end">' + escapeHtml(labels.currentOffset || "Current") + '</th>' +
                        '<th class="text-end">' + escapeHtml(labels.logEndOffset || "End") + '</th><th class="text-end">' +
                        escapeHtml(labels.lag || "Lag") + '</th></tr></thead><tbody>';
                    partitionList.forEach(function (partition)
                    {
                        html += '<tr><td>' + escapeHtml(valueAt(partition, config.partitionIdField)) +
                            '</td><td class="text-end kafka-health-number">' + escapeHtml(numberOf(valueAt(partition, config.partitionCurrentField), "currentOffset")) +
                            '</td><td class="text-end kafka-health-number">' + escapeHtml(numberOf(valueAt(partition, config.partitionEndField), "logEndOffset")) +
                            '</td><td class="text-end kafka-health-number">' + escapeHtml(numberOf(valueAt(partition, config.partitionLagField), "lag")) +
                            '</td></tr>';
                    });
                    html += '</tbody></table></div></td></tr>';
                }
            });
            html += '</tbody>';
        });

        html += '</table></div><div class="kafka-health-pagination"><span class="kafka-health-page-summary" aria-live="polite"></span>' +
            '<div class="kafka-health-page-controls"><label class="small text-muted" for="' + escapeHtml(widgetId) + '-page-size">Rows</label>' +
            '<select class="form-select form-select-sm kafka-health-page-size" id="' + escapeHtml(widgetId) +
            '-page-size"><option value="5">5</option><option value="10" selected>10</option><option value="20">20</option></select>' +
            '<button type="button" class="btn btn-sm btn-outline-secondary kafka-health-prev" aria-label="Previous page"><i class="bi bi-chevron-left"></i></button>' +
            '<span class="kafka-health-page-number"></span><button type="button" class="btn btn-sm btn-outline-secondary kafka-health-next" aria-label="Next page"><i class="bi bi-chevron-right"></i></button></div></div></div>';
        return html;
    }

    function renderTableWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        if (String(config.renderer || '').toUpperCase() === 'SUMMARY_TABLE' && window.SummaryTableWidget) return window.SummaryTableWidget.render(result, widget);
        if (String(config.renderer || '').toUpperCase() === 'SERVER_HEALTH') return renderServerHealthCards(result, widget, config);
        if (String(config.renderer || '').toUpperCase() === 'KAFKA_CONSUMER_HEALTH') return renderKafkaConsumerHealthWidget(result, widget);
        let columns = !config.rowPath && Array.isArray(result.columns)
            ? result.columns.filter(function (column)
                {
                    return column && (column.key || column.label);
                }).map(function (column) { return Object.assign({}, column); })
            : [];

        let rows = getConfiguredTableRows(result, config);

        if (columns.length === 0 && rows.length > 0)
        {
            const keys = Array.from(new Set(rows.reduce(function (allKeys, row)
            {
                return allKeys.concat(Object.keys(row));
            }, [])));
            columns = keys.map(function (key)
            {
                return {
                    key: key,
                    label: key.replace(/([a-z0-9])([A-Z])/g, "$1 $2")
                        .replace(/^./, function (character) { return character.toUpperCase(); })
                };
            });
        }

        if (columns.length === 0)
        {
            columns = [
                { key: "status", label: "Status" },
                { key: "message", label: "Message" },
                { key: "value", label: "Value" },
                { key: "lastUpdated", label: "Last Updated" }
            ];
            const summaryRow = {
                status: getStatusLabel(result.status),
                message: result.message || "",
                value: result.value ?? "",
                lastUpdated: formatLastUpdated(result.lastUpdated)
            };
            if (result.metrics && typeof result.metrics === "object")
            {
                Object.entries(result.metrics).forEach(function (entry)
                {
                    const key = entry[0];
                    columns.push({
                        key: "metric_" + key,
                        label: key.replace(/([a-z0-9])([A-Z])/g, "$1 $2")
                            .replace(/^./, function (character) { return character.toUpperCase(); })
                    });
                    summaryRow["metric_" + key] = entry[1];
                });
            }
            rows = [summaryRow];
        }

        const visibleFields = Array.isArray(config.visibleFields) ? config.visibleFields.map(String) : [];
        if (visibleFields.length > 0)
        {
            const availableColumns = new Map(columns.map(function (column)
            {
                return [String(column.key), column];
            }));
            columns = visibleFields.map(function (field)
            {
                return availableColumns.get(field) || {
                    key: field,
                    label: field.replace(/([a-z0-9])([A-Z])/g, "$1 $2")
                        .replace(/^./, function (character) { return character.toUpperCase(); })
                };
            });
        }

        if (Array.isArray(config.order) && config.order.length > 0)
        {
            const order = config.order.map(String);
            columns.sort(function (left, right)
            {
                const leftIndex = order.indexOf(String(left.key));
                const rightIndex = order.indexOf(String(right.key));
                if (leftIndex < 0 && rightIndex < 0) return 0;
                if (leftIndex < 0) return 1;
                if (rightIndex < 0) return -1;
                return leftIndex - rightIndex;
            });
        }

        const labels = config.labels && typeof config.labels === "object" ? config.labels : {};
        const formats = config.formats && typeof config.formats === "object" ? config.formats : {};
        const configuredColumns = Array.isArray(config.columns) ? config.columns : [];
        if (configuredColumns.length > 0)
        {
            const sourceColumns = new Map(columns.map(function (column) { return [String(column.key), column]; }));
            columns = configuredColumns.filter(function (column) { return column && (column.field || column.key) && column.visible !== false; })
                .map(function (column)
                {
                    const key = String(column.field || column.key);
                    const existing = sourceColumns.get(key) || {};
                    return Object.assign({}, existing, column, { key: key, label: column.label || labels[key] || existing.label || key });
                });
        }
        columns = columns.map(function (column)
        {
            return Object.assign({}, column, { label: labels[column.key] || column.label || column.key });
        });

        if (columns.length === 0)
            return '<div class="text-muted small p-3">No columns match the configured visible fields.</div>';

        const sorting = config.sorting && typeof config.sorting === "object" ? config.sorting : {};
        const search = config.search && typeof config.search === "object" ? config.search : {};
        const pagination = config.pagination && typeof config.pagination === "object" ? config.pagination : {};
        const sortingEnabled = sorting.enabled !== false;
        const searchEnabled = search.enabled !== false;
        const paginationEnabled = pagination.enabled !== false;
        const pageSize = Math.max(1, Math.min(500, Number(pagination.pageSize) || 10));
        const defaultSortField = sorting.defaultField || "";
        const defaultSortDirection = String(sorting.direction || "asc").toLowerCase() === "desc" ? "desc" : "asc";
        const widgetId = String(widget.id || "table");
        const headerHtml = columns.map(function (column, index)
        {
            const sortable = sortingEnabled && column.sortable !== false;
            return '<th scope="col"' + (sortable ? ' class="dashboard-table-sortable"' : '') +
                ' data-column-index="' + index + '" data-column-key="' + escapeHtml(column.key || "") +
                '" data-sortable="' + sortable + '"><span class="dashboard-table-header-content"><span>' +
                escapeHtml(column.label || column.key || "") +
                '</span>' + (sortable ? '<i class="bi bi-arrow-down-up dashboard-table-sort-icon" aria-hidden="true"></i>' : '') + '</span></th>';
        }).join("");

        const bodyHtml = rows.length
            ? rows.map(function (row)
                {
                    const searchable = columns.filter(function (column) { return column.searchable !== false; }).map(function (column)
                    {
                        const value = Object.prototype.hasOwnProperty.call(row, column.key) ? row[column.key] : getJsonPath(row, column.key);
                        return value === null || value === undefined ? "" : (typeof value === "object" ? JSON.stringify(value) : String(value));
                    }).join(" ").toLowerCase();
                    return '<tr data-search-text="' + escapeHtml(searchable) + '">' + columns.map(function (column)
                    {
                        const value = Object.prototype.hasOwnProperty.call(row, column.key)
                            ? row[column.key]
                            : getJsonPath(row, column.key);
                        const format = formats[column.key] || column.format;
                        if (String(format || "").toLowerCase() === "status")
                        {
                            return '<td><span class="dashboard-status ' + getStatusClass(value) + '">' +
                                escapeHtml(getStatusLabel(value)) + '</span></td>';
                        }
                        return '<td>' + escapeHtml(formatTableValue(value, format)) + '</td>';
                    }).join("") + '</tr>';
                }).join("")
            : '<tr><td colspan="' + columns.length + '">No data available.</td></tr>';

        const toolbar = searchEnabled
            ? '<div class="dashboard-table-toolbar"><input type="search" class="form-control form-control-sm dashboard-table-search" placeholder="' +
                escapeHtml(search.placeholder || "Search table...") + '" aria-label="Search table"></div>'
            : '';
        const pager = paginationEnabled
            ? '<div class="dashboard-table-pagination" data-page-size="' + pageSize + '"><span class="dashboard-table-page-summary small text-muted" aria-live="polite"></span>' +
                '<div class="d-flex align-items-center gap-2"><label class="small text-muted">Rows <select class="form-select form-select-sm dashboard-table-page-size" aria-label="Rows per page">' +
                Array.from(new Set([5, 10, 20, 50, 100, pageSize])).sort(function (a, b) { return a - b; }).map(function (size) { return '<option value="' + size + '"' + (size === pageSize ? ' selected' : '') + '>' + size + '</option>'; }).join('') +
                '</select></label><button type="button" class="btn btn-sm btn-outline-secondary dashboard-table-prev" aria-label="Previous page"><i class="bi bi-chevron-left"></i></button>' +
                '<span class="dashboard-table-page-number small"></span><button type="button" class="btn btn-sm btn-outline-secondary dashboard-table-next" aria-label="Next page"><i class="bi bi-chevron-right"></i></button></div></div>'
            : '';
        return '<div class="dashboard-configurable-table" data-widget-id="' + escapeHtml(widgetId) + '" data-default-sort="' +
            escapeHtml(defaultSortField) + '" data-default-direction="' + defaultSortDirection + '">' + toolbar +
            '<div class="dashboard-table-wrapper"><table class="table dashboard-table mb-0"><thead><tr>' + headerHtml +
            '</tr></thead><tbody>' + bodyHtml + '</tbody></table></div>' + pager + '</div>';
    }

    function renderStatusWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        const payload = result.payload && typeof result.payload === "object" ? result.payload : {};
        const configuredValue = config.valueField ? getJsonPath(payload, config.valueField) : undefined;
        const value = configuredValue !== undefined && configuredValue !== null
            ? configuredValue
            : (result.value !== null && result.value !== undefined ? result.value : "");
        const configuredStatus = config.statusField ? getJsonPath(payload, config.statusField) : undefined;
        const statusValue = configuredStatus !== undefined && configuredStatus !== null && configuredStatus !== ""
            ? configuredStatus
            : (result.status !== undefined && result.status !== null && result.status !== ""
                ? result.status
                : (result.healthStatus !== undefined && result.healthStatus !== null ? result.healthStatus : ""));
        const statusText = statusValue === "" ? "Unknown" : getStatusLabel(statusValue);
        const statusHtml = '<div class="dashboard-status ' + escapeHtml(getStatusClass(statusValue)) + '">' +
            '<i class="bi ' + escapeHtml(getStatusIcon(statusValue)) + '" aria-hidden="true"></i>' +
            '<span>' + escapeHtml(statusText) + '</span></div>';
        const displayValue = value !== null && typeof value === "object"
            ? JSON.stringify(value)
            : value;
        const message = result.message || "";
        const labels = config.labels && typeof config.labels === "object" && !Array.isArray(config.labels)
            ? config.labels : {};

        let metricsHtml = "";
        if (result.metrics && typeof result.metrics === "object" && !Array.isArray(result.metrics))
        {
            let metricEntries = Object.entries(result.metrics);
            if (Array.isArray(config.visibleFields) && config.visibleFields.length > 0)
            {
                metricEntries = metricEntries.filter(function (entry)
                {
                    return config.visibleFields.includes(entry[0]);
                });
            }

            if (metricEntries.length > 0)
            {
                metricsHtml = '<div class="dashboard-status-metrics">' +
                    metricEntries.map(function (entry)
                    {
                        const metricValue = entry[1] !== null && typeof entry[1] === "object"
                            ? JSON.stringify(entry[1]) : entry[1];
                        return '<div class="dashboard-status-metric">' +
                            '<span class="dashboard-status-metric-label">' +
                                escapeHtml(labels[entry[0]] || entry[0]) +
                            '</span><span class="dashboard-status-metric-value">' +
                                escapeHtml(metricValue) +
                            '</span></div>';
                    }).join("") +
                    '</div>';
            }
        }

        return '<div class="dashboard-status-content">' +
            statusHtml +
            (displayValue !== "" ? '<div class="dashboard-status-value">' + escapeHtml(displayValue) + '</div>' : "") +
            (message ? '<div class="dashboard-status-message">' + escapeHtml(message) + '</div>' : "") +
            metricsHtml +
            '</div>';
    }


    function renderStatWidget(result, widget)
	{
	    const config = parseWidgetFieldConfig(widget);
	    const configuredValue = config.valueField
	        ? getJsonPath(result.payload || {}, config.valueField)
	        : undefined;
	    const value = configuredValue !== undefined && configuredValue !== null
	        ? configuredValue
	        : (result.value !== null && result.value !== undefined ? result.value : "");

	    const message =
	        result.message || "";

	    let metricsHtml = "";

	    if (
	        result.metrics &&
	        typeof result.metrics === "object"
	    )
	    {
	        let metricEntries =
	            Object.entries(result.metrics);

	        if (Array.isArray(config.visibleFields) && config.visibleFields.length > 0)
	        {
	            metricEntries = metricEntries.filter(function (entry)
	            {
	                return config.visibleFields.includes(entry[0]);
	            });
	        }

	        if (metricEntries.length > 0)
	        {
	            metricsHtml =
	                '<div class="dashboard-stat-metrics">' +
	                    metricEntries.map(
	                        function (entry)
	                        {
	                            return (
	                                '<div class="dashboard-stat-metric">' +
	                                    '<span class="dashboard-stat-metric-label">' +
	                                        escapeHtml((config.labels && config.labels[entry[0]]) || entry[0]) +
	                                    '</span>' +
	                                    '<span class="dashboard-stat-metric-value">' +
	                                        escapeHtml(entry[1]) +
	                                    '</span>' +
	                                '</div>'
	                            );
	                        }
	                    ).join("") +
	                '</div>';
	        }
	    }

	    return (
	        '<div class="dashboard-stat-content">' +
	            '<div class="dashboard-stat-value">' +
	                escapeHtml(value) +
	            '</div>' +
	            (
	                message
	                    ? '<div class="dashboard-stat-message">' +
	                        escapeHtml(message) +
	                      '</div>'
	                    : ""
	            ) +
	            metricsHtml +
	        '</div>'
	    );
	}


    function renderElasticsearchWidget(result, widget, config)
    {
        const root = result && result.payload && typeof result.payload === "object" ? result.payload : {};
        const data = root.data && typeof root.data === "object" ? root.data : root;
        const cluster = data.cluster && typeof data.cluster === "object" ? data.cluster : {};
        const nodes = Array.isArray(data.nodes) ? data.nodes.filter(function (n) { return n && typeof n === "object"; }) : [];
        const pick = function (obj, keys) { for (const key of keys) if (obj && obj[key] !== undefined && obj[key] !== null && obj[key] !== "") return obj[key]; return null; };
        const num = function (v) { if (v === null || v === undefined || String(v).trim() === "") return null; const n = Number(v); return Number.isFinite(n) ? n : null; };
        const fmt = function (v, suffix) { const n = num(v); return n === null ? "N/A" : (Number.isInteger(n) ? n.toLocaleString() : n.toFixed(1)) + (suffix || ""); };
        const bytes = function (v) { let n = num(v); if (n === null || n < 0) return "N/A"; if (n < 1024) return n + " B"; const units = ["KB", "MB", "GB", "TB", "PB"]; let i = -1; do { n /= 1024; i++; } while (n >= 1024 && i < units.length - 1); return n.toFixed(1) + " " + units[i]; };
        const health = String(cluster.status || "UNKNOWN").toUpperCase();
        const clusterState = health === "GREEN" ? "healthy" : health === "YELLOW" ? "warning" : health === "RED" ? "critical" : "unknown";
        const summary = [
            ["nodes", "bi-hdd-network", "Nodes", fmt(pick(cluster, ["numberOfNodes", "nodeCount"]))],
            ["shards", "bi-exclamation-diamond", "Unassigned shards", fmt(cluster.unassignedShards)],
            ["indices", "bi-files", "Indices", fmt(pick(cluster, ["indices", "indexCount"]))],
            ["storage", "bi-database", "Store size", bytes(pick(cluster, ["storeSizeBytes", "store_size_bytes"]))]
        ].map(function (x) { return '<div class="elasticsearch-summary-item elasticsearch-summary-' + x[0] + '"><span><i class="bi ' + x[1] + '" aria-hidden="true"></i>' + x[2] + '</span><strong>' + escapeHtml(x[3]) + '</strong></div>'; }).join("");
        const cards = nodes.map(function (node, index) {
            const raw = String(node.status || node.healthStatus || "UNKNOWN").toUpperCase();
            const state = ["UP", "HEALTHY", "GREEN", "SUCCESS"].includes(raw) ? "healthy" : ["WARNING", "YELLOW"].includes(raw) ? "warning" : ["DOWN", "CRITICAL", "RED", "FAILED", "FAILURE"].includes(raw) ? "critical" : "unknown";
            const name = pick(node, ["name", "nodeName", "hostname"]) || ("Node " + (index + 1));
            const roles = pick(node, ["roles", "role"]);
            const meta = roles ? "Roles: " + (Array.isArray(roles) ? roles.join(", ") : roles) : (pick(node, ["ip", "address"]) || "Elasticsearch node");
            const metric = function (icon, title, v) { const n = num(v), shown = n === null ? "N/A" : fmt(n, "%"), width = n === null ? 0 : Math.max(0, Math.min(100, n)); return '<div class="elasticsearch-node-metric"><div><span><i class="bi ' + icon + '" aria-hidden="true"></i>' + title + '</span><strong>' + escapeHtml(shown) + '</strong></div>' + (n === null ? "" : '<div class="elasticsearch-meter"><span style="width:' + width + '%"></span></div>') + '</div>'; };
            return '<article class="elasticsearch-node-card elasticsearch-node-' + state + '"><header class="elasticsearch-node-header"><div class="elasticsearch-node-title"><i class="bi bi-hdd-network" aria-hidden="true"></i><div><strong title="' + escapeHtml(name) + '">' + escapeHtml(name) + '</strong><small>' + escapeHtml(meta) + '</small></div></div><span class="elasticsearch-node-status"><i class="bi ' + (state === "healthy" ? "bi-check-circle-fill" : state === "warning" ? "bi-exclamation-triangle-fill" : state === "critical" ? "bi-x-circle-fill" : "bi-question-circle-fill") + '" aria-hidden="true"></i>' + (state === "healthy" ? "UP" : state.toUpperCase()) + '</span></header>' +
                metric("bi-cpu", "CPU", pick(node, ["cpuPercent", "cpu_percent", "cpu"])) + metric("bi-memory", "JVM heap", pick(node, ["jvmHeapPercent", "jvm_heap_percent", "heapPercent"])) + metric("bi-device-hdd", "OS disk", pick(node, ["osDiskUsedPercent", "os_disk_used_percent"])) + metric("bi-database", "Data disk", pick(node, ["dataDiskUsedPercent", "data_disk_used_percent"])) +
                '<footer class="elasticsearch-node-footer"><span><i class="bi bi-search" aria-hidden="true"></i>Search total: ' + escapeHtml(fmt(pick(node, ["searchTotal", "search_total"]))) + '</span><span><i class="bi bi-pencil-square" aria-hidden="true"></i>Index total: ' + escapeHtml(fmt(pick(node, ["indexingTotal", "indexing_total"]))) + '</span></footer></article>';
        }).join("");
        const name = pick(cluster, ["name", "clusterName"]) || "Elasticsearch cluster";
        const version = pick(data, ["version"]) || pick(cluster, ["version"]);
        const nodeCount = num(pick(cluster, ["numberOfNodes", "nodeCount"]));
        const alertList = Array.isArray(data.alerts) ? data.alerts : [];
        const alertHtml = alertList.length ? '<div class="elasticsearch-alerts"><h6><i class="bi bi-bell me-1" aria-hidden="true"></i>Threshold alerts</h6><ul>' + alertList.map(function (a) { return '<li>' + escapeHtml(typeof a === "string" ? a : (a.message || JSON.stringify(a))) + '</li>'; }).join("") + '</ul></div>' : "";
        const notice = root.message || result.message || "";
        const unavailable = nodes.filter(function (n) { return ["DOWN", "CRITICAL", "RED", "FAILED", "FAILURE"].includes(String(n.status || n.healthStatus || "").toUpperCase()); }).length;
        return '<section class="elasticsearch-widget"><div class="elasticsearch-cluster-banner elasticsearch-cluster-' + clusterState + '"><div class="elasticsearch-cluster-identity"><span class="elasticsearch-cluster-icon"><i class="bi bi-database-check" aria-hidden="true"></i></span><div><strong>' + escapeHtml(name) + '</strong><small>' + escapeHtml(version ? "Elasticsearch " + version : "Cluster overview") + '</small></div></div><div class="elasticsearch-cluster-health"><span class="elasticsearch-health-dot" aria-hidden="true"></span><div><strong>' + escapeHtml(health) + '</strong><small>Cluster health</small></div></div></div>' +
            '<div class="elasticsearch-summary">' + summary + '</div><div class="elasticsearch-section-heading"><h6><i class="bi bi-hdd-network me-1" aria-hidden="true"></i>Node health</h6><span>' + escapeHtml(nodes.length + (nodeCount === null ? "" : " / " + nodeCount) + " reported") + '</span></div><div class="elasticsearch-node-grid">' + (cards || '<div class="elasticsearch-empty">No node metrics available.</div>') + '</div>' +
            (unavailable ? '<div class="elasticsearch-inline-alert"><i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>' + unavailable + ' node(s) report critical or unavailable status.</div>' : "") +
            (notice && (health === "UNKNOWN" || !nodes.length) ? '<div class="elasticsearch-inline-alert"><i class="bi bi-info-circle" aria-hidden="true"></i>' + escapeHtml(notice) + '</div>' : "") + alertHtml + '</section>';
    }

    /*
     * ------------------------------------------------------------
     * WIDGET RENDERING
     * ------------------------------------------------------------
     */

    function renderWidget(widgetResponse)
    {
        const widget =
            widgetResponse.widget || {};


        const result =
            widgetResponse.result || {};


        const status =
            result.status || "GRAY";


        const statusClass =
            getStatusClass(status);


        const statusLabel =
            getStatusLabel(status);


        const statusIcon =
            getStatusIcon(status);


        const value =
            result.value !== null &&
            result.value !== undefined
                ? result.value
                : null;


        const message =
            result.message || "";


        const updated =
            formatLastUpdated(
                result.lastUpdated
            );


        const widgetType =
            String(widget.widgetType || "")
                .toUpperCase();

        const fieldConfig = parseWidgetFieldConfig(widget);
        const isSummaryTable = widgetType === "SUMMARY_TABLE" || (widgetType === "TABLE" && String(fieldConfig.renderer || "").toUpperCase() === "SUMMARY_TABLE");
        const isTextWidget = widgetType === "TEXT";
        const textDisplayMode = String(fieldConfig.displayMode || "INFORMATION").toUpperCase();
        if (isTextWidget && textDisplayMode === "SECTION_HEADER")
        {
            return renderSectionHeaderWidget(widget, fieldConfig);
        }
        const summaryHeader = fieldConfig.header && typeof fieldConfig.header === "object" ? fieldConfig.header : {};
        const hideWidgetHeader = isSummaryTable && summaryHeader.enabled === false;
        const widgetTitle = isTextWidget && typeof fieldConfig.title === "string" && fieldConfig.title.trim()
            ? fieldConfig.title : (isSummaryTable && summaryHeader.title ? summaryHeader.title : (widget.name || "Widget"));
        const widgetDescription = isTextWidget && Object.prototype.hasOwnProperty.call(fieldConfig, "subtitle")
            ? fieldConfig.subtitle : (isSummaryTable && Object.prototype.hasOwnProperty.call(summaryHeader, "description")
                ? summaryHeader.description : widget.description);

        let valueHtml = "";


        if (
            value !== null &&
            value !== ""
        )
        {
            valueHtml =
                '<div class="dashboard-widget-value">' +

                    escapeHtml(value) +

                '</div>';
        }


        let messageHtml = "";


        if (message)
        {
            messageHtml =
                '<div class="dashboard-widget-message">' +

                    escapeHtml(message) +

                '</div>';
        }


        let metricsHtml = "";


        if (
            result.metrics &&
            typeof result.metrics === "object"
        )
        {
            const metricEntries =
                Object.entries(
                    result.metrics
                );


            if (metricEntries.length > 0)
            {
                metricsHtml =
                    '<div class="dashboard-kpi-metrics">' +

                        metricEntries.map(function (entry)
                        {
                            return (
                                '<div class="dashboard-kpi-metric">' +

                                    '<span class="dashboard-kpi-metric-label">' +

                                        escapeHtml(
                                            entry[0]
                                        ) +

                                    '</span>' +

                                    '<span class="dashboard-kpi-metric-value">' +

                                        escapeHtml(
                                            entry[1]
                                        ) +

                                    '</span>' +

                                '</div>'
                            );
                        }).join("") +

                    '</div>';
            }
        }


		const isStat =
		    widgetType === "STAT";
        const isIframeWidget = widgetType === "IFRAME";


        const isSystemMetrics = widgetType === "SYSTEM_METRICS";
        const isElasticsearch = widgetType === "ELASTICSEARCH";
        let headerUpdatedHtml = "";
        if (updated)
        {
            const timestampLabel = (isSystemMetrics || isElasticsearch) ? "Last collection: " : "Updated: ";
            headerUpdatedHtml = '<span class="dashboard-widget-updated dashboard-widget-collected" title="' +
                escapeHtml(timestampLabel + updated) + '"><i class="bi bi-clock-history me-1" aria-hidden="true"></i>' +
                escapeHtml(updated) + '</span>';
        }


        const detailsButton =
            widget.detailsEnabled

                ? (
                    '<button type="button"' +

                            ' class="btn btn-sm btn-link dashboard-widget-details"' +

                            ' data-widget-id="' +
                                escapeHtml(widget.id) +
                            '"' +

                            ' title="View details">' +

                        '<i class="bi bi-chevron-right"></i>' +

                    '</button>'
                )

                : "";


        const refreshButton = isIframeWidget
            ? '<button type="button" class="btn btn-sm btn-link dashboard-iframe-refresh" data-widget-id="' + escapeHtml(widget.id) + '" title="Reload embedded page" aria-label="Reload embedded page"><i class="bi bi-arrow-clockwise"></i></button>'
            : '<button type="button"' +
                    ' class="btn btn-sm btn-link dashboard-widget-refresh"' +
                    ' data-widget-id="' + escapeHtml(widget.id) + '"' +
                    ' title="Refresh widget" aria-label="Refresh widget">' +
                '<i class="bi bi-arrow-clockwise"></i>' +
            '</button>';

        const iframeConfig = isIframeWidget ? parseWidgetFieldConfig(widget) : {};
        const iframeExpandButton = isIframeWidget && iframeConfig.allowExpand !== false
            ? '<button type="button" class="btn btn-sm btn-link dashboard-iframe-expand" title="Expand embedded page" aria-label="Expand embedded page"><i class="bi bi-arrows-fullscreen"></i></button>'
            : "";

        const runNowEnabled =
            widget.runNowEnabled === true;

        const runNowButton =
            '<button type="button"' +
                    ' class="btn btn-sm btn-link dashboard-widget-run-now"' +
                    ' data-widget-id="' + escapeHtml(widget.id) + '"' +
                    (runNowEnabled ? '' : ' disabled') +
                    ' title="' +
                        escapeHtml(
                            runNowEnabled
                                ? 'Run monitoring job now'
                                : 'Run Now is not available for this job'
                        ) +
                    '"' +
                    ' aria-label="Run monitoring job now">' +
                '<i class="bi bi-play-fill"></i>' +
            '</button>';


        let widgetContentHtml;


        /*
         * KPI
         */

		if (isStat)
		{
		    widgetContentHtml =
		        renderStatWidget(result, widget);
		}				
		else if (widgetType === "STATUS")
		{
		    widgetContentHtml =
		        renderStatusWidget(result, widget);
		}
		else if (widgetType === "TEXT")
		{
		    widgetContentHtml = renderTextWidget(result, widget);
		}
        else if (isIframeWidget)
        {
            const externalApplicationStatus = String(widget.externalApplicationStatus || "").toUpperCase();
            const rawUrl = typeof widget.externalApplicationUrl === "string" ? widget.externalApplicationUrl.trim() : "";
            let safeUrl = "";
            try {
                const parsedUrl = new URL(rawUrl);
                if ((parsedUrl.protocol === "https:" || parsedUrl.protocol === "http:") && parsedUrl.host !== "" && parsedUrl.username === "" && parsedUrl.password === "") safeUrl = parsedUrl.href;
            } catch (error) { safeUrl = ""; }
            const iframeHeight = Number.isInteger(iframeConfig.defaultHeight) && iframeConfig.defaultHeight >= 250 && iframeConfig.defaultHeight <= 2000 ? iframeConfig.defaultHeight : 520;
            if (externalApplicationStatus === "DISABLED") {
                widgetContentHtml = '<div class="app-message app-message-warning" role="status"><div class="app-message-icon"><i class="bi bi-exclamation-triangle" aria-hidden="true"></i></div><div class="app-message-content"><div class="app-message-title">External application disabled</div><div class="app-message-text">This external application is disabled. Enable it in External Application Configuration to restore this dashboard widget.</div></div></div>';
            } else if (externalApplicationStatus === "NOT_FOUND") {
                widgetContentHtml = '<div class="app-message app-message-error" role="alert"><div class="app-message-icon"><i class="bi bi-x-circle" aria-hidden="true"></i></div><div class="app-message-content"><div class="app-message-title">External application not found</div><div class="app-message-text">The configured external application no longer exists. Select an available instance in Dashboard Configuration.</div></div></div>';
            } else if (externalApplicationStatus === "NOT_CONFIGURED" || !safeUrl) {
                widgetContentHtml = '<div class="app-message app-message-warning" role="status"><div class="app-message-icon"><i class="bi bi-exclamation-triangle" aria-hidden="true"></i></div><div class="app-message-content"><div class="app-message-title">External application unavailable</div><div class="app-message-text">The selected external application is unavailable or not configured. Check External Application Configuration.</div></div></div>';
            } else {
                widgetContentHtml = '<div class="dashboard-iframe-wrapper"><iframe class="dashboard-iframe" src="' + escapeHtml(safeUrl) + '" title="' + escapeHtml(widget.name || "Embedded dashboard") + '" loading="lazy" referrerpolicy="strict-origin-when-cross-origin" allow="fullscreen; clipboard-read; clipboard-write" style="height:' + iframeHeight + 'px"></iframe></div>';
            }
        }

        /*
         * TABLE
         */

        else if (widgetType === "SYSTEM_METRICS")
        {
            widgetContentHtml = renderSystemMetricsCards(result, widget, parseWidgetFieldConfig(widget));
        }
        else if (widgetType === "ELASTICSEARCH")
        {
            widgetContentHtml = renderElasticsearchWidget(result, widget, parseWidgetFieldConfig(widget));
        }
        else if (widgetType === "SUMMARY_TABLE")
        {
            widgetContentHtml = window.SummaryTableWidget
                ? window.SummaryTableWidget.render(result, widget)
                : '<div class="text-muted small p-3">Summary Table renderer is unavailable.</div>';
        }
        else if (widgetType === "TABLE")
        {
            widgetContentHtml =
                renderTableWidget(result, widget);
        }


        /*
         * CHART
         */
		else if (widgetType === "CHART")
		{
            const chartData = getConfiguredChartData(result, widget);


            if (chartData.length === 0)
            {
                widgetContentHtml =
                    '<div class="dashboard-chart-empty">' +

                        '<i class="bi bi-bar-chart"></i>' +

                        '<span>' +
                            'No chart data available.' +
                        '</span>' +

                    '</div>';
            }
            else
            {
                const canvasId =
                    getChartCanvasId(widget.id);


                widgetContentHtml =
                    '<div class="dashboard-chart-wrapper">' +

                        '<canvas id="' +
                            escapeHtml(canvasId) +
                            '"' +

                            ' aria-label="' +
                                escapeHtml(
                                    widget.name ||
                                    "Dashboard chart"
                                ) +
                            '">' +

                        '</canvas>' +

                    '</div>';
            }
        }


        /*
         * Other widget types
         */

        else
        {
            widgetContentHtml =
                valueHtml +

                messageHtml +

                '<div class="dashboard-widget-placeholder">' +

                    '<i class="bi bi-bar-chart-line"></i>' +

                    '<span>' +
                        'Widget data renderer will be added next.' +
                    '</span>' +

                '</div>';
        }


        return (

            '<div class="dashboard-widget-column ' + getWidgetSizeClass(widgetType === "ELASTICSEARCH" ? "FULL" : widget.size) + (isIframeWidget ? ' dashboard-iframe-column' : '') + '" data-widget-id="' + escapeHtml(widget.id) + '">' +
                '<div class="dashboard-widget h-100' + (hideWidgetHeader ? ' dashboard-widget-no-header' : '') +
                    (isTextWidget && fieldConfig.border === false ? ' dashboard-text-widget-no-border' : '') + '">' +


                    '<div class="dashboard-widget-header">' +


                        '<div class="dashboard-widget-title-group">' +


                            '<div class="dashboard-widget-icon">' +

                                '<i class="bi ' +
                                    escapeHtml(
                                        getWidgetIcon(widget)
                                    ) +
                                '"></i>' +

                            '</div>' +


                            '<div class="dashboard-widget-title-line min-w-0">' +

                                '<h5 class="dashboard-widget-title">' +

                                    escapeHtml(
                                        widgetTitle
                                    ) +

                                '</h5>' +

                                (
                                    widgetDescription
                                        ? (
                                            '<span class="dashboard-widget-description" title="' +
                                                escapeHtml(widgetDescription) + '">' +
                                                escapeHtml(widgetDescription) +
                                            '</span>'
                                        )
                                        : ""
                                ) +

                            '</div>' +

                        '</div>' +


                        '<div class="dashboard-widget-actions">' +

                            headerUpdatedHtml +


                            (isIframeWidget ? "" :
                                '<span class="dashboard-status ' + statusClass + '">' +
                                    '<i class="bi ' + statusIcon + '"></i>' +
                                    '<span>' + escapeHtml(statusLabel) + '</span>' +
                                '</span>') +
                            refreshButton +
                            iframeExpandButton +
                            (isIframeWidget ? "" : detailsButton) +


                        '</div>' +


                    '</div>' +


                    '<div class="dashboard-widget-body">' +

                        '<div class="dashboard-widget-main">' +

                            widgetContentHtml +

                        '</div>' +

                    '</div>' +


                '</div>' +
            '</div>'
        );
    }


    /*
     * ------------------------------------------------------------
     * TABS
     * ------------------------------------------------------------
     */

    function orderDashboardWidgets(widgetResponses)
    {
        // Dashboard tabs contain response envelopes: { widget, result }. Resolve the
        // actual widget definition before inspecting type, configuration, or IDs.
        const allResponses = (Array.isArray(widgetResponses) ? widgetResponses : [])
            .filter(function (response) { return response && response.widget; })
            .map(function (response, index) {
                return { response: response, widget: response.widget, originalIndex: index };
            })
            .sort(function (left, right) {
                const leftOrder = Number.isFinite(Number(left.widget.sortOrder)) ? Number(left.widget.sortOrder) : Number.MAX_SAFE_INTEGER;
                const rightOrder = Number.isFinite(Number(right.widget.sortOrder)) ? Number(right.widget.sortOrder) : Number.MAX_SAFE_INTEGER;
                return leftOrder - rightOrder || left.originalIndex - right.originalIndex;
            });

        const isSectionHeader = function (item) {
            if (String(item.widget.widgetType || "").toUpperCase() !== "TEXT") return false;
            const config = parseWidgetFieldConfig(item.widget);
            item.config = config;
            return String(config.displayMode || "INFORMATION").toUpperCase() === "SECTION_HEADER";
        };
        const headers = allResponses.filter(isSectionHeader);
        const regularWidgets = allResponses.filter(function (item) { return !headers.includes(item); });
        const headersByTargetId = new Map();

        headers.forEach(function (header) {
            const targetId = header.config.associatedWidgetId;
            if (targetId === null || targetId === undefined || String(targetId).trim() === "") return;
            const key = String(targetId);
            if (!headersByTargetId.has(key)) headersByTargetId.set(key, []);
            headersByTargetId.get(key).push(header);
        });

        const ordered = [];
        const placedHeaderIds = new Set();
        regularWidgets.forEach(function (item) {
            const matchingHeaders = headersByTargetId.get(String(item.widget.id)) || [];
            matchingHeaders.forEach(function (header) {
                const headerId = String(header.widget.id);
                if (placedHeaderIds.has(headerId)) return;
                ordered.push(header.response);
                placedHeaderIds.add(headerId);
            });
            ordered.push(item.response);
        });

        // Keep headers without a valid target visible, in their configured sort order.
        headers.forEach(function (header) {
            if (!placedHeaderIds.has(String(header.widget.id))) ordered.push(header.response);
        });
        return ordered;
    }

    function renderTabs(preferredTabId)
    {
        /*
         * Destroy existing charts before replacing
         * their canvas elements.
         */
        destroyDashboardCharts();


        tabsElement.innerHTML = "";

        contentElement.innerHTML = "";


        const hasPreferredTab = preferredTabId !== undefined && preferredTabId !== null
            && dashboardData.some(function (tab) { return String(tab.id) === String(preferredTabId); });

        dashboardData.forEach(
            function (tab, index)
            {
                const tabId =
                    "dashboard-tab-" +
                    tab.id;


                const paneId =
                    "dashboard-pane-" +
                    tab.id;


                const active = hasPreferredTab
                    ? String(tab.id) === String(preferredTabId)
                    : index === 0;
                if (active) activeTabId = tab.id;


                const tabButton =
                    '<button type="button"' +

                            ' class="dashboard-tab' +
                                (
                                    active
                                        ? " active"
                                        : ""
                                ) +
                            '"' +

                            ' id="' +
                                tabId +
                            '"' +

                            ' role="tab"' +

                            ' aria-selected="' +
                                active +
                            '"' +

                            ' aria-controls="' +
                                paneId +
                            '"' +

                            ' data-tab-id="' +
                                escapeHtml(tab.id) +
                            '">' +

                        '<i class="bi bi-grid-3x3-gap me-2"></i>' +

                        '<span>' +
                            escapeHtml(tab.name) +
                        '</span>' +

                    '</button>';


                tabsElement.insertAdjacentHTML(
                    "beforeend",
                    tabButton
                );


                const widgets =
                    Array.isArray(tab.widgets)
                        ? tab.widgets
                        : [];


                const orderedWidgets = orderDashboardWidgets(widgets);
                const widgetsHtml =
                    orderedWidgets.length

                        ? orderedWidgets.map(
                            renderWidget
                        ).join("")

                        : (
                            '<div class="col-12">' +

                                '<div class="dashboard-empty-tab">' +

                                    '<i class="bi bi-grid"></i>' +

                                    '<span>' +
                                        'No widgets configured for this tab.' +
                                    '</span>' +

                                '</div>' +

                            '</div>'
                        );


                const pane =
                    '<section class="dashboard-tab-pane' +
                        (
                            active
                                ? " active"
                                : ""
                        ) +
                    '"' +

                        ' id="' +
                            paneId +
                        '"' +

                        ' role="tabpanel"' +

                        ' aria-labelledby="' +
                            tabId +
                        '"' +

                        ' data-tab-id="' +
                            escapeHtml(tab.id) +
                        '">' +

                        '<div class="row g-4">' +

                            widgetsHtml +

                        '</div>' +

                    '</section>';


                contentElement.insertAdjacentHTML(
                    "beforeend",
                    pane
                );
            }
        );


        bindTabEvents();

        bindWidgetActions();
        bindConfigurableTableEvents();


        /*
         * Canvas elements now exist in the DOM.
         */
        initializeDashboardCharts();

        configureAutoRefresh();

        // The HTML above is now in the DOM. Use the actual CSS grid columns rather
        // than estimating them from .app-content, which can differ from widget width.
        synchronizeSystemMetricsPageSizes();


        /*
         * Resize the charts in the first visible tab.
         */
        if (dashboardData.length > 0)
        {
            resizeDashboardCharts(
                dashboardData[0].id
            );
        }
    }


    function synchronizeSystemMetricsPageSizes()
    {
        const activePane = contentElement.querySelector(".dashboard-tab-pane.active");
        if (!activePane) return;

        let pageSizeChanged = false;
        activePane.querySelectorAll(".server-health-grid[data-widget-id]").forEach(function (grid) {
            // Hidden grids do not have a meaningful computed column layout yet.
            if (grid.clientWidth <= 0) return;

            const template = window.getComputedStyle(grid).gridTemplateColumns;
            const columns = template && template !== "none"
                ? template.trim().split(/\s+/).filter(Boolean).length
                : 0;
            if (!columns) return;

            const widgetId = String(grid.dataset.widgetId);
            const state = serverHealthState.get(widgetId) || { status: "ALL", page: 0 };
            const actualPageSize = columns * 2;
            if (Number(state.pageSize) !== actualPageSize) {
                state.pageSize = actualPageSize;
                // Keep the current page where possible; renderSystemMetricsCards clamps
                // it if the new responsive page size leaves fewer pages.
                serverHealthState.set(widgetId, state);
                pageSizeChanged = true;
            }
        });

        // One rerender applies the measured page sizes. The next pass is stable because
        // the stored page size now matches the rendered grid's actual column count.
        if (pageSizeChanged) renderTabs(activeTabId);
    }

    // Browser zoom changes the CSS layout and fires resize; only rerender when the
    // actual column count changes.
    window.addEventListener("resize", synchronizeSystemMetricsPageSizes);


    function bindConfigurableTableEvents()
    {
        contentElement.querySelectorAll(".dashboard-configurable-table").forEach(function (container)
        {
            const table = container.querySelector("table");
            const tbody = table && table.tBodies[0];
            if (!table || !tbody || container.dataset.tableEventsBound === "true") return;
            container.dataset.tableEventsBound = "true";

            const searchInput = container.querySelector(".dashboard-table-search");
            const pagination = container.querySelector(".dashboard-table-pagination");
            const pageSizeSelect = container.querySelector(".dashboard-table-page-size");
            const summary = container.querySelector(".dashboard-table-page-summary");
            const pageNumber = container.querySelector(".dashboard-table-page-number");
            const previous = container.querySelector(".dashboard-table-prev");
            const next = container.querySelector(".dashboard-table-next");
            const state = { page: 0, pageSize: Number(pageSizeSelect && pageSizeSelect.value) || 10, sortKey: "", sortDirection: "asc" };
            const defaultSort = container.dataset.defaultSort;
            if (defaultSort)
            {
                state.sortKey = defaultSort;
                state.sortDirection = container.dataset.defaultDirection === "desc" ? "desc" : "asc";
            }

            function rows() { return Array.from(tbody.querySelectorAll("tr")).filter(function (row) { return !row.querySelector("td[colspan]"); }); }
            function sortRows()
            {
                if (!state.sortKey) return;
                const header = Array.from(table.querySelectorAll("thead th")).find(function (th) { return th.dataset.columnKey === state.sortKey; });
                if (!header || header.dataset.sortable !== "true") return;
                const index = Number(header.dataset.columnIndex);
                rows().sort(function (a, b)
                {
                    const left = (a.cells[index] && a.cells[index].textContent || "").trim();
                    const right = (b.cells[index] && b.cells[index].textContent || "").trim();
                    const ln = left === "" ? NaN : Number(left.replace(/,/g, "").replace(/%$/, ""));
                    const rn = right === "" ? NaN : Number(right.replace(/,/g, "").replace(/%$/, ""));
                    let result;
                    if (Number.isFinite(ln) && Number.isFinite(rn)) result = ln - rn;
                    else result = left.localeCompare(right, undefined, { numeric: true, sensitivity: "base" });
                    return state.sortDirection === "desc" ? -result : result;
                }).forEach(function (row) { tbody.appendChild(row); });
            }
            function renderPage()
            {
                const allRows = rows();
                const query = searchInput ? searchInput.value.trim().toLowerCase() : "";
                const filtered = allRows.filter(function (row) { return !query || (row.dataset.searchText || row.textContent.toLowerCase()).includes(query); });
                const pages = Math.max(1, Math.ceil(filtered.length / state.pageSize));
                state.page = Math.min(Math.max(0, state.page), pages - 1);
                allRows.forEach(function (row) { row.hidden = true; });
                filtered.slice(state.page * state.pageSize, (state.page + 1) * state.pageSize).forEach(function (row) { row.hidden = false; });
                if (summary) summary.textContent = "Showing " + (filtered.length ? state.page * state.pageSize + 1 : 0) + "–" + Math.min((state.page + 1) * state.pageSize, filtered.length) + " of " + filtered.length;
                if (pageNumber) pageNumber.textContent = "Page " + (state.page + 1) + " of " + pages;
                if (previous) previous.disabled = state.page === 0;
                if (next) next.disabled = state.page >= pages - 1;
            }
            table.querySelectorAll("thead th[data-sortable='true']").forEach(function (header)
            {
                header.style.cursor = "pointer";
                header.setAttribute("tabindex", "0");
                header.setAttribute("role", "button");
                function toggleSort()
                {
                    const key = header.dataset.columnKey;
                    state.sortDirection = state.sortKey === key && state.sortDirection === "asc" ? "desc" : "asc";
                    state.sortKey = key;
                    state.page = 0;
                    sortRows();
                    renderPage();
                }
                header.addEventListener("click", toggleSort);
                header.addEventListener("keydown", function (event) { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); toggleSort(); } });
            });
            if (searchInput) searchInput.addEventListener("input", function () { state.page = 0; renderPage(); });
            if (pageSizeSelect) pageSizeSelect.addEventListener("change", function () { state.pageSize = Math.max(1, Number(this.value) || 10); state.page = 0; renderPage(); });
            if (previous) previous.addEventListener("click", function () { state.page--; renderPage(); });
            if (next) next.addEventListener("click", function () { state.page++; renderPage(); });
            sortRows();
            renderPage();
            if (pagination && pageSizeSelect) pagination.dataset.pageSize = String(state.pageSize);
        });
    }


    /*
     * ------------------------------------------------------------
     * TAB EVENTS
     * ------------------------------------------------------------
     */

    function bindTabEvents()
    {
        tabsElement
            .querySelectorAll(".dashboard-tab")
            .forEach(
                function (button)
                {
                    button.addEventListener(
                        "click",
                        function ()
                        {
                            const selectedTabId =
                                this.dataset.tabId;


                            tabsElement
                                .querySelectorAll(
                                    ".dashboard-tab"
                                )
                                .forEach(
                                    function (tabButton)
                                    {
                                        const active =
                                            tabButton ===
                                            button;


                                        tabButton.classList.toggle(
                                            "active",
                                            active
                                        );


                                        tabButton.setAttribute(
                                            "aria-selected",
                                            String(active)
                                        );
                                    }
                                );


                            contentElement
                                .querySelectorAll(
                                    ".dashboard-tab-pane"
                                )
                                .forEach(
                                    function (pane)
                                    {
                                        pane.classList.toggle(
                                            "active",
                                            pane.dataset.tabId ===
                                            selectedTabId
                                        );
                                    }
                                );


                            /*
                             * Allow the browser to make the
                             * tab visible before Chart.js
                             * recalculates its dimensions.
                             */
                            requestAnimationFrame(
                                function ()
                                {
                                    resizeDashboardCharts(
                                        selectedTabId
                                    );
                                }
                            );
                        }
                    );
                }
            );
    }


    /*
     * ------------------------------------------------------------
     * WIDGET ACTIONS
     * ------------------------------------------------------------
     */

    function clearAutoRefreshTimers()
    {
        autoRefreshTimers.forEach(
            function (timer)
            {
                clearInterval(timer);
            }
        );

        autoRefreshTimers.clear();
    }


    async function refreshWidget(widgetId)
    {
        const response =
            await fetch(
                "/api/dashboard/widgets/" +
                encodeURIComponent(widgetId),
                {
                    method: "GET",
                    headers:
                    {
                        "Accept": "application/json"
                    },
                    cache: "no-store"
                }
            );

        if (!response.ok)
        {
            throw new Error("Widget refresh failed (" + response.status + ")");
        }

        const widgetResponse = await response.json();

        dashboardData.forEach(
            function (tab)
            {
                (tab.widgets || []).forEach(
                    function (item)
                    {
                        if (String(item.widget.id) === String(widgetId))
                        {
                            item.widget = widgetResponse.widget;
                            item.result = widgetResponse.result;
                        }
                    }
                );
            }
        );

        renderTabs(activeTabId);
    }


    async function runWidget(widgetId, button)
    {
        if (button.disabled)
        {
            return;
        }

        if (!window.confirm("Run this monitoring job now?"))
        {
            return;
        }

        button.disabled = true;

        try
        {
            const response =
                await fetch(
                    "/api/dashboard/widgets/" +
                    encodeURIComponent(widgetId) +
                    "/run",
                    {
                        method: "POST",
                        headers:
                        {
                            "Accept": "application/json"
                        },
                        cache: "no-store"
                    }
                );

            if (!response.ok)
            {
                let message = "Unable to run monitoring job.";
                try
                {
                    const errorBody = await response.json();
                    if (errorBody.message)
                    {
                        message = errorBody.message;
                    }
                }
                catch (ignored)
                {
                    // Keep the generic message.
                }

                throw new Error(message);
            }

            const widgetResponse = await response.json();

            dashboardData.forEach(
                function (tab)
                {
                    (tab.widgets || []).forEach(
                        function (item)
                        {
                            if (String(item.widget.id) === String(widgetId))
                            {
                                item.widget = widgetResponse.widget;
                                item.result = widgetResponse.result;
                            }
                        }
                    );
                }
            );

            renderTabs();
        }
        catch (error)
        {
            console.error("Unable to run dashboard monitoring job.", error);
            window.alert(
                error && error.message
                    ? error.message
                    : "Unable to run monitoring job."
            );
            button.disabled = false;
        }
    }


    function configureAutoRefresh()
    {
        clearAutoRefreshTimers();

        dashboardData.forEach(
            function (tab)
            {
                (tab.widgets || []).forEach(
                    function (item)
                    {
                        const widget = item.widget;

                        if (!widget ||
                            widget.autoRefresh !== true ||
                            widget.dataSourceType !== "MONITORING_JOB")
                        {
                            return;
                        }

                        const interval =
                            Number(widget.refreshInterval);

                        if (!Number.isFinite(interval) || interval <= 0)
                        {
                            return;
                        }

                        const multiplier =
                            String(widget.refreshIntervalUnit || "").toUpperCase() === "MINUTES"
                                ? 60000
                                : 1000;

                        const delay =
                            Math.max(1000, interval * multiplier);

                        const timer =
                            setInterval(
                                function ()
                                {
                                    refreshWidget(widget.id).catch(
                                        function (error)
                                        {
                                            console.error(
                                                "Unable to auto-refresh widget.",
                                                error
                                            );
                                        }
                                    );
                                },
                                delay
                            );

                        autoRefreshTimers.set(
                            String(widget.id),
                            timer
                        );
                    }
                );
            }
        );
    }


    function bindWidgetActions()
    {
        contentElement
            .querySelectorAll(".dashboard-widget-refresh")
            .forEach(
                function (button)
                {
                    button.addEventListener(
                        "click",
                        async function (event)
                        {
                            event.preventDefault();
                            event.stopPropagation();

                            const widgetId =
                                button.dataset.widgetId;

                            if (!widgetId)
                            {
                                return;
                            }

                            button.disabled = true;

                            try
                            {
                                await refreshWidget(widgetId);
                            }
                            catch (error)
                            {
                                console.error(
                                    "Unable to refresh dashboard widget.",
                                    error
                                );

                                window.alert(
                                    error && error.message
                                        ? error.message
                                        : "Unable to refresh widget."
                                );
                            }
                            finally
                            {
                                if (button.isConnected)
                                {
                                    button.disabled = false;
                                }
                            }
                        }
                    );
                }
            );


        contentElement
            .querySelectorAll(".dashboard-widget-run-now")
            .forEach(
                function (button)
                {
                    button.addEventListener(
                        "click",
                        function (event)
                        {
                            event.preventDefault();
                            event.stopPropagation();

                            const widgetId =
                                button.dataset.widgetId;

                            if (widgetId)
                            {
                                runWidget(widgetId, button);
                            }
                        }
                    );
                }
            );


        contentElement
            .querySelectorAll(".dashboard-widget-details")
            .forEach(
                function (button)
                {
                    button.addEventListener(
                        "click",
                        function (event)
                        {
                            event.preventDefault();
                            event.stopPropagation();

                            const widgetId =
                                button.dataset.widgetId;

                            if (!widgetId)
                            {
                                return;
                            }

                            window.location.href =
                                "/dashboard/widgets/" +
                                encodeURIComponent(widgetId) +
                                "/details";
                        }
                    );
                }
            );


        contentElement
            .querySelectorAll(".dashboard-table-sortable")
            .forEach(
                function (header)
                {
                    header.addEventListener(
                        "click",
                        function ()
                        {
                            const table =
                                header.closest(".dashboard-table");

                            if (!table)
                            {
                                return;
                            }

                            const tbody =
                                table.querySelector("tbody");

                            if (!tbody)
                            {
                                return;
                            }

                            const columnIndex =
                                Number(header.dataset.columnIndex);

                            const currentDirection =
                                header.dataset.sortDirection || "none";

                            const direction =
                                currentDirection === "asc"
                                    ? "desc"
                                    : "asc";

                            table
                                .querySelectorAll(".dashboard-table-sortable")
                                .forEach(
                                    function (otherHeader)
                                    {
                                        otherHeader.removeAttribute(
                                            "data-sort-direction"
                                        );

                                        const icon =
                                            otherHeader.querySelector(
                                                ".dashboard-table-sort-icon"
                                            );

                                        if (icon)
                                        {
                                            icon.className =
                                                "bi bi-arrow-down-up dashboard-table-sort-icon";
                                        }
                                    }
                                );

                            header.dataset.sortDirection = direction;

                            const activeIcon =
                                header.querySelector(
                                    ".dashboard-table-sort-icon"
                                );

                            if (activeIcon)
                            {
                                activeIcon.className =
                                    direction === "asc"
                                        ? "bi bi-arrow-up dashboard-table-sort-icon"
                                        : "bi bi-arrow-down dashboard-table-sort-icon";
                            }

                            const rows =
                                Array.from(
                                    tbody.querySelectorAll("tr")
                                );

                            rows.sort(
                                function (rowA, rowB)
                                {
                                    const cellA = rowA.cells[columnIndex];
                                    const cellB = rowB.cells[columnIndex];
                                    const valueA = cellA ? cellA.textContent.trim() : "";
                                    const valueB = cellB ? cellB.textContent.trim() : "";
                                    const numberA = Number(valueA.replace(/,/g, ""));
                                    const numberB = Number(valueB.replace(/,/g, ""));

                                    let comparison;

                                    if (
                                        valueA !== "" &&
                                        valueB !== "" &&
                                        !Number.isNaN(numberA) &&
                                        !Number.isNaN(numberB)
                                    )
                                    {
                                        comparison = numberA - numberB;
                                    }
                                    else
                                    {
                                        comparison =
                                            valueA.localeCompare(
                                                valueB,
                                                undefined,
                                                {
                                                    numeric: true,
                                                    sensitivity: "base"
                                                }
                                            );
                                    }

                                    return direction === "asc"
                                        ? comparison
                                        : -comparison;
                                }
                            );

                            rows.forEach(
                                function (row)
                                {
                                    tbody.appendChild(row);
                                }
                            );
                        }
                    );
                }
            );
    }

    function buildDashboardUrl()
    {
        return apiUrl + '?applicationId=' + encodeURIComponent(applicationFilter.value) + '&environmentId=' + encodeURIComponent(environmentFilter.value);
    }

    async function loadApplications()
    {
        let items = readSessionCache(APPLICATIONS_CACHE_KEY);
        if (!items) {
            const response = await fetch(applicationsApiUrl, { headers: { 'Accept': 'application/json' }, cache: 'no-store' });
            if (!response.ok) throw new Error('Unable to load applications (' + response.status + ')');
            items = await response.json();
            writeSessionCache(APPLICATIONS_CACHE_KEY, items);
        }
        applicationFilter.innerHTML = '';
        items.forEach(function (item) { const option = document.createElement('option'); option.value = item.id; option.textContent = item.name; applicationFilter.appendChild(option); });
        applicationFilter.disabled = items.length <= 1;
        if (items.length) {
            const primary = items.find(function (item) { return item.primary === true; });
            applicationFilter.value = String((primary || items[0]).id);
        }
        return items;
    }

    async function loadEnvironments(preferDefault)
    {
        environmentFilter.disabled = true;
        environmentFilter.innerHTML = '';
        if (!applicationFilter.value) { showState('empty'); return; }

        const cacheKey = ENVIRONMENTS_CACHE_PREFIX + applicationFilter.value;
        let items = readSessionCache(cacheKey);
        if (!items) {
            const response = await fetch(environmentsApiUrl + '?applicationId=' + encodeURIComponent(applicationFilter.value), { headers: { 'Accept': 'application/json' }, cache: 'no-store' });
            if (!response.ok) throw new Error('Unable to load environments (' + response.status + ')');
            items = await response.json();
            writeSessionCache(cacheKey, items);
        }
        items.forEach(function (item) { const option = document.createElement('option'); option.value = item.id; option.textContent = item.name; environmentFilter.appendChild(option); });
        if (!items.length) { dashboardData = []; showState('empty'); return; }
        const prod = items.find(function (item) { return String(item.name).toUpperCase() === 'PROD'; });
        environmentFilter.value = String(preferDefault ? (prod || items[0]).id : (items.find(function (item) { return String(item.id) === environmentFilter.value; }) || prod || items[0]).id);
        environmentFilter.disabled = items.length <= 1;
        await loadDashboard();
    }

    async function initializeDashboard()
    {
        try
        {
            const items = await loadApplications();
            if (pageIsUnloading) return;
            if (!items.length) { showState('empty'); return; }
            await loadEnvironments(true);
        }
        catch (error)
        {
            // Navigation can abort an in-flight fetch; do not render a stale error during page unload.
            if (pageIsUnloading) return;
            errorMessageElement.textContent = error && error.message ? error.message : 'Unable to initialize dashboard filters.';
            showState('error');
        }
    }

    /*
     * ------------------------------------------------------------
     * LOAD DASHBOARD
     * ------------------------------------------------------------
     */

    async function loadDashboard(preferredTabId)
    {
        clearAutoRefreshTimers();
        showState("loading");


        refreshButton.disabled = true;


        try
        {
            const response =
                await fetch(
                    buildDashboardUrl(),
                    {
                        method: "GET",

                        headers:
                        {
                            "Accept":
                                "application/json"
                        },

                        cache: "no-store"
                    }
                );


            if (!response.ok)
            {
                throw new Error(
                    "Dashboard request failed (" +
                    response.status +
                    ")"
                );
            }


            const data =
                await response.json();


            if (
                !Array.isArray(data) ||
                data.length === 0
            )
            {
                dashboardData = [];

                if (dateRangeContainer) dateRangeContainer.classList.add("d-none");

                destroyDashboardCharts();


                showState("empty");

                return;
            }


            dashboardData = data;

            configureDateRangeControls();
            renderTabs(preferredTabId);
            initializeKafkaHealthWidgets();

			showState("content");

            // Now that the dashboard container is visible, measure each System Metrics grid
            // at its real widget width. This corrects the initial estimate for Large/Medium
            // widgets without changing the working Full-size layout.
            synchronizeSystemMetricsPageSizes();
        }
        catch (error)
        {
            // A navigation away from this page can cancel fetch requests. That is not a dashboard failure.
            if (pageIsUnloading) return;

            console.error(
                "Unable to load dashboard",
                error
            );


            errorMessageElement.textContent =
                error && error.message
                    ? error.message
                    : "Unable to load dashboard data.";


            showState("error");
        }
        finally
        {
            refreshButton.disabled = false;
        }
    }


    function updateKafkaHealthWidget(widgetElement)
    {
        const allGroups = Array.from(widgetElement.querySelectorAll(".kafka-health-group"));
        const filter = widgetElement.dataset.kafkaFilter || "ALL";
        const searchInput = widgetElement.querySelector(".kafka-health-search");
        const search = searchInput ? searchInput.value.trim().toLowerCase() : "";
        const pageSize = Math.max(1, Number(widgetElement.dataset.kafkaPageSize || 10));
        const sortKey = widgetElement.dataset.kafkaSortKey || "lag";
        const sortDirection = widgetElement.dataset.kafkaSort || "desc";
        const sortAttribute = {consumerGroup:"consumergroup", topic:"topic", currentOffset:"currentoffset", logEndOffset:"logendoffset", lag:"lag"}[sortKey] || "lag";
        const numericSort = ["currentOffset", "logEndOffset", "lag"].includes(sortKey);
        const compareValues = function (leftValue, rightValue)
        {
            if (numericSort)
            {
                const leftNumber = leftValue === "" || leftValue === null ? null : Number(leftValue);
                const rightNumber = rightValue === "" || rightValue === null ? null : Number(rightValue);
                if (leftNumber === null && rightNumber === null) return 0;
                if (leftNumber === null) return 1;
                if (rightNumber === null) return -1;
                return leftNumber - rightNumber;
            }
            return String(leftValue || "").localeCompare(String(rightValue || ""), undefined, {numeric:true, sensitivity:"base"});
        };
        const matchingGroups = allGroups.filter(function (group)
        {
            const statusMatches = filter === "ALL" || group.dataset.groupStatus === filter;
            const searchText = (group.dataset.groupName || "") + " " + (group.dataset.groupTopics || "");
            return statusMatches && (!search || searchText.includes(search));
        });
        matchingGroups.forEach(function (group)
        {
            const topicRows = Array.from(group.querySelectorAll("tr.kafka-health-topic-row"));
            if (topicRows.length > 1)
            {
                const topicColumnIndex = Array.from(widgetElement.querySelectorAll(".kafka-health-table thead th")).findIndex(function (th)
                {
                    const button = th.querySelector("[data-kafka-sort-key]");
                    return button && button.dataset.kafkaSortKey === sortKey;
                });
                const rowValue = function (row)
                {
                    const cell = row.cells[topicColumnIndex];
                    if (!cell) return "";
                    if (sortKey === "consumerGroup") return group.dataset.groupName || "";
                    if (sortKey === "topic") return cell.textContent.trim();
                    const numberCell = cell.textContent.trim().replace(/,/g, "");
                    return numberCell === "—" ? "" : numberCell;
                };
                const sortedRows = topicRows.slice().sort(function (left, right)
                {
                    const comparison = compareValues(rowValue(left), rowValue(right));
                    return sortDirection === "desc" ? -comparison : comparison;
                });
                sortedRows.forEach(function (row)
                {
                    const expandButton = row.querySelector(".kafka-health-expand");
                    const expandId = expandButton && expandButton.dataset.expandId;
                    const partitionRow = expandId ? group.querySelector('[data-partition-parent="' + CSS.escape(expandId) + '"]') : null;
                    group.appendChild(row);
                    if (partitionRow) group.appendChild(partitionRow);
                });
                if (sortKey === "topic" && sortedRows.length)
                    group.setAttribute("data-group-sort-topic", sortedRows[0].cells[topicColumnIndex].textContent.trim().toLowerCase());
            }
        });
        matchingGroups.sort(function (left, right)
        {
            const comparison = compareValues(left.getAttribute("data-group-sort-" + sortAttribute) || "", right.getAttribute("data-group-sort-" + sortAttribute) || "");
            return sortDirection === "desc" ? -comparison : comparison;
        });
        matchingGroups.forEach(function (group) { group.parentElement.appendChild(group); });
        allGroups.forEach(function (group) { group.classList.add("d-none"); });
        const pageCount = Math.max(1, Math.ceil(matchingGroups.length / pageSize));
        let page = Math.min(Math.max(1, Number(widgetElement.dataset.kafkaPage || 1)), pageCount);
        widgetElement.dataset.kafkaPage = String(page);
        const start = (page - 1) * pageSize;
        matchingGroups.slice(start, start + pageSize).forEach(function (group) { group.classList.remove("d-none"); });
        widgetElement.querySelectorAll("[data-kafka-filter-button]").forEach(function (button)
        {
            const active = button.dataset.kafkaFilterButton === filter;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-pressed", String(active));
        });
        widgetElement.querySelectorAll("[data-kafka-sort-key]").forEach(function (button)
        {
            const active = button.dataset.kafkaSortKey === sortKey;
            const header = button.closest("th");
            const icon = button.querySelector(".kafka-health-sort-icon");
            if (header) header.setAttribute("aria-sort", active ? (sortDirection === "asc" ? "ascending" : "descending") : "none");
            button.classList.toggle("is-active", active);
            if (icon) icon.className = "bi " + (active ? (sortDirection === "asc" ? "bi-arrow-up" : "bi-arrow-down") : "bi-arrow-down-up") + " kafka-health-sort-icon";
        });
        const summary = widgetElement.querySelector(".kafka-health-page-summary");
        if (summary) summary.textContent = matchingGroups.length ? (start + 1) + "–" + Math.min(start + pageSize, matchingGroups.length) + " of " + matchingGroups.length + " groups" : "0 groups";
        const pageNumber = widgetElement.querySelector(".kafka-health-page-number");
        if (pageNumber) pageNumber.textContent = page + " / " + pageCount;
        const previous = widgetElement.querySelector(".kafka-health-prev");
        const next = widgetElement.querySelector(".kafka-health-next");
        if (previous) previous.disabled = page <= 1;
        if (next) next.disabled = page >= pageCount;
    }

    if (!contentElement.dataset.kafkaHealthEventsBound)
    {
        contentElement.dataset.kafkaHealthEventsBound = "true";
        contentElement.addEventListener("click", function (event)
        {
            const widgetElement = event.target.closest(".kafka-health-widget");
            if (!widgetElement) return;
            const filterButton = event.target.closest("[data-kafka-filter-button]");
            if (filterButton)
            {
                widgetElement.dataset.kafkaFilter = filterButton.dataset.kafkaFilterButton;
                widgetElement.dataset.kafkaPage = "1";
                updateKafkaHealthWidget(widgetElement);
                return;
            }
            const expandButton = event.target.closest(".kafka-health-expand");
            if (expandButton)
            {
                const expandId = expandButton.dataset.expandId;
                const expanded = expandButton.getAttribute("aria-expanded") === "true";
                widgetElement.querySelectorAll('[data-partition-parent="' + CSS.escape(expandId) + '"]').forEach(function (row)
                {
                    row.classList.toggle("d-none", expanded);
                });
                expandButton.setAttribute("aria-expanded", String(!expanded));
                const icon = expandButton.querySelector("i");
                if (icon) icon.className = expanded ? "bi bi-chevron-down" : "bi bi-chevron-up";
                return;
            }
            const sortHeader = event.target.closest(".kafka-health-sort-header[data-kafka-sort-key]");
            if (sortHeader)
            {
                const nextKey = sortHeader.dataset.kafkaSortKey;
                if (widgetElement.dataset.kafkaSortKey === nextKey)
                    widgetElement.dataset.kafkaSort = widgetElement.dataset.kafkaSort === "asc" ? "desc" : "asc";
                else
                {
                    widgetElement.dataset.kafkaSortKey = nextKey;
                    widgetElement.dataset.kafkaSort = nextKey === "lag" ? "desc" : "asc";
                }
                widgetElement.dataset.kafkaPage = "1";
                updateKafkaHealthWidget(widgetElement);
                return;
            }
            if (event.target.closest(".kafka-health-prev") || event.target.closest(".kafka-health-next"))
            {
                widgetElement.dataset.kafkaPage = String(Math.max(1, Number(widgetElement.dataset.kafkaPage || 1) +
                    (event.target.closest(".kafka-health-prev") ? -1 : 1)));
                updateKafkaHealthWidget(widgetElement);
            }
        });
        contentElement.addEventListener("input", function (event)
        {
            if (!event.target.matches(".kafka-health-search")) return;
            const widgetElement = event.target.closest(".kafka-health-widget");
            if (!widgetElement) return;
            widgetElement.dataset.kafkaPage = "1";
            updateKafkaHealthWidget(widgetElement);
        });
        contentElement.addEventListener("change", function (event)
        {
            if (!event.target.matches(".kafka-health-page-size")) return;
            const widgetElement = event.target.closest(".kafka-health-widget");
            if (!widgetElement) return;
            widgetElement.dataset.kafkaPageSize = event.target.value;
            widgetElement.dataset.kafkaPage = "1";
            updateKafkaHealthWidget(widgetElement);
        });
    }

    function initializeKafkaHealthWidgets()
    {
        contentElement.querySelectorAll(".kafka-health-widget").forEach(updateKafkaHealthWidget);
    }

    /*
     * ------------------------------------------------------------
     * PAGE EVENTS
     * ------------------------------------------------------------
     */

    refreshButton.addEventListener(
        "click",
        function () { loadDashboard(activeTabId); }
    );


    retryButton.addEventListener(
        "click",
        loadDashboard
    );


    applicationFilter.addEventListener("change", function () { loadEnvironments(true); });
    environmentFilter.addEventListener("change", function () { loadDashboard(); });
    contentElement.addEventListener("change", function (event)
    {
        if (!event.target.classList.contains("server-health-filter")) return;
        const widgetId = String(event.target.dataset.widgetId || "");
        const state = Object.assign({ status: "ALL", page: 0, groupBy: "", sortBy: "__health", sortDirection: "asc" }, serverHealthState.get(widgetId) || {});
        state.status = event.target.value;
        state.page = 0;
        serverHealthState.set(widgetId, state);
        loadDashboard(activeTabId);
    });
    contentElement.addEventListener("click", function (event)
    {
        const iframeRefreshButton = event.target.closest(".dashboard-iframe-refresh");
        if (iframeRefreshButton)
        {
            // Refresh the dashboard data first so disabled/deleted registry entries are
            // detected before the browser attempts to load the external page again.
            loadDashboard(activeTabId);
            return;
        }

        const expandButton = event.target.closest(".dashboard-iframe-expand");
        const collapseButton = event.target.closest(".dashboard-iframe-collapse");
        if (expandButton || collapseButton)
        {
            const column = (expandButton || collapseButton).closest(".dashboard-widget-column");
            if (!column) return;
            if (expandButton)
            {
                document.querySelectorAll(".dashboard-iframe-expanded-column").forEach(function (existing) { existing.classList.remove("dashboard-iframe-expanded-column"); });
                column.classList.add("dashboard-iframe-expanded-column");
                document.body.classList.add("dashboard-iframe-expanded");
                const actions = column.querySelector(".dashboard-widget-actions");
                if (actions && !actions.querySelector(".dashboard-iframe-collapse"))
                {
                    const button = document.createElement("button");
                    button.type = "button";
                    button.className = "btn btn-sm btn-link dashboard-iframe-collapse";
                    button.title = "Collapse embedded page";
                    button.setAttribute("aria-label", "Collapse embedded page");
                    button.innerHTML = '<i class="bi bi-fullscreen-exit"></i>';
                    actions.appendChild(button);
                }
                expandButton.hidden = true;
            }
            else
            {
                column.classList.remove("dashboard-iframe-expanded-column");
                document.body.classList.remove("dashboard-iframe-expanded");
                const expand = column.querySelector(".dashboard-iframe-expand");
                if (expand) expand.hidden = false;
                collapseButton.remove();
            }
            return;
        }
        const summaryCard = event.target.closest(".server-health-summary-filter");
        if (summaryCard)
        {
            const widgetId = String(summaryCard.dataset.widgetId || "");
            if (widgetId)
            {
                const state = serverHealthState.get(widgetId) || { status: "ALL", page: 0 };
                state.status = String(summaryCard.dataset.status || "ALL").toUpperCase();
                state.page = 0;
                serverHealthState.set(widgetId, state);
                // Apply the status filter to the dashboard data already in memory.
                renderTabs(activeTabId);
            }
            return;
        }

        const button = event.target.closest(".server-health-page");
        if (!button || button.disabled) return;
        const state = serverHealthState.get(String(button.dataset.widgetId)) || { status: "ALL", page: 0 };
        state.page = Number(button.dataset.page) || 0;
        serverHealthState.set(String(button.dataset.widgetId), state);
        // Pagination is client-side; do not reload dashboard data from the server.
        renderTabs(activeTabId);
    });

    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape" || !document.body.classList.contains("dashboard-iframe-expanded")) return;
        const column = document.querySelector(".dashboard-iframe-expanded-column");
        if (!column) return;
        column.classList.remove("dashboard-iframe-expanded-column");
        document.body.classList.remove("dashboard-iframe-expanded");
        const expand = column.querySelector(".dashboard-iframe-expand");
        if (expand) expand.hidden = false;
        const collapse = column.querySelector(".dashboard-iframe-collapse");
        if (collapse) collapse.remove();
    });

    /* Initial load. */
    initializeDashboard();

})();