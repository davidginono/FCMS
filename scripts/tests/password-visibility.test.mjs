import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

const source=await readFile(new URL('../../src/main/resources/static/js/password-visibility.js',import.meta.url),'utf8');
const jsp=await readFile(new URL('../../src/main/webapp/WEB-INF/jsp/auth/login.jsp',import.meta.url),'utf8');
const userJsp=await readFile(new URL('../../src/main/webapp/WEB-INF/jsp/users/form.jsp',import.meta.url),'utf8');
const toggle=(await readFile(new URL('../../src/main/webapp/WEB-INF/tags/passwordToggle.tag',import.meta.url),'utf8')).replace(/<%@[^%]*%>/g,'').replace('${fn:escapeXml(inputId)}','password');
const expand=markup=>markup.replace('<ui:passwordToggle inputId="password" />',toggle);
const wrapper=expand(jsp.match(/<div class="fcms-password-control"[\s\S]+?<\/div>/)[0]);

function setup(t,labels=['Show password','Hide password'],scripts=true) {
 const markup=wrapper.replaceAll("<spring:message code='password.show'/>",labels[0]).replaceAll("<spring:message code='password.hide'/>",labels[1]);
 const dom=new JSDOM('<form method="post" action="/login"><input type="hidden" name="_csrf" value="test-csrf">'+markup+'<button type="submit">Sign in</button></form>',{url:'http://localhost/login',runScripts:'outside-only'});
 t.after(()=>dom.window.close());const w=dom.window,d=w.document;
 if(scripts){w.eval(source);d.dispatchEvent(new w.Event('DOMContentLoaded'));}
 return {w,d,form:d.querySelector('form'),input:d.getElementById('password'),button:d.querySelector('[aria-controls=password]')};
}

test('eye toggles the same field without submitting or changing native credential bindings',t=>{
 const {w,d,form,input,button}=setup(t);let submissions=0;form.addEventListener('submit',()=>submissions++);
 input.value='synthetic test value';assert.equal(input.type,'password');assert.equal(button.hidden,false);
 assert.equal(button.getAttribute('aria-label'),'Show password');assert.equal(button.getAttribute('aria-pressed'),'false');
 button.focus();assert.equal(d.activeElement,button);button.click();
 assert.equal(d.getElementById('password'),input);assert.equal(input.type,'text');assert.equal(submissions,0);
 assert.equal(button.getAttribute('aria-label'),'Hide password');assert.equal(button.getAttribute('aria-pressed'),'true');
 assert.equal(button.querySelector('[data-password-show-icon]').hasAttribute('hidden'),true);
 assert.equal(button.querySelector('[data-password-hide-icon]').hasAttribute('hidden'),false);
 assert.equal(input.autocomplete,'current-password');assert.equal(input.required,true);
 assert.deepEqual([...new w.FormData(form)],[['_csrf','test-csrf'],['password','synthetic test value']]);
 button.click();assert.equal(input.type,'password');assert.equal(input.value,'synthetic test value');
});

test('Create/Edit user reuses the eye while retaining password validation and native submission',t=>{
 const wrapper=expand(userJsp.match(/<div class="fcms-password-control"[\s\S]+?<\/div>/)[0])
  .replace('<form:password id="password" path="password"','<input type="password" id="password" name="password"');
 const dom=new JSDOM('<form>'+wrapper+'</form>',{url:'http://localhost/admin/users/new',runScripts:'outside-only'});
 t.after(()=>dom.window.close());const w=dom.window,d=w.document,input=d.getElementById('password');
 w.eval(source);d.dispatchEvent(new w.Event('DOMContentLoaded'));
 const button=d.querySelector('button');input.value='synthetic test password';
 assert.equal(button.hidden,false);button.click();
 assert.equal(input.type,'text');assert.equal(input.autocomplete,'new-password');
 assert.equal(input.minLength,12);assert.equal(input.maxLength,72);
 assert.equal(input.required,false,'An edit may keep its existing password');
 assert.equal(button.type,'button');assert.equal(button.getAttribute('aria-pressed'),'true');
 assert.deepEqual([...new w.FormData(d.querySelector('form'))],[['password','synthetic test password']]);
 d.querySelector('form').dispatchEvent(new w.Event('submit'));
 assert.equal(input.type,'password');assert.equal(input.value,'synthetic test password');
});

test('submit and history restoration re-mask the password without cancelling submission or storing it',t=>{
 const {w,form,input,button}=setup(t,['Onyesha nenosiri','Ficha nenosiri']);input.value='synthetic test value';
 button.click();assert.equal(button.getAttribute('aria-label'),'Ficha nenosiri');
 const submit=new w.Event('submit',{cancelable:true});form.dispatchEvent(submit);
 assert.equal(submit.defaultPrevented,false);assert.equal(input.type,'password');assert.equal(input.value,'synthetic test value');
 button.click();w.dispatchEvent(new w.Event('pagehide'));assert.equal(input.type,'password');
 button.click();w.dispatchEvent(new w.Event('pageshow'));assert.equal(input.type,'password');
 assert.equal(button.getAttribute('aria-label'),'Onyesha nenosiri');assert.equal(button.getAttribute('aria-pressed'),'false');
 assert.equal(w.localStorage.length,0);assert.equal(w.sessionStorage.length,0);
});

test('without JavaScript the native password field remains masked and the eye stays hidden',t=>{
 const {input,button,form}=setup(t,undefined,false);
 assert.equal(input.type,'password');assert.equal(input.name,'password');assert.equal(button.hidden,true);
 assert.equal(button.type,'button');assert.equal(form.method,'post');assert.equal(form.querySelector('[name=_csrf]').value,'test-csrf');
});
