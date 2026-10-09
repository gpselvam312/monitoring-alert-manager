(() => {
    'use strict';

    const error = document.getElementById('streamingJobsError');
    const loading = document.getElementById('streamingJobsLoading');
    const empty = document.getElementById('streamingJobsEmpty');
    const tableWrapper = document.getElementById('streamingJobsTableWrapper');
    const tableBody = document.getElementById('streamingJobsTableBody');
    const searchInput = document.getElementById('streamingJobSearch');
    const pageSizeSelect = document.getElementById('streamingJobPageSize');
    const pagination = document.getElementById('streamingJobsPagination');
    const pageSummary = document.getElementById('streamingJobsPageSummary');
    const previousButton = document.getElementById('streamingJobsPrevious');
    const nextButton = document.getElementById('streamingJobsNext');
    const count = document.getElementById('streamingJobsCount');
    const details = document.getElementById('streamingJobDetails');
    const selectedName = document.getElementById('selectedStreamingJobName');
    const selectedDescription = document.getElementById('selectedStreamingJobDescription');
    const selectedStatus = document.getElementById('selectedStreamingJobStatus');
    const selectedMeta = document.getElementById('selectedStreamingJobMeta');
    const output = document.getElementById('selectedStreamingJobOutput');
    const startButton = document.getElementById('startStreamingJob');
    const stopButton = document.getElementById('stopStreamingJob');
    const clearOutputButton = document.getElementById('clearStreamingOutput');

    const activeStates = new Set(['STARTING', 'RUNNING', 'STOPPING', 'RECOVERY_REQUIRED']);
    let jobs = [];
    let selectedJobId = null;
    let page = 0;
    let sortKey = 'name';
    let sortDirection = 'asc';
    let eventSource = null;
    let refreshTimer = null;
    let outputLines = [];

    function showError(message) {
        error.textContent = message || 'Unable to complete the streaming job request.';
        error.classList.remove('d-none');
    }

    function hideError() {
        error.classList.add('d-none');
        error.textContent = '';
    }

    async function request(url, options = {}) {
        const headerName = document.querySelector('meta[name="_csrf_header"]')?.content;
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const headers = { 'Accept': 'application/json', ...(options.headers || {}) };
        if (headerName && token) headers[headerName] = token;
        const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
        if (!response.ok) {
            const message = await response.text();
            throw new Error(message || ('Request failed with HTTP ' + response.status));
        }
        return response.json();
    }

    function selectedJob() {
        return jobs.find(job => String(job.id) === String(selectedJobId)) || null;
    }

    function statusClass(status) {
        if (activeStates.has(status)) return 'text-bg-warning';
        if (status === 'COMPLETED' || status === 'STOPPED') return 'text-bg-success';
        if (status === 'FAILED' || status === 'TIMED_OUT') return 'text-bg-danger';
        return 'text-bg-secondary';
    }

    function statusBadge(status) {
        const badge = document.createElement('span');
        badge.className = 'badge ' + statusClass(status || 'IDLE');
        badge.textContent = status || 'IDLE';
        return badge;
    }

    function textCell(value, className) {
        const cell = document.createElement('td');
        if (className) cell.className = className;
        cell.textContent = value == null || value === '' ? '—' : String(value);
        return cell;
    }

    function getSortValue(job, key) {
        if (key === 'maxRuntimeSeconds') return Number(job.maxRuntimeSeconds || 0);
        if (key === 'description') return (job.description || '').toLocaleLowerCase();
        if (key === 'status') return (job.status || 'IDLE').toLocaleLowerCase();
        return (job.name || '').toLocaleLowerCase();
    }

    function visibleJobs() {
        const query = searchInput.value.trim().toLocaleLowerCase();
        return jobs.filter(job => !query || [
            job.name, job.description, job.status, job.maxRuntimeSeconds
        ].some(value => value != null && String(value).toLocaleLowerCase().includes(query)))
        .sort((a, b) => {
            const left = getSortValue(a, sortKey);
            const right = getSortValue(b, sortKey);
            const comparison = typeof left === 'number' && typeof right === 'number'
                ? left - right : String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: 'base' });
            return sortDirection === 'asc' ? comparison : -comparison;
        });
    }

    function renderTable() {
        const filtered = visibleJobs();
        const pageSize = Number(pageSizeSelect.value) || 25;
        const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
        page = Math.min(page, pageCount - 1);
        const start = page * pageSize;
        const pageJobs = filtered.slice(start, start + pageSize);

        tableBody.replaceChildren();
        for (const job of pageJobs) {
            const row = document.createElement('tr');
            row.className = 'streaming-job-row';
            row.dataset.jobId = job.id;
            row.tabIndex = 0;
            row.setAttribute('aria-selected', String(String(job.id) === String(selectedJobId)));
            if (String(job.id) === String(selectedJobId)) row.classList.add('table-active');

            const nameCell = document.createElement('td');
            const name = document.createElement('div');
            name.className = 'table-primary-text';
            name.textContent = job.name || ('Job #' + job.id);
            nameCell.appendChild(name);
            row.appendChild(nameCell);
            row.appendChild(textCell(job.description));
            const statusCell = document.createElement('td');
            statusCell.appendChild(statusBadge(job.status || 'IDLE'));
            row.appendChild(statusCell);
            row.appendChild(textCell((job.maxRuntimeSeconds || 300) + ' sec', 'text-end'));

            const actionCell = document.createElement('td');
            actionCell.className = 'text-end';
            const selectButton = document.createElement('button');
            selectButton.type = 'button';
            selectButton.className = 'btn btn-sm ' + (String(job.id) === String(selectedJobId) ? 'btn-primary' : 'btn-outline-primary');
            selectButton.textContent = String(job.id) === String(selectedJobId) ? 'Selected' : 'Select';
            selectButton.addEventListener('click', event => {
                event.stopPropagation();
                selectJob(job.id);
            });
            actionCell.appendChild(selectButton);
            row.appendChild(actionCell);

            row.addEventListener('click', () => selectJob(job.id));
            row.addEventListener('keydown', event => {
                if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    selectJob(job.id);
                }
            });
            tableBody.appendChild(row);
        }

        count.textContent = filtered.length + (filtered.length === 1 ? ' job' : ' jobs');
        loading.classList.add('d-none');
        const hasJobs = jobs.length > 0;
        empty.classList.toggle('d-none', hasJobs);
        tableWrapper.classList.toggle('d-none', !hasJobs);
        pagination.classList.toggle('d-none', !hasJobs || filtered.length === 0);
        pageSummary.textContent = filtered.length
            ? 'Showing ' + (start + 1) + '–' + Math.min(start + pageSize, filtered.length) + ' of ' + filtered.length
            : 'No jobs match your search';
        previousButton.disabled = page <= 0;
        nextButton.disabled = page >= pageCount - 1;
        updateSortIndicators();
    }

    function updateSortIndicators() {
        document.querySelectorAll('#streamingJobsTable [data-sort]').forEach(button => {
            const active = button.dataset.sort === sortKey;
            button.classList.toggle('active', active);
            const icon = button.querySelector('i');
            if (icon) icon.className = active
                ? 'bi ' + (sortDirection === 'asc' ? 'bi-caret-up-fill' : 'bi-caret-down-fill') + ' table-sort-icon'
                : 'bi bi-arrow-down-up table-sort-icon';
        });
    }

    function appendOutput(line) {
        if (!line && line !== '') return;
        outputLines.push(String(line));
        if (outputLines.length > 500) outputLines = outputLines.slice(-500);
        output.textContent = outputLines.join('\n');
        output.scrollTop = output.scrollHeight;
    }

    function closeEventSource() {
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
    }

    function connectOutput(jobId) {
        closeEventSource();
        eventSource = new EventSource('/api/streaming-jobs/' + encodeURIComponent(jobId) + '/events');
        eventSource.addEventListener('line', event => appendOutput(event.data));
        eventSource.addEventListener('state', () => refresh(false));
        eventSource.onerror = () => {
            // EventSource retries automatically; keep the selected job and output visible.
        };
    }

    function updateDetails(job) {
        if (!job) {
            details.classList.add('d-none');
            return;
        }
        details.classList.remove('d-none');
        selectedName.textContent = job.name || ('Job #' + job.id);
        selectedDescription.textContent = job.description || 'No description provided.';
        selectedStatus.textContent = job.status || 'IDLE';
        selectedStatus.className = 'badge ' + statusClass(job.status || 'IDLE');
        const elapsed = job.startedAt ? Math.max(0, Math.floor((Date.now() - new Date(job.startedAt).getTime()) / 1000)) : 0;
        const maximum = Number(job.maxRuntimeSeconds || 300);
        const remaining = Math.max(0, maximum - elapsed);
        selectedMeta.textContent = 'Maximum runtime: ' + maximum + ' sec'
            + (job.startedAt ? ' • Started: ' + new Date(job.startedAt).toLocaleString() : '')
            + (activeStates.has(job.status) ? ' • Elapsed: ' + elapsed + ' sec • Remaining: ' + remaining + ' sec' : '')
            + (job.startedBy ? ' • Started by user #' + job.startedBy : '')
            + (job.message ? ' • ' + job.message : '');

        const active = activeStates.has(job.status);
        if (startButton) startButton.disabled = active;
        if (stopButton) stopButton.disabled = !active;
    }

    function selectJob(jobId) {
        const changed = String(selectedJobId) !== String(jobId);
        selectedJobId = jobId;
        if (changed) {
            outputLines = [];
            output.textContent = 'Connecting to streaming output…';
            connectOutput(jobId);
        }
        renderTable();
        updateDetails(selectedJob());
    }

    async function runAction(action) {
        const job = selectedJob();
        if (!job) return;
        hideError();
        if (startButton) startButton.disabled = true;
        if (stopButton) stopButton.disabled = true;
        try {
            await request('/api/streaming-jobs/' + encodeURIComponent(job.id) + '/' + action, { method: 'POST' });
            await refresh(false);
        } catch (e) {
            showError(e.message);
            await refresh(false);
        }
    }

    async function refresh(showLoading = true) {
        if (showLoading) loading.classList.remove('d-none');
        hideError();
        try {
            const response = await request('/api/streaming-jobs');
            jobs = Array.isArray(response) ? response : (Array.isArray(response.content) ? response.content : []);
            if (selectedJobId != null && !jobs.some(job => String(job.id) === String(selectedJobId))) {
                selectedJobId = null;
                closeEventSource();
                outputLines = [];
                output.textContent = 'Select a streaming job to view its status and output.';
            }
            renderTable();
            updateDetails(selectedJob());
        } catch (e) {
            loading.classList.add('d-none');
            showError(e.message);
        }
    }

    searchInput.addEventListener('input', () => {
        page = 0;
        renderTable();
    });
    pageSizeSelect.addEventListener('change', () => {
        page = 0;
        renderTable();
    });
    document.querySelectorAll('#streamingJobsTable [data-sort]').forEach(button => {
        button.addEventListener('click', () => {
            if (sortKey === button.dataset.sort) {
                sortDirection = sortDirection === 'asc' ? 'desc' : 'asc';
            } else {
                sortKey = button.dataset.sort;
                sortDirection = 'asc';
            }
            renderTable();
        });
    });
    previousButton.addEventListener('click', () => {
        if (page > 0) {
            page--;
            renderTable();
        }
    });
    nextButton.addEventListener('click', () => {
        page++;
        renderTable();
    });
    document.getElementById('refreshStreamingJobs').addEventListener('click', () => refresh());
    if (startButton) startButton.addEventListener('click', () => runAction('start'));
    if (stopButton) stopButton.addEventListener('click', () => runAction('stop'));
    clearOutputButton.addEventListener('click', () => {
        outputLines = [];
        output.textContent = '';
    });

    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) refresh(false);
    });
    refresh();
    refreshTimer = window.setInterval(() => {
        if (!document.hidden) refresh(false);
    }, 5000);
    window.addEventListener('beforeunload', () => {
        window.clearInterval(refreshTimer);
        closeEventSource();
    });
})();
