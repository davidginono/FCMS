(() => {
    const form = document.querySelector('form[data-csv-import]');
    if (!form || !window.fetch || !window.AbortController) return;
    const file = form.querySelector('input[type=file]');
    const fieldError = form.querySelector('[data-import-file-error]');
    const formError = form.querySelector('[data-import-form-error]');
    let pending = false;
    const clear = () => {
        fieldError.textContent = ''; fieldError.hidden = true; fieldError.classList.remove('fcms-error');
        formError.textContent = ''; formError.hidden = true; file.removeAttribute('aria-invalid');
    };
    file.addEventListener('change', clear);
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (pending || !form.reportValidity()) return;
        const target = new URL(form.action, location.href);
        if (target.origin !== location.origin) return;
        const body = new FormData(form), controller = new AbortController();
        const buttons = [...form.querySelectorAll('button[type=submit]')].filter(button => !button.disabled);
        const fileWasDisabled = file.disabled;
        pending = true; clear(); form.setAttribute('aria-busy', 'true');
        buttons.forEach(button => button.disabled = true); file.disabled = true;
        const timeout = setTimeout(() => controller.abort(), 30000);
        try {
            const csrf = form.querySelector('input[name="_csrf"]');
            const headers = {Accept: 'text/html'};
            if (csrf && form.dataset.csrfHeader) headers[form.dataset.csrfHeader] = csrf.value;
            const response = await fetch(target, {method: 'POST', body, headers, credentials: 'same-origin', signal: controller.signal});
            const destination = new URL(response.url);
            if (response.ok && response.redirected && destination.origin === location.origin &&
                destination.pathname.startsWith(target.pathname + '/') &&
                /^[0-9a-f-]{36}$/.test(destination.pathname.split('/').at(-1))) {
                form.dispatchEvent(new Event('fcms:form-saved'));
                location.assign(destination.href); return;
            }
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            const error = page.querySelector('[data-import-file-error]')?.textContent.trim();
            const global = page.querySelector('[data-import-form-error]')?.textContent.trim();
            if (error) {
                fieldError.textContent = error; fieldError.hidden = false; fieldError.classList.add('fcms-error');
                file.setAttribute('aria-invalid', 'true');
            } else {
                formError.textContent = global || (response.status === 403 ? form.dataset.denied : form.dataset.unavailable);
                formError.hidden = false; formError.focus();
            }
        } catch {
            formError.textContent = form.dataset.unavailable; formError.hidden = false; formError.focus();
        } finally {
            clearTimeout(timeout); pending = false; form.removeAttribute('aria-busy');
            buttons.forEach(button => button.disabled = false); file.disabled = fileWasDisabled;
            if (file.getAttribute('aria-invalid') === 'true') file.focus();
        }
    });
})();
