(function ()
{
    "use strict";


    /*
     * ------------------------------------------------------------
     * CONFIGURATION
     * ------------------------------------------------------------
     */

    const apiUrl = "/api/dashboard";


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

    const applicationFilter =
        document.getElementById("dashboardApplicationFilter");

    const environmentFilter =
        document.getElementById("dashboardEnvironmentFilter");

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


    /*
     * ------------------------------------------------------------
     * STATE
     * ------------------------------------------------------------
     */

    let dashboardData = [];
    let selectedApplicationId = null;
    let selectedEnvironmentId = null;
    let dashboardFiltersBound = false;
    const serverHealthViewState = new Map();
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
            state !== "content" && state !== "empty"
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
	        case "SERVER_HEALTH":
	            return "bi-hdd-stack";

	        case "STAT":
	            return "bi-speedometer2";

	        case "TABLE":
	            return "bi-table";

	        case "CHART":
	            return "bi-bar-chart";

	        case "STATUS":
	            return "bi-heart-pulse";

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
            return Array.isArray(configuredRows) ? configuredRows.filter(isObjectRow) : [];
        }

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

    function renderTableWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
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

    function renderServerHealthWidget(result, widget)
    {
        const config = parseWidgetFieldConfig(widget);
        const rows = getConfiguredTableRows(result, config);
        if (rows.length === 0)
        {
            return '<div class="dashboard-chart-empty"><i class="bi bi-hdd-stack"></i>' +
                '<span>No server health records available.</span></div>';
        }

        const thresholds = config.thresholds && typeof config.thresholds === "object" ? config.thresholds : {};
        const cpuThresholds = thresholds.cpu || {};
        const ramThresholds = thresholds.ram || {};
        const loadThresholds = thresholds.loadPerCore || {};
        const fields = Object.assign({
            hostname: "hostname", cpu: "cpu_used_percent", ram: "ram_used_percent",
            cpuCores: "cpu_cores", load: "load_1m", ramUsed: "ram_used_mb",
            ramTotal: "ram_total_mb", collectionStatus: "collection_status"
        }, config.fields || {});
        const cpuWarning = Number(cpuThresholds.warning ?? 70);
        const cpuCritical = Number(cpuThresholds.critical ?? 85);
        const ramWarning = Number(ramThresholds.warning ?? 75);
        const ramCritical = Number(ramThresholds.critical ?? 90);
        const loadWarning = Number(loadThresholds.warning ?? 0.7);
        const loadCritical = Number(loadThresholds.critical ?? 1.0);

        function numeric(value)
        {
            if (value === null || value === undefined || value === "") return null;
            const number = Number(value);
            return Number.isFinite(number) ? number : null;
        }
        function metricStatus(value, warning, critical)
        {
            if (value === null) return "UNKNOWN";
            if (value >= critical) return "CRITICAL";
            if (value >= warning) return "WARNING";
            return "HEALTHY";
        }
        function rank(status)
        {
            return status === "CRITICAL" ? 0 : status === "WARNING" ? 1 : status === "UNKNOWN" ? 2 : 3;
        }
        function tone(status)
        {
            return status === "CRITICAL" ? "danger" : status === "WARNING" ? "warning"
                : status === "UNKNOWN" ? "secondary" : "success";
        }
        function displayNumber(value, suffix)
        {
            return value === null ? "N/A"
                : Number(value).toLocaleString(undefined, { maximumFractionDigits: 1 }) + (suffix || "");
        }

        const servers = rows.map(function (row)
        {
            const hostnameValue = getJsonPath(row, fields.hostname);
            const cpu = numeric(getJsonPath(row, fields.cpu));
            const ram = numeric(getJsonPath(row, fields.ram));
            const cores = numeric(getJsonPath(row, fields.cpuCores));
            const load = numeric(getJsonPath(row, fields.load));
            const ramUsed = numeric(getJsonPath(row, fields.ramUsed));
            const ramTotal = numeric(getJsonPath(row, fields.ramTotal));
            const collectionStatus = getJsonPath(row, fields.collectionStatus);
            const collectionFailed = collectionStatus !== null && collectionStatus !== undefined
                && String(collectionStatus).trim() !== ""
                && String(collectionStatus).trim().toUpperCase() !== "SUCCESS";
            const normalizedLoad = load !== null && cores !== null && cores > 0 ? load / cores : null;
            const cpuStatus = metricStatus(cpu, cpuWarning, cpuCritical);
            const ramStatus = metricStatus(ram, ramWarning, ramCritical);
            const loadStatus = metricStatus(normalizedLoad, loadWarning, loadCritical);
            const statuses = [cpuStatus, ramStatus, loadStatus].filter(function (s) { return s !== "UNKNOWN"; });
            let status = statuses.length === 0 ? "UNKNOWN"
                : statuses.some(function (s) { return s === "CRITICAL"; }) ? "CRITICAL"
                : statuses.some(function (s) { return s === "WARNING"; }) ? "WARNING" : "HEALTHY";
            if (collectionFailed) status = "UNKNOWN";
            const pressure = Math.max(
                cpu === null || cpuCritical <= 0 ? 0 : cpu / cpuCritical,
                ram === null || ramCritical <= 0 ? 0 : ram / ramCritical,
                normalizedLoad === null || loadCritical <= 0 ? 0 : normalizedLoad / loadCritical
            );
            return {
                hostname: hostnameValue === null || hostnameValue === undefined || hostnameValue === ""
                    ? "Unknown server" : String(hostnameValue),
                cpu: cpu, ram: ram, cores: cores, load: load, normalizedLoad: normalizedLoad,
                ramUsed: ramUsed, ramTotal: ramTotal, collectionStatus: collectionStatus,
                collectionFailed: collectionFailed, cpuStatus: cpuStatus, ramStatus: ramStatus,
                loadStatus: loadStatus, status: status, pressure: pressure
            };
        });

        servers.sort(function (left, right)
        {
            const severity = rank(left.status) - rank(right.status);
            return severity !== 0 ? severity : right.pressure - left.pressure;
        });

        const counts = { CRITICAL: 0, WARNING: 0, HEALTHY: 0, UNKNOWN: 0 };
        servers.forEach(function (server) { counts[server.status]++; });
        const summary = '<div class="row g-2 mb-3 dashboard-server-health-summary">' +
            [
                ["Total", servers.length, "secondary"], ["Healthy", counts.HEALTHY, "success"],
                ["Warning", counts.WARNING, "warning"], ["Critical", counts.CRITICAL, "danger"],
                ["Unknown", counts.UNKNOWN, "secondary"]
            ].map(function (item)
            {
                return '<div class="col-6 col-md"><div class="border rounded p-2 h-100">' +
                    '<div class="small text-muted">' + escapeHtml(item[0]) + '</div>' +
                    '<div class="fs-5 fw-semibold text-' + item[2] + '">' + item[1] + '</div></div></div>';
            }).join("") + '</div>';

        const viewState = serverHealthViewState.get(String(widget.id)) || { filter: "ALL", page: 1 };
        const allowedFilters = ["ALL", "CRITICAL", "WARNING", "HEALTHY", "UNKNOWN"];
        const activeFilter = allowedFilters.includes(viewState.filter) ? viewState.filter : "ALL";
        const filteredServers = activeFilter === "ALL"
            ? servers
            : servers.filter(function (server) { return server.status === activeFilter; });
        const pageSize = 12;
        const pageCount = Math.max(1, Math.ceil(filteredServers.length / pageSize));
        const currentPage = Math.min(Math.max(1, Number(viewState.page) || 1), pageCount);
        serverHealthViewState.set(String(widget.id), { filter: activeFilter, page: currentPage });
        const visibleServers = filteredServers.slice((currentPage - 1) * pageSize, currentPage * pageSize);

        const filterBar = '<div class="d-flex flex-wrap gap-2 mb-3">' +
            [
                ["ALL", "All", servers.length],
                ["CRITICAL", "Critical", counts.CRITICAL],
                ["WARNING", "Warning", counts.WARNING],
                ["HEALTHY", "Healthy", counts.HEALTHY],
                ["UNKNOWN", "Unknown", counts.UNKNOWN]
            ].map(function (item)
            {
                const active = item[0] === activeFilter;
                return '<button type="button" class="btn btn-sm ' +
                    (active ? "btn-primary" : "btn-outline-secondary") +
                    ' server-health-filter" data-widget-id="' + escapeHtml(widget.id) +
                    '" data-filter="' + item[0] + '">' + item[1] + ' <span class="ms-1">' + item[2] + '</span></button>';
            }).join("") + '</div>';

        const cards = visibleServers.map(function (server)
        {
            const serverTone = tone(server.status);
            const metrics = [
                { label: "CPU", value: server.cpu, status: server.cpuStatus },
                { label: "RAM", value: server.ram, status: server.ramStatus }
            ].map(function (metric)
            {
                const metricTone = tone(metric.status);
                const width = metric.value === null ? 0 : Math.max(0, Math.min(100, metric.value));
                return '<div class="col-6"><div class="small text-muted">' + metric.label + '</div>' +
                    '<div class="fw-semibold text-' + metricTone + '">' + escapeHtml(displayNumber(metric.value, "%")) + '</div>' +
                    '<div class="progress mt-1" style="height:6px" role="progressbar" aria-label="' + metric.label +
                    ' usage" aria-valuenow="' + width + '" aria-valuemin="0" aria-valuemax="100">' +
                    '<div class="progress-bar bg-' + metricTone + '" style="width:' + width + '%"></div></div></div>';
            }).join("");

            const loadLabel = server.load === null ? "N/A"
                : displayNumber(server.load, "") + (server.normalizedLoad !== null
                    ? " (" + displayNumber(server.normalizedLoad, "") + " per core)" : "");
            const memoryDetail = server.ramUsed !== null && server.ramTotal !== null
                ? '<div class="small text-muted mt-2">' + escapeHtml(displayNumber(server.ramUsed, "") +
                    " / " + displayNumber(server.ramTotal, "") + " MB") + '</div>' : "";
            const collectionMessage = server.collectionFailed
                ? '<div class="small text-secondary mt-2"><i class="bi bi-exclamation-circle me-1"></i>Collection: ' +
                    escapeHtml(server.collectionStatus) + '</div>' : "";
            const statusLabel = server.status === "HEALTHY" ? "Healthy" : server.status === "WARNING" ? "Warning"
                : server.status === "CRITICAL" ? "Critical" : "Unknown";

            return '<div class="col-12 col-md-6 col-xl-4"><article class="border border-start border-4 border-' +
                serverTone + ' rounded p-3 h-100 dashboard-server-health-card">' +
                '<div class="d-flex align-items-start gap-2 mb-3"><i class="bi bi-hdd-stack fs-5 text-' + serverTone +
                '"></i><div class="flex-grow-1 min-w-0"><div class="fw-semibold text-break">' +
                escapeHtml(server.hostname) + '</div><div class="small text-muted">Load (1m): <span class="fw-semibold text-' +
                tone(server.loadStatus) + '">' + escapeHtml(loadLabel) + '</span></div></div><span class="badge text-bg-' + serverTone + '">' +
                statusLabel + '</span></div><div class="row g-3">' + metrics + '</div>' +
                memoryDetail + collectionMessage + '</article></div>';
        }).join("");

        const firstShown = filteredServers.length === 0 ? 0 : (currentPage - 1) * pageSize + 1;
        const lastShown = Math.min(currentPage * pageSize, filteredServers.length);
        const pagination = '<div class="d-flex flex-wrap align-items-center justify-content-between gap-2 mt-3">' +
            '<small class="text-muted">Showing ' + firstShown + '–' + lastShown + ' of ' + filteredServers.length + ' servers</small>' +
            '<div class="btn-group btn-group-sm" role="group" aria-label="Server health pagination">' +
            '<button type="button" class="btn btn-outline-secondary server-health-page" data-widget-id="' +
            escapeHtml(widget.id) + '" data-page="' + (currentPage - 1) + '"' +
            (currentPage <= 1 ? ' disabled' : '') + '>Previous</button>' +
            '<button type="button" class="btn btn-outline-secondary server-health-page" data-widget-id="' +
            escapeHtml(widget.id) + '" data-page="' + (currentPage + 1) + '"' +
            (currentPage >= pageCount ? ' disabled' : '') + '>Next</button></div></div>';

        const cardsHtml = cards || '<div class="col-12"><div class="text-muted small p-3">No servers match this filter.</div></div>';
        return summary + filterBar + '<div class="row g-3 dashboard-server-health-list">' + cardsHtml + '</div>' + pagination;
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


        let updatedHtml = "";


        if (updated)
        {
            updatedHtml =
                '<div class="dashboard-widget-updated">' +

                    'Updated ' +

                    escapeHtml(updated) +

                '</div>';
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

        else if (widgetType === "SERVER_HEALTH")
        {
            widgetContentHtml = renderServerHealthWidget(result, widget);
        }

        /*
         * TABLE
         */

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
                getWidgetSizeClass(widget.size) +
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
        bindServerHealthViewEvents();


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

        renderTabs();
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

    /*
     * ------------------------------------------------------------
     * LOAD DASHBOARD
     * ------------------------------------------------------------
     */

    function bindServerHealthViewEvents()
    {
        contentElement.querySelectorAll(".server-health-filter").forEach(function (button)
        {
            button.addEventListener("click", function ()
            {
                const widgetId = String(button.dataset.widgetId);
                serverHealthViewState.set(widgetId, { filter: button.dataset.filter || "ALL", page: 1 });
                const activeTab = tabsElement.querySelector(".dashboard-tab.active");
                renderTabs(activeTab ? activeTab.dataset.tabId : undefined);
            });
        });

        contentElement.querySelectorAll(".server-health-page").forEach(function (button)
        {
            button.addEventListener("click", function ()
            {
                if (button.disabled) return;
                const widgetId = String(button.dataset.widgetId);
                const current = serverHealthViewState.get(widgetId) || { filter: "ALL", page: 1 };
                serverHealthViewState.set(widgetId, {
                    filter: current.filter,
                    page: Number(button.dataset.page) || 1
                });
                const activeTab = tabsElement.querySelector(".dashboard-tab.active");
                renderTabs(activeTab ? activeTab.dataset.tabId : undefined);
            });
        });
    }


    function populateDashboardFilter(select, options, selectedId, placeholder)
    {
        if (!select)
        {
            return;
        }

        select.innerHTML = "";

        if (!Array.isArray(options) || options.length === 0)
        {
            const option = document.createElement("option");
            option.value = "";
            option.textContent = placeholder;
            select.appendChild(option);
            select.disabled = true;
            return;
        }

        options.forEach(function (item)
        {
            const option = document.createElement("option");
            option.value = String(item.id);
            option.textContent = item.name;
            select.appendChild(option);
        });

        select.disabled = options.length <= 1;
        if (selectedId !== null && selectedId !== undefined)
        {
            select.value = String(selectedId);
        }
        if (!select.value && options.length > 0)
        {
            select.value = String(options[0].id);
        }
    }


    function bindDashboardFilterEvents()
    {
        if (dashboardFiltersBound)
        {
            return;
        }

        if (applicationFilter)
        {
            applicationFilter.addEventListener("change", function ()
            {
                selectedApplicationId = applicationFilter.value || null;
                // A new application gets its own PROD-first default environment.
                loadDashboard(undefined, selectedApplicationId, null);
            });
        }

        if (environmentFilter)
        {
            environmentFilter.addEventListener("change", function ()
            {
                selectedEnvironmentId = environmentFilter.value || null;
                loadDashboard(undefined, selectedApplicationId, selectedEnvironmentId);
            });
        }

        dashboardFiltersBound = true;
    }


    async function loadDashboard(preferredTabId, requestedApplicationId, requestedEnvironmentId)
    {
        clearAutoRefreshTimers();
        showState("loading");
        refreshButton.disabled = true;
        bindDashboardFilterEvents();

        try
        {
            const targetApplicationId =
                requestedApplicationId !== undefined
                    ? requestedApplicationId
                    : selectedApplicationId;

            const targetEnvironmentId =
                requestedEnvironmentId !== undefined
                    ? requestedEnvironmentId
                    : selectedEnvironmentId;

            const filterParams = new URLSearchParams();
            if (targetApplicationId)
            {
                filterParams.set("applicationId", targetApplicationId);
            }
            if (targetEnvironmentId)
            {
                filterParams.set("environmentId", targetEnvironmentId);
            }

            const filterResponse = await fetch(
                "/api/dashboard/filters" +
                    (filterParams.toString() ? "?" + filterParams.toString() : ""),
                {
                    method: "GET",
                    headers: { "Accept": "application/json" },
                    cache: "no-store"
                }
            );

            if (!filterResponse.ok)
            {
                throw new Error("Dashboard filter request failed (" + filterResponse.status + ")");
            }

            const filters = await filterResponse.json();
            populateDashboardFilter(
                applicationFilter,
                filters.applications,
                filters.selectedApplicationId,
                "No applications available"
            );
            populateDashboardFilter(
                environmentFilter,
                filters.environments,
                filters.selectedEnvironmentId,
                "No environments configured"
            );

            selectedApplicationId = filters.selectedApplicationId || null;
            selectedEnvironmentId = filters.selectedEnvironmentId || null;

            if (!selectedApplicationId || !selectedEnvironmentId)
            {
                dashboardData = [];
                if (dateRangeContainer) dateRangeContainer.classList.add("d-none");
                destroyDashboardCharts();
                tabsElement.innerHTML = "";
                contentElement.innerHTML = "";
                showState("empty");
                return;
            }

            const dashboardParams = new URLSearchParams({
                applicationId: String(selectedApplicationId),
                environmentId: String(selectedEnvironmentId)
            });

            const response = await fetch(
                apiUrl + "?" + dashboardParams.toString(),
                {
                    method: "GET",
                    headers: { "Accept": "application/json" },
                    cache: "no-store"
                }
            );

            if (!response.ok)
            {
                throw new Error("Dashboard request failed (" + response.status + ")");
            }

            const data = await response.json();

            if (!Array.isArray(data) || data.length === 0)
            {
                dashboardData = [];
                if (dateRangeContainer) dateRangeContainer.classList.add("d-none");
                destroyDashboardCharts();
                tabsElement.innerHTML = "";
                contentElement.innerHTML = "";
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
            console.error("Unable to load dashboard", error);
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
        function ()
        {
            loadDashboard(undefined, selectedApplicationId, selectedEnvironmentId);
        }
    );


    retryButton.addEventListener(
        "click",
        function ()
        {
            loadDashboard(undefined, selectedApplicationId, selectedEnvironmentId);
        }
    );


    /*
     * Initial load.
     */
    loadDashboard(undefined, null, null);

})();