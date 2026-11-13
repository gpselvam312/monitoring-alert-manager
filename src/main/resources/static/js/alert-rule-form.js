document.addEventListener('DOMContentLoaded', () => {
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
    const recoveryOperator = document.getElementById('recoveryOperator');
    const conditionType = document.getElementById('conditionType');
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
    const form = treeInput.closest('form');

    const sortDropdownAlphabetically = select => {
        if (!select || select.options.length < 2) return;
        const options = Array.from(select.options);
        const placeholders = options.filter(option => option.value === '');
        const choices = options.filter(option => option.value !== '');
        choices.sort((left, right) => left.textContent.trim().localeCompare(
            right.textContent.trim(), undefined, { sensitivity: 'base', numeric: true }
        ));
        const sorted = [...placeholders, ...choices];
        if (options.every((option, index) => option === sorted[index])) return;
        const selectedValues = new Set(options.filter(option => option.selected).map(option => option.value));
        select.replaceChildren(...sorted);
        Array.from(select.options).forEach(option => { option.selected = selectedValues.has(option.value); });
    };

    const sortAlertRuleDropdowns = root => {
        root.querySelectorAll('select').forEach(sortDropdownAlphabetically);
    };

    sortAlertRuleDropdowns(form);
    const dropdownSortObserver = new MutationObserver(mutations => {
        mutations.forEach(mutation => {
            if (mutation.target instanceof HTMLSelectElement) {
                sortDropdownAlphabetically(mutation.target);
            } else {
                mutation.addedNodes.forEach(node => {
                    if (node.nodeType !== Node.ELEMENT_NODE) return;
                    if (node.matches('select')) sortDropdownAlphabetically(node);
                    sortAlertRuleDropdowns(node);
                });
            }
        });
    });
    dropdownSortObserver.observe(form, { childList: true, subtree: true });

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
    form.addEventListener('submit', syncRecoveryConfig);
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
    const advancedJsonInput = document.getElementById('advancedConditionJson');
    const conditionBuilder = window.AlertConditionBuilder.create({
        treeInput, conditionType, advancedJsonInput, fieldSelect, form, showMessage
    });
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
        const field = conditionBuilder.getDiscoveredFields().find(item => item.path === fieldSelect.value);
        if (!field) { sample.textContent = 'Field type and sample value will appear here.'; return; }
        sample.textContent = 'Type: ' + field.type + ' · Sample: ' + field.sampleValue;
        applyOperatorOptions(recoveryOperator, field.type);
        if (conditionType.value === 'CONDITION') {
            const first = conditionBuilder.firstCondition();
            if (first) {
                first.fieldPath = field.path;
                if (field.type === 'number' && !['GREATER_THAN','GREATER_THAN_OR_EQUAL','LESS_THAN','LESS_THAN_OR_EQUAL','EQUALS','NOT_EQUALS','EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL'].includes(first.operator)) first.operator = 'GREATER_THAN';
                if (field.type === 'boolean' && !['EQUALS','NOT_EQUALS','EXISTS','NOT_EXISTS','IS_TRUE','IS_FALSE','IS_NULL','IS_NOT_NULL'].includes(first.operator)) first.operator = 'IS_TRUE';
                if (field.type === 'null' && !['EXISTS','NOT_EXISTS','IS_NULL','IS_NOT_NULL'].includes(first.operator)) first.operator = 'EXISTS';
                conditionBuilder.render();
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
                conditionBuilder.populateDiscoveredFields(payload.fields);
                if (!conditionBuilder.getDiscoveredFields().length) {
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
        conditionBuilder.sync();
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
        } else if (conditionBuilder.isAdvancedMode() && advancedJsonInput) {
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
        conditionBuilder.populateDiscoveredFields([]);
        fieldSelect.replaceChildren(new Option('Load a saved result to discover fields',''));
        fieldSelect.disabled = true; executionInfo.textContent = ''; message.className = 'alert d-none mb-3';
        discoverButton.disabled = !jobIdInput.value;
    });
    if (jobIdInput.value) discoverButton.disabled = false;
    syncPresentationDiscoverButton();
});
