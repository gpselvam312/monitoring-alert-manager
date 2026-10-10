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
                return "dashboard-status-green";

            case "YELLOW":
            case "WARNING":
                return "dashboard-status-yellow";

            case "RED":
            case "CRITICAL":
            case "FAILED":
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
                return "Healthy";

            case "YELLOW":
            case "WARNING":
                return "Warning";

            case "RED":
            case "CRITICAL":
            case "FAILED":
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
                return "bi-check-circle-fill";

            case "YELLOW":
            case "WARNING":
                return "bi-exclamation-triangle-fill";

            case "RED":
            case "CRITICAL":
            case "FAILED":
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
	            return "bi-table";

	        case "CHART":
	            return "bi-bar-chart";

	        case "STATUS":
	            return "bi-heart-pulse";

        case "SYSTEM_METRICS":
            return "bi-hdd-rack";


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
        if (typeof value === "number")
        {
            return Number.isFinite(value)
                ? value
                : 0;
        }

        if (
            value === null ||
            value === undefined ||
            value === ""
        )
        {
            return 0;
        }

        const number =
            Number(value);

        return Number.isFinite(number)
            ? number
            : 0;
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


        const datasets =
            seriesNames.map(function (series, index)
            {
                const color =
                    palette[index % palette.length];


                return {
                    label: series,

                    data: labels.map(function (label)
                    {
                        const key =
                            series + "\u0000" + label;

                        return values.has(key)
                            ? values.get(key)
                            : null;
                    }),

                    backgroundColor: color,

                    borderColor: color,

                    borderWidth: 2,

                    borderRadius: 4,

                    tension: 0.3,

                    pointRadius: 3,

                    pointHoverRadius: 5,

                    fill: false
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
    function buildPieChartData(data)
    {
        const labels = [];
        const values = [];

        data.forEach(function (point)
        {
            if (!point)
            {
                return;
            }

            labels.push(
                point.label === null ||
                point.label === undefined
                    ? ""
                    : String(point.label)
            );

            values.push(
                toChartNumber(point.value)
            );
        });


        const colors =
            getThemeChartColors();


        return {
            labels: labels,

            datasets:
            [
                {
                    label: "Value",

                    data: values,

                    backgroundColor:
                        labels.map(function (_, index)
                        {
                            return colors[
                                index % colors.length
                            ];
                        }),

                    borderColor:
                        getChartTheme().border,

                    borderWidth: 1
                }
            ]
        };
    }


    function createChartOptions(chartType)
    {
        const theme =
            getChartTheme();


        const options =
        {
            responsive: true,

            maintainAspectRatio: false,

            animation:
            {
                duration: 250
            },

            plugins:
            {
                legend:
                {
                    position: "bottom",

                    labels:
                    {
                        color: theme.textPrimary,

                        usePointStyle: true,

                        padding: 16
                    }
                },

                tooltip:
                {
                    enabled: true
                }
            }
        };


        if (
            chartType === "bar" ||
            chartType === "line"
        )
        {
            options.interaction =
            {
                mode: "index",
                intersect: false
            };


            options.scales =
            {
                x:
                {
                    ticks:
                    {
                        color:
                            theme.textSecondary
                    },

                    grid:
                    {
                        color:
                            theme.border
                    }
                },

                y:
                {
                    beginAtZero: true,

                    ticks:
                    {
                        color:
                            theme.textSecondary
                    },

                    grid:
                    {
                        color:
                            theme.border
                    }
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

	            if (chartType === "pie")
	            {
	                chartData =
	                    buildPieChartData(data);
	            }
	            else
	            {
	                chartData =
	                    buildChartDatasets(data);
	            }

	            /*
	             * Generate a new random palette for
	             * every dashboard initialization.
	             */
	            const colors =
	                generateRandomChartColors(
	                    data.length
	                );

	            if (
	                chartType === "bar" ||
	                chartType === "pie" ||
	                chartType === "doughnut"
	            )
	            {
	                chartData.datasets.forEach(
	                    function (dataset)
	                    {
	                        dataset.backgroundColor =
	                            colors;

	                        dataset.borderColor =
	                            colors;
	                    }
	                );
	            }
	            else if (chartType === "line")
	            {
	                const lineColor =
	                    colors[0];

	                chartData.datasets.forEach(
	                    function (dataset)
	                    {
	                        dataset.borderColor =
	                            lineColor;

	                        dataset.backgroundColor =
	                            lineColor;

	                        dataset.pointBackgroundColor =
	                            lineColor;

	                        dataset.pointBorderColor =
	                            lineColor;
	                    }
	                );
	            }

	            const chart =
	                new Chart(
	                    canvas,
	                    {
	                        type: chartType,
	                        data: chartData,
	                        options:
	                            createChartOptions(
	                                chartType
	                            )
	                    }
	                );

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

    function getConfiguredTableRows(result, config)
    {
        const payload = result.payload;
        if (config.rowPath)
        {
            const configuredRows = getJsonPath(payload, config.rowPath);
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

    function renderTextWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        const payload = result.payload || {};
        const labels = config.labels && typeof config.labels === "object" ? config.labels : {};

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
        if (Array.isArray(result.data) && result.data.length > 0)
        {
            return result.data;
        }

        const config = parseWidgetFieldConfig(widget);
        const categoryField = config.categoryField || config.labelField;
        const valueField = config.valueField;
        if (!categoryField || !valueField)
        {
            return [];
        }

        return getConfiguredTableRows(result, config).map(function (row)
        {
            return {
                label: getJsonPath(row, categoryField),
                value: getJsonPath(row, valueField),
                series: config.seriesField ? getJsonPath(row, config.seriesField) : undefined
            };
        }).filter(function (point)
        {
            return point.label !== null && point.label !== undefined
                && point.value !== null && point.value !== undefined;
        });
    }


    function formatTableValue(value, format)
    {
        if (value === null || value === undefined) return "";
        if (!format) return typeof value === "object" ? JSON.stringify(value) : value;

        const normalizedFormat = String(format).toLowerCase();
        if (normalizedFormat === "percentage" && Number.isFinite(Number(value)))
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
        const rows = getConfiguredTableRows(result, config).map(function (row) { return Object.assign({}, row); });
        const state = serverHealthState.get(String(widget.id)) || { status: "ALL", page: 0 };
        const fields = Object.assign({
            hostname: "hostname", cpu: "cpu_used_percent", ram: "ram_used_percent",
            load1m: "load_1m", load5m: "load_5m", load15m: "load_15m",
            uptimeSeconds: "uptime_seconds", diskUsedPercent: "u01_used_percent",
            collectionStatus: "collection_status", cpuCores: "cpu_cores",
            ramUsedMb: "ram_used_mb", ramTotalMb: "ram_total_mb"
        }, config.fields && typeof config.fields === "object" ? config.fields : {});
        const pick = function (row, keys)
        {
            for (const key of keys)
            {
                const mapped = fields[key] || key;
                if (row[mapped] !== undefined && row[mapped] !== null && row[mapped] !== "") return row[mapped];
                if (row[key] !== undefined && row[key] !== null && row[key] !== "") return row[key];
            }
            return null;
        };
        const number = function (value)
        {
            if (typeof value === "number") return Number.isFinite(value) ? value : null;
            if (value === null || value === undefined || String(value).trim() === "") return null;
            const parsed = Number(value);
            return Number.isFinite(parsed) ? parsed : null;
        };
        const label = function (key, fallback)
        {
            const labels = config.labels && typeof config.labels === "object" ? config.labels : {};
            return labels[key] || labels[fields[key]] || fallback;
        };
        const collected = function (row)
        {
            return ["SUCCESS", "SUCCEEDED", "OK", "COLLECTED", "HEALTHY", "TRUE"].includes(
                String(pick(row, ["collectionStatus", "collection_status"]) || "").trim().toUpperCase());
        };
        const uptimeText = function (seconds)
        {
            const v = number(seconds);
            if (v === null || v < 0) return "N/A";
            const d = Math.floor(v / 86400), h = Math.floor((v % 86400) / 3600), m = Math.floor((v % 3600) / 60);
            return d > 0 ? d + "d " + h + "h" : h > 0 ? h + "h " + m + "m" : m + "m";
        };
        const items = rows.map(function (row)
        {
            const cpu = number(pick(row, ["cpu", "cpu_used_percent", "cpuUsedPercent", "cpu_percent"]));
            const ram = number(pick(row, ["ram", "ram_used_percent", "ramUsedPercent", "memory_used_percent"]));
            const load1 = number(pick(row, ["load1m", "load_1m", "load_average_1m"]));
            const cores = Math.max(1, number(pick(row, ["cpuCores", "cpu_cores"])) || 1);
            const uptime = number(pick(row, ["uptimeSeconds", "uptime_seconds", "uptime_sec"]));
            const disk = number(pick(row, ["diskUsedPercent", "u01_used_percent", "u01_used_percentage", "u01_percent", "disk_u01_used_percent", "u01_usage_percent"]));
            let status = "UNKNOWN";
            // A successful collection must contain all required System Metrics before health is inferred.
            // Missing uptime or /u01 data is incomplete collection, not a healthy server.
            if (collected(row) && cpu !== null && ram !== null && load1 !== null && uptime !== null && disk !== null)
            {
                const critical = cpu >= 85 || ram >= 90 || load1 / cores >= 1 || disk >= 95;
                const warning = cpu >= 70 || ram >= 75 || load1 / cores >= 0.70 || disk >= 85 || uptime < 72 * 3600;
                status = critical ? "CRITICAL" : warning ? "WARNING" : "HEALTHY";
            }
            return { row: row, cpu: cpu, ram: ram, load1: load1, uptime: uptime, disk: disk, status: status,
                pressure: Math.max((cpu || 0) / 85, (ram || 0) / 90, (load1 || 0) / cores, (disk || 0) / 95) };
        }).sort(function (a, b)
        {
            const rank = { CRITICAL: 0, WARNING: 1, UNKNOWN: 2, HEALTHY: 3 };
            return rank[a.status] - rank[b.status] || b.pressure - a.pressure;
        });
        const counts = { total: items.length, CRITICAL: 0, WARNING: 0, UNKNOWN: 0, HEALTHY: 0 };
        items.forEach(function (item) { counts[item.status]++; });
        const filtered = state.status === "ALL" ? items : items.filter(function (item) { return item.status === state.status; });
        const size = 12, pages = Math.max(1, Math.ceil(filtered.length / size));
        state.page = Math.min(Math.max(0, Number(state.page) || 0), pages - 1);
        serverHealthState.set(String(widget.id), state);
        const pageItems = filtered.slice(state.page * size, (state.page + 1) * size);
        const summaries = [
            ["Total", counts.total, "total", "bi-hdd-rack"], ["Healthy", counts.HEALTHY, "healthy", "bi-check-circle"],
            ["Warning", counts.WARNING, "warning", "bi-exclamation-triangle"], ["Critical", counts.CRITICAL, "critical", "bi-x-octagon"],
            ["Unknown", counts.UNKNOWN, "unknown", "bi-question-circle"]
        ];
        const summary = '<div class="server-health-summary">' + summaries.map(function (x)
        {
            const statusFilter = x[2] === "total" ? "ALL" : x[2].toUpperCase();
            return '<button type="button" class="server-health-summary-item server-health-summary-' + x[2] + ' server-health-summary-filter' +
                (state.status === statusFilter ? ' is-active' : '') + '" data-widget-id="' + escapeHtml(widget.id) +
                '" data-status="' + statusFilter + '" aria-label="Filter servers by ' + x[0] +
                '" aria-pressed="' + (state.status === statusFilter) + '"><span><i class="bi ' + x[3] +
                '" aria-hidden="true"></i> ' + x[0] + '</span><strong class="server-health-' + x[2] +
                '">' + x[1] + '</strong></button>';
        }).join("") + '</div>';
        const filters = '<div class="server-health-toolbar"><span class="small text-muted">Showing ' +
            (filtered.length ? state.page * size + 1 : 0) + "–" + Math.min((state.page + 1) * size, filtered.length) +
            " of " + filtered.length + ' servers</span></div>';
        const metric = function (icon, name, value, meter)
        {
            const display = value === null || value === undefined || value === "" ? "N/A" : escapeHtml(value) + (meter === null ? "" : "%");
            const width = Math.max(0, Math.min(100, number(meter) || 0));
            return '<div class="server-health-metric"><div><span><i class="bi ' + icon + '" aria-hidden="true"></i> ' + escapeHtml(name) + '</span><strong>' + display + '</strong></div>' +
                (meter === null ? "" : '<div class="server-health-meter"><span style="width:' + width + '%"></span></div>') + '</div>';
        };
        const cards = pageItems.map(function (item)
        {
            const row = item.row, host = pick(row, ["hostname", "hostName", "server", "name"]) || "Unknown host";
            const sev = item.status.toLowerCase();
            const loads = [
                number(pick(row, ["load1m", "load_1m"])),
                number(pick(row, ["load5m", "load_5m"])),
                number(pick(row, ["load15m", "load_15m"]))
            ].filter(function (v) { return v !== null; });
            const loadText = loads.length ? loads.map(function (v) { return v.toFixed(2); }).join(" / ") : "N/A";
            const uptime = item.uptime === null ? "N/A" : uptimeText(item.uptime);
            const disk = item.disk === null ? "N/A" : item.disk + "%";
            const ramUsed = pick(row, ["ramUsedMb", "ram_used_mb"]), ramTotal = pick(row, ["ramTotalMb", "ram_total_mb"]);
            const statusIcon = { CRITICAL: "bi-x-octagon-fill", WARNING: "bi-exclamation-triangle-fill", UNKNOWN: "bi-question-circle-fill", HEALTHY: "bi-check-circle-fill" }[item.status];
            let extra = "";
            extra += '<div class="server-health-detail"><span><i class="bi bi-activity" aria-hidden="true"></i> ' + escapeHtml(label("load1m", "Load (1m / 5m / 15m)")) + '</span><strong>' + escapeHtml(loadText) + '</strong></div>';
            extra += '<div class="server-health-detail"><span><i class="bi bi-clock-history" aria-hidden="true"></i> ' + escapeHtml(label("uptimeSeconds", "Uptime")) + '</span><strong>' + escapeHtml(uptime) + '</strong></div>';
            extra += '<div class="server-health-detail"><span><i class="bi bi-device-hdd" aria-hidden="true"></i> ' + escapeHtml(label("diskUsedPercent", "/u01 Used")) + '</span><strong>' + escapeHtml(disk) + '</strong></div>';
            const ramCapacity = ramUsed !== null && ramTotal !== null ? '<div class="server-health-foot">' + escapeHtml(ramUsed) + " / " + escapeHtml(ramTotal) + ' MB RAM</div>' : "";
            return '<article class="server-health-card server-health-card-' + sev + '"><header class="server-health-card-header"><div class="server-health-host"><i class="bi bi-hdd-network" aria-hidden="true"></i><strong title="' + escapeHtml(host) + '">' + escapeHtml(host) + '</strong></div><span class="server-health-status server-health-status-' + sev + '"><i class="bi ' + statusIcon + '" aria-hidden="true"></i> ' + item.status + '</span></header>' +
                metric("bi-cpu", label("cpu", "CPU"), item.cpu, item.cpu) +
                metric("bi-memory", label("ram", "RAM"), item.ram, item.ram) + extra + ramCapacity + '</article>';
        }).join("");
        const pager = '<div class="server-health-pagination"><button type="button" class="btn btn-sm btn-outline-secondary server-health-page" data-widget-id="' + escapeHtml(widget.id) + '" data-page="' + Math.max(0, state.page - 1) + '"' + (state.page === 0 ? " disabled" : "") + '><i class="bi bi-chevron-left me-1" aria-hidden="true"></i>Previous</button><span class="small text-muted">Page ' + (state.page + 1) + " of " + pages + '</span><button type="button" class="btn btn-sm btn-outline-secondary server-health-page" data-widget-id="' + escapeHtml(widget.id) + '" data-page="' + Math.min(pages - 1, state.page + 1) + '"' + (state.page >= pages - 1 ? " disabled" : "") + '>Next<i class="bi bi-chevron-right ms-1" aria-hidden="true"></i></button></div>';
        return summary + filters + '<div class="server-health-grid">' + (cards || '<div class="server-health-empty"><i class="bi bi-search" aria-hidden="true"></i><span>No servers match this status filter.</span></div>') + '</div>' + pager;
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
        const pageSize = 12, pages = Math.max(1, Math.ceil(filtered.length / pageSize));
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
        return summary + filters + '<div class="server-health-grid">' + (cards || '<div class="text-muted small p-3">No servers match this status filter.</div>') + "</div>" + pager;
    }

    function renderTableWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        if (String(config.renderer || '').toUpperCase() === 'SERVER_HEALTH') return renderServerHealthCards(result, widget, config);
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
        columns = columns.map(function (column)
        {
            return Object.assign({}, column, { label: labels[column.key] || column.label || column.key });
        });

        if (columns.length === 0)
            return '<div class="text-muted small p-3">No columns match the configured visible fields.</div>';

        const headerHtml = columns.map(function (column, index)
        {
            return '<th scope="col" class="dashboard-table-sortable" data-column-index="' +
                index + '" data-column-key="' + escapeHtml(column.key || "") +
                '"><span class="dashboard-table-header-content"><span>' +
                escapeHtml(column.label || column.key || "") +
                '</span><i class="bi bi-arrow-down-up dashboard-table-sort-icon"></i></span></th>';
        }).join("");

        const bodyHtml = rows.length
            ? rows.map(function (row)
                {
                    return '<tr>' + columns.map(function (column)
                    {
                        const value = Object.prototype.hasOwnProperty.call(row, column.key)
                            ? row[column.key]
                            : getJsonPath(row, column.key);
                        return '<td>' + escapeHtml(formatTableValue(value, formats[column.key])) + '</td>';
                    }).join("") + '</tr>';
                }).join("")
            : '<tr><td colspan="' + columns.length + '">No data available.</td></tr>';

        return '<div class="dashboard-table-wrapper"><table class="table dashboard-table mb-0">' +
            '<thead><tr>' + headerHtml + '</tr></thead><tbody>' + bodyHtml + '</tbody></table></div>';
    }

    function renderStatusWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        const configuredValue = config.valueField
            ? getJsonPath(result.payload || {}, config.valueField)
            : undefined;
        const value = configuredValue !== undefined && configuredValue !== null
            ? configuredValue
            : (result.value !== null && result.value !== undefined ? result.value : "");
        const message = result.message || "";
        const labels = config.labels && typeof config.labels === "object" ? config.labels : {};

        let metricsHtml = "";
        if (result.metrics && typeof result.metrics === "object")
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
                        return '<div class="dashboard-status-metric">' +
                            '<span class="dashboard-status-metric-label">' +
                                escapeHtml(labels[entry[0]] || entry[0]) +
                            '</span><span class="dashboard-status-metric-value">' +
                                escapeHtml(entry[1]) +
                            '</span></div>';
                    }).join("") +
                    '</div>';
            }
        }

        return '<div class="dashboard-status-content">' +
            (value !== "" ? '<div class="dashboard-status-value">' + escapeHtml(value) + '</div>' : "") +
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


        const isSystemMetrics = widgetType === "SYSTEM_METRICS";
        let updatedHtml = "";
        let headerUpdatedHtml = "";
        if (updated)
        {
            if (isSystemMetrics)
            {
                headerUpdatedHtml = '<div class="dashboard-widget-updated dashboard-widget-collected"><i class="bi bi-clock-history me-1" aria-hidden="true"></i>Last collection: ' + escapeHtml(updated) + '</div>';
            }
            else
            {
                updatedHtml = '<div class="dashboard-widget-updated">Updated ' + escapeHtml(updated) + '</div>';
            }
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


        const refreshButton =
            '<button type="button"' +
                    ' class="btn btn-sm btn-link dashboard-widget-refresh"' +
                    ' data-widget-id="' + escapeHtml(widget.id) + '"' +
                    ' title="Refresh widget" aria-label="Refresh widget">' +
                '<i class="bi bi-arrow-clockwise"></i>' +
            '</button>';

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

        /*
         * TABLE
         */

        else if (widgetType === "SYSTEM_METRICS")
        {
            widgetContentHtml = renderSystemMetricsCards(result, widget, parseWidgetFieldConfig(widget));
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

            '<div class="' +
                getWidgetSizeClass(widgetType === "SYSTEM_METRICS" ? "FULL" : widget.size) +
            '">' +

                '<div class="dashboard-widget h-100">' +


                    '<div class="dashboard-widget-header">' +


                        '<div class="dashboard-widget-title-group">' +


                            '<div class="dashboard-widget-icon">' +

                                '<i class="bi ' +
                                    escapeHtml(
                                        getWidgetIcon(widget)
                                    ) +
                                '"></i>' +

                            '</div>' +


                            '<div class="min-w-0">' +

                                '<h5 class="dashboard-widget-title">' +

                                    escapeHtml(
                                        widget.name ||
                                        "Widget"
                                    ) +

                                '</h5>' +

                                headerUpdatedHtml +


                                (
                                    widget.description

                                        ? (
                                            '<div class="dashboard-widget-description">' +

                                                escapeHtml(
                                                    widget.description
                                                ) +

                                            '</div>'
                                        )

                                        : ""
                                ) +

                            '</div>' +

                        '</div>' +


                        '<div class="dashboard-widget-actions">' +


                            '<span class="dashboard-status ' +
                                statusClass +
                            '">' +

                                '<i class="bi ' +
                                    statusIcon +
                                '"></i>' +

                                '<span>' +
                                    escapeHtml(
                                        statusLabel
                                    ) +
                                '</span>' +

                            '</span>' +


                            refreshButton +


                            detailsButton +


                        '</div>' +


                    '</div>' +


                    '<div class="dashboard-widget-body">' +

                        '<div class="dashboard-widget-main">' +

                            widgetContentHtml +

                        '</div>' +

                        updatedHtml +

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


                const widgetsHtml =
                    widgets.length

                        ? widgets.map(
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


        /*
         * Canvas elements now exist in the DOM.
         */
        initializeDashboardCharts();

        configureAutoRefresh();


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
        const response = await fetch(applicationsApiUrl, { headers: { 'Accept': 'application/json' }, cache: 'no-store' });
        if (!response.ok) throw new Error('Unable to load applications (' + response.status + ')');
        const items = await response.json();
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
        const response = await fetch(environmentsApiUrl + '?applicationId=' + encodeURIComponent(applicationFilter.value), { headers: { 'Accept': 'application/json' }, cache: 'no-store' });
        if (!response.ok) throw new Error('Unable to load environments (' + response.status + ')');
        const items = await response.json();
        items.forEach(function (item) { const option = document.createElement('option'); option.value = item.id; option.textContent = item.name; environmentFilter.appendChild(option); });
        if (!items.length) { dashboardData = []; showState('empty'); return; }
        const prod = items.find(function (item) { return String(item.name).toUpperCase() === 'PROD'; });
        environmentFilter.value = String(preferDefault ? (prod || items[0]).id : (items.find(function (item) { return String(item.id) === environmentFilter.value; }) || prod || items[0]).id);
        environmentFilter.disabled = items.length <= 1;
        await loadDashboard();
    }

    async function initializeDashboard()
    {
        try { const items = await loadApplications(); if (!items.length) { showState('empty'); return; } await loadEnvironments(true); }
        catch (error) { errorMessageElement.textContent = error && error.message ? error.message : 'Unable to initialize dashboard filters.'; showState('error'); }
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

			showState("content");
        }
        catch (error)
        {
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
        serverHealthState.set(String(event.target.dataset.widgetId), { status: event.target.value, page: 0 });
        loadDashboard(activeTabId);
    });
    contentElement.addEventListener("click", function (event)
    {
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

    /* Initial load. */
    initializeDashboard();

})();