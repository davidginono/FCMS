#!/usr/bin/env python3
"""Write synthetic fixtures through native FCMS forms on an isolated loopback instance.

Requires Python Playwright, Chromium and FCMS_UI_EMAIL/FCMS_UI_PASSWORD. Do not point
this at a production tunnel: the runner creates and edits clients and ledger/work items.
"""
import argparse
import json
import os
import re
import time
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8090')
parser.add_argument('--chromium', default='/usr/bin/chromium')
parser.add_argument('--output', default='.verification/browser-commands')
args = parser.parse_args()
assert urlparse(args.base_url).hostname in ['127.0.0.1', 'localhost']
output = Path(args.output)
output.mkdir(parents=True, exist_ok=True)
results = []
suffix = str(int(time.time()))
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=args.chromium, headless=True, args=['--no-sandbox'])
    page = browser.new_page(viewport={'width': 1440, 'height': 960})
    page.goto(args.base_url + '/login')
    page.locator('[name=email]').fill(os.environ['FCMS_UI_EMAIL'])
    page.locator('[name=password]').fill(os.environ['FCMS_UI_PASSWORD'])
    page.locator('button[type=submit]').click()
    page.wait_for_url('**/dashboard')
    for lang in ['en', 'sw']:
        for category in ['saccos', 'amcos', 'unions', 'microfinance', 'others']:
            response = page.goto(args.base_url + '/clients/' + category + '/new?lang=' + lang)
            assert response.status == 200
            name = 'Browser Synthetic ' + category + ' ' + lang + ' ' + suffix
            page.locator('[name=' + ('saccosName' if category == 'saccos' else 'clientName') + ']').fill(name)
            page.locator('[name=region]').fill('Synthetic Region')
            page.locator('[name=financialYearId]').select_option('1')
            page.locator('[name=paymentStatusId]').select_option('1')
            page.locator('[name=annualFee]').fill('-0.01')
            assert not page.locator('[name=annualFee]').evaluate('node => node.checkValidity()')
            page.locator('[name=annualFee]').fill('123.45')
            page.locator('[name=amount]').fill('100.11')
            page.locator('button.btn-launch[type=submit]').click()
            page.wait_for_url(re.compile('/clients/' + category + r'/\d+\?saved'))
            assert name in page.locator('main').inner_text()
            detail = page.url.split('?')[0]
            page.goto(detail + '/edit?lang=' + lang)
            assert page.locator('[name=amount]').input_value() == '100.11'
            page.locator('[name=region]').fill('Updated Synthetic Region')
            page.locator('button.btn-launch[type=submit]').click()
            page.wait_for_url(re.compile('/clients/' + category + r'/\d+\?saved'))
            assert 'Updated Synthetic Region' in page.locator('main').inner_text()
            results.append({'flow': 'client create/edit/invalid amount recovery', 'category': category, 'lang': lang})
    page.goto(args.base_url + '/tasks/new?lang=en')
    page.locator('[name=subject]').fill('Browser Synthetic Decimal Task ' + suffix)
    page.locator('[name=description]').fill('Synthetic cents and lifecycle check')
    page.locator('[name=ownerId]').select_option('1')
    page.locator('[name=clientId]').select_option('1')
    page.locator('[name=workType]').fill('Support')
    page.locator('[name=allowance]').fill('33.37')
    page.locator('[name=amount]').fill('100.11')
    assert page.locator('[name=allowance]').evaluate('node => node.checkValidity()')
    assert page.locator('[name=amount]').evaluate('node => node.checkValidity()')
    page.locator('button.btn-launch[type=submit]').click()
    page.wait_for_url(re.compile(r'/tasks/\d+(?:\?.*)?$'))
    assert '33.37' in page.locator('main').inner_text() and '100.11' in page.locator('main').inner_text()
    page.locator('form[action$="/actions/start"] button[type=submit]').click()
    page.wait_for_load_state('networkidle')
    assert page.locator('form[action$="/actions/start"]').count() == 0
    page.locator('form[action$="/actions/complete"] button[type=submit]').click()
    page.wait_for_load_state('networkidle')
    assert page.locator('form[action$="/actions/complete"]').count() == 0
    results.append({'flow': 'task create/start/complete with exact cents'})
    page.goto(args.base_url + '/finance/bulk-sms/new?lang=en')
    page.locator('[name=category]').fill('Synthetic SMS stock')
    page.locator('[name=description]').fill('Browser Synthetic unit-only stock ' + suffix)
    page.locator('[name=reference]').fill('SYNTHETIC-STOCK-' + suffix)
    page.locator('[name=amount]').fill('0.00')
    page.locator('[name=units]').fill('13')
    assert page.locator('[name=amount]').evaluate('node => node.checkValidity()')
    page.locator('button.btn-launch[type=submit]').click()
    page.wait_for_url(re.compile(r'/finance/bulk-sms/\d+(?:\?.*)?$'))
    assert 'SYNTHETIC-STOCK-' + suffix in page.locator('main').inner_text()
    results.append({'flow': 'bulk SMS zero-money unit-only post'})
    page.screenshot(path=str(output / 'bulk-unit-only.png'), full_page=True)
    browser.close()
(output / 'results.json').write_text(json.dumps({'checks': results}, indent=2))
print(json.dumps({'checks': results}, indent=2))
