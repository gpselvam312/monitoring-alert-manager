(() => {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.querySelector('form[action*="/alerts/rules/save"]');
        const jsonInput = document.getElementById('presentationConfigJson');
        const editor = document.getElementById('presentationSections');
        const preview = document.getElementById('presentationPreview');
        if (!form || !jsonInput || !editor || !preview) return;

        const titleInput = document.getElementById('presentationTitleTemplate');
        const messageInput = document.getElementById('presentationMessageTemplate');
        const status = document.getElementById('presentationBuilderMessage');
        const blockCount = document.getElementById('presentationBlockCount');
        const whatsappCount = document.getElementById('whatsAppCharacterCount');
        const whatsappWarning = document.getElementById('whatsAppLimitWarning');
        const fields = () => Array.isArray(window.alertRuleDiscoveredFields) ? window.alertRuleDiscoveredFields : [];
        const sample = {
            ruleName: 'Kafka Consumer Lag',
            severity: 'CRITICAL',
            jobName: 'Kafka Cluster Health',
            applicationName: 'Retail Platform',
            environment: 'PRODUCTION',
            timestamp: '2026-11-13 09:30:00 IST',
            executionId: 'EX-10482',
            alertId: 'AL-2081',
            occurrenceCount: 3,
            cluster_name: 'kafka-cluster-prod',
            response: { status: 'CRITICAL', schemaVersion: 1 },
            data: {
                cluster: { name: 'kafka-cluster-prod', version: '3.8.1', status: 'CRITICAL' },
                summary: { consumerGroupCount: 12, criticalGroupCount: 2, totalLag: 18450 },
                consumers: [
                    { groupId: 'orders-service', lag: 12400, status: 'CRITICAL' },
                    { groupId: 'payments-service', lag: 6050, status: 'WARNING' }
                ]
            }
        };

        let config = { version: 1, titleTemplate: '{{severity}} — {{ruleName}}', messageTemplate: '', sections: [] };
        let jsonEdited = false;
        let activePreview = 'email';
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
                return normalized;
            });
            if (sections.length > 20) throw new Error('A presentation can contain at most 20 blocks.');
            return { version: 1, titleTemplate: parsed.titleTemplate || '', messageTemplate: parsed.messageTemplate || '', sections };
        };
        const newId = () => 'block-' + (window.crypto?.randomUUID?.() || Math.random().toString(36).slice(2, 10));
        const discoveredPaths = () => fields().filter(field => field && typeof field.path === 'string'
            && (field.path.startsWith('data.') || field.path.startsWith('response.')));
        const relativeDiscoveredPaths = sourcePath => {
            if (!sourcePath) return [];
            const prefix = sourcePath.endsWith('[]') ? sourcePath.slice(0, -2) : sourcePath;
            return discoveredPaths().map(field => {
                const dottedPrefix = prefix + '.';
                const arrayPrefix = prefix + '[].';
                const relative = field.path.startsWith(dottedPrefix) ? field.path.slice(dottedPrefix.length)
                    : field.path.startsWith(arrayPrefix) ? field.path.slice(arrayPrefix.length) : '';
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
                section.columns.forEach((column, columnIndex) => {
                    const row = element('div', 'border rounded p-2 mb-2');
                    const grid = element('div', 'row g-2 align-items-end');
                    const label = textInput(column.label, 'Column header', 100);
                    label.addEventListener('input', () => { column.label = label.value; sync(); });
                    const key = relativePathControl(section.sourcePath, column.key,
                        value => { column.key = value; sync(); }, false);
                    grid.append(control('Column header', label), control('Row field', key));
                    row.append(grid, button('Remove column', 'btn btn-sm btn-outline-danger mt-2',
                        () => { section.columns.splice(columnIndex, 1); render(); }, 'bi bi-trash'));
                    body.append(row);
                });
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
                body.append(control('Items from response array', pathControl(section.sourcePath,
                    value => { section.sourcePath = value; sync(); },
                    field => field.type === 'array', () => render()), 'Choose an array from discovered response fields.'));
                body.append(control('Field to display for each item', relativePathControl(section.sourcePath,
                    section.valuePath, value => { section.valuePath = value; sync(); }, true),
                    'Choose a discovered field or display each item as-is.'));
                const maxItems = textInput(String(section.maxItems || 10), '10', 3);
                maxItems.type = 'number'; maxItems.min = '1'; maxItems.max = '100';
                maxItems.addEventListener('input', () => { section.maxItems = Math.min(100, Math.max(1, Number(maxItems.value) || 1)); sync(); });
                body.append(control('Maximum items', maxItems));
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
                const items = resolvePath(section.sourcePath, sample);
                return {
                    type: 'list', title,
                    items: (Array.isArray(items) ? items : []).slice(0, Number(section.maxItems || 10))
                        .map(item => display(section.valuePath ? resolvePath(section.valuePath, item) : item))
                        .filter(value => value !== '')
                };
            }
            return { type: section.type.toLowerCase(), title, text: '[Legacy block preview]' };
        });
        const addTitleAndMessage = () => ({
            title: template(config.titleTemplate || '{{severity}} — {{ruleName}}'),
            message: template(config.messageTemplate || '')
        });
        const appendBlocks = (container, blocks, channel) => {
            blocks.forEach(block => {
                if (block.title) container.append(element('h6', 'fw-semibold mt-3 mb-2', block.title));
                if (block.type === 'text') container.append(element('p', 'mb-2 text-break', block.text));
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
                    if (channel === 'whatsapp') {
                        block.items.forEach((item, index) => container.append(element('div', 'font-monospace small', (index + 1) + '. ' + item)));
                    } else {
                        const list = element('ul', 'mb-2 ps-3');
                        block.items.forEach(item => list.append(element('li', 'mb-1', item)));
                        container.append(list);
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
                        const table = element('table', 'table table-sm table-bordered mb-2');
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
        const updatePreview = () => {
            const blocks = renderedBlocks();
            const header = addTitleAndMessage();
            preview.replaceChildren();
            if (activePreview === 'email') {
                const email = element('div', 'bg-white border rounded shadow-sm overflow-hidden');
                const subject = element('div', 'border-bottom px-3 py-2 small text-muted');
                subject.append(element('strong', 'me-2', 'Subject:'), document.createTextNode(header.title));
                const banner = element('div', 'p-3 text-white');
                banner.style.background = sample.severity === 'CRITICAL' ? '#b42318' : sample.severity === 'WARNING' ? '#b54708' : '#175cd3';
                banner.append(element('div', 'small fw-bold text-uppercase', sample.severity + ' ALERT'),
                    element('div', 'h5 mb-0 mt-1', header.title));
                const body = element('div', 'p-3');
                if (header.message) body.append(element('p', 'mb-2', header.message));
                appendBlocks(body, blocks, 'email');
                body.append(element('div', 'border-top mt-3 pt-2 small text-muted', 'Monitoring Alert Manager'));
                email.append(subject, banner, body);
                preview.append(email);
            } else if (activePreview === 'teams') {
                const card = element('div', 'bg-white border rounded overflow-hidden shadow-sm');
                const accent = element('div', 'p-3 text-white');
                accent.style.background = sample.severity === 'CRITICAL' ? '#b42318' : sample.severity === 'WARNING' ? '#b54708' : '#175cd3';
                accent.append(element('div', 'small fw-bold', sample.severity + ' ALERT'),
                    element('div', 'h5 mb-0 mt-1', header.title));
                const body = element('div', 'p-3');
                if (header.message) body.append(element('p', 'mb-2', header.message));
                appendBlocks(body, blocks, 'teams');
                card.append(accent, body);
                preview.append(card);
            } else {
                const chat = element('div', 'mx-auto p-3 rounded-4 shadow-sm');
                chat.style.maxWidth = '360px';
                chat.style.background = '#d9fdd3';
                const bubble = element('div', 'bg-white rounded-3 p-3 shadow-sm');
                bubble.append(element('div', 'small fw-bold mb-2', header.title));
                if (header.message) bubble.append(element('div', 'small mb-2', header.message));
                appendBlocks(bubble, blocks, 'whatsapp');
                chat.append(bubble, element('div', 'text-end small text-muted mt-2', '09:30 ✓✓'));
                preview.append(chat);
            }
            const whatsappText = buildWhatsAppText(header, blocks);
            const count = whatsappText.length;
            whatsappCount.textContent = 'WhatsApp: ' + count.toLocaleString() + ' / 1,024 characters';
            whatsappWarning.textContent = count > 1024 ? 'Message exceeds the limit' : '';
            whatsappWarning.className = count > 1024 ? 'small text-danger fw-semibold' : 'small text-success';
        };
        const buildWhatsAppText = (header, blocks) => {
            const lines = [header.title, header.message];
            blocks.forEach(block => {
                if (block.title) lines.push(block.title);
                if (block.type === 'text') lines.push(block.text);
                if (block.type === 'details' && block.fields.length) {
                    lines.push('```');
                    block.fields.forEach(field => lines.push(field.label + ': ' + field.value));
                    lines.push('```');
                }
                if (block.type === 'list' && block.items.length) {
                    block.items.forEach((item, index) => lines.push((index + 1) + '. ' + item));
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
            config.titleTemplate = titleInput.value;
            config.messageTemplate = messageInput.value;
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
            if (type === 'LIST') { section.sourcePath = ''; section.valuePath = ''; section.maxItems = 10; section.emptyText = 'No items available'; }
            config.sections.push(section);
            expandedBlockId = section.id;
            render();
        };
        document.querySelectorAll('[data-add-presentation-section]').forEach(add => add.addEventListener('click', () => addBlock(add.dataset.addPresentationSection)));
        [titleInput, messageInput].forEach(input => input?.addEventListener('input', () => { sync(); updatePreview(); }));
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
        document.getElementById('applyPresentationJson')?.addEventListener('click', () => {
            try {
                config = normalize(JSON.parse(jsonInput.value || '{}'));
                titleInput.value = config.titleTemplate;
                messageInput.value = config.messageTemplate;
                render();
                setStatus('Presentation JSON loaded into the block builder.', 'success');
            } catch (error) {
                setStatus('Unable to load presentation JSON: ' + error.message, 'danger');
            }
        });
        try {
            config = normalize(JSON.parse(jsonInput.value || '{}'));
            titleInput.value = config.titleTemplate;
            messageInput.value = config.messageTemplate;
        } catch (error) {
            config = { version: 1, titleTemplate: '', messageTemplate: '', sections: [] };
            setStatus('Saved presentation JSON could not be loaded: ' + error.message, 'warning');
        }
        window.refreshAlertPresentationDesigner = () => render();
        render();
        jsonInput.addEventListener('input', () => { jsonEdited = true; });
        form.addEventListener('submit', event => {
            if (jsonEdited) {
                try {
                    config = normalize(JSON.parse(jsonInput.value || '{}'));
                    titleInput.value = config.titleTemplate;
                    messageInput.value = config.messageTemplate;
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