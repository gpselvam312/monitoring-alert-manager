(function () {
    'use strict';

    const config = window.alertHistoryConfig;
    if (!config) return;

    const state = { batch: 0, localPage: 0, pageSize: 10, fetchSize: 100, totalElements: 0, hasNext: false, items: [], loading: false, searchTimer: null };
    const byId = id => document.getElementById(id);
    const escapeHtml = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
    const searchInput = byId(config.searchId);
    const pageSizeSelect = byId(config.pageSizeId);
    const container = byId(config.containerId);

    function paramsForBatch(batch) {
        const params = new URLSearchParams({ batch: String(batch), search: searchInput ? searchInput.value.trim() : '', sort: config.sort || 'createdAt', direction: config.direction || 'desc' });
        Object.entries(config.filters || {}).forEach(([name, id]) => {
            const field = byId(id);
            if (field && field.value) params.set(name, field.value);
        });
        return params;
    }

    function loadBatch(batch, localPage) {
        if (state.loading) return;
        state.loading = true;
        container.innerHTML = '<div class="p-4 text-muted text-center"><span class="spinner-border spinner-border-sm me-2" role="status"></span>Loading alert history...</div>';
        fetch(config.endpoint + '?' + paramsForBatch(batch).toString(), { headers: { 'Accept': 'application/json' } })
            .then(response => {
                if (!response.ok) throw new Error('Unable to load alert history.');
                return response.json();
            })
            .then(data => {
                state.batch = data.batch;
                state.fetchSize = data.fetchSize;
                state.items = data.items || [];
                state.totalElements = data.totalElements || 0;
                state.hasNext = Boolean(data.hasNext);
                state.pageSize = pageSizeSelect ? Number(pageSizeSelect.value) : 10;
                const pagesInBatch = Math.max(1, Math.ceil(state.items.length / state.pageSize));
                state.localPage = Math.min(Math.max(0, localPage || 0), pagesInBatch - 1);
                render();
            })
            .catch(error => {
                console.error(error);
                container.innerHTML = '<div class="p-4 text-danger">Unable to load alert history. Please try again.</div>';
                updatePagination(true);
            })
            .finally(() => { state.loading = false; });
    }

    function render() {
        const start = state.localPage * state.pageSize;
        const visible = state.items.slice(start, start + state.pageSize);
        if (!state.totalElements) {
            container.innerHTML = '<div class="empty-state"><div class="empty-state-icon"><i class="bi bi-clock-history"></i></div><h6>No alerts found</h6><p>Try changing the selected filters.</p></div>';
            updatePagination(false);
            return;
        }
        const headers = [
            ['alertKey', 'Alert ID', null], ['title', 'Alert', 'title'], ['severity', 'Severity', 'severity'],
            ['applicationName', 'Application / Environment', null], ['status', 'Status', 'status'],
            ['occurrenceCount', 'Occurrences', null], ['createdAt', 'Created', 'createdAt'],
            ['lastOccurredAt', 'Last Occurred', 'lastOccurredAt'], ['resolvedAt', 'Resolved', 'resolvedAt']
        ];
        const head = headers.map(([, label, sortField]) => '<th scope="col">' +
            (sortField ? '<button type="button" class="table-sort" data-sort="' + sortField + '" aria-label="Sort by ' + label + '">' + label + '<i class="bi table-sort-icon ms-1"></i></button>' : label) +
            '</th>').join('');
        const rows = visible.map(item => {
            const severityClass = item.severity === 'CRITICAL' ? 'text-bg-danger' : item.severity === 'WARNING' ? 'text-bg-warning' : item.severity === 'SUCCESS' ? 'text-bg-success' : 'text-bg-info';
            const statusClass = item.status === 'ACTIVE' ? 'text-bg-danger' : 'text-bg-success';
            const detailUrl = config.detailEndpoint + encodeURIComponent(item.id) + '?from=history';
            return '<tr>' +
                '<td><a class="fw-semibold text-decoration-none" href="' + detailUrl + '">' + escapeHtml(item.alertKey) + '</a></td>' +
                '<td><a class="fw-semibold text-decoration-none" href="' + detailUrl + '">' + escapeHtml(item.title) + '</a><div class="small text-muted">' + escapeHtml(item.jobName) + '</div></td>' +
                '<td><span class="badge ' + severityClass + '">' + escapeHtml(item.severity) + '</span></td>' +
                '<td><div>' + escapeHtml(item.applicationName) + '</div><div class="small text-muted">' + escapeHtml(item.environmentName) + '</div></td>' +
                '<td><span class="badge ' + statusClass + '">' + (item.status === 'ACTIVE' ? 'Active' : 'Recovered') + '</span></td>' +
                '<td>' + escapeHtml(item.occurrenceCount) + '</td><td>' + escapeHtml(item.createdAt) + '</td>' +
                '<td>' + escapeHtml(item.lastOccurredAt) + '</td><td>' + escapeHtml(item.resolvedAt) + '</td></tr>';
        }).join('');
        container.innerHTML = '<table class="table app-table align-middle mb-0"><thead><tr>' + head + '</tr></thead><tbody>' + rows + '</tbody></table>';
        container.querySelectorAll('.table-sort').forEach(button => button.addEventListener('click', () => {
            const nextSort = button.dataset.sort;
            if (config.sort === nextSort) config.direction = config.direction === 'asc' ? 'desc' : 'asc';
            else { config.sort = nextSort; config.direction = 'asc'; }
            state.batch = 0; state.localPage = 0; loadBatch(0, 0);
        }));
        container.querySelectorAll('.table-sort').forEach(button => {
            if (button.dataset.sort === config.sort) {
                const icon = button.querySelector('.table-sort-icon');
                icon.classList.add(config.direction === 'desc' ? 'bi-caret-down-fill' : 'bi-caret-up-fill');
            }
        });
        updatePagination(false);
        if (window.DataTable && typeof window.DataTable.updateCompactActions === 'function') {
            window.DataTable.updateCompactActions({ containerId: config.containerId });
        }
    }

    function updatePagination(error) {
        const wrapper = byId('alertHistoryPagination');
        const summary = byId('alertHistoryPaginationSummary');
        if (!wrapper || !summary) return;
        wrapper.classList.toggle('d-none', !state.totalElements || Boolean(error));
        const totalPages = Math.ceil(state.totalElements / state.pageSize);
        const absolutePage = Math.floor((state.batch * state.fetchSize) / state.pageSize) + state.localPage;
        summary.textContent = 'Page ' + (totalPages ? absolutePage + 1 : 0) + ' of ' + totalPages + ' (' + state.totalElements + ' records)';
        const first = byId('alertHistoryFirst'), previous = byId('alertHistoryPrevious');
        const next = byId('alertHistoryNext'), last = byId('alertHistoryLast');
        const firstPage = state.batch === 0 && state.localPage === 0;
        const lastPage = absolutePage >= totalPages - 1;
        first.disabled = firstPage; previous.disabled = firstPage; next.disabled = lastPage; last.disabled = lastPage;
    }

    function changePage(action) {
        if (state.loading || !state.totalElements) return;
        const pagesInBatch = Math.max(1, Math.ceil(state.items.length / state.pageSize));
        if (action === 'first') return loadBatch(0, 0);
        if (action === 'last') {
            const lastBatch = Math.max(0, Math.ceil(state.totalElements / state.fetchSize) - 1);
            const offsetInLastBatch = state.totalElements - lastBatch * state.fetchSize;
            return loadBatch(lastBatch, Math.max(0, Math.ceil(offsetInLastBatch / state.pageSize) - 1));
        }
        if (action === 'next') {
            if (state.localPage + 1 < pagesInBatch) state.localPage++;
            else if (state.hasNext) return loadBatch(state.batch + 1, 0);
        }
        if (action === 'previous') {
            if (state.localPage > 0) state.localPage--;
            else if (state.batch > 0) return loadBatch(state.batch - 1, Math.max(0, Math.ceil(state.fetchSize / state.pageSize) - 1));
        }
        render();
    }

    function resetAndLoad() { state.batch = 0; state.localPage = 0; loadBatch(0, 0); }
    function navigateWithFilters() {
        const query = new URLSearchParams();
        const currentSearch = searchInput ? searchInput.value.trim() : '';
        if (currentSearch) query.set('search', currentSearch);
        Object.entries(config.filters || {}).forEach(([name, id]) => {
            const field = byId(id);
            if (field && field.value) query.set(name, field.value);
        });
        query.set('sort', config.sort || 'createdAt');
        query.set('direction', config.direction || 'desc');
        if (pageSizeSelect && pageSizeSelect.value) query.set('size', pageSizeSelect.value);
        window.location.assign(window.location.pathname + (query.toString() ? '?' + query.toString() : ''));
    }
    Object.entries({ alertHistoryFirst: 'first', alertHistoryPrevious: 'previous', alertHistoryNext: 'next', alertHistoryLast: 'last' })
        .forEach(([id, action]) => byId(id).addEventListener('click', () => changePage(action)));

    if (searchInput) searchInput.addEventListener('input', () => {
        const clear = byId('clear-' + config.searchId);
        if (clear) clear.classList.toggle('d-none', searchInput.value.trim() === '');
        clearTimeout(state.searchTimer);
        state.searchTimer = setTimeout(navigateWithFilters, 450);
    });
    const clearSearch = byId('clear-' + config.searchId);
    if (clearSearch) clearSearch.addEventListener('click', () => { clearTimeout(state.searchTimer); searchInput.value = ''; clearSearch.classList.add('d-none'); navigateWithFilters(); });
    if (pageSizeSelect) pageSizeSelect.addEventListener('change', resetAndLoad);
    Object.entries(config.filters || {}).forEach(([name, id]) => {
        if (name === 'applicationId') return;
        const field = byId(id);
        if (field) field.addEventListener('change', navigateWithFilters);
    });
    const applicationFilter = byId(config.filters.applicationId);
    if (applicationFilter) applicationFilter.addEventListener('change', () => {
        const environmentFilter = byId(config.filters.environmentId);
        if (environmentFilter) environmentFilter.value = '';
        navigateWithFilters();
    });
    const clearFilters = byId('alertHistoryClearFilters');
    if (clearFilters) clearFilters.addEventListener('click', () => {
        if (searchInput) { searchInput.value = ''; const clear = byId('clear-' + config.searchId); if (clear) clear.classList.add('d-none'); }
        Object.values(config.filters || {}).forEach(id => { const field = byId(id); if (field) field.value = ''; });
        if (pageSizeSelect) pageSizeSelect.value = '10';
        config.sort = 'createdAt'; config.direction = 'desc';
        navigateWithFilters();
    });

    if (searchInput && searchInput.value.trim()) {
        const clear = byId('clear-' + config.searchId);
        if (clear) clear.classList.remove('d-none');
    }
    loadBatch(0, 0);
})();