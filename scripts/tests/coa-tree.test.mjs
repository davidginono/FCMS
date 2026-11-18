import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

const source = await readFile(new URL('../../src/main/resources/static/js/grouped-resource-table.js',import.meta.url),'utf8');
const formSource = await readFile(new URL('../../src/main/resources/static/js/account-form.js',import.meta.url),'utf8');
const tableSource = await readFile(new URL('../../src/main/resources/static/js/resource-table.js',import.meta.url),'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const row = (id,group=true,extra='') => `<tr data-coa-row data-id="${id}" data-depth="0" data-expandable="${group}" data-add-group="${group}" data-add-posting="false" data-editable="true" data-lifecycle="false" data-ledger="${!group}" data-active="true" data-version="3" ${extra}><td class="resource-table-selection"><input type="checkbox" name="coa-selection" value="${id}" aria-label="Select ${id}"></td><td><div class="coa-tree-code">${group?`<a data-coa-toggle href="/accounting/accounts?parentId=${id}" aria-expanded="false">Expand</a>`:''}<a class="resource-table-id-link" href="/accounting/accounts/${id}">${id}</a></div></td><td data-coa-name>Account ${id}</td><td>Parent</td><td>Asset</td><td>${group?'Group':'Posting'}</td><td>Debit</td><td>Active</td></tr>`;

function setup(t,shared=false,preferences) {
 const dom = new JSDOM(`<html><body><div id="resource-table-labels" data-actions="Actions" data-refresh="Refresh" data-previous="Previous" data-next="Next" data-pagination="Pages" data-preferences="Preferences"></div><section data-resource-table="gl-accounts" data-coa-tree data-table-selection="single" data-table-page-size="10" data-children-url="/accounting/accounts/children" data-base-url="/accounting/accounts" data-expand="Expand" data-collapse="Collapse" data-loading="Loading" data-load-error="Failed" data-empty="Empty" data-retry="Retry"><div class="app-table-titlebar"><h2>COA</h2><div class="accounting-actions"><button data-table-command data-coa-command="view" disabled>View</button><button data-table-command data-coa-command="children" disabled>Children</button><button data-table-command data-coa-command="edit" disabled>Edit</button><form method="post" data-table-command data-coa-state><input name="_csrf" value="test-csrf"><input name="version"><input name="active"><button data-coa-command="state" data-activate="Activate" data-deactivate="Deactivate" disabled>State</button></form><button data-table-command data-coa-command="ledger" disabled>Ledger</button><button data-coa-create data-group-label="Add group" data-posting-label="New posting" disabled>Create</button></div></div><form class="coa-filters"><input name="search"><input name="size" value="10"><input name="sort" value="code"><input name="direction" value="asc"><input name="classification" value=""><input name="kind" value=""></form><span data-coa-status></span><div class="erp-table-scroll"><table><thead><tr>${Array.from({length:8},(_,i)=>`<th${i===0?' class="resource-table-selection"':''}>${i?'Column '+i:'Select'}</th>`).join('')}</tr></thead><tbody>${row('1')}${row('2',false)}</tbody></table></div></section></body></html>`,{url:'http://localhost/accounting/accounts',runScripts:'outside-only',pretendToBeVisual:true});
 t.after(()=>dom.window.close());const w=dom.window,d=w.document,urls=[];
 w.WorkspaceNavigation={onReady:cb=>cb(),navigate:url=>urls.push(url)};
 w.AppLoading={spinner:()=>d.createElement('span')};
 if(preferences)w.localStorage.setItem('resource-table:v1:gl-accounts',JSON.stringify(preferences));
 if(shared)w.eval(tableSource);
 w.eval(source);
 const region=d.querySelector('section');
 return {w,d,region,urls,body:d.querySelector('tbody')};
}
const response = html => ({ok:true,redirected:false,headers:{get:()=> 'true'},text:async()=>html});

test('single selection replaces and toggles, and command permissions follow account kind',t=>{
 const {d,w,region,urls}=setup(t);const checks=d.querySelectorAll('[name=coa-selection]');
 checks[0].click();assert.equal(region.querySelector('[data-coa-create]').textContent,'Add group');
 assert.equal(region.querySelector('[data-coa-command=ledger]').disabled,true);
 checks[1].click();assert.equal(checks[0].checked,false);assert.equal(region.querySelector('[data-coa-create]').disabled,true);
 assert.equal(region.querySelector('[data-coa-command=children]').disabled,true);
 region.querySelector('[data-coa-command=ledger]').click();assert.deepEqual(urls,['/accounting/accounts/2/ledger']);
 checks[1].click();assert.equal(region.querySelector('[data-coa-command=view]').disabled,true);
 checks[0].click();w.dispatchEvent(new w.Event('pageshow'));assert.equal(checks[0].checked,false);
});

test('three real hierarchy levels load on demand, collapse descendants and retain native code links',async t=>{
 const {w,d,body}=setup(t);const requested=[];
 w.fetch=async url=>{requested.push(new URL(url));return response(row(requested.length===1?'3':'4',requested.length===1));};
 d.querySelector('[data-coa-toggle]').click();await flush();
 assert.equal(requested[0].searchParams.get('parentId'),'1');assert.equal(requested[0].searchParams.get('size'),'10');
 const subgroup=body.querySelector('[data-id="3"]');assert.equal(subgroup.dataset.treeParent,'1');
 subgroup.querySelector('[data-coa-toggle]').click();await flush();
 const posting=body.querySelector('[data-id="4"]');assert.equal(posting.dataset.depth,'2');
 posting.querySelector('input').click();let selectionCleared=false;
 posting.querySelector('input').addEventListener('change',()=>selectionCleared=!posting.querySelector('input').checked);
 d.querySelector('[data-id="1"] [data-coa-toggle]').click();
 assert.equal(posting.hidden,true);assert.equal(posting.querySelector('input').checked,false);
 assert.equal(selectionCleared,true,'Split details and shared selection receive the cleared selection');
 d.querySelector('[data-id="1"] [data-coa-toggle]').click();await flush();assert.equal(requested.length,2);assert.equal(posting.hidden,false);
 assert.equal(posting.querySelector('.resource-table-id-link').getAttribute('href'),'/accounting/accounts/4');
});

test('child paging appends once and preserves query criteria',async t=>{
 const {w,d,body}=setup(t);const urls=[];d.querySelector('[name=classification]').value='ASSET';d.querySelector('[name=kind]').value='POSTING';
 w.fetch=async url=>{urls.push(new URL(url));return response(urls.length===1?row('3',false)+'<tr data-coa-more="1"><td colspan="8"><a href="?page=1" data-coa-load-more>More</a></td></tr>':row('4',false));};
 d.querySelector('[data-coa-toggle]').click();await flush();d.querySelector('[data-coa-load-more]').focus();d.querySelector('[data-coa-load-more]').click();await flush();
 assert.equal(urls[1].searchParams.get('page'),'1');assert.equal(urls[1].searchParams.get('classification'),'ASSET');assert.equal(urls[1].searchParams.get('kind'),'POSTING');
 assert.equal(body.querySelectorAll('[data-tree-parent="1"][data-coa-row]').length,2);assert.equal(d.querySelector('[data-coa-load-more]'),null);
 assert.equal(d.activeElement,body.querySelector('[data-id="4"] .resource-table-id-link'));
});

test('failed child load retries locally and empty groups remain collapsed on restoration',async t=>{
 const {w,d}=setup(t);let count=0;
 w.fetch=async()=>{if(!count++)throw Error('network');return response('');};
 d.querySelector('[data-coa-toggle]').click();await flush();assert.equal(d.querySelector('[role=alert]').textContent,'Failed');
 d.querySelector('[data-coa-retry]').click();await flush();assert.equal(d.querySelector('.coa-tree-message').textContent,'Empty');
 w.dispatchEvent(new w.Event('pageshow'));assert.equal(d.querySelector('[data-tree-parent]'),null);assert.equal(d.querySelector('[data-coa-toggle]').getAttribute('aria-expanded'),'false');
});

test('filter changes abort obsolete requests and late responses cannot insert rows',async t=>{
 const {w,d}=setup(t);let complete,signal;
 w.fetch=(_url,options)=>{signal=options.signal;return new Promise(resolve=>complete=resolve);};
 d.querySelector('[data-coa-toggle]').click();d.querySelector('[name=search]').dispatchEvent(new w.Event('input',{bubbles:true}));
 assert.equal(signal.aborted,true);complete(response(row('9',false)));await flush();assert.equal(d.querySelector('[data-id="9"]'),null);
});

test('expanding global filter matches places an existing child in its actual branch once',async t=>{
 const {w,d,body}=setup(t);w.fetch=async()=>response(row('2',false));
 d.querySelector('[data-coa-toggle]').click();await flush();
 assert.equal(body.querySelectorAll('[data-id="2"]').length,1);assert.equal(body.querySelector('[data-id="2"]').dataset.treeParent,'1');
 assert.equal(d.querySelector('[role=alert]'),null);
});

test('moving a filtered branch clears selected descendants and filter reset notifies details',async t=>{
 const {w,d,body,region}=setup(t);body.querySelector('[data-id="2"]').outerHTML=row('2',true);
 w.fetch=async url=>response(row(new URL(url).searchParams.get('parentId')==='2'?'3':'2',false));
 d.querySelector('[data-id="2"] [data-coa-toggle]').click();await flush();
 const descendant=body.querySelector('[data-id="3"] input');descendant.click();let cleared=0;
 region.addEventListener('change',event=>{if(event.target.name==='coa-selection'&&!event.target.checked)cleared++;});
 d.querySelector('[data-id="1"] [data-coa-toggle]').click();await flush();
 assert.equal(cleared,1);assert.equal(descendant.checked,false);assert.equal(descendant.isConnected,false);
 assert.equal(body.querySelectorAll('[data-id="2"]').length,1);assert.equal(region.querySelector('[data-coa-command=view]').disabled,true);
 d.querySelector('[data-id="1"] input').click();
 d.querySelector('[name=search]').dispatchEvent(new w.Event('input',{bubbles:true}));
 assert.equal(cleared,2,'An open details pane also receives filter-reset deselection');
 assert.equal(d.querySelector('[name=coa-selection]:checked'),null);
});

test('branch requests reject redirected login pages and missing fragment identity',async t=>{
 const {w,d}=setup(t);w.fetch=async()=>({ok:true,redirected:true,headers:{get:()=>null},text:async()=>'<html>Login</html>'});
 d.querySelector('[data-coa-toggle]').click();await flush();
 assert.equal(d.querySelector('[role=alert]').textContent,'Failed');assert.equal(d.querySelectorAll('[data-coa-row]').length,2);
});

test('saved column preferences also apply to lazily inserted rows',async t=>{
 const {w,d}=setup(t,true,{hidden:[2],order:[0,1,2,3,4,5,6,7]});w.fetch=async()=>response(row('3',false));
 d.querySelector('[data-coa-toggle]').click();await flush();
 assert.equal(d.querySelector('[data-id="3"]').cells[2].hidden,true);
 assert.equal(d.querySelector('[data-id="3"] .resource-table-id-link').getAttribute('href'),'/accounting/accounts/3');
});

test('shared Actions menu retains native state form, CSRF and eligibility',async t=>{
 const {w,d,region}=setup(t,true);const parent=d.querySelector('[data-id="1"]');parent.dataset.lifecycle='true';
 parent.querySelector('input').click();await flush();const form=d.querySelector('[data-coa-state]');
 assert.equal(form.action,'http://localhost/accounting/accounts/1/state');assert.equal(form.elements._csrf.value,'test-csrf');assert.equal(form.elements.version.value,'3');
 assert.equal(form.elements.active.value,'false');assert.equal(d.querySelectorAll('.resource-table-action-menu').length,1);
 assert.equal(form.method,'post');const submit=new w.Event('submit',{cancelable:true});form.dispatchEvent(submit);assert.equal(submit.defaultPrevented,false);
 region.dispatchEvent(new w.Event('resource-table:reset'));const denied=new w.Event('submit',{cancelable:true});form.dispatchEvent(denied);assert.equal(denied.defaultPrevented,true);
});

test('posting parents suggest codes without replacing user input and enforce cash eligibility',async t=>{
 const dom=new JSDOM('<form><select id="parentId" name="parentId" data-code-url="/accounting/accounts/next-code"><option value=""></option><option value="3" data-depth="3" data-classification="LIABILITY" data-normal-balance="CREDIT">Payables</option></select><select id="normalBalance" name="normalBalance"><option>DEBIT</option><option>CREDIT</option></select><input name="cashAccount" type="checkbox"><input name="mode" value="POSTING"><input name="code"><span data-code-status data-error="Failed" data-unavailable="Full"></span></form>',{url:'http://localhost/accounting/accounts/new',runScripts:'outside-only'});
 t.after(()=>dom.window.close());const w=dom.window,d=w.document;w.WorkspaceNavigation={onReady:cb=>cb()};let complete;
 w.fetch=()=>new Promise(resolve=>complete=resolve);w.eval(formSource);
 const parent=d.querySelector('select'),code=d.querySelector('[name=code]');parent.value='3';parent.dispatchEvent(new w.Event('change'));code.value='221007';code.dispatchEvent(new w.Event('input'));
 complete({ok:true,json:async()=>({code:'221001'})});await flush();assert.equal(code.value,'221007');
 assert.equal(d.querySelector('[name=normalBalance]').value,'CREDIT');assert.equal(d.querySelector('[name=cashAccount]').disabled,true);
});
