document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('.alert-rule-form[data-readonly="true"]');
    if (!form) return;

    form.classList.add('alert-rule-readonly');
    form.setAttribute('aria-readonly', 'true');

    const lockControl = control => {
        control.disabled = true;
        control.setAttribute('aria-disabled', 'true');
        control.setAttribute('aria-readonly', 'true');
        control.tabIndex = -1;
    };

    const lockFormContent = root => {
        root.querySelectorAll('input, select, textarea, button').forEach(lockControl);
    };

    // Lock the complete form, including controls added later by the routing
    // and condition editors. The only navigation actions are outside the form.
    lockFormContent(form);

    const observer = new MutationObserver(mutations => {
        mutations.forEach(mutation => {
            mutation.addedNodes.forEach(node => {
                if (!(node instanceof Element)) return;
                if (node.matches('input, select, textarea, button')) lockControl(node);
                lockFormContent(node);
            });
        });
    });
    observer.observe(form, {childList: true, subtree: true});

    // Guard against delegated handlers or scripted controls that might still
    // attempt to open a lookup or mutate the read-only form.
    form.addEventListener('click', event => {
        if (event.target.closest('button, input, select, textarea')) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);
    form.addEventListener('submit', event => {
        event.preventDefault();
        event.stopImmediatePropagation();
    }, true);
});
