(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        const form = document.querySelector("#resultParserConfiguration")?.closest("form");
        const storedConfig = document.getElementById("resultParserConfigValue");
        const typeSelect = document.getElementById("resultParserType");
        const delimitedOptions = document.getElementById("resultParserDelimitedOptions");
        const keyValueOptions = document.getElementById("resultParserKeyValueOptions");
        const regexOptions = document.getElementById("resultParserRegexOptions");
        const delimiterPreset = document.getElementById("resultParserDelimiterPreset");
        const customDelimiterWrap = document.getElementById("resultParserCustomDelimiterWrap");
        const customDelimiter = document.getElementById("resultParserCustomDelimiter");
        const keyValueSeparator = document.getElementById("resultParserKeyValueSeparator");
        const customKeyValueSeparatorWrap = document.getElementById("resultParserCustomKeyValueSeparatorWrap");
        const customKeyValueSeparator = document.getElementById("resultParserCustomKeyValueSeparator");
        const regexFields = document.getElementById("resultParserRegexFields");
        const regexEmpty = document.getElementById("resultParserRegexEmpty");
        const advancedOptions = document.getElementById("resultParserAdvancedOptions");
        const advancedJson = document.getElementById("resultParserAdvancedJson");
        const advancedMessage = document.getElementById("resultParserAdvancedMessage");
        const configError = document.getElementById("resultParserConfigError");
        const toggleAdvanced = document.getElementById("resultParserToggleAdvanced");
        const addRegexField = document.getElementById("resultParserAddRegexField");
        const applyAdvanced = document.getElementById("resultParserApplyAdvanced");

        if (!form || !storedConfig || !typeSelect) return;

        const managedKeys = [
            "type", "delimiter", "headerRow", "trim", "ignoreBlankLines",
            "commentPrefix", "separator", "ignorePrefixes", "fields"
        ];
        let currentConfig = {};
        let advancedIsOpen = false;

        function byId(id) {
            return document.getElementById(id);
        }

        function showError(message) {
            configError.textContent = message || "";
            configError.classList.toggle("d-none", !message);
        }

        function setValue(id, value) {
            const element = byId(id);
            if (element) element.value = value == null ? "" : String(value);
        }

        function setChecked(id, value) {
            const element = byId(id);
            if (element) element.checked = value !== false;
        }

        function setSectionVisibility() {
            const type = typeSelect.value;
            delimitedOptions.classList.toggle("d-none", type !== "DELIMITED");
            keyValueOptions.classList.toggle("d-none", type !== "KEY_VALUE");
            regexOptions.classList.toggle("d-none", type !== "REGEX");

            const customDelimiterSelected = delimiterPreset.value === "custom";
            customDelimiterWrap.classList.toggle("d-none", !customDelimiterSelected);
            const customSeparatorSelected = keyValueSeparator.value === "custom";
            customKeyValueSeparatorWrap.classList.toggle("d-none", !customSeparatorSelected);
        }

        function getDelimiter() {
            if (delimiterPreset.value === "custom") return customDelimiter.value;
            return delimiterPreset.value === "\\t" ? "\t" : delimiterPreset.value;
        }

        function setDelimiter(value) {
            const normalized = value === "\t" ? "\\t" : value;
            const supported = ["|", ",", "\\t", ";"];
            if (supported.includes(normalized)) {
                delimiterPreset.value = normalized;
                customDelimiter.value = value;
            } else {
                delimiterPreset.value = "custom";
                customDelimiter.value = value || "|";
            }
        }

        function getKeyValueSeparator() {
            if (keyValueSeparator.value === "custom") return customKeyValueSeparator.value;
            return keyValueSeparator.value;
        }

        function setKeyValueSeparator(value) {
            if (["=", ":"].includes(value)) {
                keyValueSeparator.value = value;
                customKeyValueSeparator.value = value;
            } else {
                keyValueSeparator.value = "custom";
                customKeyValueSeparator.value = value || "=";
            }
        }

        function createInput(labelText, className, value, placeholder) {
            const wrapper = document.createElement("div");
            wrapper.className = className;
            const label = document.createElement("label");
            label.className = "form-label";
            label.textContent = labelText;
            const input = document.createElement("input");
            input.type = "text";
            input.className = "form-control form-control-sm";
            input.value = value == null ? "" : String(value);
            if (placeholder) input.placeholder = placeholder;
            label.appendChild(input);
            wrapper.appendChild(label);
            return { wrapper: wrapper, input: input };
        }

        function addRegexRow(field) {
            const value = field || {};
            const row = document.createElement("div");
            row.className = "border rounded p-3";
            row.dataset.regexField = "true";

            const grid = document.createElement("div");
            grid.className = "row g-2 align-items-end";

            const key = createInput("Output field name", "col-md-3", value.key, "e.g. activeNodes");
            key.input.dataset.property = "key";
            key.input.required = true;

            const pattern = createInput("Regular expression", "col-md-5", value.pattern, "e.g. active_nodes=(\\d+)");
            pattern.input.dataset.property = "pattern";
            pattern.input.required = true;

            const groupWrap = document.createElement("div");
            groupWrap.className = "col-md-1";
            const groupLabel = document.createElement("label");
            groupLabel.className = "form-label";
            groupLabel.textContent = "Group";
            const group = document.createElement("input");
            group.type = "number";
            group.min = "0";
            group.step = "1";
            group.className = "form-control form-control-sm";
            group.value = value.group == null ? 1 : value.group;
            group.dataset.property = "group";
            groupLabel.appendChild(group);
            groupWrap.appendChild(groupLabel);

            const typeWrap = document.createElement("div");
            typeWrap.className = "col-md-2";
            const typeLabel = document.createElement("label");
            typeLabel.className = "form-label";
            typeLabel.textContent = "Value type";
            const valueType = document.createElement("select");
            valueType.className = "form-select form-select-sm";
            valueType.dataset.property = "type";
            [
                ["", "Text (default)"],
                ["string", "String"],
                ["number", "Number"],
                ["integer", "Integer"],
                ["percentage", "Percentage"]
            ].forEach(function (optionData) {
                const option = document.createElement("option");
                option.value = optionData[0];
                option.textContent = optionData[1];
                valueType.appendChild(option);
            });
            valueType.value = value.type == null ? "" : String(value.type);
            typeLabel.appendChild(valueType);
            typeWrap.appendChild(typeLabel);

            const actions = document.createElement("div");
            actions.className = "col-md-1 d-flex justify-content-end";
            const remove = document.createElement("button");
            remove.type = "button";
            remove.className = "btn btn-outline-danger btn-sm";
            remove.setAttribute("aria-label", "Remove extraction field");
            remove.title = "Remove field";
            const icon = document.createElement("i");
            icon.className = "bi bi-trash";
            remove.appendChild(icon);
            remove.addEventListener("click", function () {
                row.remove();
                updateRegexEmpty();
                syncFromControls();
            });
            actions.appendChild(remove);

            const requiredWrap = document.createElement("div");
            requiredWrap.className = "col-12";
            const requiredCheck = document.createElement("input");
            requiredCheck.type = "checkbox";
            requiredCheck.className = "form-check-input me-2";
            requiredCheck.id = "resultParserRegexRequired" + (regexFields.children.length + 1);
            requiredCheck.dataset.property = "required";
            requiredCheck.checked = value.required === true;
            const requiredLabel = document.createElement("label");
            requiredLabel.className = "form-check-label small";
            requiredLabel.htmlFor = requiredCheck.id;
            requiredLabel.textContent = "Required field (fail parsing if no match is found)";
            requiredWrap.appendChild(requiredCheck);
            requiredWrap.appendChild(requiredLabel);

            [key.wrapper, pattern.wrapper, groupWrap, typeWrap, actions, requiredWrap].forEach(function (element) {
                grid.appendChild(element);
            });
            row.appendChild(grid);
            regexFields.appendChild(row);

            row.querySelectorAll("input, select").forEach(function (element) {
                element.addEventListener("input", syncFromControls);
                element.addEventListener("change", syncFromControls);
            });
            updateRegexEmpty();
        }

        function updateRegexEmpty() {
            regexEmpty.classList.toggle("d-none", regexFields.children.length > 0);
        }

        function loadConfig(config) {
            currentConfig = config && typeof config === "object" && !Array.isArray(config)
                ? Object.assign({}, config) : {};
            let type = String(currentConfig.type || "AUTO").toUpperCase();
            if (!["AUTO", "JSON", "DELIMITED", "KEY_VALUE", "REGEX"].includes(type)) type = "AUTO";
            typeSelect.value = type;

            setDelimiter(currentConfig.delimiter == null ? "|" : String(currentConfig.delimiter));
            setChecked("resultParserHeaderRow", currentConfig.headerRow !== false);
            setChecked("resultParserTrim", currentConfig.trim !== false);
            setChecked("resultParserIgnoreBlankLines", currentConfig.ignoreBlankLines !== false);
            setValue("resultParserCommentPrefix", currentConfig.commentPrefix || "");

            setKeyValueSeparator(currentConfig.separator == null ? ":" : String(currentConfig.separator));
            setValue("resultParserIgnorePrefixes",
                Array.isArray(currentConfig.ignorePrefixes) ? currentConfig.ignorePrefixes.join("\n") : "");

            regexFields.replaceChildren();
            if (Array.isArray(currentConfig.fields)) {
                currentConfig.fields.forEach(addRegexRow);
            }
            updateRegexEmpty();
            setSectionVisibility();
            advancedJson.value = JSON.stringify(currentConfig, null, 2);
            showError("");
        }

        function readRegexFields() {
            const fields = [];
            for (const row of regexFields.querySelectorAll("[data-regex-field]")) {
                const value = {};
                row.querySelectorAll("[data-property]").forEach(function (element) {
                    const property = element.dataset.property;
                    if (property === "required") {
                        if (element.checked) value.required = true;
                    } else if (property === "group") {
                        if (element.value !== "") value.group = Number(element.value);
                    } else if (element.value.trim() !== "") {
                        value[property] = element.value.trim();
                    }
                });
                fields.push(value);
            }
            return fields;
        }

        function buildConfig() {
            const config = Object.assign({}, currentConfig);
            managedKeys.forEach(function (key) { delete config[key]; });
            const type = typeSelect.value;
            config.type = type;

            if (type === "DELIMITED") {
                config.delimiter = getDelimiter();
                config.headerRow = byId("resultParserHeaderRow").checked;
                config.trim = byId("resultParserTrim").checked;
                config.ignoreBlankLines = byId("resultParserIgnoreBlankLines").checked;
                const commentPrefix = byId("resultParserCommentPrefix").value;
                if (commentPrefix) config.commentPrefix = commentPrefix;
                if (config.types && typeof config.types !== "object") delete config.types;
            } else if (type === "KEY_VALUE") {
                config.separator = getKeyValueSeparator();
                const prefixes = byId("resultParserIgnorePrefixes").value
                    .split(/\r?\n/).map(function (prefix) { return prefix.trim(); }).filter(Boolean);
                if (prefixes.length) config.ignorePrefixes = prefixes;
                if (config.types && typeof config.types !== "object") delete config.types;
            } else if (type === "REGEX") {
                config.fields = readRegexFields();
                delete config.types;
            } else {
                delete config.types;
            }
            return config;
        }

        function syncFromControls() {
            setSectionVisibility();
            const config = buildConfig();
            currentConfig = config;
            storedConfig.value = JSON.stringify(config);
            advancedJson.value = JSON.stringify(config, null, 2);
            showError("");
            if (advancedIsOpen) {
                advancedMessage.textContent = "Advanced JSON reflects the current form settings. Edit it and select Apply JSON to load changes.";
            }
        }

        function validateConfig(config) {
            if (config.type === "DELIMITED" && (!config.delimiter || !String(config.delimiter).length)) {
                return "Enter a delimiter for delimited output.";
            }
            if (config.type === "KEY_VALUE" && (!config.separator || !String(config.separator).length)) {
                return "Choose or enter a key/value separator.";
            }
            if (config.type === "REGEX") {
                if (!Array.isArray(config.fields) || config.fields.length === 0) {
                    return "Add at least one field to extract from the text response.";
                }
                for (const field of config.fields) {
                    if (!field.key || !field.pattern) return "Each extraction field needs a field name and regular expression.";
                    if (field.group != null && (!Number.isInteger(field.group) || field.group < 0)) {
                        return "Capture group must be a whole number greater than or equal to zero.";
                    }
                    try {
                        new RegExp(field.pattern, "m");
                    } catch (error) {
                        return "Invalid regular expression for field '" + field.key + "'.";
                    }
                }
            }
            return null;
        }

        typeSelect.addEventListener("change", syncFromControls);
        [
            "resultParserHeaderRow", "resultParserTrim", "resultParserIgnoreBlankLines",
            "resultParserCommentPrefix", "resultParserIgnorePrefixes", "resultParserCustomDelimiter",
            "resultParserCustomKeyValueSeparator"
        ].forEach(function (id) {
            const element = byId(id);
            if (element) {
                element.addEventListener("input", syncFromControls);
                element.addEventListener("change", syncFromControls);
            }
        });
        delimiterPreset.addEventListener("change", syncFromControls);
        keyValueSeparator.addEventListener("change", syncFromControls);
        addRegexField.addEventListener("click", function () {
            addRegexRow({});
            syncFromControls();
        });

        toggleAdvanced.addEventListener("click", function () {
            advancedIsOpen = !advancedIsOpen;
            advancedOptions.classList.toggle("d-none", !advancedIsOpen);
            toggleAdvanced.setAttribute("aria-expanded", String(advancedIsOpen));
            toggleAdvanced.textContent = advancedIsOpen ? "Hide advanced JSON" : "Advanced JSON";
            if (advancedIsOpen) advancedJson.value = JSON.stringify(currentConfig, null, 2);
        });

        applyAdvanced.addEventListener("click", function () {
            try {
                const parsed = JSON.parse(advancedJson.value || "{}");
                if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
                    throw new Error("The configuration must be a JSON object.");
                }
                const type = String(parsed.type || "AUTO").toUpperCase();
                if (!["AUTO", "JSON", "DELIMITED", "KEY_VALUE", "REGEX"].includes(type)) {
                    throw new Error("Unsupported parser type: " + type);
                }
                loadConfig(parsed);
                syncFromControls();
                advancedMessage.textContent = "JSON applied to the form.";
                showError("");
            } catch (error) {
                showError(error.message || "Unable to parse the advanced JSON configuration.");
                advancedMessage.textContent = "Fix the JSON and select Apply JSON again.";
            }
        });

        form.addEventListener("submit", function (event) {
            const config = buildConfig();
            const validationMessage = validateConfig(config);
            if (validationMessage) {
                event.preventDefault();
                showError(validationMessage);
                if (validationMessage.includes("regular expression")) {
                    if (typeSelect.value === "REGEX") {
                        regexOptions.classList.remove("d-none");
                    }
                }
                configError.scrollIntoView({ behavior: "smooth", block: "nearest" });
                return;
            }
            currentConfig = config;
            storedConfig.value = JSON.stringify(config);
        });

        let initialConfig = {};
        try {
            const parsed = JSON.parse(storedConfig.value || "{}");
            if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) initialConfig = parsed;
        } catch (error) {
            showError("The saved parser configuration is invalid JSON. Review it under Advanced JSON.");
            advancedJson.value = storedConfig.value || "";
        }
        loadConfig(initialConfig);
        syncFromControls();
    });
})();