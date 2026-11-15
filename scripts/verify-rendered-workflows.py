#!/usr/bin/env python3
"""Read-only Playwright checks against an isolated FCMS fixture instance.

Requires Python Playwright and Chromium. Supply FCMS_UI_EMAIL and FCMS_UI_PASSWORD
through the process environment. Never use a production database or real user data.
"""
import argparse
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8090')
parser.add_argument('--chromium', default='/usr/bin/chromium')
parser.add_argument('--output', default='.verification/rendered-workflows')
parser.add_argument('--quick', action='store_true', help='Recheck shared interactions and representative registers')
args = parser.parse_args()
output = Path(args.output)
output.mkdir(parents=True, exist_ok=True)
registers = ['/clients/saccos', '/clients/amcos', '/clients/unions', '/clients/microfinance',
             '/clients/others', '/tasks', '/leaves', '/staff-reports', '/finance/expenses',
             '/finance/income', '/finance/petty-cash', '/finance/cloud-backup', '/finance/bulk-sms',
             '/finance/cloud-backup/billing', '/finance/expenses/categories', '/admin/users', '/backups']
if args.quick:
    registers = ['/tasks', '/clients/saccos', '/finance/expenses', '/finance/expenses/categories']
results, failures, js_errors = [], [], []
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=args.chromium, headless=True, args=['--no-sandbox'])
    page = browser.new_page(viewport={'width': 1440, 'height': 960})
    page.on('pageerror', lambda error: js_errors.append(str(error)))
    page.goto(args.base_url + '/login')
    page.locator('[name=email]').fill(os.environ['FCMS_UI_EMAIL'])
    page.locator('[name=password]').fill(os.environ['FCMS_UI_PASSWORD'])
    page.locator('button[type=submit]').click()
    page.wait_for_url('**/dashboard')
    # Every selected workspace at desktop, then every changed register at all required widths.
    for lang in ['en', 'sw']:
        for route in registers + ['/account', '/account/password', '/dashboard', '/reports',
                                   '/sales/prospects', '/sales/invoices', '/tasks/new', '/leaves/new',
                                   '/staff-reports/new', '/finance/expenses/new', '/clients/amcos/new']:
            response = page.goto(args.base_url + route + '?lang=' + lang)
            page.wait_for_load_state('networkidle')
            ok = response.status == 200 and page.locator('main').count() == 1
            results.append({'route': route, 'lang': lang, 'status': response.status})
            if not ok:
                failures.append({'route': route, 'lang': lang, 'status': response.status})
        for width in ([375, 1440] if args.quick else [320, 375, 768, 1024, 1440, 1920]):
            page.set_viewport_size({'width': width, 'height': 960})
            for route in registers:
                response = page.goto(args.base_url + route + '?lang=' + lang)
                page.wait_for_load_state('networkidle')
                actual = page.evaluate('document.documentElement.scrollWidth')
                fact = {'route': route, 'lang': lang, 'width': width, 'document_width': actual, 'status': response.status}
                results.append(fact)
                if response.status != 200 or actual > width:
                    failures.append(fact)
                if width in [375, 1440] and route in ['/tasks', '/clients/saccos', '/finance/expenses', '/admin/users']:
                    page.screenshot(path=str(output / (route.strip('/').replace('/', '-') + '-' + lang + '-' + str(width) + '.png')), full_page=True)
    (output / 'results.json').write_text(json.dumps({'checks': len(results), 'failures': failures, 'javascript_errors': js_errors, 'results': results}, indent=2))
    page.set_viewport_size({'width': 1440, 'height': 960})
    page.goto(args.base_url + '/tasks?lang=en')
    page.wait_for_load_state('networkidle')
    checkbox = page.locator('tbody .resource-table-selection input[type=checkbox]').first
    if checkbox.count():
        checkbox.check()
        trigger = page.locator('.resource-table-action-menu > button').first
        assert trigger.is_enabled(), 'Selected record must enable its authorized actions'
        trigger.click()
        assert page.locator('.resource-table-menu:visible').count() == 1
        page.keyboard.press('Escape')
        assert trigger.evaluate('node => document.activeElement === node')
        checkbox.uncheck()
        assert trigger.is_disabled(), 'Record actions must disable after deselection'
    gear = page.locator('.resource-table-preferences-button').first
    gear.click()
    assert page.locator('dialog[open]').count() == 1
    page.keyboard.press('Escape')
    assert gear.evaluate('node => document.activeElement === node')
    page.goto(args.base_url + '/tasks?lang=en')
    page.wait_for_load_state('networkidle')
    sort = page.locator('th[data-table-sort-key=subject] a, th[data-table-sort-key=subject] button').first
    sort.click()
    page.wait_for_load_state('networkidle')
    page.wait_for_function("new URL(location.href).searchParams.get('sort') === 'subject'")
    assert 'sort=subject' in page.url, 'Sort must reach the server'
    assert page.locator('th[data-table-sort-key=subject]').get_attribute('aria-sort') in ['ascending', 'descending']
    export = page.locator('[data-table-export=pdf]')
    assert 'sort=subject' in export.get_attribute('href'), 'Export must track server sorting'
    search = page.locator('[name=search]').first
    search.fill('OWN-ACTIVITY-731')
    page.locator('form[method=get] button[type=submit]').click()
    page.wait_for_function("new URL(location.href).searchParams.get('search') === 'OWN-ACTIVITY-731'")
    assert 'search=OWN-ACTIVITY-731' in export.get_attribute('href'), 'Export must track applied filters'

    browser.close()
report = {'checks': len(results), 'failures': failures, 'javascript_errors': js_errors, 'results': results}
(output / 'results.json').write_text(json.dumps(report, indent=2))
print(json.dumps({'checks': len(results), 'failures': failures, 'javascript_errors': js_errors}, indent=2))
raise SystemExit(1 if failures or js_errors else 0)
