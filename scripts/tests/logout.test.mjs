import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

const source=await readFile(new URL('../../src/main/resources/static/js/shell-navigation.js',import.meta.url),'utf8');
const formSource=await readFile(new URL('../../src/main/resources/static/js/form-ux.js',import.meta.url),'utf8');
const fresh=()=>({ok:true,headers:new Headers({'Content-Type':'text/html'}),text:async()=>'<form method="post" action="/fcms/login"><input type="hidden" name="_csrf" value="fresh-session-token"></form>'});
function harness(t,fetcher=async()=>fresh()){
 const dom=new JSDOM(`<form method="post" action="/fcms/logout" data-shell-logout data-login-url="/fcms/login"><input type="hidden" name="_csrf" value="stale-token"><button type="submit">Logout</button></form>
 <form class="fcms-form"><input name="draft" value="original"></form>
 <div id="fcms-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-fcms-keep>Keep</button><button data-fcms-discard>Discard</button></div></div></div>`,{url:'https://fcms.test/fcms/tasks/new',runScripts:'outside-only'});
 t.after(()=>dom.window.close());
 const w=dom.window,d=w.document,requests=[],posts=[];
 w.WorkspaceNavigation={onReady:callback=>callback()};
 w.fetch=(url,options)=>{requests.push({url,options});return fetcher(url,options);};
 w.HTMLFormElement.prototype.submit=function(){posts.push({url:this.action,body:[...new w.FormData(this)]});};
 w.eval(formSource);w.eval(source);
 const form=d.querySelector('[data-shell-logout]');
 const submit=()=>form.dispatchEvent(new w.SubmitEvent('submit',{bubbles:true,cancelable:true,submitter:form.querySelector('button')}));
 return {w,d,form,requests,posts,submit};
}
async function settled(h){for(let n=0;n<100;n++){if(h.posts.length)return;await new Promise(resolve=>setTimeout(resolve,5));}throw new Error('Logout did not settle');}

test('navbar refreshes stale CSRF before one native POST and bypasses dirty-form prompts',async t=>{
 const h=harness(t);h.d.querySelector('[name=draft]').value='unsaved';
 let logoutEvents=0;h.d.addEventListener('session:logout',()=>logoutEvents++);
 h.submit();h.submit();await settled(h);
 assert.equal(logoutEvents,1);assert.equal(h.requests.length,1);assert.equal(h.requests[0].url,'/fcms/login');
 assert.equal(h.requests[0].options.credentials,'same-origin');assert.equal(h.requests[0].options.cache,'no-store');
 assert.equal(h.requests[0].options.body,undefined);assert.equal(h.posts.length,1);
 assert.deepEqual(h.posts[0],{url:'https://fcms.test/fcms/logout',body:[['_csrf','fresh-session-token']]});
 assert.equal(h.d.getElementById('fcms-discard-modal').hidden,true);
 const unload=new h.w.Event('beforeunload',{cancelable:true});h.w.dispatchEvent(unload);assert.equal(unload.defaultPrevented,false);
});

test('failed token refresh retains the protected native logout without repeating it',async t=>{
 const h=harness(t,async()=>{throw new Error('Offline');});h.submit();await settled(h);
 assert.equal(h.posts.length,1);assert.deepEqual(h.posts[0].body,[['_csrf','stale-token']]);
});

test('navigation away during verification cannot submit a detached logout form',async t=>{
 let finish;const h=harness(t,()=>new Promise(resolve=>{finish=resolve;}));h.submit();h.form.remove();finish(fresh());
 await new Promise(resolve=>setTimeout(resolve,20));assert.equal(h.posts.length,0);
});
