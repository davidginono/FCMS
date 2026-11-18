/* Reveal the existing field without replacing it or retaining its value. */
(() => {
    'use strict';
    const init = () => document.querySelectorAll('[data-password-visibility]').forEach(control => {
        if (control.dataset.passwordReady) return;
        const input = control.querySelector('input[type="password"]');
        const button = control.querySelector('button[aria-controls]');
        if (!input || !button || button.getAttribute('aria-controls') !== input.id) return;
        control.dataset.passwordReady = 'true';
        const setVisible = visible => {
            input.type = visible ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(visible));
            button.setAttribute('aria-label', visible ? button.dataset.hideLabel : button.dataset.showLabel);
            control.querySelector('[data-password-show-icon]').toggleAttribute('hidden', visible);
            control.querySelector('[data-password-hide-icon]').toggleAttribute('hidden', !visible);
        };
        button.addEventListener('click', () => setVisible(input.type === 'password'));
        input.form?.addEventListener('submit', () => setVisible(false));
        window.addEventListener('pagehide', () => setVisible(false));
        window.addEventListener('pageshow', () => setVisible(false));
        setVisible(false);
        button.hidden = false;
    });
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, {once:true});
    else init();
})();
