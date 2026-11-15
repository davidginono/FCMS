(() => {
    const form = document.querySelector('.fcms-form');
    const modal = document.getElementById('fcms-discard-modal');
    if (!form || !modal) return;
    document.body.appendChild(modal);
    const panel = modal.querySelector('[role="dialog"]');
    let submitting = false, pending, previousFocus, previousOverflow, previousRootOverflow;
    const disabledOnSubmit = new Set();
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
        if (!submitting && dirty()) { event.preventDefault(); confirmDiscard(() => {
            form.reset();
            form.querySelectorAll('select').forEach(select => select.dispatchEvent(new Event('change', {bubbles:true})));
            const rows = form.querySelectorAll('.library-rule');
            rows.forEach((row, index) => { if (index > 0) row.remove(); });
            form.querySelectorAll('.library-remove').forEach(button => button.disabled = true);
            const add = form.querySelector('#library-add-rule'); if (add) add.disabled = false;
            initial = values(); restoredErrors = false; submitting = false; event.detail.close();
        }); }
    });
    document.addEventListener('click', event => {
        const link = event.target.closest('a[href]');
        if (!link || event.defaultPrevented || event.ctrlKey || event.metaKey || event.shiftKey || submitting || !dirty() || link.hasAttribute('download') || link.target === '_blank' || link.getAttribute('href').startsWith('#')) return;
        event.preventDefault(); confirmDiscard(() => location.assign(link.href));
    });
    document.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        if (event.target === form) {
            if (submitting) { event.preventDefault(); return; }
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
        } else if (!submitting && dirty()) {
            event.preventDefault(); confirmDiscard(() => event.target.requestSubmit(event.submitter));
        }
    });
    // Security logout must never wait for a discard confirmation or beforeunload.
    document.addEventListener('session:logout', () => {
        submitting = true;
        if (!modal.hidden) close();
    });
    form.addEventListener('fcms:form-saved', () => { submitting = true; });
    window.addEventListener('beforeunload', event => { if (!submitting && dirty()) { event.preventDefault(); event.returnValue = ''; } });
    window.addEventListener('pageshow', () => {
        submitting = false; form.removeAttribute('aria-busy');
        disabledOnSubmit.forEach(button => button.disabled = false); disabledOnSubmit.clear();
        form.querySelectorAll('[data-fcms-submitter]').forEach(command => command.remove());
    });
    form.querySelectorAll('.fcms-error').forEach((error,index)=>{if(!error.textContent.trim())return;const control=error.closest('.fcms-field')?.querySelector('input,select,textarea');if(!control)return;error.id='fcms-field-error-'+index;control.setAttribute('aria-invalid','true');const described=new Set((control.getAttribute('aria-describedby')||'').split(/\s+/).filter(Boolean));described.add(error.id);control.setAttribute('aria-describedby',[...described].join(' '));const trigger=control.closest('.neo-select')?.querySelector('button');if(trigger){trigger.setAttribute('aria-invalid','true');trigger.setAttribute('aria-describedby',[...described].join(' '));}const section=error.closest('details');if(section)section.open=true;});
    if(errorSummary)errorSummary.tabIndex=-1;
    const invalid=form.querySelector('[aria-invalid="true"]');
    if(invalid){const trigger=invalid.closest('.neo-select')?.querySelector('button');(trigger||invalid).focus();}
    else if(errors)errorSummary.focus();
})();
