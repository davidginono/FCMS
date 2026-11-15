(() => {
    'use strict';
    const delay = 350;
    function bind(input, search, invalidate = () => {}) {
        let timer, pending = false, composing = false, lastValue;
        const stop = () => { clearTimeout(timer); pending = false; };
        const cancel = () => { stop(); lastValue = undefined; };
        const run = (force = false) => {
            stop();
            if (!force && lastValue === input.value) return;
            lastValue = input.value; invalidate(); search();
        };
        const flush = () => { if (pending && !composing) run(); };
        const schedule = event => {
            cancel(); invalidate();
            if (composing || event?.isComposing) return;
            pending = true; timer = setTimeout(flush, delay);
        };
        input.addEventListener('input', schedule);
        input.addEventListener('blur', flush);
        input.addEventListener('compositionstart', () => { composing = true; cancel(); invalidate(); });
        input.addEventListener('compositionend', () => { composing = false; schedule(); });
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !composing && !event.isComposing) {
                event.preventDefault(); run();
            }
        });
        return {cancel, flush, run};
    }
    window.AppSearch = {bind, delay};

})();
