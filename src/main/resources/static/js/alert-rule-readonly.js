document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('.alert-rule-form[data-readonly="true"]');
    if (!form) return;

    form.classList.add('alert-rule-readonly');
    form.setAttribute('aria-readonly', 'true');

    // Navigation and disclosure controls remain usable in read-only mode.
    const isNavigationButton = button => button.matches(
        '#alertRuleTabs [data-bs-toggle="tab"], ' +
        '#presentationPreviewTabs [data-preview-channel], ' +
        '.accordion-button[data-bs-toggle="collapse]'
    );

    const lockControl = control => {
        if (control instanceof HTMLButtonElement && isNavigationButton(control)) return;
        control.disabled = true;
        control.setAttribute('aria-disabled', 'true');
        control.setAttribute('aria-readonly', 'true');
        control.tabIndex = -1;
    };

    const lockFormContent = root => {
        root.querySelectorAll('input, select, textarea, button').forEach(lockControl);
    };

    // Lock all editable fields and actions, while preserving tab switching
    // and accordion expand/collapse for reviewing the saved configuration.
    lockFormContent(form);

    const observer = new MutationObserver(mutations => {
        mutations.forEach(mutation => {
            mutation.addedNodes.forEach(node => {
                if (!(node instanceof Element)) return;
                lockControl(node);
                lockFormContent(node);
            });
        });
    });
    observer.observe(form, {childList: true, subtree: true});

    form.addEventListener('click', event => {
        const target = event.target instanceof Element ? event.target : null;
        if (!target) return;
        const button = target.closest('button');
        if (button && isNavigationButton(button)) return;
        if (target.closest('button, input, select, textarea')) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);

    form.addEventListener('submit', event => {
        event.preventDefault();
        event.stopImmediatePropagation();
    }, true);
});
