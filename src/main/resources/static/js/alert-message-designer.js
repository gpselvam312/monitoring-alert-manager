(() => {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.querySelector('form[action*="/alerts/rules/save"]');
        const jsonInput = document.getElementById('presentationConfigJson');
        const editor = document.getElementById('presentationSections');
        const preview = document.getElementById('presentationPreview');
        if (!form || !jsonInput || !editor || !preview) return;

        const subjectInput = document.getElementById('alertSubject');
        const ruleNameInput = document.getElementById('name');
        const ruleSeverityInput = document.getElementById('severity');
        const status = document.getElementById('presentationBuilderMessage');
        const blockCount = document.getElementById('presentationBlockCount');
        const whatsappCount = document.getElementById('whatsAppCharacterCount');
        const whatsappWarning = document.getElementById('whatsAppLimitWarning');
        const fields = () => Array.isArray(window.alertRuleDiscoveredFields) ? window.alertRuleDiscoveredFields : [];
        const sample = {
            ruleName: ruleNameInput?.value?.trim() || 'Alert Rule',
            severity: ruleSeverityInput?.value || 'CRITICAL',
            jobName: 'Application Availability Check',
            applicationName: 'Retail Platform',
            environment: 'PRODUCTION',
            timestamp: '2026-11-13 09:30:00 IST',
            executionId: 'EX-10482',
            alertId: 'AL-2081',
            occurrenceCount: 3,
            response: { status: 'CRITICAL', schemaVersion: 1 },
            data: {
                service: { name: 'payment-api', status: 'DEGRADED', region: 'ap-south-1' },
                summary: { affectedInstances: 2, totalInstances: 8, errorRate: 7.4 },
                instances: [
                    { instance: 'payment-api-03', status: 'CRITICAL', errorRate: 12.8 },
                    { instance: 'payment-api-06', status: 'WARNING', errorRate: 4.1 }
                ]
            }
        };

        let config = { version: 1, sections: [] };
        let jsonEdited = false;
        let activePreview = 'email';
        const defaultSubjectTemplate = '{severity} — {ruleName}';
        const defaultSubject = () => (ruleSeverityInput?.value || 'CRITICAL') + ' — ' + (ruleNameInput?.value?.trim() || 'Alert Rule');
        let subjectManuallyEdited = !!subjectInput?.value?.trim()
            && ![defaultSubjectTemplate, '[{severity}] {ruleName}', defaultSubject()].includes(subjectInput.value.trim());
        if (subjectInput && !subjectManuallyEdited) subjectInput.value = defaultSubject();
        const previewSeverity = document.getElementById('presentationPreviewSeverity');
        const currentPreviewSeverity = () => previewSeverity?.value || ruleSeverityInput?.value || 'CRITICAL';
        if (previewSeverity && ruleSeverityInput?.value) previewSeverity.value = ruleSeverityInput.value;
        let expandedBlockId = null;
        let draggedBlockId = null;

        const element = (tag, className, text) => {
            const node = document.createElement(tag);
            if (className) node.className = className;
            if (text !== undefined) node.textContent = text;
            return node;
        };
        const button = (label, className, handler, icon) => {
            const node = element('button', className);
            node.type = 'button';
            if (icon) node.append(element('i', icon + ' me-1'));
            node.append(document.createTextNode(label));
            node.addEventListener('click', handler);
            return node;
        };
        const control = (label, input, hint) => {
            const wrapper = element('div', 'mb-3');
            wrapper.append(element('label', 'form-label', label), input);
            if (hint) wrapper.append(element('div', 'form-text', hint));
            return wrapper;
        };
        const textInput = (value, placeholder, maxLength) => {
            const input = element('input', 'form-control');
            input.type = 'text';
            input.value = value || '';
            input.placeholder = placeholder || '';
            if (maxLength) input.maxLength = maxLength;
            return input;
        };
        const textArea = (value, rows, placeholder) => {
            const input = element('textarea', 'form-control');
            input.rows = rows;
            input.value = value || '';
            input.placeholder = placeholder || '';
            return input;
        };
        const setStatus = (message, kind) => {
            status.textContent = message;
            status.className = 'alert alert-' + kind + ' mt-3 mb-0';
        };
        const normalize = parsed => {
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('Presentation configuration must be a JSON object.');
            if (parsed.version != null && String(parsed.version) !== '1') throw new Error('Only presentation version 1 is supported.');
            if (parsed.sections != null && !Array.isArray(parsed.sections)) throw new Error('Blocks must be an array.');
            const sections = (parsed.sections || []).map(section => {
                if (!section || typeof section !== 'object' || Array.isArray(section)) throw new Error('Each block must be an object.');
                const normalized = { ...section, id: section.id || newId(), type: String(section.type || '').toUpperCase() };
                if (!['TEXT', 'KEY_VALUE', 'TABLE', 'LIST', 'JSON'].includes(normalized.type)) throw new Error('Unsupported block type: ' + normalized.type);
                if (normalized.type === 'KEY_VALUE' && !Array.isArray(normalized.fields)) normalized.fields = [];
                if (normalized.type === 'TABLE' && !Array.isArray(normalized.columns)) normalized.columns = [];
                if (normalized.type === 'LIST' && !Array.isArray(normalized.fields)) normalized.fields = [];
                return normalized;
            });
            if (sections.length > 20) throw new Error('A presentation can contain at most 20 blocks.');
            return { version: 1, sections };
        };
        const newId = () => 'block-' + (window.crypto?.randomUUID?.() || Math.random().toString(36).slice(2, 10));
        const discoveredPaths = () => fields().filter(field => field && typeof field.path === 'string'
            && (field.path.startsWith('data.') || field.path.startsWith('response.')));
        const relativeDiscoveredPaths = sourcePath => {
            if (!sourcePath) return [];
            const prefixes = sourcePath.endsWith('[]')
                ? [sourcePath + '.']
                : sourcePath.includes('[]') ? [sourcePath + '.'] : [sourcePath + '.', sourcePath + '[].'];
            return discoveredPaths().map(field => {
                const matchedPrefix = prefixes.find(prefix => field.path.startsWith(prefix));
                const relative = matchedPrefix ? field.path.slice(matchedPrefix.length) : '';
                return relative ? { ...field, relativePath: relative } : null;
            }).filter(field => field && field.relativePath && !field.relativePath.includes('[]'));
        };
        const pathControl = (value, onChange, predicate, onSelection) => {
            const wrapper = element('div', 'd-grid gap-2');
            const select = element('select', 'form-select');
            select.add(new Option('Choose a discovered response field…', ''));
            discoveredPaths().filter(predicate || (() => true)).forEach(field => select.add(new Option(field.path + ' (' + field.type + ')', field.path)));
            if (value && !Array.from(select.options).some(option => option.value === value)) select.add(new Option(value + ' (saved path)', value));
            select.value = value || '';
            const manual = textInput(value, 'Or enter a response path', 512);
            select.addEventListener('change', () => {
                manual.value = select.value;
                onChange(select.value);
                if (onSelection) onSelection(select.value);
                else updatePreview();
            });
            manual.addEventListener('input', () => { onChange(manual.value.trim()); updatePreview(); });
            if (onSelection) manual.addEventListener('change', () => onSelection(manual.value.trim()));
            wrapper.append(select, manual);
            return wrapper;
        };
        const relativePathControl = (sourcePath, value, onChange, includeWholeItem) => {
            const wrapper = element('div');
            const input = element('input', 'form-control form-control-sm');
            const list = element('datalist');
            list.id = 'presentation-field-options-' + newId();
            if (includeWholeItem) list.append(new Option('Use each item as-is', ''));
            relativeDiscoveredPaths(sourcePath).forEach(field => {
                const option = element('option');
                option.value = field.relativePath;
                option.label = field.relativePath + ' (' + field.type + ')';
                list.append(option);
            });
            input.setAttribute('list', list.id);
            input.maxLength = 512;
            input.value = value || '';
            input.placeholder = includeWholeItem ? 'Optional field within each item' : 'Select or enter row field';
            input.addEventListener('input', () => { onChange(input.value.trim()); updatePreview(); });
            wrapper.append(input, list);
            return wrapper;
        };
        const insertVariable = (textarea, variable) => {
            const token = '{{' + variable + '}}';
            const start = textarea.selectionStart ?? textarea.value.length;
            const end = textarea.selectionEnd ?? start;
            textarea.setRangeText(token, start, end, 'end');
            textarea.dispatchEvent(new Event('input', { bubbles: true }));
            textarea.focus();
        };
        const variableToolbar = textarea => {
            const toolbar = element('div', 'd-flex flex-wrap gap-1 mb-2');
            const common = ['severity', 'timestamp', 'ruleName', 'jobName', 'applicationName', 'environment', 'cluster_name'];
            common.forEach(name => toolbar.append(button('{{' + name + '}}', 'btn btn-sm btn-outline-secondary',
                () => insertVariable(textarea, name))));
            discoveredPaths().slice(0, 12).forEach(field => toolbar.append(button('{{' + field.path + '}}',
                'btn btn-sm btn-outline-info', () => insertVariable(textarea, field.path))));
            return toolbar;
        };
        const moveBlock = (index, delta) => {
            const target = index + delta;
            if (target < 0 || target >= config.sections.length) return;
            const [block] = config.sections.splice(index, 1);
            config.sections.splice(target, 0, block);
            render();
        };
        const removeBlock = index => {
            const [removed] = config.sections.splice(index, 1);
            if (removed && expandedBlockId === removed.id) expandedBlockId = null;
            render();
        };
        const rowActions = (index, length) => {
            const actions = element('div', 'd-flex gap-1');
            actions.append(button('Up', 'btn btn-sm btn-outline-secondary', () => moveBlock(index, -1), 'bi bi-arrow-up'));
            actions.append(button('Down', 'btn btn-sm btn-outline-secondary', () => moveBlock(index, 1), 'bi bi-arrow-down'));
            actions.append(button('Delete', 'btn btn-sm btn-outline-danger', () => removeBlock(index), 'bi bi-trash'));
            actions.children[0].disabled = index === 0;
            actions.children[1].disabled = index === length - 1;
            return actions;
        };
        const makeBlock = (section, index) => {
            const card = element('section', 'app-card mb-0 presentation-block');
            card.draggable = true;
            card.dataset.blockId = section.id;
            const header = element('div', 'app-card-header d-flex align-items-center justify-content-between gap-2 flex-wrap');
            const title = section.type === 'TEXT' ? 'Text Block' : section.type === 'KEY_VALUE' ? 'Key-Value List Block'
                : section.type === 'LIST' ? 'List Block'
                : section.type === 'TABLE' ? 'Data Table Block' : 'Legacy ' + section.type + ' Block';
            const collapseId = 'presentation-block-collapse-' + index;
            const expanded = expandedBlockId ? expandedBlockId === section.id : index === 0;
            const heading = element('div', 'd-flex align-items-center gap-2 min-w-0');
            const dragHandle = element('span', 'presentation-block-drag-handle text-muted');
            dragHandle.append(element('i', 'bi bi-grip-vertical'));
            dragHandle.setAttribute('title', 'Drag to reorder');
            const collapseButton = element('button', 'btn btn-link text-decoration-none text-reset p-0 d-flex align-items-center gap-2 text-start');
            collapseButton.type = 'button';
            collapseButton.dataset.bsToggle = 'collapse';
            collapseButton.dataset.bsTarget = '#' + collapseId;
            collapseButton.setAttribute('aria-expanded', String(expanded));
            collapseButton.setAttribute('aria-controls', collapseId);
            const headingText = element('span', 'd-flex flex-column');
            headingText.append(element('span', 'fw-semibold', (index + 1) + '. ' + title));
            headingText.append(element('span', 'small text-muted', 'Block ' + (index + 1) + ' of ' + config.sections.length));
            collapseButton.append(headingText, element('i', 'bi bi-chevron-down small presentation-block-chevron'));
            heading.append(dragHandle, collapseButton);
            header.append(heading, rowActions(index, config.sections.length));
            card.append(header);
            const collapse = element('div', 'collapse' + (expanded ? ' show' : ''));
            collapse.id = collapseId;
            collapse.dataset.bsParent = '#presentationSections';
            const body = element('div', 'app-card-body');
            if (section.type === 'TEXT') {
                const blockTitle = textInput(section.title, 'Optional block heading', 150);
                blockTitle.addEventListener('input', () => { section.title = blockTitle.value; sync(); });
                body.append(control('Block heading', blockTitle));
                const content = textArea(section.text, 5, 'Write the notification text. Insert variables using the toolbar.');
                content.addEventListener('input', () => { section.text = content.value; sync(); });
                body.append(variableToolbar(content), control('Text content', content, 'Variables are resolved from alert metadata and the execution response.'));
            } else if (section.type === 'KEY_VALUE') {
                const blockTitle = textInput(section.title, 'Optional list heading', 150);
                blockTitle.addEventListener('input', () => { section.title = blockTitle.value; sync(); });
                body.append(control('Block heading', blockTitle));
                section.fields.forEach((field, fieldIndex) => {
                    const row = element('div', 'border rounded p-2 mb-2');
                    const grid = element('div', 'row g-2 align-items-end');
                    const label = textInput(field.label, 'Example: Host: {{cluster_name}}', 150);
                    label.addEventListener('input', () => { field.label = label.value; sync(); });
                    const valuePath = pathControl(field.path, value => { field.path = value; sync(); });
                    grid.append(control('Label', label), control('Value binding', valuePath));
                    row.append(grid, button('Remove pair', 'btn btn-sm btn-outline-danger mt-2',
                        () => { section.fields.splice(fieldIndex, 1); render(); }, 'bi bi-trash'));
                    body.append(row);
                });
                body.append(button('Add label-value pair', 'btn btn-sm btn-outline-secondary', () => {
                    section.fields.push({ label: '', path: '' }); render();
                }, 'bi bi-plus-lg'));
            } else if (section.type === 'TABLE') {
                const blockTitle = textInput(section.title, 'Optional table heading', 150);
                blockTitle.addEventListener('input', () => { section.title = blockTitle.value; sync(); });
                body.append(control('Block heading', blockTitle));
                body.append(control('Rows from response array', pathControl(section.sourcePath,
                    value => { section.sourcePath = value; sync(); },
                    field => field.type === 'array', () => render()), 'Choose an array from discovered response fields.'));
                const loadColumns = button('Load columns from array', 'btn btn-sm btn-outline-primary', () => {
                    const available = relativeDiscoveredPaths(section.sourcePath);
                    if (!available.length) {
                        setStatus('No item fields were discovered for this array. Load the latest saved result or use a sample response first.', 'warning');
                        return;
                    }
                    const existing = new Set(section.columns.map(column => column.key));
                    const added = available.filter(field => !existing.has(field.relativePath))
                        .map(field => ({ key: field.relativePath, label: field.relativePath.split('.').pop() }));
                    section.columns.push(...added);
                    render();
                    setStatus(added.length ? added.length + ' columns loaded from the selected array.'
                        : 'All discovered columns are already configured.', 'success');
                }, 'bi bi-list-columns');
                loadColumns.disabled = !section.sourcePath;
                const loadRow = element('div', 'd-flex flex-wrap align-items-center gap-2 mb-3');
                loadRow.append(loadColumns);
                body.append(loadRow);
                if (section.columns.length) {
                    const tableWrapper = element('div', 'table-responsive border rounded mb-3');
                    const columnsTable = element('table', 'table table-sm align-middle mb-0');
                    const tableHead = element('thead', 'table-light');
                    const headRow = element('tr');
                    ['Column heading', 'Array field', 'Order'].forEach(text => headRow.append(element('th', '', text)));
                    tableHead.append(headRow);
                    const tableBody = element('tbody');
                    section.columns.forEach((column, columnIndex) => {
                        const row = element('tr');
                        const labelCell = element('td', 'w-25');
                        const label = textInput(column.label, 'Column header', 100);
                        label.classList.add('form-control-sm');
                        label.addEventListener('input', () => { column.label = label.value; sync(); });
                        labelCell.append(label);
                        const keyCell = element('td');
                        const key = relativePathControl(section.sourcePath, column.key,
                            value => { column.key = value; sync(); }, false);
                        keyCell.append(key);
                        const actionCell = element('td', 'text-end text-nowrap');
                        const moveUp = button('', 'btn btn-sm btn-outline-secondary me-1',
                            () => {
                                if (columnIndex === 0) return;
                                [section.columns[columnIndex - 1], section.columns[columnIndex]] =
                                    [section.columns[columnIndex], section.columns[columnIndex - 1]];
                                render();
                            }, 'bi bi-arrow-up');
                        moveUp.title = 'Move column up';
                        moveUp.setAttribute('aria-label', 'Move column up');
                        moveUp.disabled = columnIndex === 0;
                        const moveDown = button('', 'btn btn-sm btn-outline-secondary me-1',
                            () => {
                                if (columnIndex >= section.columns.length - 1) return;
                                [section.columns[columnIndex], section.columns[columnIndex + 1]] =
                                    [section.columns[columnIndex + 1], section.columns[columnIndex]];
                                render();
                            }, 'bi bi-arrow-down');
                        moveDown.title = 'Move column down';
                        moveDown.setAttribute('aria-label', 'Move column down');
                        moveDown.disabled = columnIndex === section.columns.length - 1;
                        const removeColumn = button('', 'btn btn-sm btn-outline-danger',
                            () => { section.columns.splice(columnIndex, 1); render(); }, 'bi bi-trash');
                        removeColumn.title = 'Remove column';
                        removeColumn.setAttribute('aria-label', 'Remove column');
                        actionCell.append(moveUp, moveDown, removeColumn);
                        row.append(labelCell, keyCell, actionCell);
                        tableBody.append(row);
                    });
                    columnsTable.append(tableHead, tableBody);
                    tableWrapper.append(columnsTable);
                    body.append(tableWrapper);
                } else {
                    body.append(element('div', 'small text-muted border rounded p-3 mb-3',
                        'No columns configured. Load fields from the selected array or add a column manually.'));
                }
                body.append(button('Add column manually', 'btn btn-sm btn-outline-secondary', () => {
                    section.columns.push({ key: '', label: '' }); render();
                }, 'bi bi-plus-lg'));
                const maxRows = textInput(String(section.maxRows || 20), '20', 3);
                maxRows.type = 'number'; maxRows.min = '1'; maxRows.max = '100';
                maxRows.addEventListener('input', () => { section.maxRows = Math.min(100, Math.max(1, Number(maxRows.value) || 1)); sync(); });
                body.append(control('Maximum rows', maxRows));
            } else if (section.type === 'LIST') {
                const blockTitle = textInput(section.title, 'Optional list heading', 150);
                blockTitle.addEventListener('input', () => { section.title = blockTitle.value; sync(); });
                body.append(control('Block heading', blockTitle));
                body.append(control('List data from response element', pathControl(section.sourcePath,
                    value => { section.sourcePath = value; sync(); },
                    field => ['array', 'object'].includes(String(field.type || '').toLowerCase()), () => {
                        section.fields = [];
                        section.valuePath = '';
                        render();
                    }), 'Select an array or object. For arrays, the first object defines the available attributes; a selected child object can be used as the list source.'));
                const loadFields = button('Load attributes from selected element', 'btn btn-sm btn-outline-primary', () => {
                    const available = relativeDiscoveredPaths(section.sourcePath);
                    if (!available.length) {
                        setStatus('No child attributes were discovered for this element. Load the latest saved result or use a sample response first.', 'warning');
                        return;
                    }
                    const existing = new Set(section.fields.map(field => field.key));
                    const added = available.filter(field => !existing.has(field.relativePath))
                        .map(field => ({ key: field.relativePath, label: field.relativePath.split('.').pop() }));
                    section.fields.push(...added);
                    render();
                    setStatus(added.length ? added.length + ' attributes loaded from the selected element.'
                        : 'All discovered attributes are already configured.', 'success');
                }, 'bi bi-list-ul');
                loadFields.disabled = !section.sourcePath;
                const loadFieldsRow = element('div', 'd-flex flex-wrap align-items-center gap-2 mb-3');
                loadFieldsRow.append(loadFields);
                body.append(loadFieldsRow);
                if (section.fields.length) {
                    const fieldsWrapper = element('div', 'table-responsive border rounded mb-3');
                    const fieldsTable = element('table', 'table table-sm align-middle mb-0');
                    const fieldsHead = element('thead', 'table-light');
                    const fieldsHeader = element('tr');
                    ['Display label', 'Child attribute', 'Order'].forEach(text => fieldsHeader.append(element('th', '', text)));
                    fieldsHead.append(fieldsHeader);
                    const fieldsBody = element('tbody');
                    section.fields.forEach((field, fieldIndex) => {
                        const row = element('tr');
                        const labelCell = element('td', 'w-25');
                        const label = textInput(field.label, 'Display label', 100);
                        label.classList.add('form-control-sm');
                        label.addEventListener('input', () => { field.label = label.value; sync(); });
                        labelCell.append(label);
                        const keyCell = element('td');
                        keyCell.append(relativePathControl(section.sourcePath, field.key,
                            value => { field.key = value; sync(); }, false));
                        const actionCell = element('td', 'text-end text-nowrap');
                        const moveUp = button('', 'btn btn-sm btn-outline-secondary me-1',
                            () => {
                                if (fieldIndex === 0) return;
                                [section.fields[fieldIndex - 1], section.fields[fieldIndex]] =
                                    [section.fields[fieldIndex], section.fields[fieldIndex - 1]];
                                render();
                            }, 'bi bi-arrow-up');
                        moveUp.title = 'Move attribute up';
                        moveUp.setAttribute('aria-label', 'Move attribute up');
                        moveUp.disabled = fieldIndex === 0;
                        const moveDown = button('', 'btn btn-sm btn-outline-secondary me-1',
                            () => {
                                if (fieldIndex >= section.fields.length - 1) return;
                                [section.fields[fieldIndex], section.fields[fieldIndex + 1]] =
                                    [section.fields[fieldIndex + 1], section.fields[fieldIndex]];
                                render();
                            }, 'bi bi-arrow-down');
                        moveDown.title = 'Move attribute down';
                        moveDown.setAttribute('aria-label', 'Move attribute down');
                        moveDown.disabled = fieldIndex === section.fields.length - 1;
                        const removeField = button('', 'btn btn-sm btn-outline-danger',
                            () => { section.fields.splice(fieldIndex, 1); render(); }, 'bi bi-trash');
                        removeField.title = 'Remove attribute';
                        removeField.setAttribute('aria-label', 'Remove attribute');
                        actionCell.append(moveUp, moveDown, removeField);
                        row.append(labelCell, keyCell, actionCell);
                        fieldsBody.append(row);
                    });
                    fieldsTable.append(fieldsHead, fieldsBody);
                    fieldsWrapper.append(fieldsTable);
                    body.append(fieldsWrapper);
                } else {
                    body.append(element('div', 'small text-muted border rounded p-3 mb-3',
                        'No attributes configured. Load the discovered attributes or add one manually.'));
                }
                body.append(button('Add attribute manually', 'btn btn-sm btn-outline-secondary', () => {
                    section.fields.push({ key: '', label: '' }); render();
                }, 'bi bi-plus-lg'));
                const maxItems = textInput(String(section.maxItems || 10), '10', 3);
                maxItems.type = 'number'; maxItems.min = '1'; maxItems.max = '100';
                maxItems.addEventListener('input', () => { section.maxItems = Math.min(100, Math.max(1, Number(maxItems.value) || 1)); sync(); });
                body.append(control('Maximum list items', maxItems));
                const emptyText = textInput(section.emptyText, 'No items available', 250);
                emptyText.addEventListener('input', () => { section.emptyText = emptyText.value; sync(); });
                body.append(control('Empty-state text', emptyText));
            } else {
                const note = element('div', 'alert alert-warning mb-0',
                    'This block type was saved by an earlier version. It is retained for backward compatibility and can be reordered or deleted. New blocks use Text, Key-Value List or Data Table.');
                body.append(note);
            }
            collapse.append(body);
            card.append(collapse);
            collapseButton.addEventListener('click', () => {
                expandedBlockId = expanded ? null : section.id;
            });
            collapse.addEventListener('shown.bs.collapse', () => { expandedBlockId = section.id; });
            collapse.addEventListener('hidden.bs.collapse', () => {
                if (expandedBlockId === section.id) expandedBlockId = null;
            });
            card.addEventListener('dragstart', event => {
                if (!event.target.closest('.presentation-block-drag-handle')) {
                    event.preventDefault();
                    return;
                }
                draggedBlockId = section.id;
                card.classList.add('presentation-block-dragging');
                event.dataTransfer.effectAllowed = 'move';
                event.dataTransfer.setData('text/plain', section.id);
            });
            card.addEventListener('dragend', () => {
                draggedBlockId = null;
                card.classList.remove('presentation-block-dragging');
            });
            card.addEventListener('dragover', event => event.preventDefault());
            card.addEventListener('drop', event => {
                event.preventDefault();
                const sourceId = draggedBlockId || event.dataTransfer.getData('text/plain');
                if (!sourceId || sourceId === section.id) return;
                const from = config.sections.findIndex(item => item.id === sourceId);
                const to = config.sections.findIndex(item => item.id === section.id);
                if (from < 0 || to < 0) return;
                const [moved] = config.sections.splice(from, 1);
                config.sections.splice(to, 0, moved);
                expandedBlockId = moved.id;
                render();
            });
            return card;
        };
        const render = () => {
            editor.replaceChildren();
            config.sections.forEach((section, index) => editor.append(makeBlock(section, index)));
            if (!config.sections.length) editor.append(element('div', 'border rounded p-4 text-center text-muted',
                'No blocks yet. Add a Text, List or Table block to start building the message.'));
            blockCount.textContent = config.sections.length + (config.sections.length === 1 ? ' block' : ' blocks');
            sync();
            updatePreview();
        };
        const resolvePath = (path, root) => {
            if (!path) return undefined;
            const parts = path.split('.');
            let current = root;
            for (const part of parts) {
                if (Array.isArray(current)) current = current.map(item => item && typeof item === 'object' ? item[part] : undefined);
                else if (current && typeof current === 'object') current = current[part];
                else return undefined;
            }
            return current;
        };
        const display = value => value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value);
        const template = value => String(value || '').replace(/\{\{?([A-Za-z][A-Za-z0-9_.-]*)\}\}?/g, (match, key) => {
            if (Object.prototype.hasOwnProperty.call(sample, key)) return display(sample[key]);
            const value = resolvePath(key.startsWith('data.') || key.startsWith('response.') ? key : 'data.' + key, sample);
            if (value !== undefined) return display(value);
            const alias = resolvePath('data.' + key.replace(/_/g, '.'), sample);
            return alias === undefined ? match : display(alias);
        });
        const renderedBlocks = () => config.sections.map(section => {
            const title = template(section.title || '');
            if (section.type === 'TEXT') return { type: 'text', title, text: template(section.text || '') };
            if (section.type === 'KEY_VALUE') return {
                type: 'details', title,
                fields: (section.fields || []).map(field => ({
                    label: template(field.label || ''), value: display(resolvePath(field.path, sample))
                })).filter(field => field.label || field.value)
            };
            if (section.type === 'TABLE') {
                const rows = resolvePath(section.sourcePath, sample);
                return {
                    type: 'table', title,
                    columns: (section.columns || []).map(column => ({ key: column.key, label: template(column.label || column.key) })),
                    rows: (Array.isArray(rows) ? rows : []).slice(0, Number(section.maxRows || 20))
                        .map(row => Object.fromEntries((section.columns || []).map(column => [column.key, display(resolvePath(column.key, row))])))
                };
            }
            if (section.type === 'LIST') {
                const source = resolvePath(section.sourcePath, sample);
                let items = Array.isArray(source) ? source : source && typeof source === 'object' ? [source] : [];
                const configuredFields = Array.isArray(section.fields) ? section.fields.filter(field => field && field.key) : [];
                // The preview uses a representative sample, not the live execution payload.
                // If the selected path is not present in that sample, synthesize one row
                // from the configured fields so the designer still shows the chosen layout.
                const usingRepresentativeRow = items.length === 0 && configuredFields.length > 0;
                if (usingRepresentativeRow) items = [{}];
                return {
                    type: 'list', title,
                    emptyText: section.emptyText || 'No items available',
                    items: items.slice(0, Number(section.maxItems || 10)).map(item => {
                        if (configuredFields.length) {
                            return configuredFields.map(field => {
                                const value = resolvePath(field.key, item);
                                const renderedValue = value === undefined || value === null
                                    ? (usingRepresentativeRow ? 'Example value' : '—') : display(value);
                                return { label: field.label || field.key, value: renderedValue };
                            });
                        }
                        return [{ label: 'Value', value: display(section.valuePath ? resolvePath(section.valuePath, item) : item) }];
                    }).filter(fields => fields.length)
                };
            }
            return { type: section.type.toLowerCase(), title, text: '[Legacy block preview]' };
        });
        const addSubject = () => subjectManuallyEdited ? subjectInput.value.trim() : (currentPreviewSeverity() + ' — ' + (ruleNameInput?.value?.trim() || 'Alert Rule'));
        const appendBlocks = (container, blocks, channel) => {
            blocks.forEach(block => {
                if (block.title) container.append(element('h6', 'fw-semibold mt-3 mb-2', block.title));
                if (block.type === 'text') {
                    const textContent = element('p', 'mb-2 text-break', block.text);
                    textContent.style.whiteSpace = 'pre-wrap';
                    container.append(textContent);
                }
                if (block.type === 'details') {
                    if (channel === 'whatsapp') {
                        block.fields.forEach(field => container.append(element('div', 'font-monospace small', field.label + ': ' + field.value)));
                    } else {
                        const list = element('div', 'list-group list-group-flush mb-2');
                        block.fields.forEach(field => {
                            const row = element('div', 'list-group-item d-flex justify-content-between gap-3 px-0 py-2');
                            row.append(element('span', 'text-muted', field.label), element('strong', 'text-end text-break', field.value));
                            list.append(row);
                        });
                        container.append(list);
                    }
                }
                if (block.type === 'list') {
                    if (!block.items.length) {
                        container.append(element('div', 'small text-muted mb-2', block.emptyText || 'No items available'));
                    } else {
                        const wrapper = element('div', 'table-responsive mb-2');
                        const table = element('table', '');
                        const body = element('tbody');
                        table.setAttribute('role', 'presentation');
                        table.setAttribute('cellpadding', '0');
                        table.setAttribute('cellspacing', '0');
                        table.style.cssText = 'width:100%;border-collapse:collapse;background-color:#FFFFFF;color:#101828;border:1px solid #EAECF0;font-size:13px;';
                        let rowIndex = 0;
                        block.items.forEach(fields => fields.forEach(field => {
                            const row = element('tr');
                            const labelCell = element('td', '', field.label);
                            const valueCell = element('td', '', field.value);
                            const background = rowIndex++ % 2 === 0 ? '#FFFFFF' : '#F8FAFC';
                            [labelCell, valueCell].forEach(cell => {
                                cell.style.cssText = 'padding:9px 11px;border-bottom:1px solid #EAECF0;background-color:' + background + ';color:#101828;font-size:13px;line-height:1.5;vertical-align:top;overflow-wrap:anywhere;';
                            });
                            labelCell.style.fontWeight = '600';
                            labelCell.style.color = '#475467';
                            labelCell.style.width = '38%';
                            row.append(labelCell, valueCell);
                            body.append(row);
                        }));
                        table.append(body);
                        wrapper.append(table);
                        container.append(wrapper);
                    }
                }
                if (block.type === 'table') {
                    if (channel === 'whatsapp') {
                        block.rows.forEach((row, index) => {
                            const values = block.columns.map(column => column.label + ': ' + (row[column.key] || '')).join(' | ');
                            container.append(element('div', 'font-monospace small border-bottom py-1', (index + 1) + '. ' + values));
                        });
                    } else {
                        const wrapper = element('div', 'table-responsive');
                        const table = element('table', 'table table-sm table-striped table-bordered mb-2');
                        const head = element('thead', 'table-light');
                        const header = element('tr');
                        block.columns.forEach(column => header.append(element('th', '', column.label)));
                        head.append(header);
                        const body = element('tbody');
                        block.rows.forEach(row => {
                            const tr = element('tr');
                            block.columns.forEach(column => tr.append(element('td', '', row[column.key] || '')));
                            body.append(tr);
                        });
                        table.append(head, body);
                        wrapper.append(table);
                        container.append(wrapper);
                    }
                }
            });
        };
        const previewRouteMetadata = channel => {
            const routes = Array.from(document.querySelectorAll(".notification-route-row")).filter(row => {
                const selected = row.querySelector(".route-channel")?.selectedOptions[0];
                return row.querySelector(".route-enabled")?.checked && selected?.dataset.channelType === channel;
            });
            const entries = [];
            routes.forEach(row => {
                const channelOption = row.querySelector(".route-channel")?.selectedOptions[0];
                const targetType = row.querySelector(".route-target-type")?.value;
                const recipient = row.querySelector(".route-recipient")?.selectedOptions[0];
                const group = row.querySelector(".route-group")?.selectedOptions[0];
                if (channel === "EMAIL") {
                    if (channelOption?.dataset.from) entries.push(["From", channelOption.dataset.from]);
                    const to = targetType === "GROUP" ? (group?.value ? group.textContent : "") : (recipient?.value ? (recipient.dataset.email || recipient.textContent || "") : "");
                    if (to) entries.push(["To", to]);
                    const cc = row.querySelector(".route-cc")?.value?.trim();
                    if (cc) entries.push(["CC", cc]);
                } else if (channel === "TEAMS") {
                    if (channelOption?.dataset.channelName) entries.push(["Destination", channelOption.dataset.channelName]);
                } else {
                    const to = targetType === "GROUP" ? (group?.value ? group.textContent : "") : (recipient?.value ? (recipient.dataset.phone || recipient.textContent || "") : "");
                    if (to) entries.push(["To", to]);
                }
            });
            return entries;
        };
        const appendPreviewMetadata = (container, channel) => {
            const entries = previewRouteMetadata(channel);
            if (!entries.length) return;
            const panel = element("div", "small border-bottom px-3 py-2 mb-2");
            panel.style.color = "#475467";
            entries.forEach(([label, value]) => {
                const line = element("div", "text-break");
                line.append(element("strong", "me-2", label + ":"), document.createTextNode(value));
                panel.append(line);
            });
            container.append(panel);
        };
        const updatePreview = () => {
            sample.severity = currentPreviewSeverity();
            sample.ruleName = ruleNameInput?.value?.trim() || 'Alert Rule';
            const blocks = renderedBlocks();
            const header = { title: addSubject() };
            preview.replaceChildren();
            if (activePreview === 'email') {
                const email = element('div', 'bg-white border rounded shadow-sm overflow-hidden');
                email.style.color = '#1f2937';
                appendPreviewMetadata(email, "EMAIL");
                const subject = element('div', 'border-bottom px-3 py-2 small text-muted');
                subject.append(element('strong', 'me-2', 'Subject:'), document.createTextNode(header.title));
                const banner = element('div', 'p-3 text-white');
                banner.style.background = currentPreviewSeverity() === 'CRITICAL' ? '#b42318' : currentPreviewSeverity() === 'WARNING' ? '#b54708' : '#175cd3';
                banner.append(element('div', 'small fw-bold text-uppercase', currentPreviewSeverity() + ' ALERT'),
                    element('div', 'h5 mb-0 mt-1', header.title));
                const body = element('div', 'p-3');
                body.style.color = '#1f2937';
                appendBlocks(body, blocks, 'email');
                body.append(element('div', 'border-top mt-3 pt-2 small text-muted', 'Monitoring Alert Manager'));
                email.append(subject, banner, body);
                preview.append(email);
            } else if (activePreview === 'teams') {
                const card = element('div', 'bg-white border rounded overflow-hidden shadow-sm');
                card.style.color = '#1f2937';
                appendPreviewMetadata(card, "TEAMS");
                const accent = element('div', 'p-3 text-white');
                accent.style.background = currentPreviewSeverity() === 'CRITICAL' ? '#b42318' : currentPreviewSeverity() === 'WARNING' ? '#b54708' : '#175cd3';
                accent.append(element('div', 'small fw-bold', currentPreviewSeverity() + ' ALERT'),
                    element('div', 'h5 mb-0 mt-1', header.title));
                const body = element('div', 'p-3');
                appendBlocks(body, blocks, 'teams');
                card.append(accent, body);
                preview.append(card);
            } else {
                const chat = element('div', 'mx-auto p-3 rounded-4 shadow-sm');
                chat.style.maxWidth = '360px';
                chat.style.color = '#1f2937';
                chat.style.background = '#d9fdd3';
                appendPreviewMetadata(chat, "WHATSAPP");
                const bubble = element('div', 'bg-white rounded-3 p-3 shadow-sm');
                bubble.style.color = '#1f2937';
                bubble.append(element('div', 'small fw-bold mb-2', header.title));
                appendBlocks(bubble, blocks, 'whatsapp');
                chat.append(bubble, element('div', 'text-end small text-muted mt-2', '09:30 ✓✓'));
                preview.append(chat);
            }
            const whatsappInfo = whatsappCount?.parentElement;
            whatsappInfo?.classList.toggle('d-none', activePreview !== 'whatsapp');
            const whatsappText = buildWhatsAppText(header, blocks);
            const count = whatsappText.length;
            whatsappCount.textContent = 'WhatsApp: ' + count.toLocaleString() + ' / 1,024 characters';
            whatsappWarning.textContent = count > 1024 ? 'Message exceeds the limit' : '';
            whatsappWarning.className = count > 1024 ? 'small text-danger fw-semibold' : 'small text-success';
        };
        const buildWhatsAppText = (header, blocks) => {
            const lines = [header.title];
            blocks.forEach(block => {
                if (block.title) lines.push(block.title);
                if (block.type === 'text') lines.push(block.text);
                if (block.type === 'details' && block.fields.length) {
                    lines.push('```');
                    block.fields.forEach(field => lines.push(field.label + ': ' + field.value));
                    lines.push('```');
                }
                if (block.type === 'list' && block.items.length) {
                    block.items.forEach((fields, index) => {
                        lines.push((index + 1) + '. ' + fields.map(field => field.label + ': ' + field.value).join(' | '));
                    });
                }
                if (block.type === 'table' && block.rows.length) {
                    lines.push('```');
                    block.rows.forEach((row, index) =>
                        lines.push((index + 1) + '. ' + block.columns.map(column => column.label + ': ' + (row[column.key] || '')).join(' | ')));
                    lines.push('```');
                }
            });
            return lines.filter(Boolean).join('\n');
        };
        const sync = () => {
            jsonInput.value = JSON.stringify(config, null, 2);
            jsonEdited = false;
            updatePreview();
        };
        const addBlock = type => {
            if (config.sections.length >= 20) { setStatus('A message can contain at most 20 blocks.', 'warning'); return; }
            const section = { id: newId(), type, title: '' };
            if (type === 'TEXT') section.text = '';
            if (type === 'KEY_VALUE') section.fields = [{ label: '', path: '' }];
            if (type === 'TABLE') { section.sourcePath = ''; section.columns = []; section.maxRows = 20; }
            if (type === 'LIST') { section.sourcePath = ''; section.fields = []; section.maxItems = 10; section.emptyText = 'No items available'; }
            config.sections.push(section);
            expandedBlockId = section.id;
            render();
        };
        document.querySelectorAll('[data-add-presentation-section]').forEach(add => add.addEventListener('click', () => addBlock(add.dataset.addPresentationSection)));
        subjectInput?.addEventListener('input', () => { subjectManuallyEdited = true; updatePreview(); });
        ruleNameInput?.addEventListener('input', () => {
            sample.ruleName = ruleNameInput.value.trim() || 'Alert Rule';
            if (!subjectManuallyEdited && subjectInput) subjectInput.value = defaultSubject();
            updatePreview();
        });
        ruleSeverityInput?.addEventListener('change', () => {
            if (previewSeverity) previewSeverity.value = ruleSeverityInput.value;
            if (!subjectManuallyEdited && subjectInput) subjectInput.value = defaultSubject();
            updatePreview();
        });
        document.querySelectorAll('[data-preview-channel]').forEach(tab => tab.addEventListener('click', () => {
            activePreview = tab.dataset.previewChannel;
            document.querySelectorAll('[data-preview-channel]').forEach(item => {
                const active = item === tab;
                item.classList.toggle('active', active);
                item.setAttribute('aria-selected', String(active));
            });
            updatePreview();
        }));
        document.getElementById('refreshPresentationPreview')?.addEventListener('click', updatePreview);
        previewSeverity?.addEventListener('change', updatePreview);
        document.addEventListener('change', event => {
            if (event.target.closest('.notification-route-row')) updatePreview();
        });
        document.addEventListener('input', event => {
            if (event.target.closest('.notification-route-row')) updatePreview();
        });
        document.getElementById('applyPresentationJson')?.addEventListener('click', () => {
            try {
                config = normalize(JSON.parse(jsonInput.value || '{}'));
                render();
                setStatus('Presentation JSON loaded into the block builder.', 'success');
            } catch (error) {
                setStatus('Unable to load presentation JSON: ' + error.message, 'danger');
            }
        });
        try {
            config = normalize(JSON.parse(jsonInput.value || '{}'));
        } catch (error) {
            config = { version: 1, sections: [] };
            setStatus('Saved presentation JSON could not be loaded: ' + error.message, 'warning');
        }
        window.refreshAlertPresentationDesigner = () => render();
        render();
        jsonInput.addEventListener('input', () => { jsonEdited = true; });
        form.addEventListener('submit', event => {
            if (jsonEdited) {
                try {
                    config = normalize(JSON.parse(jsonInput.value || '{}'));
                } catch (error) {
                    event.preventDefault();
                    setStatus('Correct the advanced presentation JSON before saving: ' + error.message, 'danger');
                    return;
                }
            }
            sync();
        });
    });
})();