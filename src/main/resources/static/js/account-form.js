/* Parent metadata controls the form; the accounting service revalidates every submission. */
(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => window.addEventListener('load',callback,{once:true}));
    ready(() => {
        const parent = document.querySelector('select#parentId');
        if (!parent || parent.dataset.coaFormReady) return;
        parent.dataset.coaFormReady = 'true';
        const form = parent.form, normal = form.querySelector('select#normalBalance');
        const cash = form.querySelector('input[type=checkbox][name=cashAccount]'), code = form.elements.code;
        const status = form.querySelector('[data-code-status]');
        let request, serial = 0, lastSuggested = code.value;
        const sync = changed => {
            const option = parent.selectedOptions[0], posting = option?.dataset.depth === '3';
            normal.disabled = !posting;
            if ((changed || !posting) && option?.dataset.normalBalance) normal.value = option.dataset.normalBalance;
            cash.disabled = !posting || option.dataset.classification !== 'ASSET';
            if (cash.disabled) cash.checked = false;
            window.refreshEnhancedSelects?.();
        };
        const suggest = async () => {
            const current = ++serial;request?.abort();status.textContent = '';
            if (!parent.value || code.value && code.value !== lastSuggested) return;
            const value = code.value, selectedParent = parent.value;
            request = new AbortController();const pending = request;
            const url = new URL(parent.dataset.codeUrl,location.href);
            url.searchParams.set('parentId',selectedParent);url.searchParams.set('mode',form.elements.mode.value);
            const timeout = setTimeout(() => pending.abort(),10000);
            try {
                const response = await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'},signal:pending.signal});
                if (!response.ok || response.redirected) throw Error('suggestion');
                const result = await response.json();
                if (current !== serial || !parent.isConnected || parent.value !== selectedParent || code.value !== value) return;
                code.value = result.code;lastSuggested = result.code;
                status.textContent = result.code ? '' : status.dataset.unavailable;
            } catch (error) {
                if (current === serial && parent.isConnected) status.textContent = status.dataset.error;
            } finally {clearTimeout(timeout);}
        };
        parent.addEventListener('change',() => {sync(true);void suggest();});
        code.addEventListener('input',() => {serial++;request?.abort();status.textContent = '';});
        form.addEventListener('submit',() => {serial++;request?.abort();});
        window.addEventListener('pagehide',() => {serial++;request?.abort();},{once:true});
        sync(false);
    });
})();
