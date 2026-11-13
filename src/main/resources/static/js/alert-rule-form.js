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
            const targetType = row.querySelector('.route-target-type').value;
            return {
                channelId: row.querySelector('.route-channel').value || null,
                severity: row.querySelector('.route-severity').value,
                eventType: row.querySelector('.route-event').value,
                targetType,
                recipientId: targetType === 'RECIPIENT' ? (row.querySelector('.route-recipient').value || null) : null,
                groupId: targetType === 'GROUP' ? (row.querySelector('.route-group').value || null) : null,
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
            const enabledCount = channelRows.filter(row => row.querySelector('.route-enabled').checked).length;
            const empty = document.querySelector('[data-channel-empty="' + type + '"]');
            if (empty) empty.classList.toggle('d-none', channelRows.length > 0);
            const addButton = document.querySelector('[data-add-channel-route="' + type + '"]');
            if (addButton) addButton.disabled = !Array.from(routeTemplate.content.querySelectorAll('.route-channel option')).some(option => option.dataset.channelType === type);
        });
        rows.forEach((row, index) => {
            const type = channelTypeFor(row);
            const channelIndex = rows.filter((candidate, candidateIndex) => candidateIndex <= index && channelTypeFor(candidate) === type).length;
            row.querySelector('.route-heading').textContent = (type || 'Notification') + ' route ' + channelIndex;
        });
    };
    const setTargetType = row => {
        const selectedChannel = row.querySelector('.route-channel').selectedOptions[0];
        const isTeams = selectedChannel?.dataset.channelType === 'TEAMS';
        const targetType = row.querySelector('.route-target-type');
        if (isTeams) {
            targetType.value = 'NONE';
            row.querySelector('.route-recipient').value = '';
            row.querySelector('.route-group').value = '';
        } else if (targetType.value === 'NONE') targetType.value = 'RECIPIENT';
        const isGroup = !isTeams && targetType.value === 'GROUP';
        row.querySelector('.route-target-type-container').classList.toggle('d-none', isTeams);
        row.querySelector('.route-teams-hint').classList.toggle('d-none', !isTeams);
        row.querySelector('.route-recipient-container').classList.toggle('d-none', isTeams || isGroup);
        row.querySelector('.route-group-container').classList.toggle('d-none', isTeams || !isGroup);
        const isEmail = selectedChannel?.dataset.channelType === 'EMAIL';
        row.querySelector('.route-cc-container').classList.toggle('d-none', !isEmail);
        if (!isEmail) row.querySelector('.route-cc').value = '';
        row.querySelector('.route-recipient').required = !isTeams && !isGroup;
        row.querySelector('.route-group').required = !isTeams && isGroup;
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
            row.querySelector('.route-target-type').value = route.targetType || (route.groupId ? 'GROUP' : 'RECIPIENT');
            row.querySelector('.route-recipient').value = route.recipientId == null ? '' : String(route.recipientId);
            row.querySelector('.route-group').value = route.groupId == null ? '' : String(route.groupId);
            row.querySelector('.route-cc').value = route.ccRecipients || '';
            row.querySelector('.route-interval').value = route.minimumIntervalSeconds == null ? '300' : String(route.minimumIntervalSeconds);
            row.querySelector('.route-enabled').checked = route.enabled !== false;
        } else if (preferredType) {
            const matchingOption = Array.from(channelSelect.options).find(option => option.dataset.channelType === preferredType);
            if (matchingOption) channelSelect.value = matchingOption.value;
        }
        const type = channelTypeFor(row);
        (panelFor(type) || panelFor('EMAIL')).appendChild(row);
        row.querySelector('.route-target-type').addEventListener('change', () => setTargetType(row));
        channelSelect.addEventListener('change', () => setTargetType(row));
        row.querySelector('.remove-notification-route').addEventListener('click', () => { row.remove(); syncNotificationRoutes(); });
        row.querySelectorAll('select,input').forEach(input => input.addEventListener('change', syncNotificationRoutes));
        row.querySelectorAll('input').forEach(input => input.addEventListener('input', syncNotificationRoutes));
        setTargetType(row);
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
    document.querySelector('form')?.addEventListener('submit', syncNotificationRoutes);

    const jobIdInput = document.getElementById('monitoringJobId');
    const discoverButton = document.getElementById('discoverAlertFields');
    const presentationDiscoverButton = document.getElementById('discoverAlertFieldsForPresentation');
    let latestSavedResultLoaded = false;
    const syncPresentationDiscoverButton = () => {
        presentationDiscoverButton?.classList.toggle('d-none', !jobIdInput?.value || latestSavedResultLoaded);
    };
    presentationDiscoverButton?.addEventListener('click', () => discoverButton?.click());
    const fieldSelect = document.getElementById('discoveredFieldPath');
    const message = document.getElementById('fieldDiscoveryMessage');
    const sample = document.getElementById('discoveredFieldSample');
    const executionInfo = document.getElementById('fieldDiscoveryExecution');
    const sampleInput = document.getElementById('sampleResponseForDiscovery');
    const sampleButton = document.getElementById('discoverSampleFields');
    const recoveryPath = document.getElementById('recoveryFieldPath');
    const recoveryOperator = document.getElementById('recoveryOperator');
    const conditionType = document.getElementById('conditionType');
    const countFields = document.getElementById('countConditionFields');
    const collectionPath = document.getElementById('collectionPath');
    const countFieldPath = document.getElementById('countFieldPath');
    const countFieldOperator = document.getElementById('countFieldOperator');
    const countFieldThreshold = document.getElementById('countFieldThreshold');
    const countOperator = document.getElementById('countOperator');
    const countThreshold = document.getElementById('countThreshold');
    const countFieldPathContainer = document.getElementById('countFieldPathContainer');
    const countFieldOperatorContainer = document.getElementById('countFieldOperatorContainer');
    const countFieldThresholdContainer = document.getElementById('countFieldThresholdContainer');
    const treeInput = document.getElementById('conditionTreeJson');
    const treeEditor = document.getElementById('conditionTreeEditor');
    const addRootCondition = document.getElementById('addRootCondition');
    const addRootGroup = document.getElementById('addRootGroup');
    const addRootCollection = document.getElementById('addRootCollection');
    const form = treeInput.closest('form');
    const conditionOperators = [
        ['GREATER_THAN','Greater than'],['GREATER_THAN_OR_EQUAL','Greater than or equal'],
        ['LESS_THAN','Less than'],['LESS_THAN_OR_EQUAL','Less than or equal'],
        ['EQUALS','Equals'],['NOT_EQUALS','Not equals'],['EQUALS_IGNORE_CASE','Equals (ignore case)'],
        ['CONTAINS','Contains'],['REGEX','Regular expression'],['EXISTS','Exists'],
        ['NOT_EXISTS','Does not exist'],['IS_TRUE','Is true'],['IS_FALSE','Is false'],['IS_NULL','Is null'],['IS_NOT_NULL','Is not null'],['NOT_CONTAINS','Does not contain'],['STARTS_WITH','Starts with'],['ENDS_WITH','Ends with']
    ];
    const defaultCondition = () => ({type:'CONDITION',fieldPath:'',operator:'GREATER_THAN',threshold:''});
    const defaultGroup = (operator='AND') => ({type:'GROUP',operator,children:[defaultCondition(),defaultCondition()]});
    const defaultCollection = () => ({type:'COLLECTION',collectionPath:'data.items',quantifier:'ANY',condition:defaultGroup('AND')});
    const recoveryTreeInput = document.getElementById('recoveryTreeJson');
    const advancedRecoveryEditor = document.getElementById('advancedRecoveryEditor');
    const advancedRecoveryJson = document.getElementById('advancedRecoveryJson');
    const applyAdvancedRecoveryJson = document.getElementById('applyAdvancedRecoveryJson');
    const useStandardRecoveryFields = document.getElementById('useStandardRecoveryFields');
    const standardRecoveryFields = document.getElementById('standardRecoveryFields');
    let advancedRecoveryMode = Boolean(recoveryTreeInput?.value?.trim());
    if (advancedRecoveryMode && advancedRecoveryJson) {
        try { advancedRecoveryJson.value = JSON.stringify(JSON.parse(recoveryTreeInput.value), null, 2); }
        catch (_) { advancedRecoveryJson.value = recoveryTreeInput.value; }
        if (advancedRecoveryEditor) advancedRecoveryEditor.open = true;
    }
    const syncRecoveryConfig = () => {
        if (recoveryTreeInput) recoveryTreeInput.value = advancedRecoveryMode ? (advancedRecoveryJson?.value || '') : '';        standardRecoveryFields?.classList.toggle('d-none', advancedRecoveryMode);
    };
    syncRecoveryConfig();
    advancedRecoveryJson?.addEventListener('input', syncRecoveryConfig);
    applyAdvancedRecoveryJson?.addEventListener('click', () => {
        try {
            const parsed = JSON.parse(advancedRecoveryJson.value);
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || !parsed.type) {
                throw new Error('The recovery condition must be a JSON object with a node type.');
            }
            advancedRecoveryMode = true;
            syncRecoveryConfig();
            showMessage('Advanced recovery JSON is ready for server-side validation when you save the rule.','success');
        } catch (error) {
            showMessage('Invalid recovery JSON: ' + error.message,'danger');
        }
    });
    useStandardRecoveryFields?.addEventListener('click', () => {
        advancedRecoveryMode = false;
        syncRecoveryConfig();
        if (advancedRecoveryEditor) advancedRecoveryEditor.open = false;
    });
    const advancedEditor = document.getElementById('advancedConditionEditor');
    const advancedJsonInput = document.getElementById('advancedConditionJson');
    const applyAdvancedJson = document.getElementById('applyAdvancedConditionJson');
    const useVisualBuilder = document.getElementById('useVisualConditionBuilder');
    const isVisualConditionNode = node => node && (node.type === 'CONDITION'
        || (node.type === 'GROUP' && Array.isArray(node.children) && node.children.every(isVisualConditionNode))
        || (node.type === 'COLLECTION' && node.condition && isVisualConditionNode(node.condition)));
    let tree;
    try { tree = JSON.parse(treeInput.value); } catch (_) { tree = defaultCondition(); }
    let advancedMode = !isVisualConditionNode(tree);
    if (advancedMode) {
        if (advancedJsonInput) advancedJsonInput.value = JSON.stringify(tree || {}, null, 2);
        if (advancedEditor) advancedEditor.open = true;
        tree = defaultCondition();
    } else if (advancedJsonInput) {
        advancedJsonInput.value = JSON.stringify(tree, null, 2);
    }
    let discoveredFields = [];
    const fieldDatalist = document.createElement('datalist');
    fieldDatalist.id = 'discoveredConditionFieldPaths';
    form.appendChild(fieldDatalist);

    const populateDiscoveredFields = fields => {
        discoveredFields = Array.isArray(fields) ? fields : [];
        window.alertRuleDiscoveredFields = discoveredFields;
        window.refreshAlertPresentationDesigner?.();
        fieldDatalist.replaceChildren();
        discoveredFields.forEach(field => {
            const option = new Option();
            option.value = field.path;
            option.label = field.path + ' (' + field.type + '; sample: ' + String(field.sampleValue ?? '') + ')';
            fieldDatalist.appendChild(option);
        });
        fieldSelect.replaceChildren();
        if (!discoveredFields.length) {
            fieldSelect.add(new Option('No selectable fields found', ''));
            fieldSelect.disabled = true;
            return;
        }
        fieldSelect.add(new Option('Choose a field…', ''));
        discoveredFields.forEach(field => fieldSelect.add(new Option(field.label + ' (' + field.type + ')', field.path)));
        fieldSelect.disabled = false;
    };

    const showMessage = (text, kind) => {
        message.textContent = text;
        message.className = 'alert alert-' + kind + ' mb-3';
    };
    const setBusy = busy => {
        discoverButton.disabled = busy || !jobIdInput.value;
        discoverButton.innerHTML = busy
            ? '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>Loading saved result…'
            : '<i class="bi bi-search me-1"></i>Load Latest Saved Result';
    };
    const syncTree = () => {
        treeInput.value = advancedMode && advancedJsonInput
            ? advancedJsonInput.value : JSON.stringify(tree);
    };
    const countNodes = node => 1 + (node.type === 'GROUP' ? node.children.reduce((sum, child) => sum + countNodes(child), 0) : node.type === 'COLLECTION' ? countNodes(node.condition) : 0);
    const firstCondition = node => node.type === 'CONDITION' ? node : node.type === 'COLLECTION' ? firstCondition(node.condition) : node.children.map(firstCondition).find(Boolean);
    const updateConditionInputs = () => {
        const conditionMode = conditionType.value === 'CONDITION';
        treeEditor.querySelectorAll('.tree-field-path').forEach(input => input.required = conditionMode);
        treeEditor.querySelectorAll('.tree-collection-path').forEach(input => input.required = conditionMode);
        treeEditor.querySelectorAll('.tree-threshold').forEach(input => {
            const op = input.dataset.operator;
            input.required = conditionMode && !['EXISTS','NOT_EXISTS','IS_TRUE','IS_FALSE','IS_NULL','IS_NOT_NULL'].includes(op);
        });
        const countMode = conditionType.value === 'COUNT_TOTAL' || conditionType.value === 'COUNT_MATCHING';
        countFields.classList.toggle('d-none', !countMode);
        const matchingMode = conditionType.value === 'COUNT_MATCHING';
        countFieldPathContainer.classList.toggle('d-none', !matchingMode);
        countFieldOperatorContainer.classList.toggle('d-none', !matchingMode);
        countFieldThresholdContainer.classList.toggle('d-none', !matchingMode);
        countFieldPath.required = matchingMode;
        countFieldThreshold.required = matchingMode && !['EXISTS','NOT_EXISTS','IS_TRUE','IS_FALSE','IS_NULL','IS_NOT_NULL'].includes(countFieldOperator.value);
        collectionPath.required = countMode;
        countThreshold.required = countMode;
        treeEditor.classList.toggle('d-none', !conditionMode || advancedMode);
        addRootCondition.classList.toggle('d-none', !conditionMode || advancedMode);
        addRootGroup.classList.toggle('d-none', !conditionMode || advancedMode);
        addRootCollection?.classList.toggle('d-none', !conditionMode || advancedMode);
        if (advancedEditor) advancedEditor.classList.toggle('d-none', !conditionMode);
    };
    const makeButton = (label, className, handler) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = className;
        button.textContent = label;
        button.addEventListener('click', handler);
        return button;
    };
    const normalizeTree = node => {
        if (node.type === 'COLLECTION') { node.condition = normalizeTree(node.condition); return node; }
        if (node.type !== 'GROUP') return node;
        node.children = node.children.map(normalizeTree);
        return node.children.length === 1 ? node.children[0] : node;
    };
    const renderNode = (node, parentChildren, index, isRoot=false, scopePath=null) => {
        const card = document.createElement('div');
        card.className = 'condition-tree-node border rounded p-3 mb-3';
        const heading = document.createElement('div');
        heading.className = 'd-flex align-items-center justify-content-between gap-2 mb-3';
        const title = document.createElement('strong');
        title.className = 'small';
        title.textContent = node.type === 'GROUP' ? 'Condition group' : node.type === 'COLLECTION' ? 'Array collection rule' : 'Field condition';
        heading.appendChild(title);
        if (!isRoot) {
            const removeButton = makeButton('Remove', 'btn btn-sm btn-outline-danger', () => {
                if (parentChildren.length <= 1) return;
                parentChildren.splice(index, 1);
                tree = normalizeTree(tree);
                renderTree();
            });
            removeButton.disabled = parentChildren.length <= 1;
            heading.appendChild(removeButton);
        }
        card.appendChild(heading);
        if (node.type === 'COLLECTION') {
            const configRow = document.createElement('div'); configRow.className = 'row g-3 mb-3';
            const pathCol = document.createElement('div'); pathCol.className = 'col-12 col-lg-7';
            const pathLabel = document.createElement('label'); pathLabel.className = 'form-label'; pathLabel.textContent = 'Array path';
            const pathInput = document.createElement('input'); pathInput.type = 'text'; pathInput.className = 'form-control tree-collection-path'; pathInput.maxLength = 255; pathInput.value = node.collectionPath || ''; pathInput.setAttribute('list', fieldDatalist.id); pathInput.setAttribute('autocomplete','off');
            pathInput.addEventListener('input', () => { node.collectionPath = pathInput.value; syncTree(); }); pathCol.append(pathLabel,pathInput);
            const quantifierCol = document.createElement('div'); quantifierCol.className = 'col-12 col-lg-5';
            const quantifierLabel = document.createElement('label'); quantifierLabel.className = 'form-label'; quantifierLabel.textContent = 'Record matching';
            const quantifier = document.createElement('select'); quantifier.className = 'form-select';
            ['ANY|At least one record (ANY)', 'ALL|Every record (ALL)', 'NONE|No records (NONE)'].forEach(entry => { const [value,label] = entry.split('|'); const option = new Option(label,value); option.selected = (node.quantifier || 'ANY') === value; quantifier.add(option); });
            quantifier.addEventListener('change', () => { node.quantifier = quantifier.value; syncTree(); }); quantifierCol.append(quantifierLabel,quantifier);
            configRow.append(pathCol,quantifierCol); card.appendChild(configRow);
            const emptyRow = document.createElement('div'); emptyRow.className = 'form-check mb-3';
            const emptyToggle = document.createElement('input'); emptyToggle.type = 'checkbox'; emptyToggle.className = 'form-check-input'; emptyToggle.id = 'collectionEmptyMatches' + Math.random().toString(36).slice(2,8); emptyToggle.checked = node.emptyMatches === true;
            const emptyLabel = document.createElement('label'); emptyLabel.className = 'form-check-label small'; emptyLabel.htmlFor = emptyToggle.id; emptyLabel.textContent = 'Treat an empty array as a match for ALL';
            emptyToggle.addEventListener('change', () => { node.emptyMatches = emptyToggle.checked; syncTree(); });
            emptyRow.append(emptyToggle,emptyLabel); card.appendChild(emptyRow);
            const hint = document.createElement('div'); hint.className = 'small text-muted mb-2'; hint.textContent = 'The nested conditions below are evaluated against each individual array record. AND means all predicates must match that same record.'; card.appendChild(hint);
            card.appendChild(renderNode(node.condition, null, 0, true, node.collectionPath));
        } else if (node.type === 'GROUP') {
            const operatorRow = document.createElement('div');
            operatorRow.className = 'row g-2 align-items-end mb-3';
            const operatorCol = document.createElement('div');
            operatorCol.className = 'col-12 col-md-4';
            const label = document.createElement('label');
            label.className = 'form-label';
            label.textContent = 'Match conditions';
            const select = document.createElement('select');
            select.className = 'form-select tree-group-operator';
            ['AND|All conditions (AND)', 'OR|Any condition (OR)'].forEach(entry => {
                const [value, text] = entry.split('|');
                const option = new Option(text,value);
                option.selected = (node.operator || 'AND') === value;
                select.add(option);
            });
            select.addEventListener('change', () => { node.operator = select.value; syncTree(); });
            operatorCol.append(label,select);
            operatorRow.appendChild(operatorCol);
            card.appendChild(operatorRow);
            const children = document.createElement('div');
            children.className = 'condition-tree-children ps-3 ms-2 border-start';
            node.children.forEach((child, childIndex) => children.appendChild(renderNode(child,node.children,childIndex,false,scopePath)));
            card.appendChild(children);
            const actions = document.createElement('div');
            actions.className = 'd-flex gap-2 flex-wrap';
            actions.appendChild(makeButton('Add condition','btn btn-sm btn-outline-primary',() => {
                if (countNodes(tree) >= 100) { showMessage('A condition tree can contain at most 100 nodes.','warning'); return; }
                node.children.push(defaultCondition()); renderTree();
            }));
            actions.appendChild(makeButton('Add subgroup','btn btn-sm btn-outline-primary',() => {
                if (countNodes(tree) + 3 > 100) { showMessage('A condition tree can contain at most 100 nodes.','warning'); return; }
                node.children.push(defaultGroup('OR')); renderTree();
            }));
            actions.appendChild(makeButton('Add collection rule','btn btn-sm btn-outline-primary',() => {
                if (countNodes(tree) + 4 > 100) { showMessage('A condition tree can contain at most 100 nodes.','warning'); return; }
                node.children.push(defaultCollection()); renderTree();
            }));
            card.appendChild(actions);
        } else {
            const row = document.createElement('div');
            row.className = 'row g-3';
            const pathCol = document.createElement('div');
            pathCol.className = 'col-12 col-lg-4';
            const pathLabel = document.createElement('label');
            pathLabel.className = 'form-label';
            pathLabel.textContent = 'Value field path';
            const pathInput = document.createElement('input');
            pathInput.type = 'text'; pathInput.className = 'form-control tree-field-path';
            pathInput.maxLength = 255; pathInput.value = node.fieldPath ? (scopePath && !node.fieldPath.startsWith('data.') && !node.fieldPath.startsWith('response.') ? scopePath + '.' + node.fieldPath : node.fieldPath) : '';
            pathInput.setAttribute('list', fieldDatalist.id);
            pathInput.setAttribute('autocomplete', 'off');
            pathInput.setAttribute('aria-label', 'Value field path; type to see discovered fields');
            pathInput.addEventListener('input', () => { node.fieldPath = scopePath && pathInput.value.startsWith(scopePath + '.') ? pathInput.value.slice(scopePath.length + 1) : pathInput.value; syncTree(); });
            pathInput.addEventListener('change', () => {
                const selected = discoveredFields.find(field => field.path === pathInput.value)
                    || (scopePath ? discoveredFields.find(field => field.path === scopePath + '.' + pathInput.value) : null);
                if (selected && scopePath && selected.path.startsWith(scopePath + '.')) node.fieldPath = selected.path.slice(scopePath.length + 1);
                if (selected) {
                    if (selected.type === 'number' && !['GREATER_THAN','GREATER_THAN_OR_EQUAL','LESS_THAN','LESS_THAN_OR_EQUAL','EQUALS','NOT_EQUALS','EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL'].includes(node.operator)) node.operator = 'GREATER_THAN';
                    if (selected.type === 'boolean' && !['EQUALS','NOT_EQUALS','EXISTS','NOT_EXISTS','IS_TRUE','IS_FALSE','IS_NULL','IS_NOT_NULL'].includes(node.operator)) node.operator = 'IS_TRUE';
                    if (selected.type === 'null' && !['EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL'].includes(node.operator)) node.operator = 'EXISTS';
                    renderTree();
                }
            });
            pathCol.append(pathLabel,pathInput);
            const operatorCol = document.createElement('div');
            operatorCol.className = 'col-12 col-lg-4';
            const operatorLabel = document.createElement('label');
            operatorLabel.className = 'form-label'; operatorLabel.textContent = 'Operator';            const operatorSelect = document.createElement('select');
            operatorSelect.className = 'form-select tree-condition-operator';
            conditionOperators.forEach(([value,text]) => {
                const option = new Option(text,value); option.selected = (node.operator || 'GREATER_THAN') === value;
                operatorSelect.add(option);
            });
            operatorSelect.addEventListener('change', () => { node.operator = operatorSelect.value; renderTree(); });
            operatorCol.append(operatorLabel,operatorSelect);
            const thresholdCol = document.createElement('div');
            thresholdCol.className = 'col-12 col-lg-4';
            const thresholdLabel = document.createElement('label');
            thresholdLabel.className = 'form-label'; thresholdLabel.textContent = 'Threshold / value';
            const thresholdInput = document.createElement('input');
            thresholdInput.type = 'text'; thresholdInput.className = 'form-control tree-threshold';
            thresholdInput.maxLength = 500;
            thresholdInput.value = node.threshold ?? node.value ?? '';
            thresholdInput.dataset.operator = node.operator || 'GREATER_THAN';
            thresholdInput.addEventListener('input', () => { node.threshold = thresholdInput.value; syncTree(); });
            thresholdCol.append(thresholdLabel,thresholdInput);
            row.append(pathCol,operatorCol,thresholdCol);
            card.appendChild(row);
        }
        return card;
    };
    const renderTree = () => {
        treeEditor.replaceChildren(renderNode(tree,null,0,true));
        syncTree();
        updateConditionInputs();
    };
    const ensureGroup = () => {
        if (tree.type !== 'GROUP') tree = {type:'GROUP',operator:'AND',children:[tree]};
    };
    addRootCondition.addEventListener('click', () => {
        if (countNodes(tree) >= 100) { showMessage('A condition tree can contain at most 100 nodes.','warning'); return; }
        ensureGroup();
        tree.children.push(defaultCondition());
        renderTree();
    });
    addRootGroup.addEventListener('click', () => {
        if (countNodes(tree) + 3 > 100) { showMessage('A condition tree can contain at most 100 nodes.','warning'); return; }
        ensureGroup(); tree.children.push(defaultGroup('OR')); renderTree();
    });
    addRootCollection?.addEventListener('click', () => {
        if (countNodes(tree) + 4 > 100) { showMessage('A condition tree can contain at most 100 nodes.','warning'); return; }
        ensureGroup(); tree.children.push(defaultCollection()); renderTree();
    });
    conditionType.addEventListener('change', updateConditionInputs);
    countFieldOperator.addEventListener('change', updateConditionInputs);
    advancedJsonInput?.addEventListener('input', () => {
        if (advancedMode) treeInput.value = advancedJsonInput.value;
    });
    applyAdvancedJson?.addEventListener('click', () => {
        try {
            const parsed = JSON.parse(advancedJsonInput.value);
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || !parsed.type) {
                throw new Error('The condition must be a JSON object with a node type.');
            }
            advancedMode = true;
            treeInput.value = advancedJsonInput.value;
            updateConditionInputs();
            showMessage('Advanced condition JSON is ready for server-side validation when you save the rule.','success');
        } catch (error) {
            showMessage('Invalid condition JSON: ' + error.message,'danger');
        }
    });
    useVisualBuilder?.addEventListener('click', () => {
        try {
            const parsed = JSON.parse(advancedJsonInput.value);
            const isVisualNode = node => node && (node.type === 'CONDITION'
                || (node.type === 'GROUP' && Array.isArray(node.children) && node.children.every(isVisualNode))
                || (node.type === 'COLLECTION' && node.condition && isVisualNode(node.condition)));
            if (!isVisualNode(parsed)) throw new Error('This condition uses advanced node types that the visual builder cannot edit.');
            tree = parsed;
            advancedMode = false;
            if (advancedEditor) advancedEditor.open = false;
            renderTree();
        } catch (error) {
            showMessage(error.message,'warning');
        }
    });
    form.addEventListener('submit', () => { syncTree(); syncRecoveryConfig(); });
    renderTree();

    const applyOperatorOptions = (selectElement, type) => {
        if (!selectElement) return;
        const allowed = type === 'number'
            ? ['GREATER_THAN','GREATER_THAN_OR_EQUAL','LESS_THAN','LESS_THAN_OR_EQUAL','EQUALS','NOT_EQUALS','EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL']
            : type === 'boolean'
                ? ['EQUALS','NOT_EQUALS','EXISTS','NOT_EXISTS','IS_TRUE','IS_FALSE','IS_NULL','IS_NOT_NULL']
                : type === 'null'
                    ? ['EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL']
                    : ['EQUALS','NOT_EQUALS','EQUALS_IGNORE_CASE','CONTAINS','NOT_CONTAINS','STARTS_WITH','ENDS_WITH','REGEX','EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL'];
        Array.from(selectElement.options).forEach(option => { option.hidden = !allowed.includes(option.value); });
        if (selectElement.selectedOptions.length && selectElement.selectedOptions[0].hidden) selectElement.value = allowed[0];
    };
    const updateFieldDetails = () => {
        const field = discoveredFields.find(item => item.path === fieldSelect.value);
        if (!field) { sample.textContent = 'Field type and sample value will appear here.'; return; }
        sample.textContent = 'Type: ' + field.type + ' · Sample: ' + field.sampleValue;
        applyOperatorOptions(recoveryOperator, field.type);
        if (conditionType.value === 'CONDITION') {
            const first = firstCondition(tree);
            if (first) {
                first.fieldPath = field.path;
                if (field.type === 'number' && !['GREATER_THAN','GREATER_THAN_OR_EQUAL','LESS_THAN','LESS_THAN_OR_EQUAL','EQUALS','NOT_EQUALS','EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL'].includes(first.operator)) first.operator = 'GREATER_THAN';
                if (field.type === 'boolean' && !['EQUALS','NOT_EQUALS','EXISTS','NOT_EXISTS','IS_TRUE','IS_FALSE','IS_NULL','IS_NOT_NULL'].includes(first.operator)) first.operator = 'IS_TRUE';
                if (field.type === 'null' && !['EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL'].includes(first.operator)) first.operator = 'EXISTS';
                renderTree();
            }
        }
        if (conditionType.value === 'COUNT_MATCHING') {
            countFieldPath.value = field.path;
            applyOperatorOptions(countFieldOperator, field.type);

        }
    };
    fieldSelect.addEventListener('change', updateFieldDetails);
    if (discoverButton && jobIdInput && fieldSelect) {
        discoverButton.addEventListener('click', async () => {
            if (!jobIdInput.value) { showMessage('Choose a monitoring job before discovering fields.','warning'); return; }
            setBusy(true); message.className = 'alert d-none mb-3'; fieldSelect.disabled = true; executionInfo.textContent = '';
            try {
                const response = await fetch(discoverButton.dataset.discoveryUrl + encodeURIComponent(jobIdInput.value), {
                    headers: {'Accept':'application/json'}, credentials:'same-origin'
                });
                const payload = await response.json();
                if (!response.ok) throw new Error(payload.message || 'Unable to discover fields from the saved result.');
                latestSavedResultLoaded = true;
                syncPresentationDiscoverButton();
                populateDiscoveredFields(payload.fields);
                if (!discoveredFields.length) {
                    showMessage('The latest saved result did not contain any discoverable fields. You can enter the field path manually.','warning');
                    return;
                }
                executionInfo.textContent = 'Execution #' + payload.executionId + ' · ' + payload.executionStartedAt + ' · ' + payload.triggerSource;
                showMessage(discoveredFields.length + ' fields discovered from the latest successful saved execution.','success');
            } catch (error) {
                showMessage(error.message || 'Unable to load the saved result. Please try again.','danger');
            } finally { setBusy(false); }
        });
    }
    const previewButton = document.getElementById('previewAlertCondition');
    const previewResult = document.getElementById('conditionPreviewResult');
    previewButton?.addEventListener('click', async () => {
        const sampleText = sampleInput?.value || '';
        if (!sampleText.trim()) { showMessage('Paste a JSON sample response before testing the condition.','warning'); return; }
        syncTree();
        let conditionJson = treeInput.value;
        if (conditionType.value === 'COUNT_TOTAL') {
            conditionJson = JSON.stringify({type:'COUNT_TOTAL',collectionPath:collectionPath.value,
                countOperator:countOperator.value,countThreshold:countThreshold.value});
        } else if (conditionType.value === 'COUNT_MATCHING') {
            const matching = {type:'COUNT_MATCHING',collectionPath:collectionPath.value,
                fieldPath:countFieldPath.value,operator:countFieldOperator.value,
                countOperator:countOperator.value,countThreshold:countThreshold.value};
            if (!['EXISTS','NOT_EXISTS','IS_TRUE','IS_FALSE','IS_NULL','IS_NOT_NULL'].includes(matching.operator)) matching.threshold = countFieldThreshold.value;
            conditionJson = JSON.stringify(matching);
        } else if (advancedMode && advancedJsonInput) {
            conditionJson = advancedJsonInput.value;
        }
        previewButton.disabled = true;
        const originalText = previewButton.innerHTML;
        previewButton.innerHTML = '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>Testing…';
        previewResult.className = 'mt-3 alert alert-secondary';
        previewResult.textContent = 'Evaluating condition…';
        try {
            const response = await fetch(previewButton.dataset.previewUrl, {
                method:'POST',
                headers:{'Content-Type':'application/json','Accept':'application/json','X-CSRF-TOKEN':document.querySelector('input[name="_csrf"]')?.value || ''},
                body:JSON.stringify({sampleResponse:sampleText,condition:conditionJson}), credentials:'same-origin'
            });
            const payload = await response.json();
            if (!response.ok) throw new Error(payload.message || 'Unable to test this condition.');
            previewResult.className = 'mt-3 alert ' + (payload.matched ? 'alert-success' : 'alert-warning');
            const summary = document.createElement('div');
            summary.className = 'fw-semibold';
            summary.textContent = payload.matched ? 'MATCH — this condition would trigger for the sample.' : 'NO MATCH — this condition would not trigger for the sample.';
            const details = document.createElement('pre');
            details.className = 'small mb-0 mt-2 text-wrap';
            details.textContent = JSON.stringify(payload.details || {}, null, 2);
            previewResult.replaceChildren(summary,details);
        } catch (error) {
            previewResult.className = 'mt-3 alert alert-danger';
            previewResult.textContent = error.message || 'Unable to test this condition.';
        } finally {
            previewButton.disabled = false;
            previewButton.innerHTML = originalText;
        }
    });
    if (sampleButton) sampleButton.addEventListener('click', async () => {
        if (!sampleInput || !sampleInput.value.trim()) { showMessage('Paste a sample response before discovering fields.','warning'); return; }
        sampleButton.disabled = true;
        const originalText = sampleButton.innerHTML;
        sampleButton.innerHTML = '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>Discovering…';
        try {
            const response = await fetch(sampleButton.dataset.sampleUrl, {
                method:'POST',
                headers:{'Content-Type':'text/plain; charset=UTF-8','Accept':'application/json','X-CSRF-TOKEN':document.querySelector('input[name="_csrf"]')?.value || ''},
                body:sampleInput.value, credentials:'same-origin'
            });
            const payload = await response.json();
            if (!response.ok) throw new Error(payload.message || 'Unable to discover fields from this sample.');
            populateDiscoveredFields(payload.fields);
            if (!discoveredFields.length) throw new Error('No selectable fields were found in this sample.');
            executionInfo.textContent = 'Sample response · not saved';
            showMessage(discoveredFields.length + ' fields discovered from the sample.','success');
        } catch (error) {
            showMessage(error.message || 'Unable to discover fields from this sample.','danger');
        } finally {
            sampleButton.disabled = false; sampleButton.innerHTML = originalText;
        }
    });
    jobIdInput.addEventListener('change', () => {
        latestSavedResultLoaded = false;
        syncPresentationDiscoverButton();
        populateDiscoveredFields([]);
        fieldSelect.replaceChildren(new Option('Load a saved result to discover fields',''));
        fieldSelect.disabled = true; executionInfo.textContent = ''; message.className = 'alert d-none mb-3';
        discoverButton.disabled = !jobIdInput.value;
    });
    if (jobIdInput.value) discoverButton.disabled = false;
    syncPresentationDiscoverButton();
});
