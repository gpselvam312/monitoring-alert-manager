(function () {

    window.FormSubmitLoader = {

        show: function (form) {

            if (!form) {
                return;
            }

            const buttons = form.querySelectorAll(
                'button[type="submit"], input[type="submit"]'
            );

            buttons.forEach(function (button) {

                if (button.disabled) {
                    return;
                }

                const loadingText =
                    getLoadingText(button, form);

                button.disabled = true;

                if (button.tagName === 'BUTTON') {

                    button.dataset.submitLoaderOriginalHtml =
                        button.innerHTML;

                    button.innerHTML =
                        '<span class="spinner-border spinner-border-sm me-2" ' +
                        'role="status" aria-hidden="true"></span>' +
                        loadingText;

                } else {

                    button.dataset.submitLoaderOriginalValue =
                        button.value;

                    button.value = loadingText;
                }
            });
        }
    };


	function getLoadingText(button, form) {

	    if (button.dataset.loadingText) {
	        return button.dataset.loadingText;
	    }

	    const action =
	        (form.dataset.appAction || '').toLowerCase();

	    switch (action) {

	        case 'delete':
	            return 'Deleting...';

	        case 'enable':
	            return 'Enabling...';

	        case 'disable':
	            return 'Disabling...';
	    }

	    /*
	     * For buttons such as Enable / Disable where the action
	     * is represented by the button itself.
	     */
	    const buttonAction =
	        (
	            button.getAttribute('aria-label') ||
	            button.getAttribute('title') ||
	            button.textContent
	        ).trim().toLowerCase();

	    if (buttonAction === 'delete') {
	        return 'Deleting...';
	    }

	    if (buttonAction === 'enable') {
	        return 'Enabling...';
	    }

	    if (buttonAction === 'disable') {
	        return 'Disabling...';
	    }

	    if (buttonAction === 'submit') {
	        return 'Submitting...';
	    }

	    if (buttonAction === 'update') {
	        return 'Updating...';
	    }

	    if (buttonAction === 'create') {
	        return 'Creating...';
	    }

	    if (buttonAction === 'add') {
	        return 'Adding...';
	    }

	    return 'Saving...';
	}

})();