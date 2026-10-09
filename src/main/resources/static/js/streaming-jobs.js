(() => {
    const list = document.getElementById('streamingJobsList');
    if (!list) return;
    const template = document.getElementById('streamingJobCardTemplate');
    const loading = document.getElementById('streamingJobsLoading');
    const empty = document.getElementById('streamingJobsEmpty');
    const error = document.getElementById('streamingJobsError');
    const cards = new Map();
    let refreshTimer;

    function showError(message) {
        error.textContent = message || 'Unable to complete the streaming job request.';
        error.classList.remove('d-none');
    }
    function hideError() { error.classList.add('d-none'); }
    async function request(url, options = {}) {
        const headerName = document.querySelector('meta[name="_csrf_header"]')?.content;
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const headers = { 'Accept': 'application/json', ...(options.headers || {}) };
        if (headerName && token) headers[headerName] = token;
        const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
        if (!response.ok) throw new Error((await response.text()) || ('Request failed with HTTP ' + response.status));
        return response.json();
    }
    function addLine(output, line) {
        if (output.textContent === 'Waiting for output…') output.textContent = '';
        output.textContent += line + '\n';
        const lines = output.textContent.split('\n');
        if (lines.length > 510) output.textContent = lines.slice(-500).join('\n');
        output.scrollTop = output.scrollHeight;
    }
    function createCard(job) {
        const root = template.content.firstElementChild.cloneNode(true);
        const card = {
            root,
            name: root.querySelector('.streaming-job-name'),
            description: root.querySelector('.streaming-job-description'),
            status: root.querySelector('.streaming-job-status'),
            meta: root.querySelector('.streaming-job-meta'),
            start: root.querySelector('.streaming-start'),
            stop: root.querySelector('.streaming-stop'),
            output: root.querySelector('.streaming-output'),
            events: null
        };
        if (card.start) card.start.addEventListener('click', async () => {
            card.start.disabled = true; hideError();
            try { await request('/api/streaming-jobs/' + job.id + '/start', { method: 'POST' }); await refresh(false); }
            catch (e) { showError(e.message); await refresh(false); }
        });
        if (card.stop) card.stop.addEventListener('click', async () => {
            card.stop.disabled = true; hideError();
            try { await request('/api/streaming-jobs/' + job.id + '/stop', { method: 'POST' }); await refresh(false); }
            catch (e) { showError(e.message); await refresh(false); }
        });
        card.events = new EventSource('/api/streaming-jobs/' + job.id + '/events');
        card.events.addEventListener('line', event => addLine(card.output, event.data));
        card.events.addEventListener('state', () => refresh(false));
        return card;
    }
    function updateCard(card, job) {
        card.name.textContent = job.name;
        card.description.textContent = job.description || '';
        card.status.textContent = job.status;
        card.status.className = 'badge streaming-job-status ' +
            (['RUNNING','STARTING','STOPPING','RECOVERY_REQUIRED'].includes(job.status) ? 'bg-warning' :
                (job.status === 'COMPLETED' || job.status === 'STOPPED' ? 'bg-success' :
                    (job.status === 'FAILED' || job.status === 'TIMED_OUT' ? 'bg-danger' : 'bg-secondary')));
        const elapsed = job.startedAt ? Math.max(0, Math.floor((Date.now() - new Date(job.startedAt).getTime()) / 1000)) : 0;
        const remaining = Math.max(0, (job.maxRuntimeSeconds || 300) - elapsed);
        card.meta.textContent = 'Maximum runtime: ' + job.maxRuntimeSeconds + 's'
            + (job.startedAt ? ' • Started: ' + new Date(job.startedAt).toLocaleString() : '')
            + (['STARTING','RUNNING','STOPPING','RECOVERY_REQUIRED'].includes(job.status) ? ' • Elapsed: ' + elapsed + 's • Remaining: ' + remaining + 's' : '')
            + (job.startedBy ? ' • Started by user #' + job.startedBy : '')
            + (job.message ? ' • ' + job.message : '');
        const active = ['STARTING','RUNNING','STOPPING','RECOVERY_REQUIRED'].includes(job.status);
        if (card.start) card.start.disabled = active;
        if (card.stop) card.stop.disabled = !active;
    }
    function render(jobs) {
        loading.classList.add('d-none');
        empty.classList.toggle('d-none', jobs.length > 0);
        const ids = new Set(jobs.map(job => job.id));
        for (const [id, card] of cards) {
            if (!ids.has(id)) {
                if (card.events) card.events.close();
                card.root.remove();
                cards.delete(id);
            }
        }
        for (const job of jobs) {
            let card = cards.get(job.id);
            if (!card) { card = createCard(job); cards.set(job.id, card); }
            updateCard(card, job);
            list.appendChild(card.root);
        }
    }
    async function refresh(showLoading = true) {
        if (showLoading) loading.classList.remove('d-none');
        hideError();
        try { render(await request('/api/streaming-jobs')); }
        catch (e) { loading.classList.add('d-none'); showError(e.message); }
    }
    document.getElementById('refreshStreamingJobs').addEventListener('click', () => refresh());
    refresh();
    refreshTimer = window.setInterval(() => refresh(false), 5000);
    window.addEventListener('beforeunload', () => {
        window.clearInterval(refreshTimer);
        for (const card of cards.values()) if (card.events) card.events.close();
    });
})();
