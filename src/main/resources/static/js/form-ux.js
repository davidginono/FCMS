(() => {
    const form = document.querySelector('.fcms-form');
    const modal = document.getElementById('fcms-discard-modal');
    if (!form || !modal) return;
    document.body.appendChild(modal);
    const panel = modal.querySelector('[role="dialog"]');
    let submitting = false, creating = false, pending, previousFocus, previousOverflow, previousRootOverflow;
    const disabledOnSubmit = new Set();
    const releaseSubmission = () => {
        submitting = false; creating = false; form.removeAttribute('aria-busy');
        disabledOnSubmit.forEach(button => button.disabled = false); disabledOnSubmit.clear();
        form.querySelectorAll('[data-fcms-submitter]').forEach(command => command.remove());
    };
    const values = () => JSON.stringify(Array.from(form.elements).filter(el => el.name && (el.type !== 'hidden' || form.hasAttribute('data-financial-form')) && el.type !== 'submit' && el.name !== 'action').map(el => [el.name, el.value, el.checked]));
    let initial = values();
    const currency = form.querySelector('[data-form-currency]');
    if (currency) {
        const syncCurrency = () => form.querySelectorAll('[data-form-currency-label]').forEach(label => label.textContent = currency.value);
        currency.addEventListener('change', syncCurrency);
        syncCurrency();
    }
    const errorSummary = form.querySelector('.fcms-alert');
    const errors = errorSummary?.textContent.trim();
    let restoredErrors = Boolean(errors) || Boolean(form.querySelector('.fcms-error')) || form.dataset.unsavedDraft === 'true';
    const dirty = () => restoredErrors || values() !== initial;
    document.addEventListener('fcms:form-defaults',event=>{if(event.detail?.form===form&&!restoredErrors)initial=values();});
    const close = () => {
        modal.hidden = true; modal.classList.remove('is-open');
        document.body.style.overflow = previousOverflow;
        document.documentElement.style.overflow = previousRootOverflow;
        previousFocus?.focus(); pending = null;
    };
    const confirmDiscard = action => {
        window.FcmsModalWorkspace?.sync();
        pending = action; previousFocus = document.activeElement; previousOverflow = document.body.style.overflow; previousRootOverflow = document.documentElement.style.overflow;
        modal.hidden = false; modal.classList.add('is-open'); document.body.style.overflow = 'hidden'; document.documentElement.style.overflow = 'hidden';
        modal.querySelector('.app-modal-actions [data-fcms-keep]').focus();
    };
    modal.querySelectorAll('[data-fcms-keep]').forEach(button => button.addEventListener('click', close));
    modal.querySelector('[data-fcms-discard]').addEventListener('click', () => { const action = pending; close(); submitting = true; action?.(); });
    modal.addEventListener('keydown', event => {
        if (event.key === 'Escape') { event.preventDefault(); close(); }
        if (event.key === 'Tab') {
            const buttons = Array.from(panel.querySelectorAll('button')), first = buttons[0], last = buttons.at(-1);
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    });
    document.addEventListener('fcms:before-close', event => {
        if (creating) { event.preventDefault(); return; }
        if (!submitting && dirty()) { event.preventDefault(); confirmDiscard(() => {
            form.reset();
            form.querySelectorAll('select').forEach(select => select.dispatchEvent(new Event('change', {bubbles:true})));
            initial = values(); restoredErrors = false; submitting = false; event.detail.close();
        }); }
    });
    document.addEventListener('click', event => {
        const link = event.target.closest('a[href]');
        if (creating && link && link.target !== '_blank' && !link.hasAttribute('download')) { event.preventDefault(); return; }
        if (!link || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || submitting || !dirty() || link.hasAttribute('download') || link.target === '_blank' || link.getAttribute('href').startsWith('#') || window.WorkspaceNavigation?.handles(link)) return;
        event.preventDefault(); confirmDiscard(() => location.assign(link.href));
    });
    document.addEventListener('workspace:before-navigate', event => {
        if (creating) { event.preventDefault(); return; }
        if (!submitting && dirty()) { event.preventDefault(); confirmDiscard(event.detail.resume); }
    });
    document.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        if (event.target === form) {
            if (submitting || form.dataset.resourceUncertain === 'true' || form.dataset.resourceCommitted === 'true') { event.preventDefault(); return; }
            submitting = true; form.setAttribute('aria-busy', 'true');
            // Preserve the chosen native command before disabling submit controls.
            if (event.submitter?.name) {
                const command = document.createElement('input');
                command.type = 'hidden'; command.name = event.submitter.name;
                command.value = event.submitter.value; command.dataset.fcmsSubmitter = 'true';
                form.appendChild(command);
            }
            form.querySelectorAll('button[type="submit"],button:not([type])').forEach(button => {
                if (!button.disabled) { disabledOnSubmit.add(button); button.disabled = true; }
            });
            if (window.ResourceCreation?.handles(form,event.submitter)) {
                event.preventDefault();
                creating = true;
                void window.ResourceCreation.submit(form,event.submitter,{
                    recover: () => { releaseSubmission(); restoredErrors = true; },
                    committed: () => {
                        releaseSubmission(); submitting = true; form.dataset.resourceCommitted = 'true';
                        form.querySelectorAll('button[type="submit"],button:not([type])').forEach(button => button.disabled = true);
                    }
                });
            }
        } else if (creating) { event.preventDefault();
        } else if (!submitting && dirty()) {
            event.preventDefault(); confirmDiscard(() => event.target.requestSubmit(event.submitter));
        }
    });
    // Security logout must never wait for a discard confirmation or beforeunload.
    document.addEventListener('session:logout', () => {
        submitting = true; creating = false;
        if (!modal.hidden) close();
    });
    form.addEventListener('fcms:form-saved', () => { submitting = true; });
    window.addEventListener('beforeunload', event => { if (creating || (!submitting && dirty())) { event.preventDefault(); event.returnValue = ''; } });
    window.addEventListener('pageshow', () => {
        releaseSubmission();
    });
    const associations = new Map();
    let errorSequence = 0;
    function decorateErrors(focus = false) {
        for (const [control, ids] of associations) {
            const retained = (control.getAttribute('aria-describedby') || '').split(/\s+/).filter(id => id && !ids.has(id));
            if (retained.length) control.setAttribute('aria-describedby', retained.join(' ')); else control.removeAttribute('aria-describedby');
            control.removeAttribute('aria-invalid');
        }
        associations.clear();
        let first;
        for (const error of form.querySelectorAll('.fcms-error')) {
            if (!error.textContent.trim() || error.hidden) continue;
            error.id ||= 'fcms-field-error-' + (++errorSequence);
            const scope = error.closest('.fcms-field,[data-error-scope]');
            const controls = scope?.querySelectorAll('input:not([type="hidden"]),select,textarea') || [];
            for (const control of controls) {
                const trigger = control.closest('.neo-select')?.querySelector('button');
                for (const target of [control, trigger].filter(Boolean)) {
                    target.setAttribute('aria-invalid','true');
                    const described = new Set((target.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean));
                    described.add(error.id); target.setAttribute('aria-describedby',[...described].join(' '));
                    if (!associations.has(target)) associations.set(target,new Set()); associations.get(target).add(error.id);
                }
                first ||= trigger || control;
            }
            if (!controls.length) { error.tabIndex = -1; first ||= error; }
            for (let parent = error.parentElement; parent && parent !== form; parent = parent.parentElement) {
                if (parent.matches('details')) parent.open = true;
                if (parent.matches('[role="tabpanel"][hidden]')) {
                    const tab = Array.from(document.querySelectorAll('[role="tab"]')).find(candidate => candidate.getAttribute('aria-controls') === parent.id);
                    tab?.click();
                }
            }
        }
        const alert = Array.from(form.querySelectorAll('.fcms-alert')).find(element => !element.hidden && element.textContent.trim());
        if (alert) alert.tabIndex = -1;
        if (focus) (first || alert)?.focus();
    }
    function validate(control) {
        if (!control.matches('input:not([type="hidden"]),select,textarea') || !control.willValidate) return;
        const scope = control.closest('.fcms-field'); if (!scope) return;
        let error = scope.querySelector('[data-client-error]');
        if (control.validity.valid) { error?.remove(); decorateErrors(); return; }
        if (!error) {
            error = document.createElement('span'); error.className = 'fcms-error'; error.dataset.clientError = 'true';
            const hint = scope.querySelector('.fcms-field-hint'); if (hint) hint.before(error); else scope.append(error);
        }
        error.textContent = control.validity.valueMissing ? document.body.dataset.formRequired : document.body.dataset.formInvalid;
        decorateErrors();
    }
    form.addEventListener('focusout', event => validate(event.target));
    form.addEventListener('input', event => { if (event.target.closest('.fcms-field')?.querySelector('[data-client-error]')) validate(event.target); });
    form.addEventListener('change', event => { if (event.target.closest('.fcms-field')?.querySelector('[data-client-error]')) validate(event.target); });
    form.addEventListener('invalid', event => { event.preventDefault(); validate(event.target); queueMicrotask(() => decorateErrors(true)); }, true);
    form.addEventListener('fcms:form-errors', () => { restoredErrors = true; decorateErrors(true); });
    form.addEventListener('reset', () => queueMicrotask(() => { form.querySelectorAll('[data-client-error]').forEach(error => error.remove()); decorateErrors(); }));
    decorateErrors(true);
})();
