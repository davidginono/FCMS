import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import jsdom from 'jsdom';
const {JSDOM,VirtualConsole,requestInterceptor}=jsdom;

const root=new URL('../../',import.meta.url);
const sources=new Map();
const loadingCss=await readFile(new URL('src/main/resources/static/css/ui-loading.css',root),'utf8');
const statementCss=await readFile(new URL('src/main/resources/static/css/financial-statements.css',root),'utf8');
for(const name of ['workspace-navigation','ui-loading','form-ux','app','financial-statement-view','shell-navigation'])sources.set('/js/'+name+'.js',await readFile(new URL('src/main/resources/static/js/'+name+'.js',root)));
sources.set('/js/view.js',Buffer.from(`window.WorkspaceNavigation.onReady(()=>{
 document.addEventListener('test:command',()=>{window.commands=(window.commands||0)+1;});
 new MutationObserver(()=>{}).observe(document.getElementById('main-content'),{childList:true});
});`));
const scripts=['workspace-navigation','ui-loading','view'].map(n=>`<script defer ${n==='workspace-navigation'?'data-workspace-navigation':''} src="/js/${n}.js"></script>`).join('');
const sidebar='<aside id="fcms-sidebar"><nav class="app-sidebar-nav"><details data-nav-section="office" open><summary>Office</summary><a href="/one">One</a><a href="/two">Two</a></details></nav></aside>';
const modal='<div id="fcms-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-fcms-keep>Keep editing</button><button data-fcms-discard>Discard</button></div></div></div>';
function page(name,{content='',form=false,styles='',extraScripts=''}={}){
 return `<!doctype html><html><head><title>${name}</title><link rel="stylesheet" href="/css/shared.css">${styles}${scripts}${extraScripts}</head><body class="fcms-shell aws-console" data-navigation-loading="Loading" data-shell-loading="Loading" data-navigation-error="Unable to load" data-navigation-retry="Retry"><header class="app-topbar"><a href="/one">FCMS</a></header>${sidebar}<main id="main-content" tabindex="-1"><h1>${name}</h1>${content}${form?'<form class="fcms-form" method="post" action="/post"><input name="_csrf" value="token" type="hidden"><input name="amount" value="10"><button type="submit">Save</button></form>':''}</main>${modal}${form?'<script defer src="/js/form-ux.js"></script>':''}</body></html>`;
}
function response(body,url='http://fcms.test/two',status=200){return {ok:status>=200&&status<300,status,url,headers:new Headers({'Content-Type':'text/html'}),text:async()=>body};}
async function until(predicate){for(let n=0;n<200;n++){if(predicate())return;await new Promise(resolve=>setTimeout(resolve,5));}throw Error('Condition did not settle');}
async function harness(initial=page('One'),fetcher=async url=>response(page(new URL(url).pathname==='/one'?'One':'Two'),String(url)),assetOverride,url='http://fcms.test/one'){
 const failures=[];
 let nativeObserver;
 const resources={interceptors:[requestInterceptor(request=>{const path=new URL(request.url).pathname;return assetOverride?.(request) ?? new Response(path.endsWith('.css')?loadingCss+statementCss:sources.get(path)||'',{headers:{'Content-Type':path.endsWith('.css')?'text/css':'application/javascript'}});})]};
 const console=new VirtualConsole();console.on('jsdomError',error=>failures.push(error));
 const dom=new JSDOM(initial,{url,runScripts:'dangerously',resources,pretendToBeVisual:true,virtualConsole:console,beforeParse(window){
  nativeObserver=window.MutationObserver;
  window.fetch=fetcher;window.scrollTo=()=>{};window.matchMedia=()=>({matches:true,addEventListener(){}});
 }});
 await new Promise(resolve=>dom.window.addEventListener('load',resolve,{once:true}));
 return {dom,window:dom.window,document:dom.window.document,nativeObserver,failures,close:()=>dom.window.close()};
}

test('GET navigation preserves shell identity, groups, scroll and a single handler per workspace',async t=>{
 const h=await harness();t.after(h.close);
 const {window:w,document:d}=h,header=d.querySelector('header'),aside=d.querySelector('aside'),nav=d.querySelector('nav');
 nav.scrollTop=73;d.querySelector('details').open=false;
 for(let n=0;n<3;n++){
  await w.WorkspaceNavigation.visit('http://fcms.test/'+(n%2?'one':'two'),{force:true});
  assert.equal(d.querySelector('header'),header);assert.equal(d.querySelector('aside'),aside);
  assert.equal(nav.scrollTop,73);assert.equal(d.querySelector('details').open,false);
  w.document.dispatchEvent(new w.Event('test:command'));assert.equal(w.commands,n+1);
  assert.equal(d.querySelectorAll('#workspace-navigation-feedback').length,0);
  assert.equal(d.querySelector('#main-content').inert,false);
 }
 assert.deepEqual(h.failures,[]);
});

test('tab requests show only the spinner and reject obsolete responses',async t=>{
 const pending=[];const tabs='<div class="fs-workspace"><nav class="fs-tabs"><a href="/two">Two</a><a href="/three">Three</a></nav><section>Ready</section></div>';
 const h=await harness(page('One',{content:tabs}),(url,options)=>new Promise(resolve=>pending.push({url,options,resolve})));t.after(h.close);
 const {window:w,document:d}=h,tab=d.querySelector('.fs-tabs');
 const first=w.WorkspaceNavigation.visit('/two',{tab:true});
 assert.equal(d.querySelector('.fs-tabs'),tab);assert.equal(d.querySelector('#main-content').getAttribute('aria-busy'),'true');
 assert.equal(w.getComputedStyle(tab).visibility,'hidden');
 assert.equal(d.querySelectorAll('#workspace-navigation-feedback .app-spinner-circle').length,2);
 const second=w.WorkspaceNavigation.visit('/three',{tab:true});
 assert.equal(pending[0].options.signal.aborted,true);
 pending[1].resolve(response(page('Three'),'http://fcms.test/three'));await second;
 pending[0].resolve(response(page('Stale'),'http://fcms.test/two'));await first;
 assert.equal(d.querySelector('h1').textContent,'Three');assert.equal(w.location.pathname,'/three');
 assert.equal(d.documentElement.hasAttribute('data-workspace-loading'),false);assert.deepEqual(h.failures,[]);
});

test('failure retains original input nodes and values and offers a GET retry',async t=>{
 const h=await harness(page('One',{content:'<input id="draft" value="retained">'}),async()=>{throw Error('offline');});t.after(h.close);
 const input=h.document.getElementById('draft');input.value='changed';
 await h.window.WorkspaceNavigation.visit('/two');
 assert.equal(h.document.getElementById('draft'),input);assert.equal(input.value,'changed');
 assert.equal(h.window.location.pathname,'/one');assert.equal(h.document.querySelector('#workspace-navigation-feedback').getAttribute('role'),'alert');
 assert.equal(h.document.documentElement.hasAttribute('data-workspace-loading'),false);
 assert.equal(h.window.getComputedStyle(input).visibility,'visible');
});

test('only the spinner is exposed from dashboard fetch through incoming script and frame initialization',async t=>{
 let resolveFetch,releaseScript;
 const dashboard=page('Dashboard').replace('<h1>Dashboard</h1>','<nav class="erp-page-path">Home</nav><div class="app-page-header"><h1>Dashboard</h1><button>Old action</button></div>');
 const h=await harness(dashboard,()=>new Promise(resolve=>resolveFetch=resolve),request=>new URL(request.url).pathname==='/js/deferred.js'?new Promise(resolve=>releaseScript=resolve):undefined);t.after(h.close);
 const {window:w,document:d}=h;
 const navigation=w.WorkspaceNavigation.visit('/two');
 for(const selector of ['.app-page-header','.erp-page-path']){
  assert.equal(w.getComputedStyle(d.querySelector(selector)).visibility,'hidden');
  assert.equal(w.getComputedStyle(d.querySelector(selector)).opacity,'0');
 }
 assert.equal(w.getComputedStyle(d.querySelector('.app-topbar')).visibility,'visible');
 assert.equal(w.getComputedStyle(d.querySelector('#workspace-navigation-feedback')).visibility,'visible');
 const incoming=page('Incoming',{extraScripts:'<script defer src="/js/deferred.js"></script>'});
 const sampled=[];
 const observer=new h.nativeObserver(()=>{
  const heading=d.querySelector('main h1');
  if(heading?.textContent==='Incoming')sampled.push(w.getComputedStyle(heading).visibility);
 });observer.observe(d.body,{childList:true,subtree:true});
 resolveFetch(response(incoming));await until(()=>!!releaseScript);
 assert.equal(d.querySelector('main h1').textContent,'Incoming');
 assert.equal(w.getComputedStyle(d.querySelector('main h1')).visibility,'hidden');
 assert.equal(w.getComputedStyle(d.querySelector('[data-workspace-boot-status]')).visibility,'visible');
 assert.equal(d.querySelectorAll('main .app-loading-spinner').length,1);
 releaseScript(new Response(`requestAnimationFrame(()=>{window.frameVisibility=getComputedStyle(document.querySelector('main h1')).visibility;document.querySelector('main h1').textContent='Ready';});`,{headers:{'Content-Type':'application/javascript'}}));
 await navigation;observer.disconnect();
 assert.equal(w.frameVisibility,'hidden');assert.ok(sampled.length>0);assert.ok(sampled.every(value=>value==='hidden'));
 assert.equal(d.querySelector('main h1').textContent,'Ready');assert.equal(w.getComputedStyle(d.querySelector('main h1')).visibility,'visible');
 assert.equal(d.querySelector('main .app-loading-spinner'),null);assert.equal(d.documentElement.hasAttribute('data-workspace-loading'),false);
 assert.deepEqual(h.failures,[]);
});

test('dirty forms cancel workspace switching and preserve values until explicit discard',async t=>{
 let requests=0;const h=await harness(page('One',{form:true}),async url=>{requests++;return response(page('Two'),String(url));});t.after(h.close);
 const input=h.document.querySelector('[name=amount]');input.value='25';
 assert.equal(await h.window.WorkspaceNavigation.visit('/two'),false);assert.equal(requests,0);
 assert.equal(h.document.getElementById('fcms-discard-modal').hidden,false);
 h.document.querySelector('[data-fcms-keep]').click();assert.equal(input.value,'25');
 await h.window.WorkspaceNavigation.visit('/two');h.document.querySelector('[data-fcms-discard]').click();
 await until(()=>h.window.location.pathname==='/two'&&!h.window.WorkspaceNavigation.restoring);
 assert.equal(requests,1);assert.deepEqual(h.failures,[]);
});

test('financial POSTs, downloads, modified clicks and external URLs keep native ownership',async t=>{
 let requests=0;const h=await harness(page('One'),async()=>{requests++;return response(page('Two'));});t.after(h.close);
 const {window:w,document:d}=h;
 for(const html of ['<form method="post" action="/post"><button>Post</button></form>','<form method="get" action="/report.pdf"><button>PDF</button></form>']){
  const region=d.createElement('div');region.innerHTML=html;d.querySelector('main').append(region);
  assert.equal(w.WorkspaceNavigation.handlesForm(region.firstChild,region.querySelector('button')),false);
  const event=new w.Event('submit',{bubbles:true,cancelable:true});region.firstChild.dispatchEvent(event);assert.equal(event.defaultPrevented,false);
 }
 for(const href of ['https://elsewhere.test/page','blob:http://fcms.test/opaque','/report.pdf','/backups/id/download','/login','#section']){
  const link=d.createElement('a');link.href=href;assert.equal(w.WorkspaceNavigation.handles(link),false,href);
 }
 const link=d.querySelector('aside a');let captured;
 d.addEventListener('click',event=>{captured=event.defaultPrevented;event.preventDefault();},{once:true});
 link.dispatchEvent(new w.MouseEvent('click',{bubbles:true,cancelable:true,ctrlKey:true}));assert.equal(captured,false);assert.equal(requests,0);
});

test('links retain native behavior while the loading dependency is unavailable',async t=>{
 const h=await harness();t.after(h.close);h.window.AppLoading=undefined;
 assert.equal(h.window.WorkspaceNavigation.handles(h.document.querySelector('aside a')),false);
 assert.equal(await h.window.WorkspaceNavigation.visit('/two'),false);
});

test('GET filters preserve named fields and selected submitter without submitting a POST',async t=>{
 const requests=[];const h=await harness(page('One',{content:'<form id="filter" method="get" action="/two"><input name="from" value="2026-01-01"><input name="to" value="2026-11-16"><button name="view" value="cash">Apply</button></form>'}),async url=>{requests.push(new URL(url));return response(page('Two'),String(url));});t.after(h.close);
 const form=h.document.getElementById('filter'),button=form.querySelector('button');
 form.dispatchEvent(new h.window.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:button}));
 await until(()=>!h.window.WorkspaceNavigation.restoring&&h.window.location.pathname==='/two');
 assert.equal(requests.length,1);assert.equal(requests[0].searchParams.get('from'),'2026-01-01');assert.equal(requests[0].searchParams.get('to'),'2026-11-16');assert.equal(requests[0].searchParams.get('view'),'cash');
});

test('Back navigation restores workspace without duplicating history entries',async t=>{
 const h=await harness();t.after(h.close);
 await h.window.WorkspaceNavigation.visit('/two');const size=h.window.history.length;
 h.window.history.back();await until(()=>h.window.location.pathname==='/one'&&!h.window.WorkspaceNavigation.restoring&&h.document.querySelector('h1').textContent==='One');
 h.window.history.forward();await until(()=>h.window.location.pathname==='/two'&&!h.window.WorkspaceNavigation.restoring&&h.document.querySelector('h1').textContent==='Two');
 assert.equal(h.window.history.length,size);assert.deepEqual(h.failures,[]);
});

test('Back from a dirty form restores the displayed URL and supports keep/discard',async t=>{
 const h=await harness(page('One'),async url=>response(page(new URL(url).pathname==='/two'?'Two':'One',{form:new URL(url).pathname==='/two'}),String(url)));t.after(h.close);
 const {window:w,document:d}=h;await w.WorkspaceNavigation.visit('/two');
 const input=d.querySelector('[name=amount]');input.value='87';
 w.history.back();await until(()=>!d.getElementById('fcms-discard-modal').hidden&&w.location.pathname==='/two');
 d.querySelector('[data-fcms-keep]').click();assert.equal(input.value,'87');assert.equal(d.querySelector('h1').textContent,'Two');
 w.history.back();await until(()=>!d.getElementById('fcms-discard-modal').hidden&&w.location.pathname==='/two');
 d.querySelector('[data-fcms-discard]').click();await until(()=>w.location.pathname==='/one'&&d.querySelector('h1').textContent==='One'&&!w.WorkspaceNavigation.restoring);
 assert.deepEqual(h.failures,[]);
});

test('in-flight mutation requests retain their owner and cannot be abandoned by workspace navigation',async t=>{
 let finish;const requests=[];const h=await harness(page('One'),(url,options)=>{requests.push({url,options});return new Promise(resolve=>finish=resolve);});t.after(h.close);
 const controller=new h.window.AbortController();const write=h.window.fetch('/post',{method:'POST',signal:controller.signal});
 assert.equal(await h.window.WorkspaceNavigation.visit('/two'),false);assert.equal(requests.length,1);assert.equal(controller.signal.aborted,false);
 finish(response('done'));await write;assert.equal(requests[0].options.signal,controller.signal);
});

test('shared loading preserves live nodes and action labels during recovery',async t=>{
 const h=await harness();t.after(h.close);
 const {window:w,document:d}=h,region=d.createElement('section'),input=d.createElement('input');
 region.dataset.uiLoadingRegion='';input.value='draft';region.append(input);d.querySelector('main').append(region);
 w.AppLoading.begin(region,{table:false,replace:true});assert.equal(region.querySelectorAll('.app-spinner-circle').length,2);
 w.AppLoading.end(region);assert.equal(region.firstChild,input);assert.equal(input.value,'draft');
 const button=d.createElement('button');button.textContent='Create';region.append(button);
 const finish=w.AppLoading.action(button);assert.equal(button.querySelector('.app-action-label').textContent,'Create');finish();
 assert.equal(button.textContent,'Create');assert.equal(button.hasAttribute('aria-busy'),false);
});

test('language changes update document language, page title and localized shell data',async t=>{
 const localized=page('Ripoti').replace('<html>','<html lang="sw">').replace('data-navigation-loading="Loading"','data-navigation-loading="Inapakia"');
 const h=await harness(page('One'),async url=>response(localized,String(url)));t.after(h.close);
 await h.window.WorkspaceNavigation.visit('/two?lang=sw');
 assert.equal(h.document.documentElement.lang,'sw');assert.equal(h.document.title,'Ripoti');assert.equal(h.document.body.dataset.navigationLoading,'Inapakia');
});

test('navbar language switching honors dirty-form Keep/Discard and refreshes selection without duplicate listeners',async t=>{
 function localized(lang,form=true){
  return page(lang==='sw'?'Unda mtumiaji':'Create user',{form,extraScripts:'<script defer src="/js/shell-navigation.js"></script>'})
   .replace('<html>',`<html lang="${lang}">`)
   .replace('</header>',`<form method="get" data-shell-language><select id="lang" name="lang" aria-label="${lang==='sw'?'Lugha':'Language'}"><option value="en" ${lang==='en'?'selected':''}>ENG</option><option value="sw" ${lang==='sw'?'selected':''}>SW</option></select></form></header>`);
 }
 const requests=[];
 const h=await harness(localized('en'),async url=>{requests.push(String(url));return response(localized(new URL(url).searchParams.get('lang')),String(url));},undefined,'http://fcms.test/one?year=2026&search=A%26B');t.after(h.close);
 const {window:w,document:d}=h,input=d.querySelector('[name=amount]');input.value='20';
 function selectLanguage(value){const select=d.getElementById('lang');select.value=value;select.dispatchEvent(new w.Event('change',{bubbles:true}));}
 selectLanguage('sw');assert.equal(requests.length,0);assert.equal(d.getElementById('lang').value,'en');
 assert.equal(d.getElementById('fcms-discard-modal').hidden,false);d.querySelector('[data-fcms-keep]').click();
 assert.equal(input.value,'20');selectLanguage('sw');d.querySelector('[data-fcms-discard]').click();
 await until(()=>d.documentElement.lang==='sw'&&!d.documentElement.hasAttribute('data-workspace-loading'));
 assert.equal(d.getElementById('lang').value,'sw');assert.equal(d.querySelector('[name=amount]').value,'10');
 assert.equal(new URL(requests[0]).searchParams.get('year'),'2026');assert.equal(new URL(requests[0]).searchParams.get('search'),'A&B');
 selectLanguage('en');await until(()=>d.documentElement.lang==='en'&&!d.documentElement.hasAttribute('data-workspace-loading'));
 assert.equal(d.getElementById('lang').value,'en');assert.equal(requests.length,2);assert.deepEqual(h.failures,[]);
});

test('real app script can initialize repeatedly without redeclaration or duplicate shell listeners',async t=>{
 const extraScripts='<script defer src="/js/app.js"></script>';
 const h=await harness(page('One',{extraScripts}),async url=>response(page('Two',{extraScripts}),String(url)));t.after(h.close);
 await h.window.WorkspaceNavigation.visit('/two');await h.window.WorkspaceNavigation.visit('/one');
 assert.deepEqual(h.failures,[]);
});

const reportPath=kind=>kind==='trial'?'/accounting/trial-balance':'/accounting/statements/'+kind;
function reportPage(kind='income-statement',to='2026-11-17'){
 const path=reportPath(kind),params='?from=2026-01-01&to='+to;
 return page('Reports Preview',{extraScripts:'<script defer src="/js/financial-statement-view.js"></script>',content:`
 <div data-fs-actions><a data-print-action href="${path}/print${params}">Print</a><a data-download-action href="${path}/print.pdf${params}">PDF</a></div>
 <form data-fs-filter method="get" action="${path}"><input name="from" value="2026-01-01"><input name="to" value="${to}"><button>Apply</button></form>
 <div class="fs-workspace"><nav class="fs-tabs">${['balance-sheet','income-statement','cash-flow','changes-in-equity','notes','trial'].map(k=>`<a href="${reportPath(k)+params}" ${k===kind?'aria-current="page"':''}>${k}</a>`).join('')}</nav>
 <div class="fs-toolbar"><details class="fs-view-settings"><summary>View settings</summary><select data-fs-font><option>reference</option><option>sans</option><option>serif</option><option>mono</option></select><select data-fs-density><option>compact</option><option>comfortable</option></select><label data-fs-comparison-setting><input type="checkbox" data-fs-comparison></label></details></div>
 <div data-fs-report-section><article data-fs-paper data-kind="${kind}"><h2>${kind}</h2><input id="retained" value="ready"></article></div></div>`});
}
const reportUrl='http://fcms.test'+reportPath('income-statement')+'?from=2026-01-01&to=2026-11-17';

test('a superseded report body cannot commit after a newer response',async t=>{
 let releaseBody,bodyStarted=false;
 const h=await harness(reportPage(),async url=>{
  const next=response(reportPage('notes'),String(url));
  if(new URL(url).pathname===reportPath('cash-flow'))next.text=()=>{bodyStarted=true;return new Promise(resolve=>releaseBody=()=>resolve(reportPage('cash-flow')));};
  return next;
 },undefined,reportUrl);t.after(h.close);
 const first=h.window.WorkspaceNavigation.visit(reportPath('cash-flow'));await until(()=>bodyStarted);
 await h.window.WorkspaceNavigation.visit(reportPath('notes'));
 releaseBody();await first;
 assert.equal(h.document.querySelector('[data-fs-paper]').dataset.kind,'notes');assert.equal(h.window.location.pathname,reportPath('notes'));assert.deepEqual(h.failures,[]);
});

test('report tab loads below persistent tabs and settings, then commits report and export context together',async t=>{
 let finish;const h=await harness(reportPage(),url=>new Promise(resolve=>finish=()=>resolve(response(reportPage('cash-flow'),String(url)))),undefined,reportUrl);t.after(h.close);
 const {window:w,document:d}=h,main=d.querySelector('main'),tabs=d.querySelector('.fs-tabs'),settings=d.querySelector('.fs-view-settings'),filter=d.querySelector('[data-fs-filter]');
 settings.open=true;
 const navigation=w.WorkspaceNavigation.visit(reportPath('cash-flow'),{tab:true});
 for(const element of [d.querySelector('h1'),tabs,settings,filter])assert.equal(w.getComputedStyle(element).visibility,'visible');
 assert.equal(main.hasAttribute('aria-busy'),false);assert.equal(d.documentElement.hasAttribute('data-workspace-loading'),false);
 assert.equal(d.querySelector('[data-fs-report-section]').getAttribute('aria-busy'),'true');
 assert.equal(w.getComputedStyle(d.querySelector('[data-fs-paper]')).visibility,'hidden');
 assert.equal(d.querySelectorAll('[data-fs-report-section] .app-spinner-circle').length,2);
 finish();await navigation;
 assert.equal(d.querySelector('main'),main);assert.equal(d.querySelector('.fs-tabs'),tabs);assert.equal(d.querySelector('.fs-view-settings'),settings);assert.equal(settings.open,true);assert.equal(d.querySelector('[data-fs-filter]'),filter);
 assert.equal(d.querySelector('[data-fs-paper]').dataset.kind,'cash-flow');
 assert.equal(new URL(d.querySelector('[data-download-action]').href).pathname,reportPath('cash-flow')+'/print.pdf');
 assert.equal(d.querySelector('[data-fs-filter]').getAttribute('action'),reportPath('cash-flow'));
 assert.equal(d.activeElement,d.querySelector('.fs-tabs [aria-current]'));assert.equal(d.querySelector('.app-loading-spinner'),null);
 assert.deepEqual(h.failures,[]);
});

test('report supersession, cancellation and failure retain the last completed report with a local retry',async t=>{
 const pending=[];const h=await harness(reportPage(),(url,options)=>new Promise((resolve,reject)=>pending.push({url,options,resolve,reject})),undefined,reportUrl);t.after(h.close);
 const {window:w,document:d}=h,retained=d.getElementById('retained');
 const first=w.WorkspaceNavigation.visit(reportPath('cash-flow'),{tab:true});
 const second=w.WorkspaceNavigation.visit(reportPath('notes'),{tab:true});assert.equal(pending[0].options.signal.aborted,true);
 pending[1].reject(Error('offline'));await second;
 assert.equal(d.getElementById('retained'),retained);assert.equal(w.getComputedStyle(retained).visibility,'visible');
 assert.equal(d.querySelector('[data-fs-report-section] [role=alert]')?.id,'workspace-navigation-feedback');assert.equal(w.location.href,reportUrl);
 d.querySelector('[role=alert] button').click();pending[2].resolve(response(reportPage('notes'),String(pending[2].url)));
 await until(()=>d.querySelector('[data-fs-paper]').dataset.kind==='notes');
 pending[0].resolve(response(reportPage('cash-flow'),String(pending[0].url)));await first;
 assert.equal(d.querySelector('[data-fs-paper]').dataset.kind,'notes');
 const cancel=w.WorkspaceNavigation.visit(reportPath('cash-flow'));
 await w.WorkspaceNavigation.visit(w.location.href);assert.equal(pending[3].options.signal.aborted,true);
 pending[3].resolve(response(reportPage('cash-flow'),String(pending[3].url)));await cancel;
 assert.equal(d.querySelector('[data-fs-paper]').dataset.kind,'notes');assert.equal(d.querySelector('[aria-busy=true]'),null);assert.deepEqual(h.failures,[]);
});

test('report Back/Forward and dates update in place, restore preferences and keep native exports',async t=>{
 const requests=[];const h=await harness(reportPage(),async url=>{const parsed=new URL(url);requests.push(parsed);const kind=parsed.pathname.split('/').at(-1);return response(reportPage(kind,parsed.searchParams.get('to')||'2026-11-17'),String(url));},undefined,reportUrl);t.after(h.close);
 const {window:w,document:d}=h,main=d.querySelector('main'),settings=d.querySelector('.fs-view-settings');let initialized=0;
 w.ResourceTable={init:()=>initialized++};
 await w.WorkspaceNavigation.visit(reportPath('cash-flow'));
 const font=d.querySelector('[data-fs-font]');font.value='mono';font.dispatchEvent(new w.Event('change'));
 await w.WorkspaceNavigation.visit(reportPath('notes'));
 w.history.back();await until(()=>w.location.pathname===reportPath('cash-flow')&&d.querySelector('[data-fs-paper]').dataset.kind==='cash-flow');
 assert.equal(d.querySelector('[data-fs-paper]').dataset.font,'mono');
 w.history.forward();await until(()=>d.querySelector('[data-fs-paper]').dataset.kind==='notes');
 d.querySelector('[name=to]').value='2026-10-31';d.querySelector('[data-fs-filter]').dispatchEvent(new w.SubmitEvent('submit',{bubbles:true,cancelable:true}));
 await until(()=>d.querySelector('[data-download-action]').href.includes('2026-10-31'));
 assert.equal(requests.at(-1).pathname,reportPath('notes'));assert.equal(requests.at(-1).searchParams.get('to'),'2026-10-31');
 await w.WorkspaceNavigation.visit(reportPath('trial'));
 assert.equal(d.querySelector('[data-fs-comparison-setting]').hidden,true);assert.ok(initialized>=6);
 assert.equal(d.querySelector('main'),main);assert.equal(d.querySelector('.fs-view-settings'),settings);
 for(const link of d.querySelectorAll('[data-fs-actions] a'))assert.equal(w.WorkspaceNavigation.handles(link),false);
 assert.deepEqual(h.failures,[]);
});

test('creation presents an already-followed redirect with its flash without fetching the list a second time',async t=>{
 const h=await harness(page('Create'),async()=>assert.fail('The destination response must not be fetched twice'));t.after(h.close);
 const html=page('Resources',{content:'<div data-resource-notice data-message-type="success" role="status">Created</div>'});
 await h.window.WorkspaceNavigation.present(response(html,'http://fcms.test/resources?_notice=once'));
 assert.equal(h.document.querySelector('[data-resource-notice]').textContent,'Created');
 assert.equal(h.window.location.pathname,'/resources');assert.deepEqual(h.failures,[]);
});
