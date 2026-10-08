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

            container.innerHTML =
                '<div class="p-4 text-muted text-center">' +
                'Loading...' +
                '</div>';

            const params = new URLSearchParams({
                page: page,
                size: size,
                search: search
            });

			if (config.tabId) {
			    params.set('tabId', config.tabId);
			}
            if (config.sort) {

                params.set(
                    'sort',
                    config.sort
                );

                params.set(
                    'direction',
                    config.direction
                );
            }

            fetch(config.endpoint + '?' + params.toString())
                .then(function (response) {

                    if (!response.ok) {

                        throw new Error(
                            'Unable to load table data.'
                        );

                    }

                    return response.text();

                })
                .then(function (html) {

                    container.innerHTML = html;

                    DataTable.updateSortIndicators(config);
					DataTable.updateCompactActions(config);

                })
                .catch(function (error) {

                    console.error(error);

                    container.innerHTML =
                        '<div class="p-4 text-danger">' +
                        'Unable to load data.' +
                        '</div>';

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

