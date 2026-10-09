(() => {
    const list = document.getElementById('streamingJobsList');
    if (!list) return;
    const template = document.getElementById('streamingJobCardTemplate');
    const loading = document.getElementById('streamingJobsLoading');
    const empty = document.getElementById('streamingJobsEmpty');
    const error = document.getElementById('streamingJobsError');
    const cards = new Map();
    let refreshTimer;

    function showError(message) { error.textContent = message || 'Unable to complete the streaming job request.'; error.classList.remove('d-none'); }
    function hideError() { error.classList.add('d-none'); }
    async function request(url, options = {}) {
        const response = await fetch(url, { credentials: 'same-origin', ...options,
            headers: { 'Accept': 'application/json',
                [document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN']:
                    document.querySelector('meta[name="_csrf"]')?.content || '',
                ...(options.headers || {}) } });
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
    function attachEvents(job, output) {
        const previous = cards.get(job.id);
        if (previous && previous.events) previous.events.close();
        const events = new EventSource('/api/streaming-jobs/' + job.id + '/events');
        events.addEventListener('line', e => addLine(output, e.data));
        events.addEventListener('state', () => refresh(false));
        cards.set(job.id, { ...(cards.get(job.id) || {}), events, output });
    }
    function render(jobs) {
        loading.classList.add('d-none');
        empty.classList.toggle('d-none', jobs.length > 0);
        list.replaceChildren();
        const ids = new Set(jobs.map(job => job.id));
        for (const [id, card] of cards) if (!ids.has(id)) { if (card.events) card.events.close(); cards.delete(id); }
        jobs.forEach(job => {
            const fragment = template.content.cloneNode(true);
            const name = fragment.querySelector('.streaming-job-name');
            const description = fragment.querySelector('.streaming-job-description');
            const status = fragment.querySelector('.streaming-job-status');
            const meta = fragment.querySelector('.streaming-job-meta');
            const start = fragment.querySelector('.streaming-start');
            const stop = fragment.querySelector('.streaming-stop');
            const output = fragment.querySelector('.streaming-output');
            name.textContent = job.name;
            description.textContent = job.description || '';
            status.textContent = job.status;
            status.classList.add(['RUNNING','STARTING','STOPPING','RECOVERY_REQUIRED'].includes(job.status) ? 'bg-warning' :
                (job.status === 'COMPLETED' || job.status === 'STOPPED' ? 'bg-success' :
                    (job.status === 'FAILED' || job.status === 'TIMED_OUT' ? 'bg-danger' : 'bg-secondary')));
            const elapsed = job.startedAt ? Math.max(0, Math.floor((Date.now() - new Date(job.startedAt).getTime()) / 1000)) : 0;
            const remaining = Math.max(0, (job.maxRuntimeSeconds || 300) - elapsed);
            meta.textContent = 'Maximum runtime: ' + job.maxRuntimeSeconds + 's'
                + (job.startedAt ? ' • Started: ' + new Date(job.startedAt).toLocaleString() : '')
                + (['STARTING','RUNNING','STOPPING','RECOVERY_REQUIRED'].includes(job.status) ? ' • Elapsed: ' + elapsed + 's • Remaining: ' + remaining + 's' : '')
                + (job.startedBy ? ' • Started by user #' + job.startedBy : '')
                + (job.message ? ' • ' + job.message : '');
            const active = ['STARTING','RUNNING','STOPPING','RECOVERY_REQUIRED'].includes(job.status);
            start.disabled = active; stop.disabled = !active;
            start.addEventListener('click', async () => {
                start.disabled = true; hideError();
                try { await request('/api/streaming-jobs/' + job.id + '/start', { method: 'POST' }); await refresh(false); }
                catch (e) { showError(e.message); await refresh(false); }
            });
            stop.addEventListener('click', async () => {
                stop.disabled = true; hideError();
                try { await request('/api/streaming-jobs/' + job.id + '/stop', { method: 'POST' }); await refresh(false); }
                catch (e) { showError(e.message); await refresh(false); }
            });
            const old = cards.get(job.id);
            if (old && old.output && old.output.textContent !== 'Waiting for output…') output.textContent = old.output.textContent;
            cards.set(job.id, { ...(old || {}), output });
            list.appendChild(fragment);
            attachEvents(job, output);
        });
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
