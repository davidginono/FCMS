import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

const root = new URL('../../src/main/resources/static/js/', import.meta.url);
const sessionSource = await readFile(new URL('user-form-session.js', root), 'utf8');
const formSource = await readFile(new URL('form-ux.js', root), 'utf8');
const loadingSource = await readFile(new URL('ui-loading.js', root), 'utf8');
const response = (data = {userId:'42', parameterName:'_csrf', token:'fresh-token'}, overrides = {}) => ({
    ok:true, status:200, redirected:false, headers:new Headers({'Content-Type':'application/json'}), json:async () => data, ...overrides
});
async function until(predicate) {
    for (let attempt = 0; attempt < 100; attempt++) {if (predicate()) return; await new Promise(resolve => setTimeout(resolve, 5));}
    throw new Error('Session verification did not settle');
}
function harness(t, fetcher = async () => response(), timeout = false) {
    const dom = new JSDOM(`<div data-form-session-feedback hidden data-expired="Session expired" data-changed="Account changed" data-forbidden="Permission denied" data-unavailable="Try again"><div role="alert" tabindex="-1"><span data-form-session-message></span><a href="/login" target="_blank" rel="noopener" data-form-session-signin hidden>Sign in</a></div></div>
    <form class="fcms-form" method="post" action="/admin/users" data-form-session-url="/admin/users/form-session" data-form-session-user="42">
    <input type="hidden" name="_csrf" value="old-token-one"><input name="firstName" value="Draft"><input type="password" name="password" value="SyntheticPassword123!">
    <input type="checkbox" name="claims" value="account.write" checked><input type="hidden" name="_csrf" value="old-token-two"><button type="submit" name="operation" value="create">Create</button></form>
    <div id="fcms-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-fcms-keep>Keep</button><button data-fcms-discard>Discard</button></div></div></div>`,
    {url:'http://fcms.test/admin/users/new', runScripts:'outside-only', pretendToBeVisual:true});
    t.after(() => dom.window.close());
    const w = dom.window, d = w.document, requests = [], native = [];
    w.WorkspaceNavigation = {onReady:callback => callback()};
    if (timeout) {const original = w.setTimeout.bind(w); w.setTimeout = (callback, delay) => original(callback, Math.min(delay, 10));}
    w.fetch = (url, options) => {requests.push({url, options}); return fetcher(url, options);};
    const form = d.querySelector('form'), button = form.querySelector('button');
    // Model native submission after bubbling, including the shared owner's preserved submitter.
    form.requestSubmit = submitter => {
        if (!form.checkValidity()) return;
        const event = new w.SubmitEvent('submit', {bubbles:true, cancelable:true, submitter});
        form.dispatchEvent(event);
        if (!event.defaultPrevented) native.push([...new w.FormData(form)]);
    };
    w.eval(loadingSource); w.eval(formSource); w.eval(sessionSource);
    return {w, d, form, button, requests, native, feedback:d.querySelector('[data-form-session-feedback]')};
}

test('fresh verification updates every CSRF field and hands one native POST to the existing owner', async t => {
    const h = harness(t); h.form.requestSubmit(h.button);
    assert.equal(h.native.length, 0); assert.equal(h.form.hasAttribute('aria-busy'), false);
    await until(() => h.native.length === 1);
    assert.equal(h.requests.length, 1); assert.equal(h.requests[0].options.credentials, 'same-origin');
    assert.equal(h.requests[0].options.cache, 'no-store'); assert.equal(h.requests[0].options.body, undefined);
    assert.deepEqual(h.native[0].filter(([key]) => key === '_csrf'), [['_csrf','fresh-token'],['_csrf','fresh-token']]);
    assert.deepEqual(h.native[0].filter(([key]) => key !== '_csrf'), [['firstName','Draft'],['password','SyntheticPassword123!'],['claims','account.write'],['operation','create']]);
    assert.equal(h.form.getAttribute('aria-busy'), 'true'); assert.equal(h.button.disabled, true);
    h.form.requestSubmit(); assert.equal(h.native.length, 1); assert.equal(h.requests.length, 1);
});

test('expired sessions preserve the live draft and secret, then require an explicit retry after sign-in', async t => {
    let expired = true;
    const h = harness(t, async () => expired ? response(null, {redirected:true}) : response());
    h.form.requestSubmit(h.button); await until(() => !h.feedback.hidden);
    assert.equal(h.feedback.querySelector('[data-form-session-message]').textContent, 'Session expired');
    assert.equal(h.feedback.querySelector('[role="alert"]').dataset.messageType,'warning');
    assert.equal(h.feedback.querySelector('a').hidden, false); assert.equal(h.feedback.querySelector('a').target, '_blank');
    assert.equal(h.native.length, 0); assert.equal(h.button.disabled, false);
    assert.equal(h.form.elements.firstName.value, 'Draft'); assert.equal(h.form.elements.password.value, 'SyntheticPassword123!');
    assert.equal(h.form.querySelector('[name=claims]').checked, true);
    assert.equal(h.w.localStorage.length, 0); assert.equal(h.w.sessionStorage.length, 0);
    expired = false; assert.equal(h.native.length, 0);
    h.form.requestSubmit(h.button); await until(() => h.native.length === 1);
});

test('changed accounts and lost permissions cannot submit the old administrator form', async t => {
    for (const [result, message, signin] of [[response({userId:'99',parameterName:'_csrf',token:'other-token'}),'Account changed',true], [response(null,{ok:false,status:403}),'Permission denied',false]]) {
        const h = harness(t, async () => result); h.form.requestSubmit(h.button);
        await until(() => !h.feedback.hidden);
        assert.equal(h.native.length, 0); assert.equal(h.feedback.querySelector('[data-form-session-message]').textContent, message);
        assert.equal(h.feedback.querySelector('a').hidden, !signin);
        assert.equal(h.feedback.querySelector('[role="alert"]').dataset.messageType,signin?'warning':'error');
        assert.equal(h.form.querySelector('[name=_csrf]').value, 'old-token-one');
    }
});

test('double clicks share verification and changed drafts cancel a late response without replay', async t => {
    let complete;
    const h = harness(t, () => new Promise(resolve => complete = resolve));
    h.form.requestSubmit(h.button); h.form.requestSubmit(h.button); assert.equal(h.requests.length, 1);
    h.form.elements.firstName.value = 'Latest draft';
    h.form.elements.firstName.dispatchEvent(new h.w.Event('input', {bubbles:true}));
    assert.equal(h.requests[0].options.signal.aborted, true);
    complete(response()); await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(h.native.length, 0); assert.equal(h.form.elements.firstName.value, 'Latest draft');
    assert.equal(h.feedback.hidden, true); assert.equal(h.button.hasAttribute('aria-busy'), false);
});

test('navigation cancels verification and cannot post a form after its owner leaves', async t => {
    let complete; const h = harness(t, () => new Promise(resolve => complete = resolve));
    h.form.requestSubmit(h.button);
    h.d.dispatchEvent(new h.w.CustomEvent('workspace:before-navigate', {cancelable:true, detail:{resume(){}}}));
    complete(response()); await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(h.requests[0].options.signal.aborted, true); assert.equal(h.native.length, 0);
    assert.equal(h.button.hasAttribute('aria-busy'), false);
});

test('timeouts clear the spinner, retain fields and allow a deliberate retry', async t => {
    let offline = true;
    const h = harness(t, (url, options) => offline ? new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(new Error('timeout')))) : Promise.resolve(response()), true);
    h.form.requestSubmit(h.button); await until(() => !h.feedback.hidden);
    assert.equal(h.feedback.querySelector('[data-form-session-message]').textContent, 'Try again');
    assert.equal(h.native.length, 0); assert.equal(h.button.hasAttribute('aria-busy'), false);
    offline = false; h.form.requestSubmit(h.button); await until(() => h.native.length === 1);
});

test('the hidden recovery alert does not make an untouched form dirty', t => {
    const h = harness(t), before = new h.w.Event('beforeunload', {cancelable:true});
    h.w.dispatchEvent(before); assert.equal(before.defaultPrevented, false);
});

test('a native validation failure after verification does not bypass verification on the next click', async t => {
    let h, rejectValidation = true;
    h = harness(t, async () => {
        if (rejectValidation) h.form.elements.firstName.setCustomValidity('Synthetic validation');
        return response();
    });
    h.form.requestSubmit(h.button);
    await until(() => !h.button.hasAttribute('aria-busy'));
    assert.equal(h.native.length, 0);
    rejectValidation = false; h.form.elements.firstName.setCustomValidity('');
    h.form.requestSubmit(h.button); await until(() => h.native.length === 1);
    assert.equal(h.requests.length, 2);
});
