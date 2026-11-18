(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => window.addEventListener('load', callback, {once:true}));
    ready(() => {
        const form = document.querySelector('[data-user-permissions]');
        if (!form || form.dataset.permissionsReady) return;
        form.dataset.permissionsReady = 'true';
        const role = form.querySelector('#roleId');
        const permissions = [...form.querySelectorAll('[data-role-permission]')];
        const defaults = () => new Set((role.options[role.selectedIndex]?.dataset.defaultClaims || '').split(/\s+/).filter(Boolean));
        let additions, exclusions, resetting = false;
        const capture = () => {
            const inherited = defaults();
            additions = new Set(permissions.filter(input => input.checked && !inherited.has(input.value)).map(input => input.value));
            exclusions = new Set(permissions.filter(input => !input.checked && inherited.has(input.value)).map(input => input.value));
        };
        const sync = () => {
            const inherited = defaults();
            permissions.forEach(input => {
                input.checked = !exclusions.has(input.value) && (inherited.has(input.value) || additions.has(input.value));
            });
        };
        permissions.forEach(input => input.addEventListener('change', () => {
            if (input.checked) {
                exclusions.delete(input.value);
                if (!defaults().has(input.value)) additions.add(input.value);
            } else {
                additions.delete(input.value);
                exclusions.add(input.value);
            }
        }));
        role.addEventListener('change', () => {if (!resetting) sync();});
        form.addEventListener('reset', () => {
            resetting = true;
            queueMicrotask(() => {capture(); resetting = false;});
        });
        capture();
    });
})();
