/* Adapted from Microfinance accounting-library.js; FCMS owns routes, claims and submissions. */
(() => {
    'use strict';
    const ready = window.WorkspaceNavigation?.onReady || (callback => window.addEventListener('load',callback,{once:true}));
    ready(() => {
        document.querySelectorAll('[data-config-register]').forEach(register => {
            const inputs = () => [...register.querySelectorAll('input[name="config-selection"]')];
            const commands = [...register.querySelectorAll('[data-config-command]')];
            const selected = () => inputs().find(input=>input.checked)?.closest('[data-config-row]');
            const update = () => {
                const row=selected();
                inputs().forEach(input=>input.closest('tr').classList.toggle('resource-table-selected',input.checked));
                commands.forEach(button=>{
                    button.disabled=!row;
                    if(button.dataset.configCommand==='state') {
                        button.textContent=row?.dataset.active==='false'?button.dataset.activate:button.dataset.deactivate;
                        if(row?.dataset.active==='false' && row.dataset.parentActive==='false')button.disabled=true;
                    }
                });
                const status=register.querySelector('[data-config-selection-status]');
                status.textContent=row?row.querySelector('.resource-table-id-link').textContent.trim():'';
            };
            register.addEventListener('change',event=>{
                if(!event.target.matches('input[name="config-selection"]'))return;
                if(event.target.checked)inputs().forEach(input=>{if(input!==event.target)input.checked=false;});update();
            });
            register.addEventListener('click',event=>{
                if(event.target.closest('a,button,input,select,textarea,label'))return;
                event.target.closest('[data-config-row]')?.querySelector('input[name="config-selection"]').click();
            });
            commands.forEach(button=>button.addEventListener('click',()=>{
                const row=selected();if(!row||button.disabled)return;
                const kind=register.dataset.configRegister,id=encodeURIComponent(row.dataset.configRow);
                const base=new URL(location.href);base.pathname=base.pathname.slice(0,base.pathname.lastIndexOf('/'));
                let target=base.pathname+'/'+kind+'/'+id;
                switch(button.dataset.configCommand){
                    case 'transactions': target=base.pathname+'/transactions?activityId='+id;break;
                    case 'edit': target+=kind==='activities'?'/edit':'/template';break;
                    case 'state': target+='#state';break;
                }
                location.assign(target);
            }));
            const reset=()=>{inputs().forEach(input=>input.checked=false);update();};
            register.addEventListener('resource-table:reset',reset);register.addEventListener('resource-table:updated',reset);
            window.addEventListener('pageshow',reset);reset();
        });
        const modal=document.getElementById('config-create-modal');if(!modal)return;
        document.body.append(modal);
        const panel=modal.querySelector('[role="dialog"]'),triggers=[...document.querySelectorAll('[data-config-open]')];
        let previousFocus,bodyOverflow,rootOverflow;
        const focusable=()=>[...modal.querySelectorAll('button,a[href],input:not([type="hidden"]),select,textarea,[tabindex]')].filter(node=>!node.disabled&&node.tabIndex!==-1&&node.getClientRects().length);
        const close=()=>{
            modal.hidden=true;document.body.style.overflow=bodyOverflow||'';document.documentElement.style.overflow=rootOverflow||'';
            (previousFocus?.isConnected?previousFocus:triggers[0])?.focus();
            const url=new URL(location.href);url.searchParams.delete('create');url.searchParams.delete('created');history.replaceState(history.state,'',url.href);
        };
        const requestClose=()=>{
            const event=new CustomEvent('fcms:before-close',{cancelable:true,detail:{close}});document.dispatchEvent(event);
            if(event.defaultPrevented)return;
            close();
        };
        const open=()=>{
            previousFocus=document.activeElement;bodyOverflow=document.body.style.overflow;rootOverflow=document.documentElement.style.overflow;
            window.FcmsModalWorkspace?.sync();modal.hidden=false;document.body.style.overflow='hidden';document.documentElement.style.overflow='hidden';
            requestAnimationFrame(()=>{(modal.querySelector('[aria-invalid="true"]')||focusable()[0]||panel).focus();});
        };
        triggers.forEach(trigger=>trigger.addEventListener('click',event=>{
            if(event.button||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;
            event.preventDefault();
            const filter=document.querySelector('[data-config-register] .resource-table-filters [name="activityId"]');
            const choice=modal.querySelector('select[name="activityId"]');
            if(filter&&choice&&filter.value&&!modal.querySelector('.fcms-error')) {
                const option=filter.selectedOptions[0];if(![...choice.options].some(o=>o.value===option.value))choice.append(option.cloneNode(true));
                choice.value=filter.value;window.refreshEnhancedSelects?.(modal);
                document.dispatchEvent(new CustomEvent('fcms:form-defaults',{detail:{form:choice.form}}));
            }
            open();
        }));
        modal.querySelectorAll('[data-config-close]').forEach(button=>button.addEventListener('click',requestClose));
        modal.addEventListener('keydown',event=>{
            if(event.key==='Escape'){event.preventDefault();event.stopPropagation();requestClose();}
            if(event.key==='Tab'){
                const controls=focusable(),first=controls[0],last=controls.at(-1);
                if(!first){event.preventDefault();panel.focus();}
                else if(event.shiftKey&&(document.activeElement===first||document.activeElement===panel)){event.preventDefault();last.focus();}
                else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
            }
        });
        if(modal.dataset.autoOpen==='true')open();
    });
})();
