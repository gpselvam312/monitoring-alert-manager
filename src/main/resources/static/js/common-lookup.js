(function () {
    "use strict";

    const modalElement = document.getElementById("commonLookupModal");
    if (!modalElement || !window.bootstrap) return;

    const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
    const elements = {
        title: document.getElementById("commonLookupTitle"),
        description: document.getElementById("commonLookupDescription"),
        search: document.getElementById("commonLookupSearch"),
        clearSearch: document.getElementById("commonLookupClearSearch"),
        application: document.getElementById("commonLookupApplication"),
        environment: document.getElementById("commonLookupEnvironment"),
        pageSize: document.getElementById("commonLookupPageSize"),
        rows: document.getElementById("commonLookupRows"),
        summary: document.getElementById("commonLookupSummary"),
        selectionSummary: document.getElementById("commonLookupSelectionSummary"),
        previous: document.getElementById("commonLookupPrevious"),
        next: document.getElementById("commonLookupNext"),
        clearSelection: document.getElementById("commonLookupClearSelection"),
        confirm: document.getElementById("commonLookupConfirm"),
        confirmText: document.getElementById("commonLookupConfirmText")
    };

    const state = {
        trigger: null,
        endpoint: "",
        selectionMode: "SINGLE",
        selected: new Map(),
        items: [],
        totalElements: 0,
        batch: 0,
        fetchSize: 100,
        hasNext: false,
        localPage: 0,
        pageSize: 5,
        search: "",
        applicationId: "",
        environmentId: "",
        sort: "name",
        direction: "asc",
        loading: false,
        requestSequence: 0,
        searchTimer: null
    };

    function escapeHtml(value) {
        return String(value == null ? "" : value).replace(/[&<>"']/g, function (character) {
            return ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[character]);
        });
    }

    function setBusy(busy) {
        state.loading = busy;
        [elements.search, elements.application, elements.environment, elements.pageSize,
            elements.previous, elements.next, elements.confirm].forEach(function (element) {
            if (element) element.disabled = busy;
        });
        modalElement.classList.toggle("common-lookup-loading", busy);
    }

    function resetBatch() {
        state.batch = 0;
        state.localPage = 0;
        state.items = [];
        state.totalElements = 0;
        state.hasNext = false;
        loadBatch(0);
    }

    async function loadFilters() {
        const response = await fetch(state.endpoint + "/filters", {
            headers: {"Accept": "application/json"},
            credentials: "same-origin"
        });
        if (!response.ok) throw new Error("Unable to load lookup filters.");
        const data = await response.json();
        const selectedApp = state.applicationId;
        elements.application.replaceChildren(new Option("All applications", ""));
        (data.applications || []).forEach(function (item) {
            elements.application.add(new Option(item.name, String(item.id)));
        });
        elements.application.value = selectedApp;

        const selectedEnv = state.environmentId;
        elements.environment.replaceChildren(new Option("All environments", ""));
        (data.environments || []).forEach(function (item) {
            elements.environment.add(new Option(item.name, String(item.id)));
        });
        elements.environment.value = selectedEnv;
    }

    async function loadBatch(batch) {
        if (state.loading) return;
        const sequence = ++state.requestSequence;
        setBusy(true);
        elements.summary.textContent = "Loading records…";
        elements.rows.innerHTML = '<tr><td colspan="6" class="text-center py-4"><span class="spinner-border spinner-border-sm me-2" role="status"></span>Loading records…</td></tr>';
        const params = new URLSearchParams({
            search: state.search,
            batch: String(batch),
            sort: state.sort,
            direction: state.direction
        });
        if (state.applicationId) params.set("applicationId", state.applicationId);
        if (state.environmentId) params.set("environmentId", state.environmentId);
        try {
            const response = await fetch(state.endpoint + "?" + params.toString(), {
                headers: {"Accept": "application/json"},
                credentials: "same-origin"
            });
            if (!response.ok) throw new Error(response.status === 403
                ? "You are not authorized to view these records."
                : "Unable to load lookup records. Please try again.");
            const result = await response.json();
            if (sequence !== state.requestSequence) return;
            state.items = result.items || [];
            state.totalElements = result.totalElements || 0;
            state.batch = result.batch || 0;
            state.fetchSize = result.fetchSize || 100;
            state.hasNext = Boolean(result.hasNext);
            state.localPage = Math.max(0, Math.min(state.localPage, Math.ceil(state.items.length / state.pageSize) - 1));
            renderRows();
        } catch (error) {
            if (sequence !== state.requestSequence) return;
            state.items = [];
            state.hasNext = false;
            elements.rows.innerHTML = '<tr><td colspan="6" class="text-center text-danger py-4"><i class="bi bi-exclamation-triangle me-2"></i>' + escapeHtml(error.message || "Lookup failed.") + '</td></tr>';
            elements.summary.textContent = "Lookup failed";
        } finally {
            if (sequence === state.requestSequence) setBusy(false);
        }
    }

    function visibleItems() {
        const start = state.localPage * state.pageSize;
        return state.items.slice(start, start + state.pageSize);
    }

    function renderRows() {
        const items = visibleItems();
        const multiple = state.selectionMode === "MULTIPLE";
        modalElement.querySelector(".common-lookup-selection-column").innerHTML =
            multiple ? '<input type="checkbox" class="form-check-input" id="commonLookupSelectPage" aria-label="Select all rows on this page">' : '<span class="visually-hidden">Select</span>';

        if (!items.length) {
            elements.rows.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4"><i class="bi bi-search me-2"></i>No matching records found.</td></tr>';
        } else {
            elements.rows.innerHTML = items.map(function (item) {
                const id = String(item.id);
                const selected = state.selected.has(id);
                const inputType = multiple ? "checkbox" : "radio";
                return '<tr class="common-lookup-row' + (selected ? ' is-selected' : '') + '" data-lookup-id="' + escapeHtml(id) + '">' +
                    '<td><input class="form-check-input common-lookup-choice" type="' + inputType + '" name="commonLookupChoice" value="' + escapeHtml(id) + '" ' +
                    (selected ? 'checked ' : '') + 'aria-label="Select ' + escapeHtml(item.name) + '"></td>' +
                    '<td><div class="fw-semibold">' + escapeHtml(item.name) + '</div>' +
                    (item.description ? '<div class="small text-muted text-truncate common-lookup-description-cell">' + escapeHtml(item.description) + '</div>' : '') + '</td>' +
                    '<td>' + escapeHtml(item.applicationName) + '</td>' +
                    '<td>' + escapeHtml(item.environmentName || "—") + '</td>' +
                    '<td><span class="badge text-bg-secondary">' + escapeHtml(item.type || "—") + '</span></td>' +
                    '<td><span class="badge ' + (item.enabled ? 'text-bg-success' : 'text-bg-secondary') + '">' + (item.enabled ? 'Enabled' : 'Disabled') + '</span></td>' +
                    '</tr>';
            }).join("");
        }

        const absoluteStart = state.totalElements === 0 ? 0 : state.batch * state.fetchSize + state.localPage * state.pageSize + 1;
        const absoluteEnd = Math.min(state.batch * state.fetchSize + state.localPage * state.pageSize + items.length, state.totalElements);
        const localPages = Math.max(1, Math.ceil(state.items.length / state.pageSize));
        elements.summary.textContent = state.totalElements === 0
            ? "No records found"
            : "Showing " + absoluteStart + "–" + absoluteEnd + " of " + state.totalElements +
              " records · batch " + (state.batch + 1) + " (" + state.items.length + " loaded)";
        elements.selectionSummary.textContent = state.selected.size
            ? state.selected.size + " record(s) selected"
            : (state.selectionMode === "MULTIPLE" ? "Select one or more records across pages." : "Select one record.");
        elements.previous.disabled = state.loading || (state.batch === 0 && state.localPage === 0);
        elements.next.disabled = state.loading ||
            (state.localPage >= localPages - 1 && !state.hasNext);
        elements.clearSelection.disabled = state.loading || state.selected.size === 0;
        elements.confirmText.textContent = state.selectionMode === "MULTIPLE"
            ? "Select " + state.selected.size + " record(s)" : "Select";
        elements.confirm.disabled = state.loading || state.selected.size === 0;
        const selectPage = document.getElementById("commonLookupSelectPage");
        if (selectPage) {
            const pageIds = items.map(item => String(item.id));
            selectPage.checked = pageIds.length > 0 && pageIds.every(id => state.selected.has(id));
            selectPage.indeterminate = pageIds.some(id => state.selected.has(id)) && !selectPage.checked;
            selectPage.addEventListener("change", function () {
                pageIds.forEach(function (id) {
                    if (selectPage.checked) {
                        const item = state.items.find(record => String(record.id) === id);
                        if (item) state.selected.set(id, item);
                    } else {
                        state.selected.delete(id);
                    }
                });
                renderRows();
            });
        }
    }

    function selectItem(item, checked) {
        const id = String(item.id);
        if (state.selectionMode === "SINGLE") {
            state.selected.clear();
            if (checked) state.selected.set(id, item);
        } else if (checked) {
            state.selected.set(id, item);
        } else {
            state.selected.delete(id);
        }
        renderRows();
    }

    function moveNext() {
        const localPages = Math.max(1, Math.ceil(state.items.length / state.pageSize));
        if (state.localPage < localPages - 1) {
            state.localPage++;
            renderRows();
        } else if (state.hasNext) {
            state.localPage = 0;
            loadBatch(state.batch + 1);
        }
    }

    function movePrevious() {
        if (state.localPage > 0) {
            state.localPage--;
            renderRows();
        } else if (state.batch > 0) {
            state.localPage = 0;
            loadBatch(state.batch - 1);
            // Previous batch is rendered after it arrives; show its last local page.
            const onRows = function () {
                if (state.batch >= 0) {
                    state.localPage = Math.max(0, Math.ceil(state.items.length / state.pageSize) - 1);
                    renderRows();
                }
                elements.rows.removeEventListener("lookup:loaded", onRows);
            };
            elements.rows.addEventListener("lookup:loaded", onRows);
        }
    }

    function renderLoadedMarker() {
        elements.rows.dispatchEvent(new Event("lookup:loaded"));
    }

    const originalRenderRows = renderRows;
    renderRows = function () {
        originalRenderRows();
        renderLoadedMarker();
    };

    document.addEventListener("click", async function (event) {
        const trigger = event.target.closest("[data-common-lookup]");
        if (!trigger) return;
        event.preventDefault();
        state.trigger = trigger;
        state.endpoint = trigger.dataset.lookupEndpoint;
        state.selectionMode = (trigger.dataset.selectionMode || "SINGLE").toUpperCase() === "MULTIPLE" ? "MULTIPLE" : "SINGLE";
        state.selected.clear();
        const target = document.getElementById(trigger.dataset.targetId);
        const label = document.getElementById(trigger.dataset.labelId);
        if (target && target.value) {
            const ids = target.value.split(",").map(value => value.trim()).filter(Boolean);
            if (state.selectionMode === "SINGLE" && ids.length) {
                state.selected.set(ids[0], {id: ids[0], name: label ? label.value : ids[0]});
            } else if (state.selectionMode === "MULTIPLE" && label) {
                const names = label.value.split(",").map(value => value.trim());
                ids.forEach((id, index) => state.selected.set(id, {id: id, name: names[index] || id}));
            }
        }
        state.search = "";
        state.applicationId = "";
        state.environmentId = "";
        state.sort = "name";
        state.direction = "asc";
        state.pageSize = Number(elements.pageSize.value) || 5;
        elements.title.textContent = trigger.dataset.lookupTitle || "Select a record";
        elements.description.textContent = state.selectionMode === "MULTIPLE"
            ? "Select multiple records. Your selection is preserved across pages."
            : "Search, filter and select one record from the table.";
        elements.search.value = "";
        elements.application.value = "";
        elements.environment.value = "";
        elements.confirmText.textContent = state.selectionMode === "MULTIPLE" ? "Select 0 record(s)" : "Select";
        modal.show();
        try {
            await loadFilters();
            resetBatch();
        } catch (error) {
            elements.rows.innerHTML = '<tr><td colspan="6" class="text-center text-danger py-4">' + escapeHtml(error.message) + '</td></tr>';
            elements.summary.textContent = "Unable to load lookup filters";
        }
    });

    elements.rows.addEventListener("change", function (event) {
        const input = event.target.closest(".common-lookup-choice");
        if (!input) return;
        const item = state.items.find(record => String(record.id) === input.value);
        if (item) selectItem(item, input.checked);
    });

    elements.rows.addEventListener("click", function (event) {
        const row = event.target.closest("tr[data-lookup-id]");
        if (!row || event.target.closest("input")) return;
        const input = row.querySelector(".common-lookup-choice");
        if (input) {
            input.checked = state.selectionMode === "SINGLE" ? true : !input.checked;
            input.dispatchEvent(new Event("change", {bubbles: true}));
        }
    });

    elements.search.addEventListener("input", function () {
        clearTimeout(state.searchTimer);
        state.searchTimer = setTimeout(function () {
            state.search = elements.search.value.trim();
            resetBatch();
        }, 250);
    });
    elements.clearSearch.addEventListener("click", function () {
        elements.search.value = "";
        state.search = "";
        resetBatch();
    });
    elements.application.addEventListener("change", async function () {
        state.applicationId = elements.application.value;
        state.environmentId = "";
        try {
            await loadFilters();
            state.environmentId = elements.environment.value;
            resetBatch();
        } catch (error) {
            elements.summary.textContent = error.message;
        }
    });
    elements.environment.addEventListener("change", function () {
        state.environmentId = elements.environment.value;
        resetBatch();
    });
    elements.pageSize.addEventListener("change", function () {
        state.pageSize = Number(elements.pageSize.value) || 5;
        state.localPage = 0;
        renderRows();
    });
    elements.previous.addEventListener("click", movePrevious);
    elements.next.addEventListener("click", moveNext);
    elements.clearSelection.addEventListener("click", function () {
        state.selected.clear();
        renderRows();
    });
    modalElement.querySelectorAll("[data-lookup-sort]").forEach(function (button) {
        button.addEventListener("click", function () {
            const sort = button.dataset.lookupSort;
            state.direction = state.sort === sort && state.direction === "asc" ? "desc" : "asc";
            state.sort = sort;
            resetBatch();
        });
    });
    elements.confirm.addEventListener("click", function () {
        const target = document.getElementById(state.trigger.dataset.targetId);
        const label = document.getElementById(state.trigger.dataset.labelId);
        const selected = Array.from(state.selected.values());
        if (!target || selected.length === 0) return;
        target.value = selected.map(item => item.id).join(",");
        target.dispatchEvent(new Event("change", {bubbles: true}));
        if (label) {
            label.value = selected.map(item => item.name || item.applicationName || item.id).join(", ");
            label.dispatchEvent(new Event("change", {bubbles: true}));
        }
        modal.hide();
    });
    modalElement.addEventListener("hidden.bs.modal", function () {
        clearTimeout(state.searchTimer);
        state.requestSequence++;
        state.trigger = null;
        state.items = [];
        state.totalElements = 0;
        state.selected.clear();
        state.applicationId = "";
        state.environmentId = "";
        elements.rows.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">Open the lookup to search records.</td></tr>';
        elements.summary.textContent = "Ready";
        elements.selectionSummary.textContent = "";
    });

    window.CommonLookup = {
        open: function (trigger) {
            if (trigger) trigger.click();
        }
    };
})();
