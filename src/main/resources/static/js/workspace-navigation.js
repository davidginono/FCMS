/* Progressive JSP navigation. The server still renders and authorizes every destination. */
(() => {
    'use strict';
    if (window.WorkspaceNavigation || !window.fetch || !window.AbortSignal?.any) return;
    const ownScript = document.currentScript || document.querySelector('script[data-workspace-navigation]');
    const native = {add:EventTarget.prototype.addEventListener, fetch:window.fetch.bind(window), ResizeObserver:window.ResizeObserver,
        push:history.pushState.bind(history), replace:history.replaceState.bind(history)};
    let scope, booting=false, committing=false, queued, request, generation=0, serial=Date.now();
    let viewKey=history.state?.workspaceNavigation?.key || ++serial;
    let entry={url:location.href,index:history.state?.workspaceNavigation?.index || 0};
    let reversing=false;
    let pendingWrites=0;
    const scrollPositions=new Map();
    const timers = {};
    for (const [schedule,cancel] of [['setTimeout','clearTimeout'],['setInterval','clearInterval'],['requestAnimationFrame','cancelAnimationFrame'],['requestIdleCallback','cancelIdleCallback']]) {
        if (!window[schedule]) continue;
        const start=window[schedule].bind(window), stop=window[cancel].bind(window);timers[schedule]=start;
        window[schedule]=(callback,...args)=>{
            const owner=scope;let id;
            id=start(typeof callback==='function' ? (...values)=>{
                if(schedule!=='setInterval')owner?.timers.delete(schedule+':'+id);
                if(owner?.alive!==false)callback(...values);
            } : callback,...args);
            owner?.timers.set(schedule+':'+id,()=>stop(id));return id;
        };
        window[cancel]=id=>{scope?.timers.delete(schedule+':'+id);stop(id);};
    }
    const createScope=()=>({alive:true,controller:new AbortController(),timers:new Map(),observers:new Set(),ready:[],load:[]});
    scope=createScope();
    let documentReady=document.readyState==='complete';
    function onReady(callback){
        if(booting)scope.ready.push(callback);
        else if(!documentReady)document.addEventListener('DOMContentLoaded',callback,{once:true});
        else callback();
    }
    function prepare(main,label=document.body.dataset.navigationLoading){
        if(!main || main.hasAttribute('data-workspace-initializing'))return;
        main.dataset.workspaceInitializing='true';main.inert=true;main.setAttribute('inert','');main.setAttribute('aria-busy','true');
        const status=window.AppLoading.status(label,true,'large');
        status.dataset.workspaceBootStatus='true';status.classList.add('workspace-initializing-status');main.prepend(status);
    }
    function reveal(){
        document.documentElement.removeAttribute('data-workspace-booting');
        document.documentElement.removeAttribute('data-workspace-loading');
        const main=document.querySelector('[data-workspace-initializing]');
        if(main){main.querySelector('[data-workspace-boot-status]')?.remove();main.inert=false;main.removeAttribute('inert');main.removeAttribute('aria-busy');delete main.dataset.workspaceInitializing;}
    }
    native.add.call(document,'DOMContentLoaded',()=>{documentReady=true;timers.requestAnimationFrame(()=>{if(!committing&&!document.documentElement.hasAttribute('data-workspace-loading'))reveal();});},{once:true});
    EventTarget.prototype.addEventListener=function(type,callback,options){
        if (!callback) return;
        const owner=scope, settings=typeof options==='boolean'?{capture:options}:{...options};
        if(booting && ((this===document && type==='DOMContentLoaded') || (this===window && type==='load'))){
            owner[type==='load'?'load':'ready'].push(()=>typeof callback==='function'?callback.call(this,new Event(type)):callback.handleEvent(new Event(type)));
            return;
        }
        settings.signal=settings.signal ? AbortSignal.any([settings.signal,owner.controller.signal]) : owner.controller.signal;
        return native.add.call(this,type,callback,settings);
    };
    for (const name of ['MutationObserver','ResizeObserver','IntersectionObserver']) {
        const Original=window[name];if(!Original)continue;
        window[name]=class extends Original {
            constructor(callback){const owner=scope;super((...args)=>{if(owner.alive)callback(...args);});owner.observers.add(this);}
        };
    }
    window.fetch=async(url,options={})=>{
        // Existing mutation owners retain their request, timeout and recovery semantics.
        if(!['GET','HEAD'].includes((options.method || url?.method || 'GET').toUpperCase())){
            pendingWrites++;
            try{return await native.fetch(url,options);}finally{pendingWrites--;}
        }
        const owner=scope, signal=options.signal ? AbortSignal.any([options.signal,owner.controller.signal]) : owner.controller.signal;
        const response=await native.fetch(url,{...options,signal});
        if(!owner.alive)throw new DOMException('Workspace changed','AbortError');return response;
    };
    const state=(value,key=viewKey,index=entry.index)=>({...value,workspaceNavigation:{key,index}});
    history.pushState=(value,title,url)=>{native.push(state(value,viewKey,entry.index+1),title,url);entry={url:location.href,index:entry.index+1};};
    history.replaceState=(value,title,url)=>{native.replace(state(value),title,url);entry.url=location.href;};
    native.replace(state(history.state),'',location.href);
    function dispose(){
        window.dispatchEvent(new PageTransitionEvent('pagehide'));
        scope.alive=false;scope.controller.abort();scope.timers.forEach(stop=>stop());scope.observers.forEach(observer=>observer.disconnect());
        // These app singletons otherwise retain observers or closures from the previous JSP.
        delete window.__neoSelectEnhancerLoaded;
    }
    async function prepareStyles(page,signal){
        // Body-owned page styles must be ready before their new components become visible.
        for(const style of page.body.querySelectorAll('link[rel="stylesheet"],style'))page.head.append(style);
        const current=new Map([...document.head.querySelectorAll('link[rel="stylesheet"]')].map(link=>[link.href,link]));
        const staged=new Map(),pending=new Set();
        try{
            await Promise.all([...page.head.querySelectorAll('link[rel="stylesheet"]')].map(source=>{
                const href=new URL(source.getAttribute('href'),location.href).href;
                if(current.has(href) || staged.has(href))return;
                const link=document.importNode(source,true);link.media='not all';staged.set(href,link);
                return new Promise((resolve,reject)=>{
                    let settled=false;
                    const finish=error=>{if(settled)return;settled=true;clearTimeout(timeout);pending.delete(abort);signal.removeEventListener('abort',abort);error?reject(error):resolve();};
                    const abort=()=>finish(new DOMException('Workspace changed','AbortError'));
                    const timeout=timers.setTimeout(()=>finish(new Error('Workspace stylesheet')),20000);
                    native.add.call(link,'load',()=>finish(),{once:true});native.add.call(link,'error',()=>finish(new Error('Workspace stylesheet')),{once:true});
                    pending.add(abort);signal.addEventListener('abort',abort,{once:true});document.head.append(link);
                    if(signal.aborted)abort();
                });
            }));
            return staged;
        }catch(error){pending.forEach(abort=>abort());staged.forEach(link=>link.remove());throw error;}
    }
    function replaceHead(page,staged){
        const styles=new Map([...document.head.querySelectorAll('link[rel="stylesheet"]')].map(link=>[link.href,link]));
        const keep=new Set(ownScript?[ownScript]:[]), additions=[];
        for(const child of [...page.head.childNodes]){
            const existing=child.nodeType===1 && child.matches('link[rel="stylesheet"]') && styles.get(new URL(child.getAttribute('href'),location.href).href);
            if(existing){
                keep.add(existing);additions.push(existing);
                if(staged.has(existing.href)){if(child.hasAttribute('media'))existing.media=child.media;else existing.removeAttribute('media');}
            }else additions.push(document.importNode(child,true));
        }
        // Shared styles stay connected, so the header and sidebar never lose their layout.
        for(const child of [...document.head.childNodes])if(!keep.has(child))child.remove();
        // Do not detach/reappend unchanged styles: that can reload them and flash the shell.
        let anchor=null;
        for(const child of additions.reverse()){
            if(child.parentNode!==document.head || (anchor && child.compareDocumentPosition(anchor)&Node.DOCUMENT_POSITION_PRECEDING))document.head.insertBefore(child,anchor);
            anchor=child;
        }
    }
    function replaceBody(page){
        // Replace the workspace while updating authorized shell text/attributes in place.
        // Unchanged header, navigation and layout nodes remain connected throughout the save.
        function sync(current,fresh){
            if(current.nodeType!==fresh.nodeType || (current.nodeType===1 && (current.tagName!==fresh.tagName || current.id!==fresh.id || fresh.matches('#main-content,template,dialog,[role="dialog"],[role="alertdialog"],.app-modal-overlay,.session-timeout-overlay')))){
                current.replaceWith(fresh);return fresh;
            }
            if(current.nodeType!==1){if(current.nodeValue!==fresh.nodeValue)current.nodeValue=fresh.nodeValue;return current;}
            for(const attribute of [...current.attributes])if(!fresh.hasAttribute(attribute.name))current.removeAttribute(attribute.name);
            for(const attribute of fresh.attributes)if(current.getAttribute(attribute.name)!==attribute.value)current.setAttribute(attribute.name,attribute.value);
            const keyed=new Map([...current.children].filter(child=>child.id).map(child=>[child.id,child]));
            let cursor=current.firstChild;
            for(const child of [...fresh.childNodes]){
                const id=child.nodeType===1?child.id:'';
                const match=id?keyed.get(id):cursor;
                if(!match || (!id && match.nodeType===1 && match.id)){
                    current.insertBefore(child,cursor);continue;
                }
                if(match!==cursor)current.insertBefore(match,cursor);
                cursor=sync(match,child).nextSibling;
            }
            while(cursor){const next=cursor.nextSibling;cursor.remove();cursor=next;}
            return current;
        }
        sync(document.body,document.importNode(page.body,true));
    }
    async function execute(source,key){
        if(source.hasAttribute('src') && new URL(source.getAttribute('src'),location.href).href===ownScript?.src)return;
        await new Promise((resolve,reject)=>{
            const script=document.createElement('script');
            for(const attribute of source.attributes)if(!['async','defer','src'].includes(attribute.name))script.setAttribute(attribute.name,attribute.value);
            if(source.src){
                let url=new URL(source.getAttribute('src'),location.href);
                // Modules are otherwise evaluated once per document, including form-specific modules.
                if(source.type==='module')url.searchParams.set('workspace',String(key));
                script.src=url.href;script.async=false;
                const timeout=timers.setTimeout(()=>{script.remove();reject(new Error('Workspace script'));},20000);
                native.add.call(script,'load',()=>{clearTimeout(timeout);resolve();},{once:true});
                native.add.call(script,'error',()=>{clearTimeout(timeout);reject(new Error('Workspace script'));},{once:true});
            }else script.textContent='{\n'+source.textContent+'\n}';
            document.head.append(script);
            if(!source.src && source.type!=='module')resolve();
        });
    }
    function eligible(url){
        return ['http:','https:'].includes(url.protocol) && url.origin===location.origin && !/\/(?:logout|login|auth|print|export|download|avatar|contracts)(?:\/|$)|\.(?:pdf|xlsx?|csv|zip|png|jpe?g|gif|svg)$/i.test(url.pathname);
    }
    function handles(link){
        if(!link?.matches('a[href]') || link.matches('[download],[target]:not([target="_self"]),[data-download-action],[data-print-action],[data-native-navigation],[data-table-export]'))return false;
        const href=link.getAttribute('href');
        if(!href || href.startsWith('#'))return false;
        const url=new URL(link.href,location.href);
        return !!window.AppLoading && eligible(url) && !(url.pathname===location.pathname && url.search===location.search && url.hash) && !!document.getElementById('main-content');
    }
    function handlesForm(form,submitter){
        if(!window.AppLoading || !form?.matches('form') || !form.closest('#main-content') || form.hasAttribute('data-native-navigation'))return false;
        if((submitter?.getAttribute('formmethod') || form.method || 'get').toLowerCase()!=='get')return false;
        const target=submitter?.getAttribute('formtarget') || form.target;
        return (!target || target==='_self') && eligible(new URL(submitter?.getAttribute('formaction') || form.getAttribute('action') || location.href,location.href));
    }
    const allowed=(url,resume)=>document.dispatchEvent(new CustomEvent('workspace:before-navigate',{cancelable:true,detail:{url,resume}}));
    const reportPath=/\/accounting\/(?:statements\/(?:income-statement|balance-sheet|cash-flow|changes-in-equity|notes)|trial-balance)$/;
    function reportTransition(url){
        return !!document.querySelector('[data-fs-report-section]') && reportPath.test(location.pathname) && reportPath.test(url.pathname) && !url.searchParams.has('lang');
    }
    function clearFeedback({keepLoading=false}={}){
        if(!keepLoading)document.documentElement.removeAttribute('data-workspace-loading');
        document.getElementById('workspace-navigation-feedback')?.remove();
        const main=document.getElementById('main-content');
        if(main?.dataset.workspacePending){
            main.removeAttribute('aria-busy');delete main.dataset.workspacePending;
        }
        const report=document.querySelector('[data-fs-report-section][data-workspace-pending]');
        if(report){report.removeAttribute('aria-busy');report.inert=false;report.removeAttribute('inert');delete report.dataset.workspacePending;}
        document.querySelector('[data-workspace-nav-pending]')?.removeAttribute('data-workspace-nav-pending');
    }
    function feedback(url,{failed=false,tab=false,report=false,committed=false}={}){
        clearFeedback();
        const main=document.getElementById('main-content');if(!main)return;
        const box=document.createElement('div');box.id='workspace-navigation-feedback';
        const region=report && document.querySelector('[data-fs-report-section]');
        box.className=region?'fs-tab-status':failed?'workspace-navigation-error':'workspace-navigation-pending';
        if(failed){
            box.setAttribute('role','alert');
            const message=document.createElement('span');message.textContent=document.body.dataset.navigationError;
            const retry=document.createElement(committed?'a':'button');retry.className='app-btn btn-neutral';retry.textContent=document.body.dataset.navigationRetry;
            if(committed){retry.href=url;retry.dataset.nativeNavigation='';}
            else{retry.type='button';retry.addEventListener('click',()=>visit(url,{force:true,tab}));}
            box.append(message,retry);
        }else{
            if(region){
                box.setAttribute('role','status');box.setAttribute('aria-label',document.body.dataset.navigationLoading);
                box.append(window.AppLoading.spinner('large'));
                region.dataset.workspacePending='';region.setAttribute('aria-busy','true');
            }else{
                box.append(window.AppLoading.status(document.body.dataset.navigationLoading,true,'large'));
                document.documentElement.dataset.workspaceLoading='true';main.dataset.workspacePending='';main.setAttribute('aria-busy','true');
            }
        }
        (region || main).prepend(box);
    }
    async function visit(destination,options={}){
        const url=new URL(destination,location.href);
        if(!window.AppLoading || !eligible(url))return false;
        if(pendingWrites || document.querySelector('#main-content form[aria-busy="true"]')){
            if(options.pop){reversing=true;history.go(options.previous.index-options.pop.index);}
            return false;
        }
        if(committing){queued=[url.href,options];return true;}
        if(!options.force && !options.pop && url.href===location.href){++generation;request?.abort();clearFeedback();return true;}
        const resume=()=>options.pop?history.go(options.pop.index-options.previous.index):visit(url.href,options);
        if(!allowed(url.href,resume)){
            if(options.pop){reversing=true;history.go(options.previous.index-options.pop.index);}
            return false;
        }
        options={...options,report:reportTransition(url)};
        request?.abort();const controller=new AbortController();request=controller;const version=++generation;
        const timeout=timers.setTimeout(()=>controller.abort(),20000);
        scrollPositions.set(viewKey,{top:window.scrollY,left:window.scrollX});
        if(scrollPositions.size>32)scrollPositions.delete(scrollPositions.keys().next().value);
        feedback(url.href,options);options.sidebarLink?.setAttribute('data-workspace-nav-pending','');
        let staged,didCommit=false;
        try{
            const response=options.response || await native.fetch(url.href,{credentials:'same-origin',cache:'no-store',headers:{Accept:'text/html'},signal:controller.signal});
            if(version!==generation)return true;
            const finalUrl=new URL(response.url || url.href,location.href);
            if(!finalUrl.hash && url.hash && finalUrl.pathname===url.pathname && finalUrl.search===url.search)finalUrl.hash=url.hash;
            if(finalUrl.origin!==location.origin)throw new Error('Workspace origin');
            if(/\/(?:login|auth)(?:\/|$)/.test(finalUrl.pathname)){location.assign(finalUrl.href);return true;}
            if(!response.ok || !response.headers.get('Content-Type')?.includes('text/html') || response.headers.get('Content-Disposition'))throw new Error('Workspace response');
            const page=new DOMParser().parseFromString(await response.text(),'text/html');
            if(version!==generation)return true;
            if(!page.body.matches('.fcms-shell') || !page.querySelector('#main-content') || !page.querySelector('#fcms-sidebar'))throw new Error('Workspace shell');
            const navigationScript=page.querySelector('script[data-workspace-navigation]');
            if(!navigationScript || new URL(navigationScript.getAttribute('src'),location.href).href!==ownScript.src){location.assign(finalUrl.href);return true;}
            // Only same-origin, server-rendered script resources participate in workspace lifecycle.
            const scripts=[...page.querySelectorAll('script')].filter(script=>!script.type || /^(?:text\/javascript|application\/javascript|module)$/.test(script.type));
            if(scripts.some(script=>!script.hasAttribute('src') || new URL(script.getAttribute('src'),location.href).origin!==location.origin))throw new Error('Workspace script origin');
            if([...page.querySelectorAll('link[rel="stylesheet"]')].some(link=>new URL(link.getAttribute('href'),location.href).origin!==location.origin))throw new Error('Workspace style origin');
            if(options.report){
                const region=document.querySelector('[data-fs-report-section]'),next=page.querySelector('[data-fs-report-section]');
                if(!next || !reportPath.test(finalUrl.pathname))throw new Error('Statement response');
                if(page.documentElement.lang===document.documentElement.lang){
                    clearFeedback();
                    region.replaceWith(document.importNode(next,true));
                    for(const selector of ['[data-fs-actions]','.fs-tabs','[data-fs-filter]']){
                        const current=document.querySelector(selector),fresh=page.querySelector(selector);
                        if(!current || !fresh){
                            if(current)current.remove();
                            else if(fresh && selector==='[data-fs-filter]')document.querySelector('.fs-workspace').before(document.importNode(fresh,true));
                            continue;
                        }
                        if(selector==='[data-fs-actions]')current.replaceChildren(...document.importNode(fresh,true).childNodes);
                        else if(selector==='.fs-tabs'){
                            for(const link of current.querySelectorAll('a')){
                                const incoming=[...fresh.querySelectorAll('a')].find(item=>new URL(item.href,location.href).pathname===new URL(link.href).pathname);
                                if(!incoming)continue;
                                link.setAttribute('href',incoming.getAttribute('href'));
                                if(incoming.hasAttribute('aria-current'))link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');
                            }
                        }else if([...current.elements].filter(field=>field.name).map(field=>field.name).join()!==[...fresh.elements].filter(field=>field.name).map(field=>field.name).join()){
                            current.replaceWith(document.importNode(fresh,true));
                        }else{
                            current.setAttribute('action',fresh.getAttribute('action'));
                            for(const field of current.querySelectorAll('input[name]')){
                                const incoming=[...fresh.querySelectorAll('input[name]')].find(item=>item.name===field.name);
                                if(incoming){field.value=incoming.value;field.setAttribute('value',incoming.value);field.dispatchEvent(new Event('change',{bubbles:true}));}
                            }
                        }
                    }
                    viewKey=options.pop?.key || ++serial;
                    const replace=options.pop || options.force && finalUrl.pathname===location.pathname;
                    entry={url:finalUrl.href,index:options.pop?.index ?? (replace?entry.index:entry.index+1)};
                    (replace?native.replace:native.push)(state(history.state),'',finalUrl.href);
                    didCommit=true;
                    document.dispatchEvent(new CustomEvent('workspace:report-navigated'));
                    if(options.tab || options.pop)document.querySelector('.fs-tabs [aria-current="page"]')?.focus({preventScroll:true});
                    if(options.pop)window.scrollTo({...scrollPositions.get(viewKey) || {top:0,left:0},behavior:'instant'});
                    document.dispatchEvent(new CustomEvent('workspace:navigated',{detail:{url:finalUrl.href}}));
                    return true;
                }
            }
            scripts.forEach(script=>script.remove());
            staged=await prepareStyles(page,controller.signal);
            if(version!==generation)return true;
            const sidebar=document.querySelector('.app-sidebar-nav');
            const sidebarScroll=sidebar.scrollTop,closed=document.body.classList.contains('fcms-navigation-closed');
            const groups=new Map([...document.querySelectorAll('details[data-nav-group],details[data-nav-section]')].map(group=>[group.dataset.navGroup || group.dataset.navSection,group.open]));
            prepare(page.querySelector('#main-content'),page.body.dataset.navigationLoading);
            clearFeedback({keepLoading:true});committing=true;didCommit=true;
            dispose();scope=createScope();booting=true;
            replaceHead(page,staged);replaceBody(page);
            for(const name of ['lang','dir']){
                if(page.documentElement.hasAttribute(name))document.documentElement.setAttribute(name,page.documentElement.getAttribute(name));
                else document.documentElement.removeAttribute(name);
            }
            document.body.classList.toggle('fcms-navigation-closed',closed);
            document.body.classList.remove('sidebar-open');document.querySelector('#fcms-sidebar').classList.remove('is-open');
            document.querySelectorAll('details[data-nav-group],details[data-nav-section]').forEach(group=>{const key=group.dataset.navGroup || group.dataset.navSection;if(groups.has(key))group.open=groups.get(key);});
            sidebar.scrollTop=sidebarScroll;
            viewKey=options.pop?.key || ++serial;
            entry={url:finalUrl.href,index:options.pop?.index ?? (options.force && finalUrl.pathname===location.pathname?entry.index:entry.index+1)};
            const marker={...history.state,workspaceNavigation:{key:viewKey,index:entry.index}};
            if(options.pop || options.force && finalUrl.pathname===location.pathname)native.replace(marker,'',finalUrl.href);
            else native.push(marker,'',finalUrl.href);
            document.getElementById('main-content').inert=true;
            const deferred=scripts.filter(script=>script.defer || script.type==='module');
            for(const script of [...scripts.filter(script=>!deferred.includes(script)),...deferred])await execute(script,version);
            for(const callback of scope.ready.splice(0))callback();
            for(const callback of scope.load.splice(0))callback();
            booting=false;
            sidebar.scrollTop=sidebarScroll;
            window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:false}));
            // Let component initialization and pageshow frame work finish beneath the same mask.
            await new Promise(resolve=>timers.requestAnimationFrame(resolve));
            reveal();
            const title=(options.tab && document.querySelector('.fs-tabs [aria-current="page"]')) || document.querySelector('#main-content h1,#main-content .app-table-titlebar h2') || document.getElementById('main-content');
            if(!title.matches('a,button'))title.tabIndex=-1;
            title.focus({preventScroll:true});
            const anchor=finalUrl.hash && document.getElementById(decodeURIComponent(finalUrl.hash.slice(1)));
            if(anchor && !options.pop)anchor.scrollIntoView({block:'start',behavior:'instant'});
            else window.scrollTo({...options.pop && scrollPositions.get(viewKey) || {top:0,left:0},behavior:'instant'});
            document.dispatchEvent(new CustomEvent('workspace:navigated',{detail:{url:finalUrl.href}}));
        }catch(error){
            if(version===generation){
                if(options.pop && !didCommit){reversing=true;history.go(options.previous.index-options.pop.index);}
                reveal();feedback(url.href,{...options,failed:true,committed:didCommit});
            }
        }finally{
            clearTimeout(timeout);
            if(!didCommit)staged?.forEach(link=>link.remove());
            if(version===generation && !didCommit && document.getElementById('workspace-navigation-feedback')?.getAttribute('role')!=='alert')clearFeedback();
            if(didCommit){booting=false;committing=false;if(queued){const next=queued;queued=null;void visit(...next);}}
        }
        return true;
    }
    function navigate(destination,options={}){
        if(eligible(new URL(destination,location.href)))return visit(destination,options);
        location.assign(destination);
    }
    window.WorkspaceNavigation={visit,navigate,handles,handlesForm,onReady,
        present:response=>visit(response.url,{force:true,response}),
        get restoring(){return committing;}};
    native.add.call(window,'click',event=>{
        if(event.defaultPrevented || event.button!==0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey)return;
        const link=event.target.closest?.('a[href]');if(!handles(link))return;
        event.preventDefault();void visit(link.href,{tab:!!link.closest('.fs-tabs'),sidebarLink:link.closest('#fcms-sidebar')?link:undefined});
    });
    native.add.call(window,'submit',event=>{
        if(event.defaultPrevented || !handlesForm(event.target,event.submitter))return;
        const form=event.target,submitter=event.submitter;
        const url=new URL(submitter?.getAttribute('formaction') || form.getAttribute('action') || location.href,location.href);
        url.search='';
        const data=new FormData(form,submitter);
        for(const [name,value] of data)url.searchParams.append(name,typeof value==='string'?value:value.name);
        event.preventDefault();void visit(url.href,{force:true});
    });
    native.add.call(window,'popstate',event=>{
        const marker=event.state?.workspaceNavigation;
        if(reversing){reversing=false;return;}
        if(!marker || marker.key===viewKey)return;
        event.stopImmediatePropagation();
        void visit(location.href,{pop:marker,previous:{...entry}});
    });
    native.add.call(window,'pagehide',()=>{if(!committing){++generation;request?.abort();clearFeedback();}});
})();
