(function (window) {
    'use strict';

    const create = ({treeInput, conditionType, advancedJsonInput, fieldSelect, form, showMessage}) => {
        const treeEditor = document.getElementById('conditionTreeEditor');
        const addRootCondition = document.getElementById('addRootCondition');
        const addRootGroup = document.getElementById('addRootGroup');
        const addRootCollection = document.getElementById('addRootCollection');
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
    const advancedEditor = document.getElementById('advancedConditionEditor');
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
    form.addEventListener('submit', syncTree);
    renderTree();


        return {
            populateDiscoveredFields,
            getDiscoveredFields: () => discoveredFields,
            firstCondition: () => firstCondition(tree),
            isAdvancedMode: () => advancedMode,
            sync: syncTree,
            render: renderTree
        };
    };

    window.AlertConditionBuilder = {create};
})(window);
