/* Verify a user-form session before handing its one native POST to the shared form owner. */
(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => window.addEventListener('load', callback, {once:true}));
    ready(() => {
        const form = document.querySelector('form[data-form-session-url]');
        const feedback = document.querySelector('[data-form-session-feedback]');
        if (!form || !feedback || form.dataset.formSessionReady) return;
        if (form.dataset.resourceCreate === 'true') return;
        form.dataset.formSessionReady = 'true';
        const message = feedback.querySelector('[data-form-session-message]');
        const signIn = feedback.querySelector('[data-form-session-signin]');
        let pending, serial = 0, nativeReady = false;
        const show = (kind, authenticate = false) => {
            message.textContent = feedback.dataset[kind];
            signIn.hidden = !authenticate;
            feedback.hidden = false;
            feedback.querySelector('[role="alert"]').dataset.messageType = authenticate ? 'warning' : 'error';
            feedback.querySelector('[role="alert"]').focus();
        };
        const cancel = () => {
            serial++;
            pending?.controller.abort();
            pending?.finish();
            pending = null;
        };
        form.addEventListener('submit', async event => {
            if (nativeReady) {nativeReady = false; return;}
            event.preventDefault();
            if (pending || form.getAttribute('aria-busy') === 'true') return;
            const submitter = event.submitter;
            if (submitter?.disabled) return;
            feedback.hidden = true;
            const owner = ++serial, controller = new AbortController();
            const finish = window.AppLoading?.action(submitter) || (() => {});
            pending = {controller, finish};
            const timeout = setTimeout(() => controller.abort(), 10000);
            try {
                const response = await fetch(form.dataset.formSessionUrl, {
                    credentials:'same-origin', cache:'no-store',
                    headers:{Accept:'application/json'}, signal:controller.signal
                });
                if (owner !== serial || !form.isConnected) return;
                const json = response.headers.get('Content-Type')?.includes('application/json');
                if (response.redirected || response.status === 401 || response.ok && !json) {
                    show('expired', true); return;
                }
                if (response.status === 403) {show('forbidden'); return;}
                if (!response.ok || !json) throw new Error('Session unavailable');
                const session = await response.json();
                if (owner !== serial || !form.isConnected) return;
                if (session.userId !== form.dataset.formSessionUser) {show('changed', true); return;}
                if (typeof session.parameterName !== 'string' || typeof session.token !== 'string'
                        || !session.token || session.token.length > 2048) throw new Error('Session response');
                const tokens = [...form.querySelectorAll('input[type="hidden"]')].filter(input => input.name === session.parameterName);
                if (!tokens.length) throw new Error('Session field');
                for (const input of tokens) {input.value = session.token; input.defaultValue = session.token;}
                finish(); pending = null;
                nativeReady = true;
                try {form.requestSubmit(submitter);} finally {nativeReady = false;}
            } catch (error) {
                if (owner === serial && form.isConnected) show('unavailable');
            } finally {
                clearTimeout(timeout);
                if (owner === serial) {pending?.finish(); pending = null;}
            }
        });
        // A changed draft or navigation cancels verification, never submits a later version silently.
        form.addEventListener('input', () => {if (pending) cancel();});
        form.addEventListener('change', () => {if (pending) cancel();});
        form.addEventListener('reset', cancel);
        document.addEventListener('workspace:before-navigate', cancel);
        document.addEventListener('fcms:before-close', cancel);
        document.addEventListener('session:logout', cancel);
        window.addEventListener('pagehide', cancel);
    });
})();
