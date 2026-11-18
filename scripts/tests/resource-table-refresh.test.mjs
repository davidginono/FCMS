import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

const source=await readFile(new URL('../../src/main/resources/static/js/resource-table.js',import.meta.url),'utf8');
const loading=await readFile(new URL('../../src/main/resources/static/js/ui-loading.js',import.meta.url),'utf8');
const labels={en:{loadError:'Could not load the table. Try refreshing again.',refresh:'Refresh'},sw:{loadError:'Jedwali halikupakiwa. Jaribu kuonyesha upya.',refresh:'Onyesha upya'}};
function page(name='Original',lang='en'){
 const l=labels[lang];
 return `<html lang="${lang}"><body class="aws-console" data-shell-loading="Loading"><template id="resource-table-labels" data-actions="Actions" data-records="Users" data-select="Select" data-view="View" data-select-first="Select first" data-clear-selection="Clear" data-refresh="${l.refresh}" data-load-error="${l.loadError}"></template>
 <section class="erp-table-wrap" data-resource-table="users" data-table-selection="single" data-table-page-size="10"><div class="app-table-titlebar"><h2>Users</h2></div><form method="get" action="/admin/users"><input name="search" type="search"><input name="sort" value="name"><input name="direction" value="asc"><button>Apply</button></form><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th data-table-sort-key="name" data-table-sort-asc="name" data-table-sort-desc="name">Name</th><th>Email</th><th>Role</th><th>Status</th><th data-table-actions-header>Actions</th></tr></thead><tbody><tr><td><a class="resource-table-id-link" href="/admin/users/1/edit">${name}</a></td><td>fixture@test.invalid</td><td>Administrator</td><td>Active</td><td><a class="app-btn" href="/admin/users/1/edit">Edit</a></td></tr></tbody></table></div><nav data-table-pagination data-page="0"></nav></section></body></html>`;
}
function setup(t,fetcher,lang='en',html=page('Original',lang)){
 const dom=new JSDOM(html,{url:'http://fcms.test/admin/users',runScripts:'outside-only',pretendToBeVisual:true});
 t.after(()=>dom.window.close());const w=dom.window,d=w.document,navigations=[];
 w.WorkspaceNavigation={onReady:callback=>callback(),navigate:url=>navigations.push(url)};w.matchMedia=()=>({matches:false,addEventListener(){}});w.fetch=fetcher;
 w.eval(loading);w.eval(source);
 return {w,d,navigations,region:d.querySelector('section'),refresh:()=>d.querySelector('[data-resource-refresh]').click()};
}
const response=html=>({ok:true,redirected:false,text:async()=>html});
const emptyPage=(lang='en')=>page('Original',lang).replace(/<tbody>.*?<\/tbody>/s,`<tbody><tr data-table-empty><td colspan="5">${lang==='sw'?'Hakuna rekodi':'No records'} <a href="/admin/users/new">Create</a></td></tr></tbody>`);
async function until(predicate){for(let n=0;n<200;n++){if(predicate())return;await new Promise(resolve=>setTimeout(resolve,5));}throw new Error('Refresh did not settle');}

test('a successful Users refresh adapts selection and commands without a false failure',async t=>{
 const h=setup(t,async()=>response(page('Updated')));h.refresh();
 await until(()=>h.d.querySelector('tbody .resource-table-id-link')?.textContent==='Updated'&&!h.region.hasAttribute('aria-busy'));
 assert.equal(h.d.querySelector('.resource-table-load-error'),null);
 assert.equal(h.d.querySelectorAll('tbody tr').length,1);
 assert.equal(h.d.querySelector('tbody tr').cells.length,5);
 assert.ok(h.d.querySelector('tbody input[type=checkbox]'));
 h.d.querySelector('tbody input[type=checkbox]').click();assert.equal(h.d.querySelector('[role=menuitem]:not(:disabled)')?.textContent,'Edit');
});

for(const lang of ['en','sw'])test(`failed refresh keeps records and shows one wrapping alert outside the rows (${lang})`,async t=>{
 const h=setup(t,async()=>{throw new Error('Network');},lang);const row=h.d.querySelector('tbody tr');
 h.refresh();await until(()=>h.d.querySelector('[role=alert]'));
 const alert=h.d.querySelector('[role=alert]');
 assert.equal(alert.closest('table'),null);
 assert.equal(alert.closest('.erp-table-scroll'),null);
 assert.equal(alert.nextElementSibling,h.d.querySelector('.erp-table-scroll'));
 assert.equal(alert.querySelector('span').textContent,labels[lang].loadError);
 assert.equal(alert.querySelector('button').textContent,labels[lang].refresh);
 assert.equal(h.d.querySelector('tbody tr'),row);assert.equal(row.hidden,false);
 assert.equal(h.d.querySelectorAll('tbody tr').length,1);assert.equal(h.d.querySelector('.app-loading-row'),null);
});

test('retry removes old feedback, keeps controls mounted and clears the alert after success',async t=>{
 let calls=0;const h=setup(t,async()=>{if(!calls++)throw new Error('Network');return response(page('Recovered'));});
 const title=h.d.querySelector('h2'),filters=h.d.querySelector('form');h.refresh();await until(()=>h.d.querySelector('[role=alert]'));
 const retry=h.d.querySelector('[role=alert] button');retry.focus();retry.click();
 assert.equal(h.d.activeElement,h.d.querySelector('[data-resource-refresh]'));
 await until(()=>h.d.querySelector('tbody .resource-table-id-link')?.textContent==='Recovered');
 assert.equal(h.d.querySelector('[role=alert]'),null);assert.equal(h.d.querySelector('h2'),title);assert.equal(h.d.querySelector('form'),filters);assert.equal(calls,2);
});

test('a superseded failed request cannot overwrite a newer successful refresh',async t=>{
 let rejectOld,calls=0;
 const h=setup(t,()=>++calls===1?new Promise((resolve,reject)=>{rejectOld=reject;}):Promise.resolve(response(page('Newest'))));
 h.refresh();h.refresh();await until(()=>h.d.querySelector('tbody .resource-table-id-link')?.textContent==='Newest');
 rejectOld(new Error('Old request failed'));await new Promise(resolve=>setTimeout(resolve,20));
 assert.equal(h.d.querySelector('[role=alert]'),null);assert.equal(h.d.querySelector('tbody .resource-table-id-link').textContent,'Newest');
});

test('retry retains the attempted filters and never inserts server error HTML into the table',async t=>{
 const urls=[];const h=setup(t,async url=>{urls.push(new URL(url));return response('<html><body>Private server error</body></html>');});
 const form=h.d.querySelector('form');form.elements.search.value='Example';form.requestSubmit();
 await until(()=>h.d.querySelector('[role=alert]'));h.d.querySelector('[role=alert] button').click();
 await until(()=>urls.length===2&&h.d.querySelector('[role=alert]'));
 assert.equal(urls[0].searchParams.get('search'),'Example');assert.equal(urls[1].href,urls[0].href);
 assert.equal(form.elements.search.value,'Example');assert.equal(h.d.querySelectorAll('[role=alert]').length,1);
 assert.equal(h.d.body.textContent.includes('Private server error'),false);
 assert.equal(h.d.querySelector('tbody .resource-table-id-link').textContent,'Original');
});

test('a valid same-origin redirected response reloads records and retains the final query',async t=>{
 const h=setup(t,async()=>({...response(page('Redirected records')),redirected:true,url:'http://fcms.test/admin/users?size=10&sort=name'}));
 h.refresh();await until(()=>h.d.querySelector('tbody .resource-table-id-link')?.textContent==='Redirected records');
 assert.equal(h.d.querySelector('[role=alert]'),null);assert.equal(h.w.location.search,'?size=10&sort=name');
 assert.equal(h.navigations.length,0);
});

test('an expired session returns to sign in without parsing login HTML or showing a table error',async t=>{
 let reads=0;
 const h=setup(t,async()=>({ok:true,redirected:true,url:'http://fcms.test/fcms/login',text:async()=>{reads++;return '<html>Sign in</html>';}}));
 h.refresh();await until(()=>h.navigations.length===1);
 assert.deepEqual(h.navigations,['http://fcms.test/fcms/login']);assert.equal(reads,0);
 assert.equal(h.d.querySelector('[role=alert]'),null);assert.equal(h.d.querySelector('.app-loading-row'),null);
});

test('a cross-origin redirected response cannot replace the authorized records',async t=>{
 const h=setup(t,async()=>({...response(page('Untrusted records')),redirected:true,url:'https://untrusted.invalid/admin/users'}));
 h.refresh();await until(()=>h.d.querySelector('[role=alert]'));
 assert.equal(h.d.querySelector('tbody .resource-table-id-link').textContent,'Original');assert.equal(h.navigations.length,0);
});

test('a superseded login redirect cannot interrupt a newer successful refresh',async t=>{
 let finishOld,calls=0;
 const h=setup(t,()=>++calls===1?new Promise(resolve=>{finishOld=resolve;}):Promise.resolve(response(page('Current records'))));
 h.refresh();h.refresh();await until(()=>h.d.querySelector('tbody .resource-table-id-link')?.textContent==='Current records');
 finishOld({ok:true,redirected:true,url:'http://fcms.test/login'});
 await new Promise(resolve=>setTimeout(resolve,20));
 assert.equal(h.navigations.length,0);assert.equal(h.d.querySelector('[role=alert]'),null);
 assert.equal(h.d.querySelector('tbody .resource-table-id-link').textContent,'Current records');
});

for(const lang of ['en','sw'])test(`empty placeholders retain spanning semantics and recovery links across refresh (${lang})`,async t=>{
 const h=setup(t,async()=>response(emptyPage(lang)),lang,emptyPage(lang));
 const content=h.d.querySelector('.resource-table-empty-message'),cell=content.parentElement;
 assert.equal(cell.colSpan,h.d.querySelector('thead tr').cells.length);
 assert.equal(h.d.querySelectorAll('.resource-table-empty-message').length,1);
 assert.equal(content.querySelector('a').getAttribute('href'),'/admin/users/new');
 assert.match(content.textContent,new RegExp(lang==='sw'?'Hakuna rekodi':'No records'));
 assert.equal(h.d.querySelector('tbody input[type=checkbox]'),null);
 assert.equal(h.d.querySelector('.app-table-count').textContent,'(0)');
 h.refresh();await until(()=>h.d.querySelector('.resource-table-empty-message')!==content&&!h.region.hasAttribute('aria-busy'));
 assert.equal(h.d.querySelectorAll('.resource-table-empty-message').length,1);
 assert.equal(h.d.querySelector('.resource-table-empty-message').parentElement.colSpan,h.d.querySelector('thead tr').cells.length);
 assert.equal(h.d.querySelector('.app-table-count').textContent,'(0)');
});

test('populated rows replace empty placeholders and keep selected-record commands operable',async t=>{
 const h=setup(t,async()=>response(page('First record')),'en',emptyPage());h.refresh();
 await until(()=>h.d.querySelector('tbody .resource-table-id-link')?.textContent==='First record');
 assert.equal(h.d.querySelector('.resource-table-empty-message'),null);
 h.d.querySelector('tbody input[type=checkbox]').click();
 assert.equal(h.d.querySelector('[role=menuitem]:not(:disabled)').textContent,'Edit');
});

test('summary tables use the same empty treatment without changing real financial total rows',t=>{
 const extra=`<section class="erp-table-wrap"><div class="app-table-titlebar"><h2>Summary</h2></div><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th>Name</th><th>Amount</th></tr></thead><tbody><tr><td colspan="2">No records</td></tr></tbody></table></div></section>
 <div class="erp-table-scroll"><table class="erp-table"><tbody><tr><td>Entry</td><td>20.00</td></tr><tr><td colspan="2">Total: 20.00</td></tr></tbody></table></div>`;
 const h=setup(t,async()=>response(page()),'en',page().replace('</body>',extra+'</body>'));
 assert.equal(h.d.querySelectorAll('.resource-table-empty-message').length,1);
 assert.equal(h.d.querySelector('.resource-table-empty-message').textContent,'No records');
 const total=[...h.d.querySelectorAll('td')].find(cell=>cell.textContent==='Total: 20.00');
 assert.equal(total.querySelector('.resource-table-empty-message'),null);
 h.w.ResourceTable.init();assert.equal(h.d.querySelectorAll('.resource-table-empty-message').length,1);
});

test('existing client/voucher empty markers are reused without nesting another wrapper',t=>{
 const html=emptyPage().replace('No records <a','<span class="resource-table-empty-message">No records</span> <a');
 const h=setup(t,async()=>response(html),'en',html);
 const cell=h.d.querySelector('tbody td');
 assert.ok(cell.classList.contains('resource-table-empty-cell'));
 assert.equal(cell.querySelectorAll('.resource-table-empty-message').length,1);
 assert.equal(cell.querySelector('.resource-table-empty-message').tagName,'SPAN');
 assert.ok(cell.querySelector('.resource-table-empty-message a'));
});

test('local no-match placeholders share canvas centering and disappear when search clears',async t=>{
 const html=page().replace(/<form\b.*?<\/form>/s,'');
 const h=setup(t,async()=>response(html),'en',html),search=h.d.querySelector('.resource-table-search input');
 search.value='missing';search.dispatchEvent(new h.w.Event('input',{bubbles:true}));
 await until(()=>h.d.querySelector('.resource-table-no-matches .resource-table-empty-message'));
 const row=h.d.querySelector('.resource-table-no-matches');assert.equal(row.hidden,false);
 assert.equal(row.cells[0].getAttribute('role'),'status');
 assert.equal(row.cells[0].colSpan,h.d.querySelector('thead tr').cells.length);
 search.value='';search.dispatchEvent(new h.w.Event('input',{bubbles:true}));
 assert.equal(row.hidden,true);assert.equal(h.d.querySelector('.app-table-count').textContent,'(1)');
});
