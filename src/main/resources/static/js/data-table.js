(function () {

    window.DataTable = {

        configs: {},

        register: function (name, config) {

            if (!name || !config) {
                return;
            }

            if (config.sort === undefined) {
                config.sort = '';
            }

            if (config.direction !== 'asc' &&
                config.direction !== 'desc') {

                config.direction = 'asc';
            }

            config._requestSequence = config._requestSequence || 0;
            config._activeRequest = config._activeRequest || null;
            DataTable.configs[name] = config;
        },

        init: function (name, config) {

            if (!name || !config) {
                return;
            }

            DataTable.register(name, config);
			DataTable.updateSortIndicators(config);
			DataTable.updateCompactActions(config);

            const searchInput =
                document.getElementById(config.searchId);

            const pageSizeSelect =
                document.getElementById(config.pageSizeId);

            const clearButton =
                document.getElementById(config.clearSearchId);

            let searchTimer;

            if (searchInput) {

                searchInput.addEventListener('input', function () {

                    clearTimeout(searchTimer);

                    if (clearButton) {

                        clearButton.classList.toggle(
                            'd-none',
                            this.value.trim() === ''
                        );

                    }

                    searchTimer = setTimeout(function () {

                        DataTable.load(
                            config,
                            0,
                            pageSizeSelect
                                ? Number(pageSizeSelect.value)
                                : 5
                        );

                    }, 1000);

                });

            }

            if (clearButton) {

                clearButton.addEventListener('click', function () {

                    clearTimeout(searchTimer);

                    if (searchInput) {
                        searchInput.value = '';
                    }

                    this.classList.add('d-none');

                    DataTable.load(
                        config,
                        0,
                        pageSizeSelect
                            ? Number(pageSizeSelect.value)
                            : 5
                    );

                });

            }

            if (pageSizeSelect) {

                pageSizeSelect.addEventListener('change', function () {

                    clearTimeout(searchTimer);

                    DataTable.load(
                        config,
                        0,
                        Number(this.value)
                    );

                });

            }

        },

        load: function (config, page, size) {

            const searchInput =
                document.getElementById(config.searchId);

            const container =
                document.getElementById(config.containerId);

            if (!container) {
                return;
            }

            const search =
                searchInput
                    ? searchInput.value.trim()
                    : '';

            if (config._activeRequest) {
                config._activeRequest.abort();
            }

            const requestController = new AbortController();
            const requestSequence = ++config._requestSequence;
            config._activeRequest = requestController;

            container.innerHTML =
                '<div class="p-4 text-muted text-center">Loading...</div>';

            const params = new URLSearchParams({
                page: page,
                size: size,
                search: search
            });

            if (config.tabId) {
                params.set('tabId', config.tabId);
            }
            if (config.filterIds && typeof config.filterIds === 'object') {
                Object.entries(config.filterIds).forEach(function (entry) {
                    const parameterName = entry[0];
                    const filter = document.getElementById(entry[1]);
                    if (filter && filter.value !== '') {
                        params.set(parameterName, filter.value);
                    }
                });
            }
            if (config.sort) {
                params.set('sort', config.sort);
                params.set('direction', config.direction);
            }

            fetch(config.endpoint + '?' + params.toString(), {
                signal: requestController.signal,
                headers: {
                    'Accept': 'text/html',
                    'X-Requested-With': 'XMLHttpRequest'
                }
            })
                .then(function (response) {
                    if (response.status === 401) {
                        window.location.assign('/login?expired');
                        throw new Error('Your session has expired.');
                    }
                    if (response.status === 403) {
                        throw new Error('You do not have permission to view this data.');
                    }
                    if (!response.ok) {
                        throw new Error('Unable to load table data.');
                    }
                    return response.text();
                })
                .then(function (html) {
                    if (requestSequence !== config._requestSequence) {
                        return;
                    }
                    container.innerHTML = html;
                    DataTable.updateSortIndicators(config);
                    DataTable.updateCompactActions(config);
                })
                .catch(function (error) {
                    if (error.name === 'AbortError' || requestSequence !== config._requestSequence) {
                        return;
                    }
                    console.error(error);
                    container.textContent = error.message === 'You do not have permission to view this data.'
                        ? error.message : 'Unable to load data.';
                    container.classList.add('text-danger');
                })
                .finally(function () {
                    if (requestSequence === config._requestSequence) {
                        config._activeRequest = null;
                    }
                });

        },

        sort: function (button) {

            const configName =
                button.dataset.config;

            const sortField =
                button.dataset.sort;

            const config =
                DataTable.configs[configName];

            if (!config || !sortField) {

                console.error(
                    'DataTable sorting configuration not found:',
                    configName
                );

                return;
            }

            if (config.sort === sortField) {

                config.direction =
                    config.direction === 'asc'
                        ? 'desc'
                        : 'asc';

            } else {

                config.sort = sortField;
                config.direction = 'asc';

            }

            const pageSizeSelect =
                document.getElementById(config.pageSizeId);

            DataTable.load(
                config,
                0,
                pageSizeSelect
                    ? Number(pageSizeSelect.value)
                    : 5
            );

        },
		updateSortIndicators: function (config) {

		    if (!config || !config.containerId) {
		        return;
		    }

		    const container =
		        document.getElementById(config.containerId);

		    if (!container) {
		        return;
		    }

		    const sortButtons =
		        container.querySelectorAll(
		            '.table-sort'
		        );

		    sortButtons.forEach(function (button) {

		        const icon =
		            button.querySelector(
		                '.table-sort-icon'
		            );

		        if (!icon) {
		            return;
		        }

		        const isActive =
		            button.dataset.sort === config.sort;

		        button.classList.toggle(
		            'active',
		            isActive
		        );

		        icon.className =
		            'bi table-sort-icon';

		        if (isActive) {

		            if (config.direction === 'desc') {

		                icon.classList.add(
		                    'bi-caret-down-fill'
		                );

		            } else {

		                icon.classList.add(
		                    'bi-caret-up-fill'
		                );

		            }

		            icon.setAttribute(
		                'aria-label',
		                config.direction === 'asc'
		                    ? 'Sorted ascending'
		                    : 'Sorted descending'
		            );

		        } else {

		            icon.classList.add(
		                'bi-caret-up-fill'
		            );

		            icon.setAttribute(
		                'aria-label',
		                'Sort ascending'
		            );

		        }

		    });

		},
		updateCompactActions: function (config) {

		    if (!config || !config.containerId) {
		        return;
		    }

		    const container =
		        document.getElementById(config.containerId);

		    if (!container) {
		        return;
		    }

		    const table =
		        container.querySelector('.app-table');

		    if (!table) {
		        return;
		    }

		    const wrapper =
		        table.closest('.table-responsive');

		    if (!wrapper) {
		        return;
		    }

		    const hasOverflow =
		        table.scrollWidth > wrapper.clientWidth;

		    table.classList.toggle(
		        'compact-actions',
		        hasOverflow
		    );
		},
        pagination: function (button) {

            const configName =
                button.dataset.config;

            const config =
                DataTable.configs[configName];

            if (!config) {

                console.error(
                    'DataTable configuration not registered:',
                    configName
                );

                return;
            }

            DataTable.load(
                config,
                Number(button.dataset.page),
                Number(button.dataset.size)
            );

        }

    };

})();

