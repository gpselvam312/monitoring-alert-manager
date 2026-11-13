document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('.alert-rule-form[data-readonly="true"]');
    if (!form) return;

    form.classList.add('alert-rule-readonly');
    form.setAttribute('aria-readonly', 'true');

    // Keep the shared form's navigation and non-mutating preview controls usable.
    const isSafeButton = button => button.matches(
        '#alertRuleTabs [data-bs-toggle="tab], ' +
        '#presentationPreviewTabs [data-preview-channel], ' +
        '.accordion-button[data-bs-toggle="collapse], ' +
        '#refreshPresentationPreview'
    );

    form.querySelectorAll('input, select, textarea').forEach(control => {
        if (control.id === 'presentationPreviewSeverity') return;
        control.disabled = true;
        control.setAttribute('aria-readonly', 'true');
    });

    form.querySelectorAll('button').forEach(button => {
        if (isSafeButton(button)) return;
        button.hidden = true;
        button.disabled = true;
    });

    form.addEventListener('submit', event => event.preventDefault());
});
