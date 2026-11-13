(() => {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        const form = document.querySelector('form');
        const jsonInput = document.getElementById('presentationConfigJson');
        const editor = document.getElementById('presentationSections');
        if (!form || !jsonInput || !editor) return;

        const fields = () => Array.isArray(window.alertRuleDiscoveredFields) ? window.alertRuleDiscoveredFields : [];
        let config = { version: 1, titleTemplate: '', messageTemplate: '', sections: [] };
        let jsonEdited = false;
        const $ = (tag, className, text) => {
            const element = document.createElement(tag);
            if (className) element.className = className;
            if (text !== undefined) element.textContent = text;
            return element;
        };
        const option = (select, value, label) => {
            const item = new Option(label, value);
            item.selected = String(value) === String(select.value);
            select.add(item);
        };
        const control = (labelText, element, hint) => {
            const wrapper = $('div', 'col-12');
            wrapper.append($('label', 'form-label', labelText), element);
            if (hint) wrapper.append($('div', 'form-text', hint));
            return wrapper;
        };
        const input = (value, placeholder, maxLength) => {
            const element = $('input', 'form-control');
            element.type = 'text';
            element.value = value || '';
            element.placeholder = placeholder || '';
            if (maxLength) element.maxLength = maxLength;
            return element;
        };
        const selectPath = (value, predicate, allowRelative = false) => {
            const select = $('select', 'form-select');
            const candidates = fields().filter(predicate);
            select.add(new Option('Select a discovered field…', ''));
            candidates.forEach(field => {
                const path = allowRelative ? field.path : field.path;
                option(select, path, field.path + ' (' + field.type + ')');
            });
            if (value && !candidates.some(field => field.path === value)) {
                option(select, value, value + ' (saved value)');
            }
            select.value = value || '';
            return select;
        };
        const documentPath = field => field.path.startsWith('data.') || field.path.startsWith('response.');
        const collectionFields = () => fields().filter(field => field.type === 'array' && documentPath(field));
        const documentFields = () => fields().filter(field => documentPath(field) && field.type !== 'array');
        const relativeFields = sourcePath => fields()
            .filter(field => field.path.startsWith(sourcePath + '.') && field.type !== 'array')
            .map(field => ({ path: field.path.slice(sourcePath.length + 1), type: field.type }))
            .filter((field, index, all) => all.findIndex(candidate => candidate.path === field.path) === index);
        const relativeSelect = (sourcePath, value) => {
            const select = $('select', 'form-select');
            const candidates = relativeFields(sourcePath);
            select.add(new Option('Select a row field…', ''));
            candidates.forEach(field => option(select, field.path, field.path + ' (' + field.type + ')'));
            if (value && !candidates.some(field => field.path === value)) option(select, value, value + ' (saved value)');
            if (!value && candidates.length === 0) option(select, 'value', 'value (primitive item)');
            select.value = value || (candidates.length === 0 ? 'value' : '');
            return select;
        };
        const withManualPath = (select, value, onChange, placeholder) => {
            const wrapper = $('div', 'd-grid gap-2');
            const manual = input(value, placeholder || 'Or enter a response path manually', 255);
            select.addEventListener('change', () => {
                manual.value = select.value;
                onChange(select.value);
            });
            manual.addEventListener('input', () => onChange(manual.value.trim()));
            wrapper.append(select, manual);
            return wrapper;
        };
        const newId = () => 'section-' + (window.crypto?.randomUUID?.() || Math.random().toString(36).slice(2, 10));
        const normalize = parsed => {
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('Presentation configuration must be a JSON object.');
            if (parsed.version != null && String(parsed.version) !== '1') throw new Error('Only presentation version 1 is supported.');
            if (parsed.sections != null && !Array.isArray(parsed.sections)) throw new Error('Sections must be an array.');
            const sections = (parsed.sections || []).map(section => ({
                ...section,
                id: section.id || newId(),
                type: String(section.type || '').toUpperCase()
            }));
            if (sections.length > 20) throw new Error('A presentation can contain at most 20 sections.');
            return { version: 1, titleTemplate: parsed.titleTemplate || '', messageTemplate: parsed.messageTemplate || '', sections };
        };
        const move = (list, index, target) => {
            if (index < 0 || index >= list.length || target < 0 || target >= list.length || index === target) return;
            const [item] = list.splice(index, 1);
            list.splice(target, 0, item);
        };
        const moveToolbar = (toolbar, list, index, rerender) => {
            const actions = [
                ['Move to top', () => move(list, index, 0)],
                ['Move up', () => move(list, index, index - 1)],
                ['Move down', () => move(list, index, index + 1)],
                ['Move to bottom', () => move(list, index, list.length - 1)]
            ];
            actions.forEach(([label, action]) => {
                const button = $('button', 'btn btn-sm btn-outline-secondary', label);
                button.type = 'button';
                button.addEventListener('click', () => { action(); rerender(); });
                toolbar.append(button);
            });
        };
        const sync = () => {
            jsonInput.value = JSON.stringify(config, null, 2);
            jsonEdited = false;
        };
        const addFieldRow = (section, container, index, rerender) => {
            const row = $('div', 'row g-2 align-items-end border rounded p-2 mb-2');
            const label = input(section.fields[index]?.label, 'Display label', 100);
            const path = selectPath(section.fields[index]?.path, documentPath);
            label.addEventListener('input', () => { section.fields[index].label = label.value; sync(); });
            path.addEventListener('change', () => { section.fields[index].path = path.value; sync(); });
            row.append(control('Column label', label), control('Response field', withManualPath(path, section.fields[index]?.path || '', value => { section.fields[index].path = value; sync(); })));
            const remove = $('button', 'btn btn-sm btn-outline-danger', 'Remove');
            remove.type = 'button';
            remove.addEventListener('click', () => { section.fields.splice(index, 1); rerender(); });
            const actions = $('div', 'col-12 d-flex gap-2 flex-wrap');
            actions.append(remove);
            row.append(actions);
            container.append(row);
        };
        const addColumnRow = (section, container, index, rerender) => {
            const column = section.columns[index];
            const row = $('div', 'row g-2 align-items-end border rounded p-2 mb-2');
            const label = input(column.label, 'Column heading', 100);
            const key = relativeSelect(section.sourcePath, column.key);
            label.addEventListener('input', () => { column.label = label.value; sync(); });
            key.addEventListener('change', () => { column.key = key.value; sync(); });
            row.append(control('Column heading', label), control('Value from each row', withManualPath(key, column.key || '', value => { column.key = value; sync(); }, 'Or enter a row field such as groupId')));
            const actions = $('div', 'col-12 d-flex gap-2 flex-wrap');
            const toolbar = $('div', 'd-flex gap-1 flex-wrap');
            moveToolbar(toolbar, section.columns, index, rerender);
            actions.append(toolbar);
            const remove = $('button', 'btn btn-sm btn-outline-danger', 'Remove column');
            remove.type = 'button';
            remove.addEventListener('click', () => { section.columns.splice(index, 1); rerender(); });
            actions.append(remove);
            row.append(actions);
            container.append(row);
        };
        const renderSection = (section, index) => {
            const card = $('section', 'border rounded p-3 mb-3 bg-body presentation-section');
            card.draggable = true;
            card.dataset.index = String(index);
            card.addEventListener('dragstart', event => {
                event.dataTransfer.setData('text/plain', String(index));
                event.dataTransfer.effectAllowed = 'move';
            });
            card.addEventListener('dragover', event => { event.preventDefault(); card.classList.add('border-primary'); });
            card.addEventListener('dragleave', () => card.classList.remove('border-primary'));
            card.addEventListener('drop', event => {
                event.preventDefault();
                card.classList.remove('border-primary');
                const from = Number(event.dataTransfer.getData('text/plain'));
                move(config.sections, from, index);
                render();
            });

            const heading = $('div', 'd-flex align-items-start justify-content-between gap-2 flex-wrap mb-3');
            const headingText = $('div');
            headingText.append($('strong', '', (index + 1) + '. ' + section.type.replace('_', ' ') + ' section'));
            headingText.append($('div', 'small text-muted', 'Drag this section or use the position controls to arrange the notification.'));
            heading.append(headingText);
            const actions = $('div', 'd-flex gap-1 flex-wrap');
            moveToolbar(actions, config.sections, index, render);
            const remove = $('button', 'btn btn-sm btn-outline-danger', 'Remove');
            remove.type = 'button';
            remove.addEventListener('click', () => { config.sections.splice(index, 1); render(); });
            actions.append(remove);
            heading.append(actions);
            card.append(heading);

            const title = input(section.title, 'Optional section heading', 150);
            title.addEventListener('input', () => { section.title = title.value; sync(); });
            card.append(control('Section title', title));

            const body = $('div', 'row g-3 mt-1');
            if (section.type === 'TEXT') {
                const text = $('textarea', 'form-control');
                text.rows = 4; text.maxLength = 10000; text.value = section.text || '';
                text.placeholder = 'Write text and use placeholders such as {data.cluster.name}';
                text.addEventListener('input', () => { section.text = text.value; sync(); });
                body.append(control('Text / message', text, 'Supports alert placeholders and response paths such as {data.cluster.name}.'));
            } else if (section.type === 'KEY_VALUE') {
                section.fields = Array.isArray(section.fields) ? section.fields : [];
                const list = $('div', 'col-12');
                section.fields.forEach((_, fieldIndex) => addFieldRow(section, list, fieldIndex, render));
                const add = $('button', 'btn btn-sm btn-outline-primary', 'Add field');
                add.type = 'button';
                add.disabled = section.fields.length >= 50;
                add.addEventListener('click', () => { section.fields.push({ label: '', path: '' }); render(); });
                list.append(add);
                body.append(list);
            } else if (section.type === 'TABLE') {
                const source = selectPath(section.sourcePath, field => field.type === 'array' && documentPath(field));
                source.addEventListener('change', () => {
                    section.sourcePath = source.value;
                    section.columns = (section.columns || []).map(column => ({ ...column }));
                    render();
                });
                body.append(control('Rows from response collection', withManualPath(source, section.sourcePath || '', value => { section.sourcePath = value; sync(); render(); }, 'Or enter an array path such as data.consumerGroups'), 'Choose a discovered array or enter its path.'));
                section.columns = Array.isArray(section.columns) ? section.columns : [];
                const columns = $('div', 'col-12');
                columns.append($('div', 'd-flex align-items-center justify-content-between gap-2 mb-2',
                    'Table columns · arrange columns in the order they should appear'));
                section.columns.forEach((_, columnIndex) => addColumnRow(section, columns, columnIndex, render));
                const addColumn = $('button', 'btn btn-sm btn-outline-primary', 'Add column');
                addColumn.type = 'button';
                addColumn.disabled = section.columns.length >= 20;
                addColumn.addEventListener('click', () => { section.columns.push({ key: '', label: '' }); render(); });
                columns.append(addColumn);
                body.append(columns);
                const maxRows = $('input', 'form-control');
                maxRows.type = 'number'; maxRows.min = '1'; maxRows.max = '100'; maxRows.value = section.maxRows || 20;
                maxRows.addEventListener('input', () => { section.maxRows = Number(maxRows.value); sync(); });
                body.append(control('Maximum rows', maxRows));
            } else if (section.type === 'LIST') {
                const source = selectPath(section.sourcePath, field => field.type === 'array' && documentPath(field));
                source.addEventListener('change', () => { section.sourcePath = source.value; render(); });
                const valuePath = relativeSelect(section.sourcePath, section.valuePath || 'value');
                valuePath.addEventListener('change', () => { section.valuePath = valuePath.value; sync(); });
                body.append(control('Items from response collection', withManualPath(source, section.sourcePath || '', value => { section.sourcePath = value; sync(); render(); }, 'Or enter an array path')), control('Display field for each item', withManualPath(valuePath, section.valuePath || 'value', value => { section.valuePath = value; sync(); }, 'Or enter a field relative to each item')));
                const maxItems = $('input', 'form-control');
                maxItems.type = 'number'; maxItems.min = '1'; maxItems.max = '100'; maxItems.value = section.maxItems || 20;
                maxItems.addEventListener('input', () => { section.maxItems = Number(maxItems.value); sync(); });
                body.append(control('Maximum items', maxItems));
            } else if (section.type === 'JSON') {
                const source = selectPath(section.sourcePath, documentPath);
                source.addEventListener('change', () => { section.sourcePath = source.value; sync(); });
                const maxLength = $('input', 'form-control');
                maxLength.type = 'number'; maxLength.min = '100'; maxLength.max = '10000'; maxLength.value = section.maxLength || 4000;
                maxLength.addEventListener('input', () => { section.maxLength = Number(maxLength.value); sync(); });
                body.append(control('Object / response field', withManualPath(source, section.sourcePath || '', value => { section.sourcePath = value; sync(); }, 'Or enter a path such as data.cluster'), control('Maximum characters', maxLength));
            }
            if (section.type !== 'TEXT') {
                const emptyText = input(section.emptyText, 'Message shown when no data is available', 300);
                emptyText.addEventListener('input', () => { section.emptyText = emptyText.value; sync(); });
                body.append(control('When no data is available', emptyText));
            }
            card.append(body);
            return card;
        };
        const render = () => {
            editor.replaceChildren();
            config.sections.forEach((section, index) => editor.append(renderSection(section, index)));
            if (!config.sections.length) editor.append($('div', 'alert alert-light border mb-0', 'No presentation sections yet. Add a section to build the notification layout.'));
            sync();
        };

        const titleTemplate = document.getElementById('presentationTitleTemplate');
        const messageTemplate = document.getElementById('presentationMessageTemplate');
        [titleTemplate, messageTemplate].forEach((element, index) => {
            element?.addEventListener('input', () => {
                config[index === 0 ? 'titleTemplate' : 'messageTemplate'] = element.value;
                sync();
            });
        });
        const addSection = type => {
            if (config.sections.length >= 20) return;
            const section = { id: newId(), type, title: '' };
            if (type === 'TEXT') section.text = '';
            if (type === 'KEY_VALUE') section.fields = [{ label: '', path: '' }];
            if (type === 'TABLE') section.columns = [{ key: '', label: '' }], section.maxRows = 20, section.sourcePath = '';
            if (type === 'LIST') section.maxItems = 20, section.sourcePath = '', section.valuePath = 'value';
            if (type === 'JSON') section.maxLength = 4000, section.sourcePath = '';
            config.sections.push(section);
            render();
        };
        document.querySelectorAll('[data-add-presentation-section]').forEach(button => {
            button.addEventListener('click', () => addSection(button.dataset.addPresentationSection));
        });

        const applyJson = document.getElementById('applyPresentationJson');
        applyJson?.addEventListener('click', () => {
            try {
                config = normalize(JSON.parse(jsonInput.value || '{}'));
                if (titleTemplate) titleTemplate.value = config.titleTemplate;
                if (messageTemplate) messageTemplate.value = config.messageTemplate;
                render();
                editor.scrollIntoView({ behavior: 'smooth', block: 'start' });
            } catch (error) {
                const status = document.getElementById('presentationBuilderMessage');
                status.textContent = 'Unable to load JSON: ' + error.message;
                status.className = 'alert alert-danger mb-3';
            }
        });

        try {
            config = normalize(JSON.parse(jsonInput.value || '{}'));
            if (titleTemplate) titleTemplate.value = config.titleTemplate;
            if (messageTemplate) messageTemplate.value = config.messageTemplate;
        } catch (error) {
            config = { version: 1, titleTemplate: '', messageTemplate: '', sections: [] };
            const status = document.getElementById('presentationBuilderMessage');
            status.textContent = 'The saved presentation JSON could not be loaded in the visual designer. Open Advanced JSON to correct it. ' + error.message;
            status.className = 'alert alert-warning mb-3';
        }
        render();
        form.addEventListener('submit', () => {
            if (jsonEdited) {
                try { config = normalize(JSON.parse(jsonInput.value || '{}')); }
                catch (_) { /* Server validation returns the authoritative error. */ }
            }
            sync();
        });
        jsonInput.addEventListener('input', () => { jsonEdited = true; });
    });
})();