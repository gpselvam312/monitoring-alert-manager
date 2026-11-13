(() => {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
    const routesInput = document.getElementById('notificationRoutesJson');
    const routesContainer = document.getElementById('notificationRoutingAccordion');
    const routesEmpty = document.getElementById('notificationRoutesEmpty');
    const routeTemplate = document.getElementById('notificationRouteTemplate');
    const channelTypes = ['EMAIL', 'TEAMS', 'WHATSAPP'];
    const panelFor = type => routesContainer?.querySelector('[data-channel-routes="' + type + '"]');
    const channelTypeFor = row => row.querySelector('.route-channel')?.selectedOptions[0]?.dataset.channelType || '';
    const routeRows = () => Array.from(routesContainer.querySelectorAll('.notification-route-row'));
    const panelIdFor = type => 'routingPanel' + (type === 'WHATSAPP' ? 'Whatsapp' : type === 'TEAMS' ? 'Teams' : 'Email');
    const isChannelEnabled = type => !!document.querySelector('[data-channel-toggle="' + type + '"]')?.checked;
    const setChannelEnabled = (type, enabled, openPanel = false) => {
        const toggle = document.querySelector('[data-channel-toggle="' + type + '"]');
        const item = routesContainer.querySelector('[data-channel-accordion-item="' + type + '"]');
        const panel = document.getElementById(panelIdFor(type));
        if (!toggle || !item || !panel) return;
        toggle.checked = enabled;
        item.classList.toggle('d-none', !enabled);
        document.querySelector('[data-channel-option="' + type + '"]')?.classList.toggle('channel-enabled', enabled);
        if (!enabled) {
            panel.classList.remove('show');
            panel.querySelectorAll('.route-enabled').forEach(input => { input.checked = false; });
        } else {
            panel.querySelectorAll('.route-enabled').forEach(input => { input.checked = true; });
            const hasChannelOption = Array.from(routeTemplate.content.querySelectorAll('.route-channel option'))
                .some(option => option.dataset.channelType === type);
            const hasRoute = routeRows().some(row => channelTypeFor(row) === type);
            if (!hasRoute && hasChannelOption) addRoute(null, type);
            if (openPanel && window.bootstrap?.Collapse) {
                window.bootstrap.Collapse.getOrCreateInstance(panel, { toggle: false }).show();
            } else if (openPanel) {
                panel.classList.add('show');
            }
        }
        syncNotificationRoutes();
    };
    const moveRouteToChannel = row => {
        const destination = panelFor(channelTypeFor(row));
        if (destination && row.parentElement !== destination) destination.appendChild(row);
        else if (!destination) panelFor('EMAIL')?.appendChild(row);
    };
    const syncNotificationRoutes = () => {
        const rows = routeRows();
        routesInput.value = JSON.stringify(rows.map(row => {
            return {
                channelId: row.querySelector('.route-channel').value || null,
                severity: row.querySelector('.route-severity').value,
                eventType: row.querySelector('.route-event').value,
                recipientIds: Array.from(row.querySelector('.route-recipients').selectedOptions).map(option => option.value),
                groupIds: Array.from(row.querySelector('.route-groups').selectedOptions).map(option => option.value),
                ccRecipients: channelTypeFor(row) === 'EMAIL' ? row.querySelector('.route-cc').value.trim() : '',
                minimumIntervalSeconds: row.querySelector('.route-interval').value || '300',
                enabled: row.querySelector('.route-enabled').checked
            };
        }));
        const hasEnabledRoute = rows.some(row => row.querySelector('.route-enabled').checked
            && Boolean(row.querySelector('.route-channel').value));
        routesEmpty.classList.toggle('d-none', hasEnabledRoute);
        const activeChannels = channelTypes.filter(type => isChannelEnabled(type));
        document.getElementById('activeChannelCount').textContent = activeChannels.length + ' Active';
        channelTypes.forEach(type => {
            const channelRows = rows.filter(row => channelTypeFor(row) === type);
            const empty = document.querySelector('[data-channel-empty="' + type + '"]');
            if (empty) empty.classList.toggle('d-none', channelRows.length > 0);
            const addButton = document.querySelector('[data-add-channel-route="' + type + '"]');
            if (addButton) addButton.disabled = !Array.from(routeTemplate.content.querySelectorAll('.route-channel option')).some(option => option.dataset.channelType === type);
        });
        rows.forEach((row, index) => {
            initializeSearchableMultiSelect(row.querySelector('.route-recipients'), 'Select recipients...');
        initializeSearchableMultiSelect(row.querySelector('.route-groups'), 'Select recipient groups...');
        const type = channelTypeFor(row);
            const channelIndex = rows.filter((candidate, candidateIndex) => candidateIndex <= index && channelTypeFor(candidate) === type).length;
            row.querySelector('.route-heading').textContent = (type || 'Notification') + ' route ' + channelIndex;
        });
    };
    const initializeSearchableMultiSelect = (select, placeholder) => {
        if (!select || select.dataset.searchableReady === 'true') return;
        select.dataset.searchableReady = 'true';

        const wrapper = document.createElement('div');
        wrapper.className = 'route-multiselect dropdown';
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'btn btn-outline-secondary route-multiselect-toggle dropdown-toggle w-100';
        toggle.dataset.bsToggle = 'dropdown';
        toggle.dataset.bsAutoClose = 'outside';
        toggle.setAttribute('aria-expanded', 'false');
        toggle.setAttribute('aria-label', placeholder);
        const selectedContent = document.createElement('span');
        selectedContent.className = 'route-multiselect-selection';
        toggle.appendChild(selectedContent);

        const menu = document.createElement('div');
        menu.className = 'dropdown-menu route-multiselect-menu p-2 shadow';
        const searchWrap = document.createElement('div');
        searchWrap.className = 'input-group input-group-sm mb-2';
        const searchIcon = document.createElement('span');
        searchIcon.className = 'input-group-text';
        searchIcon.innerHTML = '<i class="bi bi-search" aria-hidden="true"></i>';
        const search = document.createElement('input');
        search.type = 'search';
        search.className = 'form-control route-multiselect-search';
        search.placeholder = 'Search ' + placeholder.toLowerCase() + '...';
        search.autocomplete = 'off';
        search.setAttribute('aria-label', 'Search ' + placeholder.toLowerCase());
        searchWrap.append(searchIcon, search);
        const actions = document.createElement('div');
        actions.className = 'd-flex justify-content-between align-items-center small border-bottom pb-2 mb-2';
        const selectAll = document.createElement('button');
        selectAll.type = 'button';
        selectAll.className = 'btn btn-link btn-sm p-0 text-decoration-none';
        selectAll.textContent = 'Select all';
        const clearAll = document.createElement('button');
        clearAll.type = 'button';
        clearAll.className = 'btn btn-link btn-sm p-0 text-decoration-none text-secondary';
        clearAll.textContent = 'Clear all';
        actions.append(selectAll, clearAll);
        const list = document.createElement('div');
        list.className = 'route-multiselect-options';
        menu.append(searchWrap, actions, list);
        wrapper.append(toggle, menu);
        select.parentElement.insertBefore(wrapper, select);
        wrapper.appendChild(select);

        const render = () => {
            selectedContent.replaceChildren();
            const selected = Array.from(select.selectedOptions);
            if (!selected.length) {
                const hint = document.createElement('span');
                hint.className = 'route-multiselect-placeholder';
                hint.textContent = placeholder;
                selectedContent.appendChild(hint);
            } else {
                selected.forEach(option => {
                    const chip = document.createElement('span');
                    chip.className = 'route-multiselect-chip';
                    const label = document.createElement('span');
                    label.textContent = option.textContent.trim();
                    const remove = document.createElement('span');
                    remove.className = 'route-multiselect-remove';
                    remove.setAttribute('role', 'button');
                    remove.setAttribute('tabindex', '0');
                    remove.setAttribute('aria-label', 'Remove ' + option.textContent.trim());
                    remove.innerHTML = '&times;';
                    const deselect = event => {
                        event.preventDefault();
                        event.stopPropagation();
                        option.selected = false;
                        select.dispatchEvent(new Event('change', { bubbles: true }));
                    };
                    remove.addEventListener('click', deselect);
                    remove.addEventListener('keydown', event => {
                        if (event.key === 'Enter' || event.key === ' ') deselect(event);
                    });
                    chip.append(label, remove);
                    selectedContent.appendChild(chip);
                });
            }
            list.replaceChildren();
            Array.from(select.options).forEach((option, index) => {
                const item = document.createElement('label');
                item.className = 'route-multiselect-option';
                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.className = 'form-check-input';
                checkbox.checked = option.selected;
                checkbox.addEventListener('change', () => {
                    option.selected = checkbox.checked;
                    select.dispatchEvent(new Event('change', { bubbles: true }));
                });
                const text = document.createElement('span');
                text.textContent = option.textContent.trim();
                item.append(checkbox, text);
                item.dataset.searchText = (option.textContent || '').toLocaleLowerCase();
                item.hidden = option.hidden || !item.dataset.searchText.includes(search.value.trim().toLocaleLowerCase());
                list.appendChild(item);
            });
        };

        search.addEventListener('input', render);
        search.addEventListener('click', event => event.stopPropagation());
        search.addEventListener('keydown', event => event.stopPropagation());
        selectAll.addEventListener('click', () => {
            Array.from(list.children).forEach((item, index) => {
                if (!item.hidden) select.options[index].selected = true;
            });
            select.dispatchEvent(new Event('change', { bubbles: true }));
        });
        clearAll.addEventListener('click', () => {
            Array.from(select.options).forEach(option => { option.selected = false; });
            select.dispatchEvent(new Event('change', { bubbles: true }));
        });
        select.addEventListener('change', render);
        render();
    };
    const initializeEmailChipInput = input => {
        if (!input || input.dataset.chipsReady === 'true') return;
        input.dataset.chipsReady = 'true';
        input.type = 'text';
        input.classList.add('visually-hidden');
        input.setAttribute('tabindex', '-1');
        input.setAttribute('aria-hidden', 'true');

        const wrapper = document.createElement('div');
        wrapper.className = 'route-email-multiselect';
        wrapper.setAttribute('role', 'group');
        wrapper.setAttribute('aria-label', 'CC email addresses');
        const chips = document.createElement('div');
        chips.className = 'route-email-chips';
        const editor = document.createElement('input');
        editor.type = 'email';
        editor.className = 'route-email-editor';
        editor.placeholder = 'Type an email address and press Enter';
        editor.autocomplete = 'off';
        editor.setAttribute('aria-label', 'Add CC email address');
        const feedback = document.createElement('div');
        feedback.className = 'invalid-feedback route-email-feedback';
        wrapper.append(chips, editor, feedback);
        input.parentElement.insertBefore(wrapper, input);
        wrapper.appendChild(input);

        const parse = () => input.value.split(',').map(value => value.trim()).filter(Boolean);
        const render = () => {
            chips.replaceChildren();
            parse().forEach(address => {
                const chip = document.createElement('span');
                chip.className = 'route-multiselect-chip';
                const label = document.createElement('span');
                label.textContent = address;
                const remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'route-multiselect-remove';
                remove.textContent = '×';
                remove.setAttribute('aria-label', 'Remove ' + address);
                remove.addEventListener('click', () => {
                    input.value = parse().filter(item => item.toLocaleLowerCase() !== address.toLocaleLowerCase()).join(', ');
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    render();
                });
                chip.append(label, remove);
                chips.appendChild(chip);
            });
            wrapper.classList.toggle('is-invalid', input.classList.contains('is-invalid'));
        };
        const addAddress = () => {
            const address = editor.value.trim().replace(/[,;]+$/, '');
            if (!address) return;
            const valid = /^[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]+$/.test(address);
            const existing = parse();
            if (!valid) {
                feedback.textContent = 'Enter a valid email address.';
                wrapper.classList.add('is-invalid');
                editor.focus();
                return;
            }
            if (existing.some(item => item.toLocaleLowerCase() === address.toLocaleLowerCase())) {
                feedback.textContent = 'This email address has already been added.';
                wrapper.classList.add('is-invalid');
                editor.focus();
                return;
            }
            if (existing.length >= 20) {
                feedback.textContent = 'You can add up to 20 CC email addresses.';
                wrapper.classList.add('is-invalid');
                editor.focus();
                return;
            }
            input.value = [...existing, address].join(', ');
            editor.value = '';
            feedback.textContent = '';
            wrapper.classList.remove('is-invalid');
            input.dispatchEvent(new Event('change', { bubbles: true }));
            render();
        };
        editor.addEventListener('keydown', event => {
            if (event.key === 'Enter' || event.key === ',' || event.key === ';') {
                event.preventDefault();
                addAddress();
            } else if (event.key === 'Backspace' && !editor.value && parse().length) {
                input.value = parse().slice(0, -1).join(', ');
                input.dispatchEvent(new Event('change', { bubbles: true }));
                render();
            }
        });
        editor.addEventListener('blur', () => {
            if (editor.value.trim()) addAddress();
        });
        editor.addEventListener('input', () => {
            wrapper.classList.remove('is-invalid');
            feedback.textContent = '';
        });
        input.addEventListener('change', render);
        render();
        input._routeEmailEditor = editor;
    };
    const validateRouteTargets = row => {
        const isTeams = channelTypeFor(row) === 'TEAMS';
        const hasRecipients = row.querySelector('.route-recipients').selectedOptions.length > 0;
        const hasGroups = row.querySelector('.route-groups').selectedOptions.length > 0;
        const message = !isTeams && row.querySelector('.route-enabled').checked && !hasRecipients && !hasGroups
            ? 'Select at least one recipient or recipient group for this enabled route.' : '';
        row.querySelectorAll('.route-multiselect-toggle').forEach(toggle => {
            toggle.classList.toggle('is-invalid', Boolean(message));
            toggle.setAttribute('aria-invalid', message ? 'true' : 'false');
        });
        if (!message) row.querySelector('.route-target-validation-message')?.remove();
        return !message;
    };
    const setRouteChannelOptions = row => {
        const isTeams = channelTypeFor(row) === 'TEAMS';
        const isEmail = channelTypeFor(row) === 'EMAIL';
        row.querySelector('.route-teams-hint').classList.toggle('d-none', !isTeams);
        row.querySelector('.route-recipient-container').classList.toggle('d-none', isTeams);
        row.querySelector('.route-group-container').classList.toggle('d-none', isTeams);
        row.querySelector('.route-cc-container').classList.toggle('d-none', !isEmail);
        if (isTeams) {
            row.querySelector('.route-recipients').selectedIndex = -1;
            row.querySelector('.route-groups').selectedIndex = -1;
        } else {
            const expectedGroupType = isEmail ? 'EMAIL' : 'WHATSAPP';
            const groupSelect = row.querySelector('.route-groups');
            Array.from(groupSelect.options).forEach(option => {
                const eligible = option.dataset.groupChannel === expectedGroupType;
                option.hidden = !eligible;
                if (!eligible) option.selected = false;
            });
            groupSelect.dispatchEvent(new Event('change', { bubbles: true }));
            const recipientSelect = row.querySelector('.route-recipients');
            Array.from(recipientSelect.options).forEach(option => {
                const hasDestination = isEmail ? Boolean(option.dataset.email) : Boolean(option.dataset.phone);
                option.hidden = !hasDestination;
                if (!hasDestination) option.selected = false;
            });
            recipientSelect.dispatchEvent(new Event('change', { bubbles: true }));
        }
        if (!isEmail) {
            row.querySelector('.route-cc').value = '';
            row.querySelector('.route-cc').dispatchEvent(new Event('change', { bubbles: true }));
            const editor = row.querySelector('.route-email-editor');
            if (editor) editor.value = '';
        }
        validateRouteTargets(row);
        moveRouteToChannel(row);
        syncNotificationRoutes();
    };
    const addRoute = (route, preferredType) => {
        const fragment = routeTemplate.content.cloneNode(true);
        const row = fragment.querySelector('.notification-route-row');
        const channelSelect = row.querySelector('.route-channel');
        if (route) {
            channelSelect.value = route.channelId == null ? '' : String(route.channelId);
            row.querySelector('.route-severity').value = route.severity || 'CRITICAL';
            row.querySelector('.route-event').value = route.eventType || 'CREATED';
            const recipientIds = Array.isArray(route.recipientIds) ? route.recipientIds
                : route.recipientId == null ? [] : [route.recipientId];
            const groupIds = Array.isArray(route.groupIds) ? route.groupIds
                : route.groupId == null ? [] : [route.groupId];
            Array.from(row.querySelector('.route-recipients').options).forEach(option => {
                option.selected = recipientIds.map(String).includes(option.value);
            });
            Array.from(row.querySelector('.route-groups').options).forEach(option => {
                option.selected = groupIds.map(String).includes(option.value);
            });
            row.querySelector('.route-cc').value = route.ccRecipients || '';
            row.querySelector('.route-cc').dispatchEvent(new Event('change', { bubbles: true }));
            row.querySelector('.route-interval').value = route.minimumIntervalSeconds == null ? '300' : String(route.minimumIntervalSeconds);
            row.querySelector('.route-enabled').checked = route.enabled !== false;
        } else if (preferredType) {
            const matchingOption = Array.from(channelSelect.options).find(option => option.dataset.channelType === preferredType);
            if (matchingOption) channelSelect.value = matchingOption.value;
        }
        initializeEmailChipInput(row.querySelector('.route-cc'));
        const type = channelTypeFor(row);
        (panelFor(type) || panelFor('EMAIL')).appendChild(row);
        channelSelect.addEventListener('change', () => setRouteChannelOptions(row));
        row.querySelector('.remove-notification-route').addEventListener('click', () => { row.remove(); syncNotificationRoutes(); });
        row.querySelectorAll('select,input').forEach(input => input.addEventListener('change', () => {
            validateRouteTargets(row);
            syncNotificationRoutes();
        }));
        row.querySelectorAll('input').forEach(input => input.addEventListener('input', syncNotificationRoutes));
        setRouteChannelOptions(row);
        validateRouteTargets(row);
        syncNotificationRoutes();
    };
    document.querySelectorAll('[data-channel-toggle]').forEach(toggle => toggle.addEventListener('change', () => setChannelEnabled(toggle.dataset.channelToggle, toggle.checked, toggle.checked)));
    document.querySelectorAll('[data-add-channel-route]').forEach(button => button.addEventListener('click', () => addRoute(null, button.dataset.addChannelRoute)));
    try {
        const initialRoutes = JSON.parse(routesInput?.value || '[]');
        if (Array.isArray(initialRoutes)) initialRoutes.forEach(route => addRoute(route));
        else throw new Error('Invalid routes');
    } catch (error) {
        routesInput.value = '[]';
        routesEmpty.classList.remove('d-none');
    }
    channelTypes.forEach(type => {
        const hasEnabledRoute = routeRows().some(row => channelTypeFor(row) === type && row.querySelector('.route-enabled').checked);
        setChannelEnabled(type, hasEnabledRoute, false);
    });
    document.querySelector('form')?.addEventListener('submit', event => {
        const invalidRoute = routeRows().find(row => !validateRouteTargets(row));
        if (invalidRoute) {
            event.preventDefault();
            const invalidSelector = invalidRoute.querySelector('.route-multiselect-toggle');
            invalidSelector?.focus();
            invalidSelector?.setAttribute('aria-describedby', 'notificationRouteTargetError');
            let error = invalidRoute.querySelector('.route-target-validation-message');
            if (!error) {
                error = document.createElement('div');
                error.id = 'notificationRouteTargetError';
                error.className = 'invalid-feedback d-block route-target-validation-message';
                error.textContent = 'Select at least one recipient or recipient group for this enabled route.';
                invalidRoute.querySelector('.route-recipient-container')?.appendChild(error);
            }
            return;
        }
        syncNotificationRoutes();
    });
    });
})();
