/* Actual parent-account branches; shared resource-table owns sorting, preferences and paging. */
(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => window.addEventListener('load', callback, {once:true}));
    ready(() => document.querySelectorAll('[data-coa-tree]').forEach(region => {
        if (region.dataset.coaReady) return;
        region.dataset.coaReady = 'true';
        const body = region.querySelector('tbody'), filters = region.querySelector('.coa-filters');
        const commands = [...region.querySelectorAll('[data-coa-command]')];
        const create = region.querySelector('[data-coa-create]'), state = region.querySelector('[data-coa-state]');
        const requests = new Map(), status = region.querySelector('[data-coa-status]');
        let epoch = 0;
        const rows = () => [...body.querySelectorAll('[data-coa-row]')];
        const input = row => row.querySelector('[name="coa-selection"]');
        const clearSelection = row => {
            const selection = input(row);
            if (selection?.checked) {
                selection.checked = false;
                selection.dispatchEvent(new Event('change', {bubbles:true}));
            }
        };
        const selected = () => rows().find(row => !row.hidden && input(row).checked);
        const navigate = url => window.WorkspaceNavigation?.navigate ? window.WorkspaceNavigation.navigate(url) : location.assign(url);
        const recordUrl = (row, suffix = '') => region.dataset.baseUrl + '/' + row.dataset.id + suffix;
        const update = () => {
            const row = selected();
            rows().forEach(item => item.classList.toggle('resource-table-selected', input(item).checked));
            commands.forEach(command => {
                const kind = command.dataset.coaCommand;
                command.disabled = !row || kind === 'children' && row.dataset.expandable !== 'true'
                    || kind === 'edit' && row.dataset.editable !== 'true'
                    || kind === 'state' && row.dataset.lifecycle !== 'true'
                    || kind === 'ledger' && row.dataset.ledger !== 'true';
                if (kind === 'state') command.textContent = row?.dataset.active === 'false' ? command.dataset.activate : command.dataset.deactivate;
            });
            if (state) {
                if (row) {
                    state.action = recordUrl(row, '/state');
                    state.elements.version.value = row.dataset.version;
                    state.elements.active.value = row.dataset.active === 'false' ? 'true' : 'false';
                } else state.removeAttribute('action');
            }
            if (create) {
                const posting = row?.dataset.addPosting === 'true';
                create.disabled = !row || !posting && row.dataset.addGroup !== 'true';
                create.textContent = posting ? create.dataset.postingLabel : create.dataset.groupLabel;
            }
            status.textContent = row ? input(row).getAttribute('aria-label') : '';
        };
        const visibility = () => {
            const mounted = new Map();
            [...body.rows].forEach(row => {
                const parent = mounted.get(row.dataset.treeParent);
                row.hidden = !!row.dataset.treeParent && (!parent || parent.hidden || parent.dataset.expanded !== 'true');
                if (row.dataset.id) mounted.set(row.dataset.id, row);
                if (row.hidden) clearSelection(row);
                const toggle = row.querySelector('[data-coa-toggle]');
                if (toggle) {
                    const expanded = row.dataset.expanded === 'true';
                    toggle.setAttribute('aria-expanded', String(expanded));
                    toggle.setAttribute('aria-label', (expanded ? region.dataset.collapse : region.dataset.expand) + ' ' + row.querySelector('.resource-table-id-link').textContent.trim());
                    const children = [...body.rows].filter(child => child.dataset.treeParent === row.dataset.id).map((child,index) => child.id ||= 'coa-child-' + row.dataset.id + '-' + index);
                    if (children.length) toggle.setAttribute('aria-controls', children.join(' '));
                    else toggle.removeAttribute('aria-controls');
                }
            });
            update();
        };
        const abort = () => {
            epoch++;
            requests.forEach(request => request.abort()); requests.clear();
            body.querySelectorAll('[data-coa-pending]').forEach(row => {
                if (row.coaPrevious) row.replaceWith(row.coaPrevious);else row.remove();
            });
            rows().forEach(row => {delete row.dataset.loading;row.removeAttribute('aria-busy');});
        };
        const reset = () => {abort(); rows().forEach(clearSelection); update();};
        const message = (parent, text, retry, page) => {
            const row = document.createElement('tr'); row.dataset.treeParent = parent.dataset.id;
            row.dataset.depth = Number(parent.dataset.depth) + 1;
            const cell = row.insertCell(); cell.colSpan = 8; cell.className = 'coa-tree-message';
            const label = document.createElement('span'); label.textContent = text; label.setAttribute('role', retry ? 'alert' : 'status');cell.append(label);
            if (retry) {
                const button = document.createElement('button');button.type = 'button';button.className = 'app-btn btn-neutral';
                button.dataset.coaRetry = String(page);button.textContent = region.dataset.retry;cell.append(button);
            }
            return row;
        };
        const load = async (parent, page = 0, previous) => {
            if (requests.has(parent.dataset.id)) return;
            const restoreFocus = previous?.contains(document.activeElement);
            let focusTarget;
            const request = new AbortController(), current = epoch;
            requests.set(parent.dataset.id, request);
            parent.dataset.loading = 'true';parent.setAttribute('aria-busy','true');
            const anchor = message(parent, region.dataset.loading, false, page);
            anchor.dataset.coaPending = '';
            anchor.coaPrevious = previous;
            if (previous) previous.replaceWith(anchor);else parent.after(anchor);
            const spinner = window.AppLoading?.spinner('small');if (spinner) anchor.cells[0].prepend(spinner);
            visibility();
            const url = new URL(region.dataset.childrenUrl,location.href);
            new FormData(filters).forEach((value,key) => url.searchParams.set(key,value));
            url.searchParams.set('parentId',parent.dataset.id);url.searchParams.set('page',page);
            const timeout = setTimeout(() => request.abort(),20000);
            try {
                const response = await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'text/html','X-Requested-With':'XMLHttpRequest'},signal:request.signal});
                if (!response.ok || response.redirected || response.headers.get('X-COA-Children') !== 'true') throw Error('children');
                const html = await response.text();
                if (current !== epoch || !region.isConnected || !anchor.isConnected) return;
                const fragment = document.createElement('tbody');fragment.innerHTML = html;
                const children = [...fragment.rows], existing = new Map(rows().map(row => [row.dataset.id,row]));
                // Global filter matches may already show a child as an independent result.
                // Move that occurrence into its real branch; never duplicate an account or ancestor.
                const replaced = children.map(child => existing.get(child.dataset.id)).filter(Boolean);
                if (replaced.some(prior => prior === parent || prior.dataset.treeParent)) throw Error('duplicate');
                if (!children.length && page > 0) throw Error('page');
                for (const prior of replaced) {
                    let descendant = prior.nextElementSibling;
                    while (descendant && Number(descendant.dataset.depth) > Number(prior.dataset.depth)) {
                        const next = descendant.nextElementSibling;clearSelection(descendant);descendant.remove();descendant = next;
                    }
                    clearSelection(prior);
                    prior.remove();
                }
                children.forEach(row => {
                    row.dataset.treeParent = parent.dataset.id;row.dataset.depth = Number(parent.dataset.depth) + 1;
                    row.querySelector('.coa-tree-code')?.style.setProperty('--coa-depth',row.dataset.depth);
                    body.insertBefore(row,anchor);
                });
                if (!children.length) anchor.replaceWith(message(parent,region.dataset.empty,false,page));else anchor.remove();
                focusTarget = children.find(row => row.dataset.coaMore)?.querySelector('[data-coa-load-more]')
                    || children.find(row => row.dataset.id)?.querySelector('.resource-table-id-link');
                parent.dataset.loaded = 'true';
                region.dispatchEvent(new CustomEvent('resource-table:rows-added'));
            } catch (error) {
                if (current === epoch && region.isConnected && anchor.isConnected) {
                    const error = message(parent,region.dataset.loadError,true,page);
                    anchor.replaceWith(error);focusTarget = error.querySelector('[data-coa-retry]');
                }
            } finally {
                clearTimeout(timeout);
                if (requests.get(parent.dataset.id) === request) {
                    requests.delete(parent.dataset.id);delete parent.dataset.loading;parent.removeAttribute('aria-busy');
                }
                if (current === epoch) {
                    visibility();
                    if (restoreFocus && parent.dataset.expanded === 'true' && !parent.hidden) focusTarget?.focus();
                }
            }
        };
        region.addEventListener('change',event => {
            if (!event.target.matches('[name="coa-selection"]')) return;
            if (event.target.checked) rows().forEach(row => {if (input(row) !== event.target) input(row).checked = false;});
            update();
        });
        region.addEventListener('click',event => {
            if (event.defaultPrevented) return;
            const toggle = event.target.closest('[data-coa-toggle]');
            if (toggle) {
                if (event.button || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
                event.preventDefault();
                const row = toggle.closest('[data-coa-row]');row.dataset.expanded = String(row.dataset.expanded !== 'true');
                visibility();
                if (row.dataset.expanded === 'true' && row.dataset.loaded !== 'true' && ![...body.rows].some(child => child.dataset.treeParent === row.dataset.id)) void load(row);
                return;
            }
            const more = event.target.closest('[data-coa-load-more],[data-coa-retry]');
            if (more) {
                if (event.button || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
                event.preventDefault();const placeholder = more.closest('tr');
                const parent = rows().find(row => row.dataset.id === placeholder.dataset.treeParent);
                if (parent) void load(parent,Number(placeholder.dataset.coaMore || more.dataset.coaRetry || 0),placeholder);
                return;
            }
            const row = event.target.closest('[data-coa-row]');
            if (row && !event.target.closest('input,button,a,select,textarea,label')) {input(row).click();return;}
            const command = event.target.closest('[data-coa-command]'), chosen = selected();
            if (command && !command.disabled && chosen) {
                const kind = command.dataset.coaCommand;
                if (kind === 'state') return;
                if (kind === 'children') navigate(region.dataset.baseUrl + '?parentId=' + chosen.dataset.id);
                else navigate(recordUrl(chosen,kind === 'edit' ? '/edit' : kind === 'ledger' ? '/ledger' : ''));
            }
            if (event.target.closest('[data-coa-create]') && chosen && !create.disabled)
                navigate(region.dataset.baseUrl + '/new?parentId=' + chosen.dataset.id + '&mode=' + (chosen.dataset.addPosting === 'true' ? 'POSTING' : 'GROUP'));
        });
        region.addEventListener('keydown',event => {if (event.key === ' ' && event.target.matches('[data-coa-toggle]')) {event.preventDefault();event.target.click();}});
        state?.addEventListener('submit',event => {
            if (!selected() || state.querySelector('button').disabled) event.preventDefault();
        });
        // Shared refresh removes every mounted branch; filter edits must also discard old responses.
        const discardBranches = () => {
            reset();body.querySelectorAll('[data-tree-parent]').forEach(row => row.remove());
            rows().forEach(row => {delete row.dataset.loaded;delete row.dataset.expanded;});visibility();
        };
        filters.addEventListener('input',discardBranches);filters.addEventListener('change',discardBranches);
        region.addEventListener('resource-table:reset',reset);
        region.addEventListener('resource-table:updated',() => {reset();visibility();});
        const lifetime = new AbortController();
        window.addEventListener('pagehide',discardBranches,{signal:lifetime.signal});
        window.addEventListener('pageshow',discardBranches,{signal:lifetime.signal});
        const observer = new MutationObserver(() => {if (!region.isConnected) {abort();lifetime.abort();observer.disconnect();}});
        observer.observe(document.body,{childList:true,subtree:true});
        reset();visibility();
    }));
})();
