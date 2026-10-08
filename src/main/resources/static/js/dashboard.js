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


    /*
     * ------------------------------------------------------------
     * STATE
     * ------------------------------------------------------------
     */

    let dashboardData = [];

    /*
     * Keep references to Chart.js instances.
     *
     * Key   = canvas id
     * Value = Chart.js instance
     */
    const dashboardCharts = new Map();


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

	            const data =
	                Array.isArray(result.data)
	                    ? result.data
	                    : [];

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

	function renderTableWidget(result)
	{
	    const columns =
	        Array.isArray(result.columns)
	            ? result.columns
	            : [];

	    const rows =
	        Array.isArray(result.rows)
	            ? result.rows
	            : [];

	    if (columns.length === 0)
	    {
	        return (
	            '<div class="dashboard-table-empty">' +
	                '<i class="bi bi-table"></i>' +
	                '<span>' +
	                    'No table columns configured.' +
	                '</span>' +
	            '</div>'
	        );
	    }

	    const headerHtml =
	        columns.map(function (column, index)
	        {
	            return (
	                '<th scope="col"' +
	                    ' class="dashboard-table-sortable"' +
	                    ' data-column-index="' +
	                        index +
	                    '"' +
	                    ' data-column-key="' +
	                        escapeHtml(column.key || "") +
	                    '"' +
	                    '>' +
	                    '<span class="dashboard-table-header-content">' +
	                        '<span>' +
	                            escapeHtml(
	                                column.label ||
	                                column.key ||
	                                ""
	                            ) +
	                        '</span>' +
	                        '<i class="bi bi-arrow-down-up dashboard-table-sort-icon"></i>' +
	                    '</span>' +
	                '</th>'
	            );
	        }).join("");

	    const bodyHtml =
	        rows.length
	            ? rows.map(function (row)
	            {
	                return (
	                    '<tr>' +
	                        columns.map(function (column)
	                        {
	                            const value =
	                                row[column.key];

	                            return (
	                                '<td>' +
	                                    escapeHtml(
	                                        value === null ||
	                                        value === undefined
	                                            ? ""
	                                            : value
	                                    ) +
	                                '</td>'
	                            );
	                        }).join("") +
	                    '</tr>'
	                );
	            }).join("")
	            : (
	                '<tr>' +
	                    '<td colspan="' +
	                        columns.length +
	                    '">' +
	                        'No data available.' +
	                    '</td>' +
	                '</tr>'
	            );

	    return (
	        '<div class="dashboard-table-wrapper">' +
	            '<table class="table dashboard-table mb-0">' +
	                '<thead>' +
	                    '<tr>' +
	                        headerHtml +
	                    '</tr>' +
	                '</thead>' +
	                '<tbody>' +
	                    bodyHtml +
	                '</tbody>' +
	            '</table>' +
	        '</div>'
	    );
	}

	
	function renderStatusWidget(result)
	{
	    const value =
	        result.value !== null &&
	        result.value !== undefined
	            ? result.value
	            : "";

	    const message =
	        result.message || "";

	    let metricsHtml = "";

	    if (
	        result.metrics &&
	        typeof result.metrics === "object"
	    )
	    {
	        const metricEntries =
	            Object.entries(result.metrics);

	        if (metricEntries.length > 0)
	        {
	            metricsHtml =
	                '<div class="dashboard-status-metrics">' +
	                    metricEntries.map(
	                        function (entry)
	                        {
	                            return (
	                                '<div class="dashboard-status-metric">' +
	                                    '<span class="dashboard-status-metric-label">' +
	                                        escapeHtml(entry[0]) +
	                                    '</span>' +
	                                    '<span class="dashboard-status-metric-value">' +
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
	        '<div class="dashboard-status-content">' +
	            (
	                value !== ""
	                    ? (
	                        '<div class="dashboard-status-value">' +
	                            escapeHtml(value) +
	                        '</div>'
	                    )
	                    : ""
	            ) +
	            (
	                message
	                    ? (
	                        '<div class="dashboard-status-message">' +
	                            escapeHtml(message) +
	                        '</div>'
	                    )
	                    : ""
	            ) +
	            metricsHtml +
	        '</div>'
	    );
	}
	
	function renderStatWidget(result)
	{
	    const value =
	        result.value !== null &&
	        result.value !== undefined
	            ? result.value
	            : "";

	    const message =
	        result.message || "";

	    let metricsHtml = "";

	    if (
	        result.metrics &&
	        typeof result.metrics === "object"
	    )
	    {
	        const metricEntries =
	            Object.entries(result.metrics);

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
	                                        escapeHtml(entry[0]) +
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

                    ' data-widget-id="' +
                        escapeHtml(widget.id) +
                    '"' +

                    ' title="Refresh widget">' +

                '<i class="bi bi-arrow-clockwise"></i>' +

            '</button>';


        let widgetContentHtml;


        /*
         * KPI
         */

		if (isStat)
		{
		    widgetContentHtml =
		        renderStatWidget(result);
		}				
		else if (widgetType === "STATUS")
		{
		    widgetContentHtml =
		        renderStatusWidget(result);
		}
		else if (widgetType === "TEXT")
		{
		    widgetContentHtml =
		        '<div class="dashboard-text-content">' +
		            (
		                message
		                    ? '<div class="dashboard-text-message">' +
		                        escapeHtml(message) +
		                      '</div>'
		                    : '<div class="dashboard-text-empty">' +
		                        'No text available.' +
		                      '</div>'
		            ) +
		        '</div>';
		}

        /*
         * TABLE
         */

        else if (widgetType === "TABLE")
        {
            widgetContentHtml =
                renderTableWidget(result);
        }


        /*
         * CHART
         */
		else if (widgetType === "CHART")
		{
            const chartData =
                Array.isArray(result.data)
                    ? result.data
                    : [];


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

    function renderTabs()
    {
        /*
         * Destroy existing charts before replacing
         * their canvas elements.
         */
        destroyDashboardCharts();


        tabsElement.innerHTML = "";

        contentElement.innerHTML = "";


        dashboardData.forEach(
            function (tab, index)
            {
                const tabId =
                    "dashboard-tab-" +
                    tab.id;


                const paneId =
                    "dashboard-pane-" +
                    tab.id;


                const active =
                    index === 0;


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

    function bindWidgetActions()
    {
        /*
         * Keep widget refresh disabled for now.
         *
         * The actual per-widget execution API is not yet
         * implemented.
         */
        contentElement
            .querySelectorAll(
                ".dashboard-widget-refresh"
            )
            .forEach(
                function (button)
                {
                    button.addEventListener(
                        "click",
                        function (event)
                        {
                            event.preventDefault();
                            event.stopPropagation();
                        }
                    );


                    button.setAttribute(
                        "disabled",
                        "disabled"
                    );


                    button.setAttribute(
                        "title",
                        "Widget refresh will be enabled with live data execution"
                    );
                }
            );


        /*
         * Details button.
         */
        contentElement
            .querySelectorAll(
                ".dashboard-widget-details"
            )
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


                            /*
                             * Keep the common details URL ready.
                             * The backend details page can be
                             * connected when that part is implemented.
                             */
                            window.location.href =
                                "/dashboard/widgets/" +
                                encodeURIComponent(
                                    widgetId
                                ) +
                                "/details";
                        }
                    );
                }
            );
			
			/*
			 * Table column sorting.
			 */
			contentElement
			    .querySelectorAll(
			        ".dashboard-table-sortable"
			    )
			    .forEach(
			        function (header)
			        {
			            header.addEventListener(
			                "click",
			                function ()
			                {
			                    const table =
			                        header.closest(
			                            ".dashboard-table"
			                        );

			                    if (!table)
			                    {
			                        return;
			                    }

			                    const tbody =
			                        table.querySelector(
			                            "tbody"
			                        );

			                    if (!tbody)
			                    {
			                        return;
			                    }

			                    const columnIndex =
			                        Number(
			                            header.dataset.columnIndex
			                        );

			                    const currentDirection =
			                        header.dataset.sortDirection ||
			                        "none";

			                    const direction =
			                        currentDirection === "asc"
			                            ? "desc"
			                            : "asc";

			                    table
			                        .querySelectorAll(
			                            ".dashboard-table-sortable"
			                        )
			                        .forEach(
			                            function (otherHeader)
			                            {
			                                otherHeader
			                                    .removeAttribute(
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

			                    header.dataset.sortDirection =
			                        direction;

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
			                            tbody.querySelectorAll(
			                                "tr"
			                            )
			                        );

			                    rows.sort(
			                        function (rowA, rowB)
			                        {
			                            const cellA =
			                                rowA.cells[columnIndex];

			                            const cellB =
			                                rowB.cells[columnIndex];

			                            const valueA =
			                                cellA
			                                    ? cellA.textContent.trim()
			                                    : "";

			                            const valueB =
			                                cellB
			                                    ? cellB.textContent.trim()
			                                    : "";

			                            const numberA =
			                                Number(
			                                    valueA.replace(
			                                        /,/g,
			                                        ""
			                                    )
			                                );

			                            const numberB =
			                                Number(
			                                    valueB.replace(
			                                        /,/g,
			                                        ""
			                                    )
			                                );

			                            let comparison;

			                            if (
			                                valueA !== "" &&
			                                valueB !== "" &&
			                                !Number.isNaN(numberA) &&
			                                !Number.isNaN(numberB)
			                            )
			                            {
			                                comparison =
			                                    numberA - numberB;
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

    async function loadDashboard()
    {
        showState("loading");


        refreshButton.disabled = true;


        try
        {
            const response =
                await fetch(
                    apiUrl,
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


                destroyDashboardCharts();


                showState("empty");

                return;
            }


            dashboardData =
                data;


            renderTabs();

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
        loadDashboard
    );


    retryButton.addEventListener(
        "click",
        loadDashboard
    );


    /*
     * Initial load.
     */
    loadDashboard();

})();