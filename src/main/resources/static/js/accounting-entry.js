/* Microfinance voucher rows and split entries; the shared form owner handles POSTs. */
(() => {
    'use strict';
    const ready=window.WorkspaceNavigation?.onReady || (fn=>document.addEventListener('DOMContentLoaded',fn,{once:true}));
    const refresh=root=>{window.refreshEnhancedSelects?.(root);window.FcmsChoices?.init(root);};
    let serial=1000;
    function cleanClone(row) {
        const copy=row.cloneNode(true);
        copy.querySelectorAll('.neo-select').forEach(wrapper=>{
            const select=wrapper.querySelector('select'); if(!select){wrapper.remove();return;}
            select.removeAttribute('data-neo-enhanced'); select.removeAttribute('data-fcms-choice-ready');
            select.removeAttribute('aria-hidden');select.removeAttribute('tabindex');select.classList.remove('neo-select-native');wrapper.replaceWith(select);
        });
        copy.querySelector('[data-split-list]')?.replaceChildren();
        copy.querySelectorAll('input[type="hidden"]:not([data-voucher-amount])').forEach(input=>input.removeAttribute('id'));
        const identity=String(++serial);
        copy.querySelectorAll('[id],[for],[aria-labelledby]').forEach(node=>{for(const attribute of ['id','for','aria-labelledby'])if(node.hasAttribute(attribute))node.setAttribute(attribute,node.getAttribute(attribute).replace(/-\d+$/,'-'+identity));});
        if(copy.hasAttribute('aria-labelledby'))copy.setAttribute('aria-labelledby',copy.getAttribute('aria-labelledby').replace(/-\d+$/,'-'+identity));
        copy.querySelectorAll('.fcms-error,.fcms-alert').forEach(node=>node.remove());
        copy.querySelectorAll('[aria-describedby]').forEach(node=>node.removeAttribute('aria-describedby'));
        copy.querySelectorAll('input').forEach(input=>{input.value=input.name.endsWith('.component')?'TOTAL':'';input.removeAttribute('aria-invalid');});
        copy.querySelectorAll('select').forEach(select=>{select.selectedIndex=0;});
        return copy;
    }
    function renumber(container) {
        const rows=[...container.children];
        rows.forEach((row,index)=>{
            row.querySelectorAll('[name]').forEach(node=>node.name=node.name.replace(/\[\d+\]/,'['+index+']'));
            const mappingScope=row.querySelector('[data-configured-row]');if(mappingScope)mappingScope.dataset.errorScope='mapping-'+index;
            const entryScope=row.querySelector('[data-error-scope^=entries-]');if(entryScope)entryScope.dataset.errorScope='entries-'+index;
            row.querySelectorAll('[data-split-entry]').forEach((entry,e)=>entry.querySelectorAll('[name]').forEach(node=>node.name=node.name.replace(/\.entries\[\d+\]/,'.entries['+e+']')));
            const number=row.querySelector('[data-row-number]'); if(number)number.textContent=String(index+1);
            row.querySelector('[data-remove-voucher-row]').disabled=rows.length===1;
        });
    }
    ready(()=>{
        document.querySelectorAll('[data-voucher-form]').forEach(form=>{
            if(form.dataset.entryReady)return;form.dataset.entryReady='true';
            const container=form.querySelector('[data-voucher-rows]'),activity=form.querySelector('[data-voucher-activity]'),add=form.querySelector('[data-add-voucher-row]'),status=form.querySelector('[data-voucher-status]');
            const number=form.querySelector('[data-voucher-number]'),date=form.querySelector('[name=date]'),retry=form.querySelector('[data-number-retry]'),numberError=form.querySelector('[data-number-error]');
            let reservationController,reservationVersion=0;
            async function reserveNumber() {
                const version=++reservationVersion;reservationController?.abort();
                if(!date.value){number.replaceChildren();return;}
                const request=new AbortController();reservationController=request;
                number.setAttribute('aria-busy','true');number.setAttribute('aria-label',document.body.dataset.shellLoading);
                const spinner=window.AppLoading?.spinner('small');number.replaceChildren(...(spinner?[spinner]:[]));retry.hidden=true;numberError.hidden=true;
                const token=form.querySelector('input[name="_csrf"]');
                const body=new URLSearchParams({requestKey:form.querySelector('[name=requestKey]').value,date:date.value});if(token)body.set(token.name,token.value);
                const timeout=setTimeout(()=>request.abort(),10000);
                try {
                    const response=await fetch(form.dataset.voucherBase+'/number',{method:'POST',credentials:'same-origin',headers:{Accept:'application/json'},body,signal:request.signal});
                    if(!response.ok||response.redirected)throw new Error('number');const result=await response.json();
                    if(version===reservationVersion&&form.isConnected)number.textContent=result.number;
                }catch(error){if(version===reservationVersion&&form.isConnected){number.replaceChildren();numberError.hidden=false;retry.hidden=false;}}
                finally{clearTimeout(timeout);if(version===reservationVersion){number.setAttribute('aria-busy','false');number.removeAttribute('aria-label');}}
            }
            date.addEventListener('change',reserveNumber);retry.addEventListener('click',reserveNumber);window.addEventListener('pagehide',()=>reservationController?.abort(),{once:true});void reserveNumber();
            const cents=value=>{
                if(!/^\d+(\.\d{0,2})?$/.test(value))return 0n;
                const [whole,fraction='']=value.split('.');return BigInt(whole)*100n+BigInt((fraction+'00').slice(0,2));
            };
            const total=()=>{
                let value=0n;
                for(const row of container.children) {
                    const input=row.querySelector('[data-voucher-amount]');
                    if(activity.value)value+=cents(input.value);
                    else {
                        let debits=0n;
                        row.querySelectorAll('[data-split-entry]').forEach(entry=>{if(entry.querySelector('[data-entry-field=side]').value==='DEBIT')debits+=cents(entry.querySelector('[data-entry-field=amount]').value);});
                        input.value=debits?String(debits/100n)+'.'+String(debits%100n).padStart(2,'0'):'';value+=debits;
                    }
                }
                const locale=document.documentElement.lang==='sw'?'sw-TZ':'en-TZ';
                form.querySelector('[data-voucher-total]').textContent=new Intl.NumberFormat(locale).format(value/100n)+'.'+String(value%100n).padStart(2,'0');
            };
            const sync=(changed)=>{
                renumber(container);add.disabled=container.children.length>=50;
                container.querySelectorAll('[data-configured-row]').forEach(node=>node.hidden=!activity.value);
                const manual=!activity.value;
                container.querySelectorAll('[data-manual-description]').forEach(node=>{node.hidden=!manual;node.querySelector('input').disabled=!manual;});
                container.querySelectorAll('[data-manual-row]').forEach(node=>{node.hidden=!manual;node.querySelectorAll('input,select,button').forEach(control=>{if(control.disabled!==!manual)control.disabled=!manual;});});
                container.querySelectorAll('[data-configured-amount]').forEach(node=>{node.hidden=manual;const input=node.querySelector('[data-voucher-amount]');input.type=manual?'hidden':'number';input.disabled=manual;});
                let count=0;
                container.querySelectorAll('[data-voucher-row]').forEach(row=>{
                    if(manual&&!row.querySelector('[data-split-entry]')) {
                        const value=row.querySelector('[data-voucher-amount]').value;
                        for(const side of ['DEBIT','CREDIT'])addEntry(row,side,'','',value);
                    }
                    const entries=[...row.querySelectorAll('[data-split-entry]')];count+=manual?entries.length:2;
                    for(const side of ['DEBIT','CREDIT']){
                        const same=entries.filter(e=>e.querySelector('[data-entry-field=side]').value===side);
                        same.forEach(e=>e.querySelector('[data-remove-entry]').disabled=!manual||same.length<=1);
                    }
                    row.querySelectorAll('[data-add-entry]').forEach(button=>button.disabled=!manual||entries.length>=50);
                });
                if(count>=500)container.querySelectorAll('[data-add-entry]').forEach(button=>button.disabled=true);
                add.disabled=container.children.length>=50||count>498;
                container.querySelectorAll('[data-voucher-mapping]').forEach(select=>{if(select.disabled!==!activity.value)select.disabled=!activity.value;select.dataset.choiceUrl=form.dataset.voucherBase+'/choices?activityId='+encodeURIComponent(activity.value);});
                if(changed)refresh(changed);total();
            };
            activity.addEventListener('change',()=>{
                container.querySelectorAll('[data-voucher-row]').forEach(row=>{
                    row.querySelector('[data-split-list]').replaceChildren();
                    row.querySelectorAll('input[type=hidden]').forEach(input=>{input.value=input.name.endsWith('.component')?'TOTAL':'';});
                    const choice=row.querySelector('[data-voucher-mapping]');choice.replaceChildren(new Option(form.dataset.chooseTransaction,''));choice.dispatchEvent(new Event('fcms:choices-reset'));
                    row.querySelectorAll('[data-manual-row] select').forEach(select=>select.value='');
                });sync(container);
            });
            container.addEventListener('change',event=>{
                const choice=event.target.closest('[data-voucher-mapping]');if(!choice)return;
                const row=choice.closest('[data-voucher-row]'),[id,component]=choice.value.split(':');
                row.querySelector('input[name$=".mappingId"]').value=id||'';row.querySelector('input[name$=".component"]').value=component||'TOTAL';
                const selected=choice.selectedOptions[0];if(selected?.dataset.flow){form.querySelector('[name=cashFlow]').value=selected.dataset.flow;refresh(form);}
            });
            function addEntry(row,side,accountId='',label='',amount='') {
                const entry=form.querySelector('[data-voucher-entry-template]').content.firstElementChild.cloneNode(true),identity=String(++serial);
                const index=[...container.children].indexOf(row),entryIndex=row.querySelectorAll('[data-split-entry]').length;
                entry.querySelectorAll('[data-entry-field]').forEach(control=>{control.name='rows['+index+'].entries['+entryIndex+'].'+control.dataset.entryField;});
                entry.querySelector('[data-entry-field=side]').value=side;
                const select=entry.querySelector('select'),input=entry.querySelector('[data-entry-field=amount]');
                select.id='entry-account-'+identity;input.id='entry-amount-'+identity;
                entry.querySelector('[data-entry-account-label]').htmlFor=select.id;
                entry.querySelector('[data-entry-account-label]').textContent=form.querySelector(side==='DEBIT'?'[data-debit-label]':'[data-credit-label]').textContent;
                entry.querySelector('[data-entry-amount-label]').htmlFor=input.id;
                const money=form.dataset.kind==='RECEIPT'?side==='DEBIT':form.dataset.kind==='PAYMENT'?side==='CREDIT':false;
                select.dataset.choiceUrl=form.dataset.voucherBase+'/accounts?purpose='+(form.dataset.kind==='JOURNAL'?'':money?'MONEY':'OTHER');
                if(accountId)select.add(new Option(label,accountId,true,true));input.value=amount;
                row.querySelector('[data-split-list]').append(entry);
                return entry;
            }
            container.addEventListener('click',event=>{
                const button=event.target.closest('[data-add-entry]');
                if(button&&!button.disabled&&!activity.value){
                    const row=button.closest('[data-voucher-row]');
                    const entry=addEntry(row,button.dataset.addEntry);sync(row);entry.querySelector('select').closest('.neo-select')?.querySelector('button')?.focus();form.dispatchEvent(new Event('input',{bubbles:true}));return;
                }
                const remove=event.target.closest('[data-remove-entry]');
                if(remove&&!remove.disabled&&!activity.value){const row=remove.closest('[data-voucher-row]');remove.closest('[data-split-entry]').remove();sync();row.querySelector('[data-add-entry]').focus();form.dispatchEvent(new Event('input',{bubbles:true}));}
            });
            add.addEventListener('click',()=>{
                if(container.children.length>=50){status.textContent=form.dataset.rowLimit;return;}
                const row=cleanClone(container.firstElementChild);container.append(row);sync(row);row.querySelector('input:not([type=hidden])')?.focus();form.dispatchEvent(new Event('input',{bubbles:true}));
            });
            container.addEventListener('click',event=>{const button=event.target.closest('[data-remove-voucher-row]');if(!button||container.children.length<=1)return;button.closest('[data-voucher-row]').remove();sync();add.focus();form.dispatchEvent(new Event('input',{bubbles:true}));});
            form.addEventListener('input',()=>{form.querySelector('[data-voucher-preview]')?.remove();total();});
            form.addEventListener('change',()=>form.querySelector('[data-voucher-preview]')?.remove());sync(container);
        });
    });
})();
