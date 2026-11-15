/* Cloudscape grouped resources, composed with resource-table.js and server-owned queries. */
(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => window.addEventListener('load', callback, {once:true}));
    ready(() => document.querySelectorAll('[data-grouped-resources]').forEach(region => {
        if (region.dataset.groupedReady) return;
        region.dataset.groupedReady = 'true';
        region.classList.add('grouped-resources-ready');
        const groups = new Map(), requests = new Map();
        const all = region.querySelector('[data-grouped-select-all]');
        const open = region.querySelector('[data-grouped-open]');
        const status = region.querySelector('[data-grouped-status]');
        const number = new Intl.NumberFormat(document.documentElement.lang || 'en');
        let epoch = 0, criteria;
        const children = key => [...region.querySelectorAll('[data-account-group]')].filter(row => row.dataset.accountGroup === key);
        const count = group => group.all ? group.total - group.excluded.size : group.included.size;
        const selected = (group,id) => group.all ? !group.excluded.has(id) : group.included.has(id);
        const counter = (chosen,total) => '(' + (chosen ? number.format(chosen) + '/' : '') + number.format(total) + ')';
        const selectionLabel = (chosen,total) => region.dataset.selectionLabel.replace('{selected}',number.format(chosen)).replace('{total}',number.format(total));
        const sync = () => {
            let total = 0, chosen = 0;
            for (const [key,group] of groups) {
                const n = count(group); total += group.total; chosen += n;
                const check = group.heading.querySelector('[data-grouped-select]');
                check.checked = n === group.total; check.indeterminate = n > 0 && n < group.total;
                const badge = group.heading.querySelector('[data-grouped-count]');
                badge.textContent = counter(n,group.total); badge.setAttribute('aria-label',selectionLabel(n,group.total));
                const toggle = group.heading.querySelector('[data-grouped-toggle]');
                toggle.setAttribute('aria-expanded',String(group.expanded));
                toggle.setAttribute('aria-label',(group.expanded ? region.dataset.collapseLabel : region.dataset.expandLabel) + ' ' + group.heading.querySelector('[data-grouped-name]').textContent.trim());
                const controls = [];
                children(key).forEach((row,index) => {
                    row.id = 'gl-group-' + key + '-' + index; controls.push(row.id);
                    row.hidden = !group.expanded;
                    const input = row.querySelector('[data-grouped-account]');
                    if (input) {input.checked = selected(group,input.dataset.groupedAccount); row.classList.toggle('resource-table-selected',input.checked);}
                });
                if (controls.length) toggle.setAttribute('aria-controls',controls.join(' '));
            }
            all.checked = total > 0 && chosen === total; all.indeterminate = chosen > 0 && chosen < total; all.disabled = total === 0;
            const badge = region.querySelector('[data-grouped-counter]');
            badge.textContent = counter(chosen,total); badge.setAttribute('aria-label',selectionLabel(chosen,total));
            status.textContent = selectionLabel(chosen,total);
            open.disabled = chosen !== 1 || !region.querySelector('[data-grouped-account]:checked');
        };
        const abort = () => {
            epoch++;
            requests.forEach(request => request.abort()); requests.clear();
            region.querySelectorAll('[data-grouped-spinner]').forEach(spinner=>spinner.remove());
            region.querySelectorAll('[data-grouped-more]').forEach(link => {link.removeAttribute('aria-busy');link.removeAttribute('aria-disabled');});
        };
        const reset = () => {
            abort();
            groups.forEach(group => {group.all=false;group.included.clear();group.excluded.clear();});
            sync();
        };
        const mount = () => {
            const previous = new Map([...groups].map(([key,group])=>[key,group.expanded]));
            groups.clear();
            const values = ['search','status','parentId'].map(name=>region.querySelector('[name="'+name+'"]')?.value || '');
            const nextCriteria = JSON.stringify(values), changed = criteria !== nextCriteria;
            const filtered = values.some(Boolean); criteria = nextCriteria;
            region.querySelectorAll('[data-grouped-heading]').forEach(heading => {
                groups.set(heading.dataset.groupedHeading,{heading,total:Number(heading.dataset.groupTotal),
                    all:false,included:new Set(),excluded:new Set(),expanded:changed ? filtered : previous.get(heading.dataset.groupedHeading) ?? filtered});
            });
            sync();
        };
        const load = async link => {
            const key = link.dataset.groupedMore, group = groups.get(key);
            if (!group || requests.has(key)) return;
            const url = new URL(link.href,location.href);
            if (url.origin !== location.origin || url.pathname !== location.pathname) return;
            const request = new AbortController(), current = epoch;
            requests.set(key,request);
            const timeout = setTimeout(()=>request.abort(),20000);
            const row = link.closest('tr');
            row.querySelector('[data-grouped-error]').textContent = '';
            link.setAttribute('aria-busy','true');link.setAttribute('aria-disabled','true');
            const spinner=document.createElement('span');spinner.className='app-loading-spinner';spinner.dataset.groupedSpinner='';spinner.setAttribute('aria-hidden','true');link.append(spinner);
            try {
                const response = await fetch(url.href,{credentials:'same-origin',cache:'no-store',headers:{Accept:'text/html'},signal:request.signal});
                if (!response.ok || response.redirected) throw new Error('group');
                const page = new DOMParser().parseFromString(await response.text(),'text/html');
                if (current !== epoch || !region.isConnected) return;
                const fresh = page.querySelector('[data-grouped-resources]');
                const heading = [...(fresh?.querySelectorAll('[data-grouped-heading]') || [])].find(item=>item.dataset.groupedHeading===key);
                if (!heading || Number(heading.dataset.groupTotal) !== group.total) {
                    reset(); window.ResourceTable.refresh(region.querySelector('[data-resource-refresh]')); return;
                }
                const next = [...fresh.querySelectorAll('[data-account-group]')].filter(item=>item.dataset.accountGroup===key);
                const existing = new Set(children(key).map(item=>item.dataset.accountId).filter(Boolean));
                const added = next.filter(item=>(!item.dataset.accountId || !existing.has(item.dataset.accountId)) && (!item.classList.contains('grouped-resource-more') || item.querySelector('[data-grouped-more]'))).map(item=>document.importNode(item,true));
                if (!added.some(item=>item.hasAttribute('data-account-id'))) throw new Error('group');
                added.forEach(item=>item.querySelectorAll('[data-grouped-previous]').forEach(previous=>previous.remove()));
                row.replaceWith(...added);
                sync();
                const more = added.find(item=>item.querySelector('[data-grouped-more]'))?.querySelector('[data-grouped-more]');
                (more || added.find(item=>item.querySelector('a'))?.querySelector('a'))?.focus();
            } catch (error) {
                if (current === epoch && row.isConnected) row.querySelector('[data-grouped-error]').textContent = region.dataset.loadError;
            } finally {
                clearTimeout(timeout);spinner.remove();
                if (requests.get(key) === request) requests.delete(key);
                link.removeAttribute('aria-busy');link.removeAttribute('aria-disabled');
            }
        };
        region.addEventListener('change',event => {
            const input = event.target;
            if (input === all) groups.forEach(group=>{group.all=input.checked;group.included.clear();group.excluded.clear();});
            else if (input.matches('[data-grouped-select]')) {
                const group = groups.get(input.dataset.groupedSelect);
                group.all=input.checked;group.included.clear();group.excluded.clear();
            } else if (input.matches('[data-grouped-account]')) {
                const group = groups.get(input.closest('[data-account-group]').dataset.accountGroup), id=input.dataset.groupedAccount;
                const set = group.all ? group.excluded : group.included;
                if (input.checked !== group.all) set.add(id); else set.delete(id);
            } else return;
            sync();
        });
        region.addEventListener('click',event => {
            const toggle = event.target.closest('[data-grouped-toggle]');
            if (toggle) {const group=groups.get(toggle.dataset.groupedToggle);group.expanded=!group.expanded;sync();return;}
            const more = event.target.closest('[data-grouped-more]');
            if (more) {
                if (event.button || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return;
                event.preventDefault();void load(more);return;
            }
            const row = event.target.closest('[data-account-id]');
            if (row && !event.target.closest('input,button,a,select,textarea,label')) row.querySelector('[data-grouped-account]').click();
        });
        open.addEventListener('click',()=>{
            if (!open.disabled) {
                const link=region.querySelector('[data-grouped-account]:checked')?.closest('tr').querySelector('a.resource-table-id-link');
                if(link)location.assign(link.href);
            }
        });
        region.addEventListener('resource-table:reset',reset);
        region.addEventListener('resource-table:updated',mount);
        window.addEventListener('pagehide',reset);
        window.addEventListener('pageshow',reset);
        mount();
    }));
})();
