/* Keep modal paint inside the visible workspace while retaining dialog focus behavior. */
(() => {
    'use strict';
    let frame = 0;
    const sync = () => {
        const content = document.querySelector('.app-content');
        if (!content) return;
        const rect = content.getBoundingClientRect();
        const topbar = document.querySelector('.app-topbar')?.getBoundingClientRect();
        const sidebar = document.querySelector('[data-fcms-sidebar-panel]')?.getBoundingClientRect();
        const top = Math.min(innerHeight,Math.max(0,rect.top,topbar?.bottom || 0));
        const left = Math.min(innerWidth,Math.max(0,rect.left,sidebar?.right || 0));
        const right = Math.max(0,innerWidth - Math.min(innerWidth,rect.right));
        const bottom = Math.max(0,innerHeight - Math.min(innerHeight,rect.bottom));
        for (const [name,value] of Object.entries({top,left,right,bottom})) {
            const property = '--fcms-modal-' + name, pixels = value + 'px';
            if (document.documentElement.style.getPropertyValue(property) !== pixels)
                document.documentElement.style.setProperty(property,pixels);
        }
    };
    const schedule = () => {
        if (!frame) frame=requestAnimationFrame(()=>{frame=0;sync();});
    };
    window.FcmsModalWorkspace = {sync};
    const init = () => {
        sync();
        if (window.ResizeObserver) {
            const observer = new ResizeObserver(schedule);
            document.querySelectorAll('.app-content,.app-topbar,[data-fcms-sidebar-panel]').forEach(node=>observer.observe(node));
        }
        new MutationObserver(schedule).observe(document.body,{attributes:true,attributeFilter:['class']});
        window.addEventListener('resize',schedule,{passive:true});
        window.addEventListener('scroll',schedule,{passive:true});
        window.visualViewport?.addEventListener('resize',schedule,{passive:true});
        document.addEventListener('beforetoggle',sync,true);
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init,{once:true});
    else init();
})();
