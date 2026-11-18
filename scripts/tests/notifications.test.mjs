import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

const root=new URL('../../',import.meta.url);
const css=await readFile(new URL('src/main/resources/static/css/notifications.css',root),'utf8');
const app=await readFile(new URL('src/main/resources/static/js/app.js',root),'utf8');
function setup(t,content){
 const dom=new JSDOM(`<html><head><style>${css}</style></head><body><main id="main-content" tabindex="-1">${content}</main></body></html>`,{url:'http://fcms.test/users',runScripts:'outside-only',pretendToBeVisual:true});
 t.after(()=>dom.window.close());return {w:dom.window,d:dom.window.document};
}

test('severity is consistent for task outcomes, contextual warnings and dynamically inserted errors',t=>{
 const {w,d}=setup(t,'<div class="console-message" role="status" data-message-type="success">Saved</div><div class="fcms-alert" role="alert" data-message-type="warning">Check the result before retrying</div><div class="fcms-alert" role="status">Information</div>');
 const alerts=d.querySelectorAll('main > div');
 assert.equal(w.getComputedStyle(alerts[0]).getPropertyValue('--fcms-notice-color'),'#037f0c');
 assert.equal(w.getComputedStyle(alerts[1]).getPropertyValue('--fcms-notice-color'),'#8d6605');
 assert.equal(w.getComputedStyle(alerts[2]).getPropertyValue('--fcms-notice-color'),'#0972d3');
 for(const kind of ['fcms-alert','app-loading-error','workspace-navigation-error','fs-tab-status']){
  const message=d.createElement('div');message.className=kind;message.role='alert';message.textContent='Request failed';d.querySelector('main').append(message);
  assert.equal(w.getComputedStyle(message).getPropertyValue('--fcms-notice-color'),'#d13212',kind);
  const rules=[...d.styleSheets[0].cssRules].filter(rule=>rule.selectorText?.endsWith('::before'));
  assert.ok(rules.some(rule=>message.matches(rule.selectorText.replace(/::before$/,''))),kind+' has a decorative icon');
 }
});

test('hidden and empty feedback remains hidden and field errors keep their compact presentation',t=>{
 const {w,d}=setup(t,'<div class="fcms-alert" hidden>Secret draft error</div><div class="fcms-alert"></div><span class="fcms-error">Required</span><div class="neo-select-empty" role="status">No matches</div>');
 assert.equal(w.getComputedStyle(d.querySelector('[hidden]')).display,'none');
 assert.equal(w.getComputedStyle(d.querySelector('.fcms-alert:empty')).display,'none');
 const field=d.querySelector('.fcms-error');assert.equal(w.getComputedStyle(field).minHeight,'18px');
 assert.notEqual(w.getComputedStyle(field).backgroundColor,'rgb(255, 247, 247)');
 assert.equal(w.getComputedStyle(d.querySelector('.neo-select-empty')).getPropertyValue('--fcms-notice-icon'),'');
});

test('Cloudscape artwork uses distinct, valid SVG geometry from the retained source and licenses',async t=>{
 const {w}=setup(t,'');
 const source=await readFile(new URL('reference/microfinance-ui/source/src/main/resources/static/js/console-resource-details.js',root),'utf8');
 const artwork=JSON.parse(source.slice(source.indexOf('{',source.indexOf('const artwork =')),source.indexOf('\n};',source.indexOf('const artwork ='))+2));
 const names={success:'status-positive',error:'status-negative',warning:'status-warning',info:'status-info'},icons=new Set();
 const paths=doc=>[...doc.querySelectorAll('path')].map(path=>path.getAttribute('d').replace(/\s/g,'')).join('');
 for(const [kind,name] of Object.entries(names)){
  const encoded=css.match(new RegExp('--fcms-icon-'+kind+':url\\("data:image/svg\\+xml,([^"\\n]+)"\\)'))?.[1];assert.ok(encoded,kind);
  const svg=decodeURIComponent(encoded);icons.add(svg);
  const actual=new w.DOMParser().parseFromString(svg,'image/svg+xml');assert.equal(actual.querySelector('parsererror'),null);
  const expected=new w.DOMParser().parseFromString('<svg xmlns="http://www.w3.org/2000/svg">'+artwork[name]+'</svg>','image/svg+xml');
  assert.equal(paths(actual),paths(expected),kind);
  assert.equal(actual.querySelector('circle')?.getAttribute('r'),expected.querySelector('circle')?.getAttribute('r'),kind);
 }
 assert.equal(icons.size,4);
 assert.match(await readFile(new URL('src/main/resources/static/js/vendor/cloudscape-icons.LICENSE',root),'utf8'),/Apache License/);
});

test('dismissing a Flashbar preserves other notices, links and unsaved fields and restores focus',t=>{
 const {w,d}=setup(t,'<input name="draft" value="Unsaved"><div class="console-message" data-resource-notice data-message-type="success" role="status" tabindex="-1">Saved <a href="/users/1">View resource</a><button data-dismiss-notice aria-label="Dismiss"><svg aria-hidden="true"><path d="M2 2L14 14"/></svg></button></div><div class="console-message" data-resource-notice data-message-type="info" role="status" tabindex="-1">No changes<button data-dismiss-notice aria-label="Dismiss"></button></div>');
 w.matchMedia=()=>({matches:false,addEventListener(){}});w.scrollTo=()=>{};w.eval(app);
 const draft=d.querySelector('input'),link=d.querySelector('a');assert.equal(link.getAttribute('href'),'/users/1');
 const first=d.querySelector('[data-dismiss-notice]');first.focus();first.click();
 assert.equal(d.querySelectorAll('[data-resource-notice]').length,1);assert.equal(d.activeElement,d.querySelector('[data-resource-notice]'));
 assert.equal(d.querySelector('input'),draft);assert.equal(draft.value,'Unsaved');
 const last=d.querySelector('[data-dismiss-notice]');last.focus();last.click();assert.equal(d.activeElement,d.querySelector('main'));
});

test('every standalone authentication and error page loads notifications without requiring script initialization',async()=>{
 for(const name of ['auth/login.jsp','auth/forgot.jsp','auth/reset.jsp','auth/user-form-session-error.jsp','shared/error.jsp','shared/header.jspf']){
  const source=await readFile(new URL('src/main/webapp/WEB-INF/jsp/'+name,root),'utf8');
  assert.match(source,/\/css\/notifications\.css\?v=/,name);
 }
 const notice=await readFile(new URL('src/main/webapp/WEB-INF/jsp/reusableuicomponents/resource-notice.jspf',root),'utf8');
 assert.match(notice,/<ui:flashbar /);
 const flashbar=await readFile(new URL('src/main/webapp/WEB-INF/tags/flashbar.tag',root),'utf8');
 assert.doesNotMatch(flashbar,/console-message__icon|&#215;/);
 assert.match(flashbar,/data-dismiss-notice aria-label=/);
});
