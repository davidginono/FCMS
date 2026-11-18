import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';

const source = await readFile(new URL('../../src/main/resources/static/js/user-permissions.js', import.meta.url), 'utf8');
const ux = await readFile(new URL('../../src/main/resources/static/js/form-ux.js', import.meta.url), 'utf8');
const bundles = {clerk:['account.write','accounting.write'],staff:['account.write','leave.write'],admin:['account.write','iam.user.manage'],director:['account.write','leave.review']};
function harness(extras = [], selected = 'clerk', excluded = []) {
    const claims = [...new Set([...Object.values(bundles).flat(), 'backup.manage'])];
    const dom = new JSDOM(`<form class="fcms-form" data-user-permissions><input type="hidden" name="permissionsSubmitted" value="true"><select id="roleId" name="roleId">${Object.entries(bundles).map(([role, defaults]) => `<option value="${role}" data-default-claims="${defaults.join(' ')}" ${role === selected ? 'selected' : ''}>${role}</option>`).join('')}</select>${claims.map(claim => `<label><input type="checkbox" name="claims" value="${claim}" data-role-permission ${!excluded.includes(claim) && (extras.includes(claim) || bundles[selected].includes(claim)) ? 'checked' : ''}></label>`).join('')}</form><div id="fcms-discard-modal" hidden><div role="dialog"><div class="app-modal-actions"><button data-fcms-keep>Keep</button><button data-fcms-discard>Discard</button></div></div></div>`, {url:'http://fcms.test/admin/users/new', runScripts:'outside-only'});
    const {window} = dom;
    window.WorkspaceNavigation = {onReady:callback => callback()};
    const form = window.document.querySelector('form'), role = form.querySelector('select');
    window.eval(ux);
    window.eval(source);
    const input = claim => form.querySelector(`input[type=checkbox][value="${claim}"]`);
    const choose = value => {role.value = value; role.dispatchEvent(new window.Event('change', {bubbles:true}));};
    const submitted = () => [...new window.FormData(form).getAll('claims')].sort();
    return {dom,window,form,input,choose,submitted};
}

test('each role checks its defaults and every checked permission remains editable and submitted', t => {
    const h = harness(); t.after(() => h.dom.window.close());
    for (const [role, defaults] of Object.entries(bundles)) {
        h.choose(role);
        for (const input of h.form.querySelectorAll('[data-role-permission]')) {
            assert.equal(input.checked, defaults.includes(input.value));
            assert.equal(input.disabled, false);
        }
        assert.deepEqual(h.submitted(), [...defaults].sort());
    }
});

test('edit and validation recovery retain unchecked defaults and additional selections', t => {
    const h = harness(['backup.manage'], 'clerk', ['accounting.write']); t.after(() => h.dom.window.close());
    assert.equal(h.input('accounting.write').checked, false);
    h.choose('staff');
    assert.equal(h.input('accounting.write').checked, false);
    assert.equal(h.input('accounting.write').disabled, false);
    h.input('accounting.write').click();
    h.choose('clerk');
    assert.equal(h.input('accounting.write').checked, true);
    h.choose('director');
    assert.equal(h.input('accounting.write').checked, true);
    assert.deepEqual(h.submitted(), [...bundles.director,'accounting.write','backup.manage'].sort());
});

test('a manually added permission survives a temporary role inheritance and is not duplicated', t => {
    const h = harness([], 'staff'); t.after(() => h.dom.window.close());
    h.input('iam.user.manage').click();
    h.choose('admin');
    assert.deepEqual(h.submitted(), [...bundles.admin].sort());
    h.choose('director');
    assert.deepEqual(h.submitted(), [...bundles.director,'iam.user.manage'].sort());
    h.choose('admin');
    h.window.eval(source);
    assert.deepEqual(h.submitted(), [...bundles.admin].sort());
    assert.equal(h.form.querySelectorAll('input[name=claims][value="iam.user.manage"]').length, 1);
});

test('reset restores the original role and explicit grants', async t => {
    const h = harness(['backup.manage']); t.after(() => h.dom.window.close());
    h.input('backup.manage').click();
    h.input('account.write').click();
    h.choose('director');
    h.form.reset();
    h.form.querySelector('select').dispatchEvent(new h.window.Event('change', {bubbles:true}));
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.equal(h.form.querySelector('select').value, 'clerk');
    assert.equal(h.input('accounting.write').disabled, false);
    assert.equal(h.input('account.write').checked, true);
    assert.deepEqual(h.submitted(), [...bundles.clerk,'backup.manage'].sort());
});

test('initial defaults stay pristine while role changes trigger shared unsaved-change protection', t => {
    const h = harness(); t.after(() => h.dom.window.close());
    const before = new h.window.Event('beforeunload', {cancelable:true});
    h.window.dispatchEvent(before);
    assert.equal(before.defaultPrevented, false);
    h.input('account.write').click();
    const unchecked = new h.window.Event('beforeunload', {cancelable:true});
    h.window.dispatchEvent(unchecked);
    assert.equal(unchecked.defaultPrevented, true);
    h.input('account.write').click();
    h.choose('staff');
    const changed = new h.window.Event('beforeunload', {cancelable:true});
    h.window.dispatchEvent(changed);
    assert.equal(changed.defaultPrevented, true);
});

test('an unchecked role default stays unchecked through role switching and can be re-enabled', t => {
    const h = harness(); t.after(() => h.dom.window.close());
    h.input('account.write').click();
    for (const role of Object.keys(bundles)) {
        h.choose(role);
        assert.equal(h.input('account.write').checked, false);
        assert.equal(h.submitted().includes('account.write'), false);
    }
    h.input('account.write').click();
    assert.equal(h.submitted().includes('account.write'), true);
});

test('an explicitly empty selection survives validation initialization without restoring defaults', t => {
    const h = harness([], 'clerk', bundles.clerk); t.after(() => h.dom.window.close());
    assert.deepEqual(h.submitted(), []);
    assert.equal(h.form.elements.permissionsSubmitted.value, 'true');
    h.window.eval(source);
    assert.deepEqual(h.submitted(), []);
});
