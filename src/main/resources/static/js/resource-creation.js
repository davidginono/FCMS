/* Used by the shared form owner; never registers a second submit handler. */
(() => {
    const handles = (form, button) => form.dataset.resourceCreate === 'true' && Boolean(window.WorkspaceNavigation?.present)
        && !['addItem', 'removeItem'].includes(button?.name) && (button?.name !== 'operation' || button.value === 'save');
    const sameOrigin = url => new URL(url, location.href).origin === location.origin;
    const actions = form => Array.from(form.querySelectorAll('.fcms-form-actions')).at(-1);
    function message(form, key, href) {
        form.querySelector('[data-creation-feedback]')?.remove();
        const alert = document.createElement('div');
        alert.className = 'fcms-alert'; alert.role = 'alert'; alert.tabIndex = -1;
        alert.dataset.messageType = ['formUncertain','formExpired','formChanged'].includes(key) ? 'warning' : 'error';
        alert.dataset.creationFeedback = 'true'; alert.textContent = document.body.dataset[key];
        if (href) {
            const link = document.createElement('a'); link.href = href;
            if (key === 'formExpired' || key === 'formChanged') {
                link.target = '_blank'; link.rel = 'noopener'; link.dataset.nativeNavigation = 'true';
                link.textContent = document.body.dataset.formSignIn;
            } else link.textContent = document.body.dataset.formCheckList;
            alert.append(' ', link);
        }
        (actions(form) || form.lastElementChild)?.before(alert); alert.focus();
    }
    function retainErrors(form, incoming) {
        form.querySelectorAll('.fcms-error,.fcms-alert').forEach(error => error.remove());
        for (const error of incoming.querySelectorAll('.fcms-error')) {
            const scope = error.closest('.fcms-field,[data-error-scope]');
            const name = scope?.querySelector('input:not([type="hidden"])[name],select[name],textarea[name]')?.name;
            const control = Array.from(form.elements).find(element => element.name === name);
            const group = scope?.dataset.errorScope;
            const target = group ? Array.from(form.querySelectorAll('[data-error-scope]')).find(element => element.dataset.errorScope === group) : control?.closest('.fcms-field');
            if (target) {
                const copy = document.createElement('span'); copy.className = 'fcms-error'; copy.textContent = error.textContent;
                const hint = target.querySelector('.fcms-field-hint'); if (hint) hint.before(copy); else target.append(copy);
            } else {
                const copy = document.createElement('div'); copy.className = 'fcms-alert'; copy.role = 'alert'; copy.textContent = error.textContent;
                (actions(form) || form.lastElementChild)?.before(copy);
            }
        }
        for (const error of incoming.querySelectorAll('.fcms-alert')) {
            const copy = document.createElement('div'); copy.className = 'fcms-alert'; copy.role = 'alert'; copy.textContent = error.textContent;
            if (error.dataset.messageType) copy.dataset.messageType = error.dataset.messageType;
            (actions(form) || form.lastElementChild)?.before(copy);
        }
        // Rotate CSRF only. Native controls, passwords, uploads and request keys stay live.
        for (const token of incoming.querySelectorAll('input[type="hidden"][name="_csrf"]'))
            Array.from(form.elements).filter(element => element.name === token.name).forEach(element => element.value = token.value);
        form.dispatchEvent(new CustomEvent('fcms:form-errors'));
    }
    async function submit(form, button, owner) {
        const settings = document.body.dataset;
        const probe = new AbortController(); let commandStarted = false, changed = false;
        const onChange = () => { changed = true; if (!commandStarted) probe.abort(); };
        form.addEventListener('input', onChange); form.addEventListener('change', onChange);
        const loading = window.AppLoading?.action(button);
        let postTimeout;
        const timeout = setTimeout(() => probe.abort(), 10000);
        try {
            const session = await fetch(settings.formSessionUrl, {credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},signal:probe.signal});
            if (changed) { owner.recover(); return; }
            if (session.status === 403) { owner.recover(); message(form,'formForbidden'); return; }
            if (session.status === 401 || session.redirected || !session.headers.get('content-type')?.includes('application/json')) {
                owner.recover(); message(form, 'formExpired', settings.formLoginUrl); return;
            }
            if (!session.ok) { owner.recover(); message(form, session.status === 403 ? 'formForbidden' : 'formUnavailable'); return; }
            const identity = await session.json();
            if (changed) { owner.recover(); return; }
            if (String(identity.userId) !== settings.formActor) { owner.recover(); message(form,'formChanged',settings.formLoginUrl); return; }
            if (typeof identity.token !== 'string' || identity.token.length > 2048 || !identity.token) throw new Error('Unavailable session');
            const tokens = Array.from(form.elements).filter(element => element.type === 'hidden' && element.name === identity.parameterName);
            if (!tokens.length) throw new Error('Missing CSRF control');
            tokens.forEach(token => token.value = identity.token);
            // Named action controls can mask the HTMLFormElement.action property.
            const destination = new URL(button?.getAttribute('formaction') || form.getAttribute('action') || location.href,location.href).href;
            if (!sameOrigin(destination)) throw new Error('Invalid destination');
            const data = new FormData(form);
            const body = form.enctype === 'multipart/form-data' ? data : new URLSearchParams(Array.from(data, ([key,value]) => [key, typeof value === 'string' ? value : value.name]));
            clearTimeout(timeout); commandStarted = true;
            // A failed transport after this point has an unknown outcome: never replay it.
            const command = new AbortController();
            postTimeout = setTimeout(() => command.abort(), 45000);
            const response = await fetch(destination, {method:'POST',credentials:'same-origin',cache:'no-store',headers:{Accept:'text/html'},body,signal:command.signal});
            if (response.status === 413) {
                owner.recover();
                for (const file of form.querySelectorAll('input[type="file"]')) {
                    const scope = file.closest('.fcms-field'); if (!scope) continue;
                    scope.querySelectorAll('.fcms-error').forEach(error => error.remove());
                    const error = document.createElement('span'); error.className = 'fcms-error'; error.textContent = settings.formTooLarge; scope.append(error);
                }
                form.dispatchEvent(new CustomEvent('fcms:form-errors')); return;
            }
            if (!sameOrigin(response.url || destination) || !response.headers.get('content-type')?.includes('text/html')) throw new Error('Unknown outcome');
            const page = new DOMParser().parseFromString(await response.clone().text(), 'text/html');
            const notice = page.querySelector('[data-resource-notice]');
            if (response.ok && response.redirected && notice && ['success','info'].includes(notice.dataset.messageType) && page.querySelector('body.fcms-shell main')) {
                owner.committed();
                if (!changed) await window.WorkspaceNavigation.present(response);
                if (form.isConnected) {
                    const copy = document.importNode(notice, true); form.before(copy); copy.focus();
                }
                return;
            }
            const incoming = Array.from(page.querySelectorAll('form.fcms-form')).find(candidate => new URL(candidate.getAttribute('action') || response.url, response.url).pathname === new URL(destination,location.href).pathname);
            if (incoming && response.ok && button?.name === 'action' && button.value === 'preview' && form.matches('[data-voucher-form]') && incoming.querySelector('[data-voucher-preview]')) {
                owner.recover(); retainErrors(form,incoming);
                form.querySelector('[data-voucher-preview]')?.remove();
                if (!changed) {
                    const preview=document.importNode(incoming.querySelector('[data-voucher-preview]'),true);
                    actions(form).before(preview); preview.focus();
                }
                return;
            }
            if (incoming && [200,400,409,422].includes(response.status) && incoming.querySelector('.fcms-error,.fcms-alert')) {
                owner.recover(); retainErrors(form,incoming); return;
            }
            if (response.status === 403) { owner.recover(); message(form,'formForbidden'); return; }
            throw new Error('Unknown outcome');
        } catch (error) {
            owner.recover();
            if (!commandStarted && changed) return;
            if (commandStarted) {
                form.dataset.resourceUncertain = 'true';
                form.querySelectorAll('button[type="submit"],button:not([type])').forEach(control => control.disabled = true);
                const list = actions(form)?.querySelector('a[href]')?.href;
                message(form,'formUncertain',list);
            } else message(form,'formUnavailable');
        } finally {
            clearTimeout(timeout); clearTimeout(postTimeout); form.removeEventListener('input',onChange); form.removeEventListener('change',onChange);
            loading?.();
        }
    }
    window.ResourceCreation = {handles,submit};
})();
