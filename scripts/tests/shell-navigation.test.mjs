import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import jsdom from 'jsdom';
const {JSDOM}=jsdom;
const root=new URL('../../',import.meta.url);
const shell=await readFile(new URL('src/main/resources/static/js/shell-navigation.js',root),'utf8');
const search=await readFile(new URL('src/main/resources/static/js/live-search.js',root),'utf8');
const css=await readFile(new URL('src/main/resources/static/css/console-shell.css',root),'utf8');
const tableCss=await readFile(new URL('src/main/resources/static/css/resource-table.css',root),'utf8');
const groupedCss=await readFile(new URL('src/main/resources/static/css/grouped-resource-table.css',root),'utf8');
const fixture=`<header class="app-topbar"><button data-fcms-sidebar-toggle></button><div class="app-global-search"><input id="consoleNavigationSearch"><div id="navigation-search-results" hidden></div></div><form method="get" data-shell-language><select id="lang" name="lang" data-native-select="true" aria-label="Language"><option value="en">ENG</option><option value="sw">SW</option></select></form><div class="app-profile-control"><button data-shell-profile-toggle></button><div id="fcms-profile-panel" hidden><a href="/account">Account</a></div></div><form method="post" action="/logout"><input type="hidden" name="_csrf" value="test"></form></header><aside class="app-sidebar" data-fcms-sidebar-panel><button data-shell-sidebar-close></button><nav class="app-sidebar-nav"><details><summary>Office</summary><a href="/accounting/accounts">Chart of accounts</a><a href="/accounting/accounts/history">Account history</a></details><a href="/tasks">Tasks</a><a href="/dashboard">Dashboard</a></nav></aside><div data-fcms-sidebar-overlay class="hidden"></div><main></main>`;
function setup(t,mobile=false,path='/accounting/accounts'){
 const dom=new JSDOM(`<style>${css}</style><body class="fcms-shell aws-console" data-nav-open="Open navigation" data-nav-close="Close navigation">${fixture}</body>`,{url:'http://fcms.test'+path,runScripts:'outside-only',pretendToBeVisual:true});
 t.after(()=>dom.window.close());const {window:w}=dom,urls=[],destinations=[];
 w.AppLoading={};w.matchMedia=()=>({matches:!mobile,addEventListener(){}});w.WorkspaceNavigation={onReady:callback=>callback(),navigate:url=>{destinations.push(url);urls.push(new URL(url).pathname);}};
 w.HTMLElement.prototype.scrollIntoView=function(){};
 w.eval(search);w.eval(shell);return {w,d:w.document,urls,destinations};
}
function key(w,target,value){target.dispatchEvent(new w.KeyboardEvent('keydown',{key:value,bubbles:true,cancelable:true}));}

test('desktop close/expand uses the source rail while keeping the rail button operable',t=>{
 const {d}=setup(t),sidebar=d.querySelector('aside'),nav=d.querySelector('nav'),close=d.querySelector('[data-shell-sidebar-close]');
 assert.equal(nav.inert,false);close.click();assert.equal(d.body.classList.contains('fcms-navigation-closed'),true);assert.equal(sidebar.inert,false);assert.equal(nav.inert,true);assert.equal(close.getAttribute('aria-label'),'Open navigation');
 close.click();assert.equal(nav.inert,false);assert.equal(close.getAttribute('aria-expanded'),'true');
 assert.equal(d.querySelector('[aria-current=page]').textContent,'Chart of accounts');
});

test('mobile navigation uses overlay, native close and Escape focus restoration',t=>{
 const {w,d}=setup(t,true),toggle=d.querySelector('[data-fcms-sidebar-toggle]'),overlay=d.querySelector('[data-fcms-sidebar-overlay]');
 toggle.click();assert.equal(overlay.classList.contains('hidden'),false);assert.equal(d.querySelector('aside').inert,false);
 key(w,d,'Escape');assert.equal(overlay.classList.contains('hidden'),true);assert.equal(d.activeElement,toggle);
 toggle.click();overlay.click();assert.equal(d.body.classList.contains('sidebar-open'),false);
});

test('navigation search uses only authorized links and Enter preserves the highlighted result',t=>{
 const {w,d,urls}=setup(t),input=d.getElementById('consoleNavigationSearch');
 input.value='account';key(w,input,'ArrowDown');key(w,input,'ArrowDown');
 assert.equal(d.querySelector('[aria-selected=true]').textContent,'Account history');
 key(w,input,'Enter');assert.deepEqual(urls,['/accounting/accounts/history']);
 input.value='administrator';key(w,input,'ArrowDown');assert.equal(d.getElementById('navigation-search-results').hidden,true);
});

test('profile controls close on Escape/outside click and logout remains a native CSRF form',t=>{
 const {w,d}=setup(t),button=d.querySelector('[data-shell-profile-toggle]'),panel=d.getElementById('fcms-profile-panel');
 button.click();assert.equal(panel.hidden,false);key(w,d,'Escape');assert.equal(panel.hidden,true);assert.equal(d.activeElement,button);
 button.click();d.querySelector('main').click();assert.equal(panel.hidden,true);
 const form=d.querySelector('form[action="/logout"]');assert.equal(form.method,'post');assert.equal(form.querySelector('[name=_csrf]').value,'test');
 const event=new w.Event('submit',{bubbles:true,cancelable:true});form.dispatchEvent(event);assert.equal(event.defaultPrevented,false);
});

test('navbar language select keeps the current route, query and fragment, then restores the displayed language until navigation completes',t=>{
 const {w,d,destinations}=setup(t,false,'/accounting/statements/cash-flow?year=2026&search=A%26B&lang=en#notes');
 const select=d.getElementById('lang');select.value='sw';select.dispatchEvent(new w.Event('change',{bubbles:true}));
 assert.equal(destinations.length,1);const url=new URL(destinations[0]);
 assert.equal(url.pathname,'/accounting/statements/cash-flow');assert.equal(url.searchParams.get('year'),'2026');
 assert.equal(url.searchParams.get('search'),'A&B');assert.equal(url.searchParams.get('lang'),'sw');assert.equal(url.hash,'#notes');
 assert.equal(select.value,'en','Cancelled navigation must not display an unapplied language');
 assert.equal(select.name,'lang');assert.equal(select.dataset.nativeSelect,'true');
 assert.equal(select.form.method,'get');
});

test('language control rejects unsupported values without navigating',t=>{
 const {w,d,destinations}=setup(t),select=d.getElementById('lang');
 select.value='unsupported';select.dispatchEvent(new w.Event('change',{bubbles:true}));
 assert.equal(destinations.length,0);assert.equal(select.value,'en');
});

test('COA group rows share one surface and selection remains visibly distinct',t=>{
 const {w,d}=setup(t);const style=d.createElement('style');style.textContent=tableCss+'\n'+groupedCss;d.head.append(style);
 const section=d.createElement('section');section.dataset.resourceTable='coa';section.dataset.coaTree='';section.innerHTML='<table class="erp-table coa-table"><tbody><tr class="coa-group-row"><td>Select</td><td>111000</td></tr></tbody></table>';d.querySelector('main').append(section);
 const row=section.querySelector('tr'),cells=row.children;
 assert.equal(w.getComputedStyle(cells[0]).backgroundColor,'rgb(250, 250, 250)');assert.equal(w.getComputedStyle(cells[1]).backgroundColor,'rgb(250, 250, 250)');
 row.classList.add('resource-table-selected');assert.equal(w.getComputedStyle(cells[0]).backgroundColor,'rgb(241, 250, 255)');
});


test('dashboard landing paths retain shell selection and open service navigation',t=>{
 for(const path of ['/dashboard','/admin/dashboard','/staff/dashboard','/accounting/dashboard','/director/dashboard']){
  const {d}=setup(t,false,path);
  assert.equal(d.querySelector('[aria-current=page]').textContent,'Dashboard');
  assert.equal(d.body.classList.contains('fcms-navigation-closed'),false);
 }
});

test('sidebar parents start expanded, share filled triangles and follow business workflow order',async()=>{
 const sidebar=await readFile(new URL('src/main/webapp/WEB-INF/jsp/reusableuicomponents/navigation/sidebar.jspf',root),'utf8');
 const finance=await readFile(new URL('src/main/webapp/WEB-INF/jsp/reusableuicomponents/navigation/finance-navigation.jspf',root),'utf8');
 const caret=await readFile(new URL('src/main/webapp/WEB-INF/tags/navigationCaret.tag',root),'utf8');
 const markup=sidebar.replace('<%@ include file="finance-navigation.jspf" %>',finance);
 const parents=[...markup.matchAll(/<details\b([^>]*)>\s*<summary\b[^>]*>(.*?)<\/summary>/gs)];
 assert.equal(parents.length,9);
 for(const [,attributes,summary] of parents){assert.match(attributes,/\bopen\b/);assert.match(summary,/<ui:navigationCaret\s*\/>/);}
 const groups=parents.map(([,attributes])=>attributes.match(/data-nav-group="([^"]+)"/)[1]);
 assert.deepEqual(groups,['clients','sales','accounting-office','financial-transactions','vouchers','accounting-configuration','financial-reports','operations','administration']);
 assert.match(caret,/M8 11L12 5H4L8 11Z/);
 assert.match(css,/fill:currentColor/);
 assert.match(css,/details:not\(\[open\]\) > summary > \.app-navigation-caret/);
 assert.equal(markup.includes('<section class="app-sidebar-section-group"'),false);
});
