(() => {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        const tabContent = document.getElementById('alertRuleTabContent');
        const form = document.getElementById('alertRuleForm');
        if (!tabContent || !form) return;

        let loadPromise = null;
        const secondaryTabIds = [
            'notification-routing-pane',
            'alert-condition-pane',
            'recovery-criteria-pane',
            'message-presentation-pane'
        ];

        const showLoadError = message => {
            const placeholder = tabContent.querySelector('[data-lazy-secondary-placeholder]');
            if (!placeholder) return;
            placeholder.innerHTML = '';
            const alert = document.createElement('div');
            alert.className = 'alert alert-danger m-3';
            alert.textContent = message;
            placeholder.appendChild(alert);
        };

        const loadSecondaryTabs = () => {
            if (tabContent.dataset.secondaryLoaded === 'true') return Promise.resolve();
            if (loadPromise) return loadPromise;

            loadPromise = (async () => {
                const response = await fetch(tabContent.dataset.lazySecondaryUrl, {
                    credentials: 'same-origin',
                    headers: {
                        'Accept': 'text/html',
                        'X-Requested-With': 'XMLHttpRequest'
                    }
                });
                if (response.status === 401) {
                    window.location.assign('/login?expired');
                    throw new Error('Your session has expired. Sign in again to continue.');
                }
                if (response.status === 403) {
                    throw new Error('You do not have permission to load these alert rule settings.');
                }
                if (!response.ok) throw new Error('Unable to load the remaining alert rule settings.');

                const html = await response.text();
                const template = document.createElement('template');
                template.innerHTML = html;
                const loadedIds = new Set(Array.from(template.content.querySelectorAll('.tab-pane'))
                    .map(pane => pane.id));
                if (secondaryTabIds.some(id => !loadedIds.has(id))) {
                    throw new Error('The alert rule settings response was incomplete. Please retry.');
                }

                tabContent.querySelectorAll('[data-lazy-secondary-placeholder]').forEach(placeholder => placeholder.remove());
                tabContent.appendChild(template.content);
                tabContent.dataset.secondaryLoaded = 'true';
                document.dispatchEvent(new CustomEvent('alert-rule-secondary-tabs-loaded'));
            })().catch(error => {
                showLoadError(error.message || 'Unable to load alert rule settings.');
                throw error;
            }).finally(() => {
                loadPromise = null;
            });
            return loadPromise;
        };

        const activateTabFor = element => {
            const pane = element.closest('.tab-pane');
            if (!pane || pane.classList.contains('active')) return Promise.resolve();
            const button = document.querySelector('[data-bs-target="#' + pane.id + '"]');
            if (!button) return Promise.resolve();
            return new Promise(resolve => {
                button.addEventListener('shown.bs.tab', resolve, {once: true});
                button.click();
            });
        };

        document.querySelectorAll('[data-lazy-secondary="true"]').forEach(button => {
            button.addEventListener('show.bs.tab', event => {
                if (tabContent.dataset.secondaryLoaded === 'true') return;
                event.preventDefault();
                loadSecondaryTabs().then(() => button.click()).catch(() => {});
            });
        });

        form.addEventListener('submit', async event => {
            if (form.dataset.secondaryReady === 'true') {
                delete form.dataset.secondaryReady;
                return;
            }
            event.preventDefault();

            try {
                await loadSecondaryTabs();
            } catch (_) {
                return;
            }

            const invalid = form.querySelector(':invalid');
            if (invalid) {
                const pane = invalid.closest('.tab-pane');
                if (pane && !pane.classList.contains('active')) {
                    const tabButton = document.querySelector('[data-bs-target="#' + pane.id + '"]');
                    if (tabButton) {
                        tabButton.addEventListener('shown.bs.tab', () => {
                            const collapsed = invalid.closest('.collapse:not(.show)');
                            if (collapsed && window.bootstrap?.Collapse) {
                                window.bootstrap.Collapse.getOrCreateInstance(collapsed).show();
                            }
                            window.setTimeout(() => invalid.reportValidity(), 150);
                        }, {once: true});
                        tabButton.click();
                        return;
                    }
                }
                invalid.reportValidity();
                return;
            }

            form.dataset.secondaryReady = 'true';
            if (event.submitter) form.requestSubmit(event.submitter);
            else form.requestSubmit();
        });
    });
})();
