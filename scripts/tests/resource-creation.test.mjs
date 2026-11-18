import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
const source = name => readFile(new URL('../../src/main/resources/static/js/'+name+'.js',import.meta.url),'utf8');
const creation = await source('resource-creation'), owner = await source('form-ux'), loading = await source('ui-loading');
const json = data => ({ok:true,status:200,redirected:false,headers:new Headers({'content-type':'application/json'}),json:async()=>data});
const html = (body,{status=200,redirected=false,url='http://fcms.test/resources'}={}) => ({ok:status<400,status,redirected,url,headers:new Headers({'content-type':'text/html'}),clone:()=>({text:async()=>body})});
const settle = async predicate => {for(let i=0;i<100;i++){if(predicate())return;await new Promise(resolve=>setTimeout(resolve,5));}throw Error('Did not settle');};
function harness(t, post) {
 const dom = new JSDOM(`<body data-form-actor="42" data-form-session-url="/form-session" data-form-login-url="/login" data-form-expired="Expired" data-form-changed="Changed account" data-form-forbidden="Forbidden" data-form-unavailable="Unavailable" data-form-uncertain="Check outcome" data-form-check-list="Check list" data-form-sign-in="Sign in" data-form-required="Required" data-form-invalid="Invalid">
 <form class="fcms-form" data-resource-create="true" action="/resources" method="post" enctype="multipart/form-data"><input type="hidden" name="_csrf" value="old"><input type="hidden" name="requestKey" value="unchanged-key">
 <div class="fcms-field"><input name="name" required value="Draft" aria-describedby="hint"><span id="hint">Hint</span></div>
 <details><summary>Optional</summary><div class="fcms-field"><input name="email" type="email" value="draft@test.invalid"></div></details>
 <div class="fcms-field"><input name="password" type="password" value="LivePrivateValue123!"></div><div class="fcms-field"><input name="file" type="file"></div>
 <div class="fcms-form-actions"><a href="/resources">Cancel</a><button type="submit" name="operation" value="save">Create</button><button type="submit" disabled>Business disabled</button></div></form>
 <div id="fcms-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-fcms-keep>Keep</button><button data-fcms-discard>Discard</button></div></div></div></body>`,{url:'http://fcms.test/resources/new',runScripts:'outside-only',pretendToBeVisual:true});
 t.after(()=>dom.window.close());const w=dom.window,d=w.document,requests=[],presented=[];
 w.WorkspaceNavigation={handles:()=>false,present:async response=>{presented.push(response);d.querySelector('form').remove();}};
 w.fetch=async (url,options)=>{requests.push({url,options});return requests.length===1?json({userId:'42',parameterName:'_csrf',token:'fresh'}):post(url,options);};
 w.eval(loading);w.eval(creation);w.eval(owner);
 const form=d.querySelector('form'),button=form.querySelector('button');
 const submit=()=>form.dispatchEvent(new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:button}));
 return {w,d,form,button,submit,requests,presented};
}
test('success presents the followed list response once, with the native command, CSRF and request key',async t=>{
 const response=html('<body class="fcms-shell"><main><div data-resource-notice data-message-type="success">Created</div></main></body>',{redirected:true,url:'http://fcms.test/resources?_notice=one'});
 const h=harness(t,async()=>response);h.submit();h.submit();await settle(()=>h.presented.length===1);
 assert.equal(h.requests.length,2);assert.equal(h.presented[0],response);
 const data=h.requests[1].options.body;assert.equal(data.get('operation'),'save');assert.equal(data.get('requestKey'),'unchanged-key');assert.equal(data.get('_csrf'),'fresh');
 assert.equal(data.get('password'),'LivePrivateValue123!');assert.equal(h.requests[1].options.method,'POST');
 assert.equal(h.w.localStorage.length,0);assert.equal(h.w.sessionStorage.length,0);
});
test('field and unbound errors retain passwords, file and other native nodes, reveal optional fields and restore owned locks',async t=>{
 const h=harness(t,async()=>html('<form action=""><input name="navigationSearch"></form><form class="fcms-form" action="/resources"><div class="fcms-field"><input name="email"><span class="fcms-error">Already used</span></div><div class="fcms-alert">Review failed</div><input type="hidden" name="_csrf" value="rotated"></form>'));
 const password=h.form.elements.password,file=h.form.elements.file,name=h.form.elements.name;
 h.submit();await settle(()=>h.form.querySelector('.fcms-error'));
 assert.equal(h.form.elements.password,password);assert.equal(password.value,'LivePrivateValue123!');assert.equal(h.form.elements.file,file);assert.equal(h.form.elements.name,name);
 assert.equal(h.form.elements._csrf.value,'rotated');assert.equal(h.form.elements.requestKey.value,'unchanged-key');
 assert.equal(h.form.elements.email.getAttribute('aria-invalid'),'true');assert.equal(h.d.activeElement,h.form.elements.email);assert.equal(h.form.querySelector('details').open,true);
 assert.equal(h.form.querySelector('.fcms-alert').nextElementSibling.className,'fcms-form-actions');assert.equal(h.button.disabled,false);assert.equal(h.form.querySelectorAll('button')[1].disabled,true);
 assert.equal(h.presented.length,0);
});
test('unknown POST outcome is never replayed and offers the collection link with the draft retained',async t=>{
 const h=harness(t,async()=>{throw Error('network lost');});h.submit();await settle(()=>h.form.dataset.resourceUncertain==='true');
 assert.equal(h.form.elements.password.value,'LivePrivateValue123!');assert.equal(h.button.disabled,true);
 assert.equal(h.form.querySelector('[data-creation-feedback]').dataset.messageType,'warning');
 assert.equal(h.form.querySelector('[data-creation-feedback] a').pathname,'/resources');h.submit();assert.equal(h.requests.length,2);
});
test('changed account is blocked before any POST',async t=>{
 const h=harness(t,()=>assert.fail('POST forbidden'));h.w.fetch=async()=>json({userId:'99',parameterName:'_csrf',token:'fresh'});
 h.submit();await settle(()=>h.form.querySelector('[data-creation-feedback]'));assert.equal(h.form.querySelector('[data-creation-feedback]').textContent,'Changed account Sign in');assert.equal(h.button.disabled,false);
 assert.equal(h.form.querySelector('[data-creation-feedback]').dataset.messageType,'warning');
});
test('editing during session verification cancels the stale submission',async t=>{
 const h=harness(t,()=>assert.fail('POST forbidden'));let resolve;h.w.fetch=()=>new Promise(r=>resolve=r);
 h.submit();h.form.elements.name.value='Changed draft';h.form.elements.name.dispatchEvent(new h.w.Event('input',{bubbles:true}));resolve(json({userId:'42',parameterName:'_csrf',token:'fresh'}));
 await settle(()=>!h.button.disabled);assert.equal(h.form.elements.name.value,'Changed draft');assert.equal(h.presented.length,0);
});
test('pristine constraints are quiet; blur and correction keep hint associations and server conflicts',async t=>{
 const h=harness(t,()=>assert.fail('No POST'));const input=h.form.elements.name;
 assert.equal(h.form.querySelector('.fcms-error'),null);input.value='';input.dispatchEvent(new h.w.FocusEvent('focusout',{bubbles:true}));
 assert.equal(input.getAttribute('aria-invalid'),'true');assert.match(input.getAttribute('aria-describedby'),/^hint fcms-field-error-/);
 input.value='Corrected';input.dispatchEvent(new h.w.Event('input',{bubbles:true}));assert.equal(h.form.querySelector('.fcms-error'),null);assert.equal(input.getAttribute('aria-describedby'),'hint');assert.equal(input.hasAttribute('aria-invalid'),false);
});
test('an upload rejected before command execution gets a local file error and an explicit retry',async t=>{
 const h=harness(t,async()=>html('',{status:413}));h.d.body.dataset.formTooLarge='Select a smaller file';
 const file=h.form.elements.file;h.submit();await settle(()=>file.getAttribute('aria-invalid')==='true');
 assert.equal(h.d.activeElement,file);assert.equal(file.closest('.fcms-field').querySelector('.fcms-error').textContent,'Select a smaller file');
 assert.equal(h.button.disabled,false);assert.notEqual(h.form.dataset.resourceUncertain,'true');assert.equal(h.form.elements.file,file);
});
test('a successful-looking 200 without a server redirect and notice cannot claim creation succeeded',async t=>{
 const h=harness(t,async()=>html('<body class="fcms-shell"><main>Saved?</main></body>'));h.submit();
 await settle(()=>h.form.dataset.resourceUncertain==='true');assert.equal(h.presented.length,0);assert.equal(h.requests.length,2);
});
