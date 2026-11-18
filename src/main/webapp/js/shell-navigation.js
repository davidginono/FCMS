/* Microfinance shell navigation/search adapted to FCMS's authorized sidebar and lifecycle. */
(() => {
 'use strict';
 const ready=window.WorkspaceNavigation?.onReady || (callback=>document.addEventListener('DOMContentLoaded',callback,{once:true}));
 ready(()=>{
  const logout=document.querySelector('form[data-shell-logout]');
  if(logout){
   let pending=false;
   logout.addEventListener('submit',async event=>{
    event.preventDefault();
    if(pending)return;
    pending=true;
    document.dispatchEvent(new CustomEvent('session:logout'));
    const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),10000);
    try{
     // Login renders the current session's CSRF token, including after another tab signs in.
     const response=await fetch(logout.dataset.loginUrl,{credentials:'same-origin',cache:'no-store',headers:{Accept:'text/html'},signal:controller.signal});
     if(!response.ok||!response.headers.get('content-type')?.includes('text/html'))throw new Error('Session unavailable');
     const page=new DOMParser().parseFromString(await response.text(),'text/html');
     const field=logout.querySelector('input[type="hidden"]');
     const token=[...page.querySelectorAll('form input[type="hidden"]')].find(input=>input.name===field?.name);
     if(!token?.value||token.value.length>2048)throw new Error('Session token unavailable');
     field.value=token.value;
    }catch(error){
     // A failed read has not submitted logout. Retain the protected native POST fallback.
    }finally{
     clearTimeout(timeout);
     if(logout.isConnected)HTMLFormElement.prototype.submit.call(logout);
    }
   });
  }
  const language=document.querySelector('[data-shell-language] select[name="lang"]');
  if(language){
   const currentLanguage=()=>document.documentElement.lang==='sw'?'sw':'en';
   language.value=currentLanguage();
   language.form.addEventListener('submit',event=>{
    if(!['en','sw'].includes(language.value)){event.preventDefault();language.value=currentLanguage();return;}
    if(!window.WorkspaceNavigation||!window.AppLoading)return;
    event.preventDefault();const destination=new URL(location.href);destination.searchParams.set('lang',language.value);
    language.value=currentLanguage();
    window.WorkspaceNavigation.navigate(destination.href);
   });
   language.addEventListener('change',()=>{language.form.requestSubmit();language.value=currentLanguage();});
  }
  const sidebar=document.querySelector('[data-fcms-sidebar-panel]'),nav=sidebar?.querySelector('.app-sidebar-nav');
  const toggle=document.querySelector('[data-fcms-sidebar-toggle]'),close=sidebar?.querySelector('[data-shell-sidebar-close]'),overlay=document.querySelector('[data-fcms-sidebar-overlay]');
  if(!sidebar||!toggle||!nav)return;
  const desktop=matchMedia('(min-width:900px)');
  const shell=document.documentElement,focused=Boolean(document.querySelector('[data-resource-form-page],[data-focused-workspace]'));
  const wasFocused=shell.hasAttribute('data-sidebar-focused-page');
  if(focused){
   if(!wasFocused)shell.dataset.sidebarReturnClosed=String(document.body.classList.contains('fcms-navigation-closed'));
   document.body.classList.add('fcms-navigation-closed');
  }else if(wasFocused){
   document.body.classList.toggle('fcms-navigation-closed',shell.dataset.sidebarReturnClosed==='true');
   delete shell.dataset.sidebarReturnClosed;
  }
  shell.toggleAttribute('data-sidebar-focused-page',focused);
  function sync(){
   const open=desktop.matches?!document.body.classList.contains('fcms-navigation-closed'):sidebar.classList.contains('is-open');
   sidebar.inert=!desktop.matches&&!open;nav.inert=!open;
   for(const button of [toggle,close].filter(Boolean)){button.setAttribute('aria-expanded',String(open));button.setAttribute('aria-label',open?document.body.dataset.navClose:document.body.dataset.navOpen);}
   overlay?.classList.toggle('hidden',desktop.matches||!open);document.body.classList.toggle('sidebar-open',!desktop.matches&&open);
   if(!open&&nav.contains(document.activeElement))(desktop.matches?close:toggle)?.focus();
   window.FcmsModalWorkspace?.sync();
  }
  function flip(){
   if(desktop.matches){
    document.body.classList.toggle('fcms-navigation-closed');
    if(focused)shell.dataset.sidebarReturnClosed=String(document.body.classList.contains('fcms-navigation-closed'));
   }else sidebar.classList.toggle('is-open');
   sync();
  }
  toggle.addEventListener('click',flip);close?.addEventListener('click',flip);
  overlay?.addEventListener('click',()=>{sidebar.classList.remove('is-open');sync();});
  desktop.addEventListener('change',sync);sync();
  const links=[...nav.querySelectorAll('a[href]')];
  function markCurrent(){
  const current=new URL(location.href);let selected,rank=-1;
  const legacyJournal=current.pathname.match(/^(.*)\/accounting\/journals(?:\/\d+)?$/);
  const voucherKind=document.querySelector('[data-voucher-kind]')?.dataset.voucherKind;
  if(legacyJournal && voucherKind)current.pathname=legacyJournal[1]+'/accounting/vouchers/'+({RECEIPT:'receipts',PAYMENT:'payments'}[voucherKind]||'journals');
  for(const link of links){
   link.classList.remove('is-active');link.removeAttribute('aria-current');const url=new URL(link.href);
   const dashboard=/^(?:\/(?:admin|staff|accounting|director))?\/dashboard$/;
   if(current.pathname!==url.pathname&&!(dashboard.test(current.pathname)&&dashboard.test(url.pathname))&&!current.pathname.startsWith(url.pathname+'/'))continue;
   if([...url.searchParams].some(([key,value])=>current.searchParams.get(key)!==value))continue;
   const score=url.pathname.length+url.searchParams.size*100;if(score>rank){rank=score;selected=link;}
  }
  if(selected){selected.classList.add('is-active');selected.setAttribute('aria-current','page');for(let group=selected.closest('details');group;group=group.parentElement.closest('details'))group.open=true;}
  }
  markCurrent();document.addEventListener('workspace:report-navigated',markCurrent);
  const search=document.getElementById('consoleNavigationSearch'),results=document.getElementById('navigation-search-results');
  let options=[],active=-1;
  const hideSearch=()=>{if(!results)return;results.hidden=true;search.setAttribute('aria-expanded','false');search.removeAttribute('aria-activedescendant');active=-1;};
  function activate(index){active=index;options.forEach((link,n)=>link.setAttribute('aria-selected',String(n===active)));if(options[active]){search.setAttribute('aria-activedescendant',options[active].id);options[active].scrollIntoView({block:'nearest'});}}
  function render(){
   results.replaceChildren();const query=search.value.trim().toLocaleLowerCase();options=[];active=-1;
   if(!query){hideSearch();return;}
   const matches=links.filter(link=>link.textContent.trim().toLocaleLowerCase().includes(query)).slice(0,25);
   options=matches.map((link,index)=>{const item=document.createElement('a');item.href=link.href;item.textContent=link.textContent.trim();item.id='navigation-result-'+index;item.setAttribute('role','option');item.setAttribute('aria-selected','false');results.append(item);return item;});
   results.hidden=!options.length;search.setAttribute('aria-expanded',String(options.length>0));
  }
  if(search&&results){
   const binding=window.AppSearch.bind(search,render,hideSearch);
   search.addEventListener('keydown',event=>{
    if(event.key==='Escape'){event.preventDefault();hideSearch();return;}
    if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){
     event.preventDefault();if(results.hidden)render();if(!options.length)return;
     activate(event.key==='Home'?0:event.key==='End'?options.length-1:active<0?(event.key==='ArrowUp'?options.length-1:0):(active+(event.key==='ArrowUp'?-1:1)+options.length)%options.length);
    }
    if(event.key==='Enter'&&!event.isComposing){event.preventDefault();event.stopImmediatePropagation();if(results.hidden)binding.run(true);const destination=options[active>=0?active:0];if(destination){hideSearch();window.WorkspaceNavigation?window.WorkspaceNavigation.navigate(destination.href):location.assign(destination.href);}}
   },true);
   results.addEventListener('click',event=>{if(event.target.closest('a'))hideSearch();});
   search.closest('.app-global-search').addEventListener('focusout',event=>{if(!event.currentTarget.contains(event.relatedTarget))hideSearch();});
  }
  const profile=document.querySelector('[data-shell-profile-toggle]'),panel=document.getElementById('fcms-profile-panel');
  function hideProfile(restore=false){if(!panel)return;panel.hidden=true;profile.setAttribute('aria-expanded','false');if(restore)profile.focus();}
  profile?.addEventListener('click',()=>{panel.hidden=!panel.hidden;profile.setAttribute('aria-expanded',String(!panel.hidden));hideSearch();});
  panel?.addEventListener('click',event=>{if(event.target.closest('a'))hideProfile();});
  profile?.closest('.app-profile-control').addEventListener('focusout',event=>{if(!event.currentTarget.contains(event.relatedTarget))hideProfile();});
  document.addEventListener('click',event=>{if(!event.target.closest('.app-global-search'))hideSearch();if(!event.target.closest('.app-profile-control'))hideProfile();});
  document.addEventListener('keydown',event=>{
   if(event.defaultPrevented||event.key!=='Escape')return;
   if(panel&&!panel.hidden){event.preventDefault();hideProfile(true);return;}
   hideSearch();if(!desktop.matches&&sidebar.classList.contains('is-open')){sidebar.classList.remove('is-open');sync();toggle.focus();}
  });
 });
})();
