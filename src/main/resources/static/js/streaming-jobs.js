(() => {
    'use strict';

    const error = document.getElementById('streamingJobsError');
    const loading = document.getElementById('streamingJobsLoading');
    const empty = document.getElementById('streamingJobsEmpty');
    const noResults = document.getElementById('streamingJobsNoResults');
    const tableWrapper = document.getElementById('streamingJobsTableWrapper');
    const tableBody = document.getElementById('streamingJobsTableBody');
    const searchInput = document.getElementById('streamingJobSearch');
    const clearSearchButton = document.getElementById('clear-streamingJobSearch');
    const pageSizeSelect = document.getElementById('streamingJobPageSize');
    const pagination = document.getElementById('streamingJobsPagination');
    const pageSummary = document.getElementById('streamingJobsPageSummary');
    const firstButton = document.getElementById('streamingJobsFirst');
    const previousButton = document.getElementById('streamingJobsPrevious');
    const nextButton = document.getElementById('streamingJobsNext');
    const lastButton = document.getElementById('streamingJobsLast');
    const canView = Boolean(document.getElementById('streamingJobsCanView'));
    const canConfigure = Boolean(document.getElementById('streamingJobsCanConfigure'));
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
    let pageLeaving = false;

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
        if (key === 'enabled') return job.enabled ? 1 : 0;
        const value = job[key];
        return value == null ? '' : String(value).toLocaleLowerCase();
    }

    function visibleJobs() {
        const query = searchInput.value.trim().toLocaleLowerCase();
        return jobs.filter(job => !query || [
            job.name, job.application, job.environment, job.machine, job.type, job.severity, job.schedule,
            job.enabled ? 'enabled' : 'disabled', job.status, job.description
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
            row.appendChild(textCell(job.application));
            row.appendChild(textCell(job.environment));
            row.appendChild(textCell(job.machine));
            row.appendChild(textCell(job.type));
            const severityCell = document.createElement('td');
            if (job.severity) {
                const severityBadge = document.createElement('span');
                severityBadge.className = 'status-badge ' + (job.severity === 'CRITICAL' ? 'status-critical'
                    : job.severity === 'WARNING' ? 'status-warning' : 'status-info');
                severityBadge.textContent = job.severity;
                severityCell.appendChild(severityBadge);
            } else {
                severityCell.textContent = '—';
            }
            row.appendChild(severityCell);
            row.appendChild(textCell(job.schedule));
            const enabledCell = document.createElement('td');
            const enabledBadge = document.createElement('span');
            enabledBadge.className = 'status-badge ' + (job.enabled ? 'status-healthy' : 'status-disabled');
            const statusDot = document.createElement('span');
            statusDot.className = 'status-dot';
            enabledBadge.appendChild(statusDot);
            enabledBadge.appendChild(document.createTextNode(job.enabled ? ' ENABLED' : ' DISABLED'));
            enabledCell.appendChild(enabledBadge);
            row.appendChild(enabledCell);

            const actionCell = document.createElement('td');
            actionCell.className = 'text-end table-actions-column';
            const textActions = document.createElement('div');
            textActions.className = 'table-actions table-actions-text';
            const selectButton = document.createElement('button');
            selectButton.type = 'button';
            selectButton.className = 'btn btn-sm ' + (String(job.id) === String(selectedJobId) ? 'btn-primary' : 'btn-outline-primary');
            selectButton.disabled = !job.enabled;
            selectButton.innerHTML = '<i class="bi bi-play-circle"></i><span class="ms-1">'
                + (String(job.id) === String(selectedJobId) ? 'Selected' : 'Select') + '</span>';
            selectButton.addEventListener('click', event => {
                event.stopPropagation();
                selectJob(job.id);
            });
            textActions.appendChild(selectButton);
            if (canView) {
                const viewLink = document.createElement('a');
                viewLink.className = 'btn btn-sm btn-outline-secondary';
                viewLink.href = '/monitoring/jobs/' + encodeURIComponent(job.id) + '?mode=STREAMING';
                viewLink.title = 'View';
                viewLink.setAttribute('aria-label', 'View');
                viewLink.innerHTML = '<i class="bi bi-eye"></i><span class="ms-1">View</span>';
                viewLink.addEventListener('click', event => event.stopPropagation());
                textActions.appendChild(viewLink);
            }
            if (canConfigure) {
                const editLink = document.createElement('a');
                editLink.className = 'btn btn-sm btn-outline-secondary';
                editLink.href = '/monitoring/jobs/' + encodeURIComponent(job.id) + '/edit?mode=STREAMING';
                editLink.title = 'Edit';
                editLink.setAttribute('aria-label', 'Edit');
                editLink.innerHTML = '<i class="bi bi-pencil"></i><span class="ms-1">Edit</span>';
                editLink.addEventListener('click', event => event.stopPropagation());
                textActions.appendChild(editLink);
            }
            actionCell.appendChild(textActions);

            const iconActions = document.createElement('div');
            iconActions.className = 'table-actions table-actions-icons';
            const selectIconButton = document.createElement('button');
            selectIconButton.type = 'button';
            selectIconButton.className = 'btn btn-sm ' + (String(job.id) === String(selectedJobId) ? 'btn-primary' : 'btn-outline-primary');
            selectIconButton.disabled = !job.enabled;
            selectIconButton.title = String(job.id) === String(selectedJobId) ? 'Selected' : 'Select';
            selectIconButton.setAttribute('aria-label', selectIconButton.title);
            selectIconButton.innerHTML = '<i class="bi bi-play-circle"></i>';
            selectIconButton.addEventListener('click', event => {
                event.stopPropagation();
                selectJob(job.id);
            });
            iconActions.appendChild(selectIconButton);
            if (canView) {
                const viewIconLink = document.createElement('a');
                viewIconLink.className = 'btn btn-sm btn-outline-secondary';
                viewIconLink.href = '/monitoring/jobs/' + encodeURIComponent(job.id) + '?mode=STREAMING';
                viewIconLink.title = 'View';
                viewIconLink.setAttribute('aria-label', 'View');
                viewIconLink.innerHTML = '<i class="bi bi-eye"></i>';
                viewIconLink.addEventListener('click', event => event.stopPropagation());
                iconActions.appendChild(viewIconLink);
            }
            if (canConfigure) {
                const editIconLink = document.createElement('a');
                editIconLink.className = 'btn btn-sm btn-outline-secondary';
                editIconLink.href = '/monitoring/jobs/' + encodeURIComponent(job.id) + '/edit?mode=STREAMING';
                editIconLink.title = 'Edit';
                editIconLink.setAttribute('aria-label', 'Edit');
                editIconLink.innerHTML = '<i class="bi bi-pencil"></i>';
                editIconLink.addEventListener('click', event => event.stopPropagation());
                iconActions.appendChild(editIconLink);
            }
            actionCell.appendChild(iconActions);
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

        if (count) count.textContent = filtered.length + (filtered.length === 1 ? ' job' : ' jobs');
        loading.classList.add('d-none');
        const hasJobs = jobs.length > 0;
        const hasMatches = filtered.length > 0;
        empty.classList.toggle('d-none', hasJobs);
        noResults.classList.toggle('d-none', !hasJobs || hasMatches);
        tableWrapper.classList.toggle('d-none', !hasMatches);
        pagination.classList.toggle('d-none', !hasMatches);
        pageSummary.textContent = filtered.length
            ? 'Showing ' + (start + 1) + '–' + Math.min(start + pageSize, filtered.length) + ' of ' + filtered.length
            : 'No jobs match your search';
        firstButton.disabled = page <= 0;
        previousButton.disabled = page <= 0;
        nextButton.disabled = page >= pageCount - 1;
        lastButton.disabled = page >= pageCount - 1;
        updateSortIndicators();
        if (window.DataTable) {
            window.DataTable.updateCompactActions({ containerId: 'streamingJobsTableWrapper' });
        }
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
        if (startButton) startButton.disabled = !job.enabled || !job.manualRunEnabled || active;
        if (stopButton) stopButton.disabled = !active;
    }

    function selectJob(jobId) {
        const changed = String(selectedJobId) !== String(jobId);
        selectedJobId = jobId;
        if (changed) {
            outputLines = [];
            const job = selectedJob();
            if (job && job.enabled && activeStates.has(job.status)) {
                output.textContent = 'Connecting to streaming output…';
                connectOutput(jobId);
            } else {
                closeEventSource();
                output.textContent = job && !job.enabled
                    ? 'This streaming job is disabled. Enable it before running or viewing live output.'
                    : 'This job is not running. Click Run to start it.';
            }
        }
        renderTable();
        updateDetails(selectedJob());
    }

    async function runAction(action) {
        const job = selectedJob();
        if (!job) return;
        if (!job.enabled) {
            showError('This streaming job is disabled. Enable it before running.');
            return;
        }
        if (action === 'start' && !job.manualRunEnabled) {
            showError('Manual execution is disabled for this job.');
            return;
        }
        hideError();
        if (startButton) startButton.disabled = true;
        if (stopButton) stopButton.disabled = true;
        try {
            await request('/api/streaming-jobs/' + encodeURIComponent(job.id) + '/' + action, { method: 'POST' });
            await refresh(false);
            const refreshedJob = selectedJob();
            if (action === 'start' && refreshedJob && activeStates.has(refreshedJob.status)) {
                output.textContent = 'Connecting to streaming output…';
                connectOutput(refreshedJob.id);
            }
        } catch (e) {
            if (!pageLeaving) {
                showError(e.message);
                await refresh(false);
            }
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
            // Navigating away can cancel an in-flight fetch; that is not a user-facing error.
            if (!pageLeaving) showError(e.message);
        }
    }

    searchInput.addEventListener('input', () => {
        page = 0;
        if (clearSearchButton) clearSearchButton.classList.toggle('d-none', searchInput.value.trim() === '');
        renderTable();
    });
    if (clearSearchButton) {
        clearSearchButton.addEventListener('click', () => {
            searchInput.value = '';
            clearSearchButton.classList.add('d-none');
            page = 0;
            renderTable();
            searchInput.focus();
        });
    }
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
    firstButton.addEventListener('click', () => {
        page = 0;
        renderTable();
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
    lastButton.addEventListener('click', () => {
        const pageSize = Number(pageSizeSelect.value) || 10;
        page = Math.max(0, Math.ceil(visibleJobs().length / pageSize) - 1);
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
        pageLeaving = true;
        window.clearInterval(refreshTimer);
        closeEventSource();
    });
})();
