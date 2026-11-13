(() => {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.querySelector('form[action*="/alerts/rules/save"]');
        if (!form || form.dataset.ruleWorkspaceReady === 'true') return;

        const cards = Array.from(form.querySelectorAll(':scope > .app-card'));
        const identify = card => {
            const heading = card.querySelector('.app-card-header h2');
            return (heading?.textContent || '').trim().toLowerCase();
        };
        const groups = [
            { id: 'basics', label: 'Rule Details', icon: 'bi-sliders', match: title => title === 'rule details' },
            { id: 'detection', label: 'Detection', icon: 'bi-diagram-3', match: title => title === 'alert condition' },
            { id: 'lifecycle', label: 'Alert Lifecycle', icon: 'bi-arrow-repeat', match: title => title === 'recovery criteria' },
            { id: 'notifications', label: 'Notifications', icon: 'bi-bell', match: title => title.includes('notifications') },
            { id: 'presentation', label: 'Message Presentation', icon: 'bi-envelope-paper', match: title => title === 'alert content' || title.includes('alert presentation designer') }
        ];

        const assigned = new Map(groups.map(group => [group.id, []]));
        cards.forEach(card => {
            const title = identify(card);
            const group = groups.find(candidate => candidate.match(title));
            if (group) assigned.get(group.id).push(card);
        });
        if (groups.some(group => assigned.get(group.id).length === 0)) return;

        form.dataset.ruleWorkspaceReady = 'true';
        const workspace = document.createElement('section');
        workspace.className = 'alert-rule-workspace mb-4';
        workspace.setAttribute('aria-label', 'Alert rule configuration');

        const navigation = document.createElement('ul');
        navigation.className = 'nav nav-tabs mb-3';
        navigation.setAttribute('role', 'tablist');

        const content = document.createElement('div');
        content.className = 'tab-content';

        // Insert the workspace before moving the cards out of the form.
        // The original first card is no longer a child of the form afterwards.
        form.insertBefore(workspace, cards[0]);

        const activate = (group, pane, button) => {
            navigation.querySelectorAll('[role="tab"]').forEach(tab => {
                const active = tab === button;
                tab.classList.toggle('active', active);
                tab.setAttribute('aria-selected', String(active));
                tab.tabIndex = active ? 0 : -1;
            });
            content.querySelectorAll('.tab-pane').forEach(tabPane => {
                const active = tabPane === pane;
                tabPane.classList.toggle('active', active);
                tabPane.classList.toggle('show', active);
            });
        };

        groups.forEach((group, index) => {
            const paneId = 'alert-rule-tab-' + group.id;
            const navItem = document.createElement('li');
            navItem.className = 'nav-item';
            navItem.setAttribute('role', 'presentation');
            const tab = document.createElement('button');
            tab.type = 'button';
            tab.className = 'nav-link' + (index === 0 ? ' active' : '');
            tab.id = paneId + '-tab';
            tab.dataset.bsToggle = 'tab';
            tab.dataset.bsTarget = '#' + paneId;
            tab.setAttribute('role', 'tab');
            tab.setAttribute('aria-controls', paneId);
            tab.setAttribute('aria-selected', String(index === 0));
            tab.innerHTML = '<i class="bi ' + group.icon + ' me-2" aria-hidden="true"></i>' + group.label;
            navItem.appendChild(tab);
            navigation.appendChild(navItem);

            const pane = document.createElement('div');
            pane.className = 'tab-pane fade' + (index === 0 ? ' show active' : '');
            pane.id = paneId;
            pane.setAttribute('role', 'tabpanel');
            pane.setAttribute('aria-labelledby', tab.id);
            pane.tabIndex = 0;

            assigned.get(group.id).forEach((card, cardIndex) => {
                const header = card.querySelector('.app-card-header');
                const body = card.querySelector('.app-card-body');
                if (!header || !body) {
                    pane.appendChild(card);
                    return;
                }
                const collapseId = paneId + '-panel-' + cardIndex;
                const title = header.querySelector('h2');
                const expandedByDefault = group.id === 'presentation'
                    ? (title?.textContent || '').toLowerCase().includes('message presentation designer')
                    : index === 0 && cardIndex === 0;
                const toggle = document.createElement('button');
                toggle.type = 'button';
                toggle.className = 'btn btn-link text-decoration-none text-reset p-0 d-flex align-items-center gap-2';
                toggle.dataset.bsToggle = 'collapse';
                toggle.dataset.bsTarget = '#' + collapseId;
                toggle.setAttribute('aria-expanded', String(expandedByDefault));
                toggle.setAttribute('aria-controls', collapseId);
                const chevron = document.createElement('i');
                chevron.className = 'bi bi-chevron-down small';
                chevron.setAttribute('aria-hidden', 'true');
                if (title) {
                    const heading = document.createElement('span');
                    heading.className = title.className;
                    heading.textContent = title.textContent.trim();
                    toggle.appendChild(heading);
                    title.replaceWith(toggle);
                } else {
                    header.prepend(toggle);
                }
                const collapse = document.createElement('div');
                collapse.id = collapseId;
                collapse.className = 'collapse' + (expandedByDefault ? ' show' : '');
                body.parentNode.insertBefore(collapse, body);
                collapse.appendChild(body);
                toggle.appendChild(chevron);
                toggle.addEventListener('click', () => {
                    const expanded = toggle.getAttribute('aria-expanded') === 'true';
                    toggle.setAttribute('aria-expanded', String(!expanded));
                    chevron.className = 'bi ' + (expanded ? 'bi-chevron-down' : 'bi-chevron-up') + ' small';
                });
                pane.appendChild(card);
            });

            content.appendChild(pane);
            tab.addEventListener('click', () => activate(group, pane, tab));
            tab.addEventListener('keydown', event => {
                if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
                event.preventDefault();
                const tabs = Array.from(navigation.querySelectorAll('[role="tab"]'));
                const current = tabs.indexOf(tab);
                const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
                    : (current + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
                tabs[next].focus();
                tabs[next].click();
            });
        });

        workspace.append(navigation, content);

        const revealInvalidControl = () => {
            const invalid = form.querySelector(':invalid');
            if (!invalid) return;
            const pane = invalid.closest('.tab-pane');
            if (!pane) return;
            const tab = navigation.querySelector('[aria-controls="' + pane.id + '"]');
            if (tab) tab.click();
            const collapse = invalid.closest('.collapse');
            if (collapse && !collapse.classList.contains('show')) {
                if (window.bootstrap?.Collapse) window.bootstrap.Collapse.getOrCreateInstance(collapse, { toggle: false }).show();
                else collapse.classList.add('show');
                const trigger = form.querySelector('[data-bs-target="#' + collapse.id + '"]');
                trigger?.setAttribute('aria-expanded', 'true');
            }
            window.setTimeout(() => invalid.focus({ preventScroll: false }), 80);
        };

        form.addEventListener('submit', event => {
            if (!form.checkValidity()) {
                event.preventDefault();
                event.stopPropagation();
                revealInvalidControl();
                form.classList.add('was-validated');
            }
        }, true);

        const serverError = form.parentElement?.querySelector('.alert-danger[role="alert"]');
        if (serverError) {
            const text = serverError.textContent.toLowerCase();
            const target = text.includes('recover') ? 'lifecycle'
                : text.includes('condition') ? 'detection'
                : text.includes('notification') || text.includes('recipient') ? 'notifications'
                : text.includes('presentation') || text.includes('message') ? 'presentation' : null;
            if (target) {
                const tab = navigation.querySelector('#alert-rule-tab-' + target + '-tab');
                tab?.click();
            }
        }
    });
})();