(function () {
    'use strict';

    const config = window.alertHistoryConfig;
    if (!config) return;

    const state = { batch: 0, localPage: 0, pageSize: 10, fetchSize: 100, totalElements: 0, hasNext: false, items: [], loading: false, searchTimer: null, requestId: 0, requestController: null };
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
        if (state.requestController) state.requestController.abort();
        const requestId = ++state.requestId;
        const requestController = new AbortController();
        state.requestController = requestController;
        state.loading = true;
        container.innerHTML = '<div class="p-4 text-muted text-center"><span class="spinner-border spinner-border-sm me-2" role="status"></span>Loading alert history...</div>';
        fetch(config.endpoint + '?' + paramsForBatch(batch).toString(), {
            headers: { 'Accept': 'application/json' },
            signal: requestController.signal
        })
            .then(response => {
                if (!response.ok) throw new Error('Unable to load alert history.');
                return response.json();
            })
            .then(data => {
                if (requestId !== state.requestId) return;
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
                if (requestId !== state.requestId || error.name === 'AbortError') return;
                console.error(error);
                container.innerHTML = '<div class="p-4 text-danger">Unable to load alert history. Please try again.</div>';
                updatePagination(true);
            })
            .finally(() => {
                if (requestId === state.requestId) {
                    state.loading = false;
                    state.requestController = null;
                }
            });
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
            ['alertKey', 'Alert ID', 'alertKey'], ['triggeredAt', 'Triggered Time', 'triggeredAt'],
            ['severity', 'Severity', 'severity'], ['ruleTarget', 'Rule & Target', null],
            ['status', 'Alert Status', 'status'], ['channels', 'Channels', null],
            ['occurrenceCount', 'Occurrences', 'occurrenceCount'], ['action', 'Action', null]
        ];
        const head = headers.map(([, label, sortField]) => '<th scope="col">' +
            (sortField ? '<button type="button" class="table-sort" data-sort="' + sortField + '" aria-label="Sort by ' + label + '">' + label + '<i class="bi table-sort-icon ms-1"></i></button>' : label) +
            '</th>').join('');
        const channelDefinitions = [
            ['EMAIL', 'Email', 'envelope'],
            ['TEAMS', 'Teams', 'microsoft-teams'],
            ['WHATSAPP', 'WhatsApp', 'whatsapp']
        ];
        const channelMarkup = item => channelDefinitions.map(([type, label, icon]) => {
            const channel = item.channels && item.channels[type]
                ? item.channels[type] : { configured: false, status: 'NOT_CONFIGURED' };
            const status = channel.status || 'NOT_SENT';
            const counts = Number(channel.sentCount || 0) + Number(channel.failedCount || 0)
                + Number(channel.pendingCount || 0) + Number(channel.skippedCount || 0);
            const countText = counts > 1
                ? ' (' + channel.sentCount + ' delivered, ' + channel.failedCount + ' failed, '
                    + channel.pendingCount + ' pending, ' + channel.skippedCount + ' skipped)'
                : '';
            const tooltip = label + ': ' + (channel.configured
                ? ({ SENT: 'Delivered', FAILED: 'Failed', PENDING: 'Pending / retrying',
                    SKIPPED: 'Skipped', NOT_SENT: 'Not sent' }[status] || 'No delivery recorded')
                : 'Not Configured') + countText;
            let badgeClass = 'ch-badge text-secondary opacity-50';
            let statusMark = '—';
            if (channel.configured && status === 'SENT') {
                badgeClass = 'ch-badge text-success';
                statusMark = '✔';
            } else if (channel.configured && status === 'FAILED') {
                badgeClass = 'ch-badge text-danger border-danger';
                statusMark = '✖';
            } else if (channel.configured && status === 'PENDING') {
                badgeClass = 'ch-badge text-warning border-warning';
                statusMark = '…';
            } else if (channel.configured && status === 'SKIPPED') {
                badgeClass = 'ch-badge text-secondary opacity-75';
                statusMark = '—';
            }
            return '<span class="' + badgeClass + '" title="' + escapeHtml(tooltip)
                + '" aria-label="' + escapeHtml(tooltip) + '"><i class="bi bi-' + icon
                + '" aria-hidden="true"></i> ' + statusMark + '</span>';
        }).join('');
        const rows = visible.map(item => {
            const severityClass = item.severity === 'CRITICAL' ? 'text-bg-danger' : item.severity === 'WARNING' ? 'text-bg-warning' : item.severity === 'SUCCESS' ? 'text-bg-success' : 'text-bg-info';
            const statusClass = item.status === 'ACTIVE' ? 'text-bg-danger' : 'text-bg-success';
            const detailUrl = config.detailEndpoint + encodeURIComponent(item.id) + '?from=history';
            return '<tr>' +
                '<td><a class="fw-semibold text-decoration-none" href="' + detailUrl + '">' + escapeHtml(item.alertKey) + '</a></td>' +
                '<td class="text-nowrap">' + escapeHtml(item.triggeredAt) + '</td>' +
                '<td><span class="badge ' + severityClass + '">' + escapeHtml(item.severity) + '</span></td>' +
                '<td title="' + escapeHtml(item.title) + '"><div class="fw-semibold">' + escapeHtml(item.ruleName) + '</div>' +
                    '<div class="small text-muted">' + escapeHtml(item.jobName) + ' · ' + escapeHtml(item.applicationName)
                    + ' / ' + escapeHtml(item.environmentName) + '</div></td>' +
                '<td><span class="badge ' + statusClass + '">' + (item.status === 'ACTIVE' ? 'Active' : 'Recovered') + '</span></td>' +
                '<td class="text-nowrap">' + channelMarkup(item) + '</td>' +
                '<td>' + escapeHtml(item.occurrenceCount) + '</td>' +
                '<td><a class="btn btn-sm btn-outline-secondary" href="' + detailUrl + '" aria-label="View alert '
                    + escapeHtml(item.alertKey) + '" title="View alert"><i class="bi bi-eye" aria-hidden="true"></i></a></td></tr>';
        }).join('');
        container.innerHTML = '<table class="table app-table align-middle mb-0"><thead><tr>' + head + '</tr></thead><tbody>' + rows + '</tbody></table>';
        container.querySelectorAll('.table-sort').forEach(button => button.addEventListener('click', () => {
            const nextSort = button.dataset.sort;
            if (config.sort === nextSort) config.direction = config.direction === 'asc' ? 'desc' : 'asc';
            else { config.sort = nextSort; config.direction = 'asc'; }
            state.batch = 0; state.localPage = 0; loadBatch(0, 0);
        }));
        if (window.DataTable && typeof window.DataTable.updateSortIndicators === 'function') {
            window.DataTable.updateSortIndicators({
                containerId: config.containerId,
                sort: config.sort,
                direction: config.direction
            });
        } else {
            container.querySelectorAll('.table-sort').forEach(button => {
                const icon = button.querySelector('.table-sort-icon');
                if (icon && button.dataset.sort === config.sort) {
                    button.classList.add('active');
                    icon.classList.add(config.direction === 'desc' ? 'bi-caret-down-fill' : 'bi-caret-up-fill');
                }
            });
        }
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