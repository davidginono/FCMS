#!/usr/bin/env python3
"""Native category/report/leave acceptance on an isolated synthetic loopback instance.

The workforce checks write fixtures. Supply FCMS_UI_EMAIL/PASSWORD,
FCMS_UI_STAFF_EMAIL/PASSWORD and FCMS_UI_TEAM_EMAIL/PASSWORD/ID. Never use real data.
"""
import argparse
import json
import os
import re
import time
from pathlib import Path
from urllib.parse import urlparse, urljoin
from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:8090')
parser.add_argument('--output', default='.verification/expense-workforce-browser')
args = parser.parse_args()
assert urlparse(args.base_url).hostname in ['127.0.0.1', 'localhost']
output = Path(args.output)
output.mkdir(parents=True, exist_ok=True)
results, errors = [], []
suffix = str(int(time.time()))

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path='/usr/bin/chromium', headless=True, args=['--no-sandbox'])

    def session(prefix):
        context = browser.new_context(viewport={'width': 1440, 'height': 960})
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(args.base_url + '/login')
        page.locator('[name=email]').fill(os.environ[prefix + '_EMAIL'])
        page.locator('[name=password]').fill(os.environ[prefix + '_PASSWORD'])
        page.locator('button[type=submit]').click()
        page.wait_for_url('**/dashboard')
        return page

    admin, staff, team = [session(prefix) for prefix in ['FCMS_UI', 'FCMS_UI_STAFF', 'FCMS_UI_TEAM']]

    def navigate_click(page, selector):
        with page.expect_navigation(wait_until='networkidle'):
            page.locator(selector).click()

    for lang in ['en', 'sw']:
        for width in [320, 375, 768, 1024, 1440, 1920]:
            admin.set_viewport_size({'width': width, 'height': 960})
            response = admin.goto(args.base_url + '/finance/expenses/categories?year=2026&lang=' + lang)
            admin.wait_for_load_state('networkidle')
            assert response.status == 200
            assert admin.evaluate('document.documentElement.scrollWidth') <= width
            assert admin.locator('main h2').count() == 1
            assert admin.locator('[data-table-query-value=total]').inner_text().replace(',', '') == '1026.76'
            assert admin.locator('tbody .resource-table-selection').count() == 0
            results.append({'flow': 'category summary', 'lang': lang, 'width': width})
            if width in [375, 1440]:
                admin.screenshot(path=str(output / ('categories-' + lang + '-' + str(width) + '.png')), full_page=True)
        admin.set_viewport_size({'width': 1440, 'height': 960})
        admin.locator('th[data-table-sort-key=amount] a').click()
        admin.wait_for_function("new URL(location.href).searchParams.get('sort') === 'amount'")
        admin.locator('[name=year]').fill('2025')
        admin.locator('form[method=get] button[type=submit]').click()
        admin.wait_for_function("new URL(location.href).searchParams.get('year') === '2025'")
        assert admin.locator('[data-table-query-value=total]').inner_text().replace(',', '') == '21.01'
        export = admin.locator('[data-table-export=pdf]').get_attribute('href')
        assert 'year=2025' in export and 'sort=amount' in export
        response = admin.request.get(urljoin(args.base_url, export))
        assert response.status == 200 and response.body().startswith(b'%PDF')
        navigate_click(admin, 'tbody .resource-table-id-link')
        assert admin.locator('[name=year]').input_value() == '2025'
        assert admin.locator('[name=category]').input_value() == 'Travel'
        assert '21.01' in admin.locator('tbody').inner_text()
        assert '999.99' not in admin.locator('tbody').inner_text()
        results.append({'flow': 'sort/filter/total/PDF/category drill retention', 'lang': lang})

        for route in ['leaves', 'staff-reports']:
            response = staff.goto(args.base_url + '/' + route + '/new?lang=' + lang)
            assert response.status == 200
            name = 'Browser Synthetic ' + route + ' ' + lang + ' ' + suffix
            staff.locator('[name=subject]').fill(name)
            staff.locator('[name=description]').fill('Synthetic activity ' + suffix)
            staff.locator('[name=startAt]').fill('2026-11-01T10:30')
            staff.locator('[name=endAt]').fill('2026-11-02T17:15')
            if route == 'staff-reports':
                staff.locator('[name=workType]').select_option('GROUP')
                staff.locator('#participantChoice').select_option(os.environ['FCMS_UI_TEAM_ID'])
                staff.locator('[data-team-add]').click()
                assert staff.locator('[name=participants]:checked').count() >= 1
                staff.locator('[name=outcome]').select_option('COMPLETED')
            navigate_click(staff, 'button.btn-launch[type=submit]')
            assert re.search('/' + route + r'/\d+(?:\?.*)?$', staff.url), staff.url
            detail = staff.url.split('?')[0]
            assert name in staff.locator('main').inner_text()
            if route == 'staff-reports':
                assert team.goto(detail).status == 200, 'A report participant must have scoped read access'
            else:
                assert team.goto(detail).status in [400, 403, 404], 'Leave scope must exclude another staff member'
            navigate_click(staff, 'form[action$="/actions/submit"] button')
            assert staff.locator('form[action$="/actions/submit"]').count() == 0
            assert staff.locator('form[action$="/actions/approve"]').count() == 0
            admin.goto(detail + '?lang=' + lang)
            admin.locator('[name=comment]').fill('Synthetic independent review ' + suffix)
            navigate_click(admin, 'form[action$="/actions/approve"] button:not([formaction])')
            assert admin.locator('form[action$="/actions/approve"]').count() == 0
            staff.goto(detail)
            assert 'Synthetic independent review ' + suffix in staff.locator('main').inner_text()
            assert staff.locator('a[href$="/edit"]').count() == 0
            response = staff.request.get(args.base_url + '/' + route + '/print.pdf?search=' + suffix)
            assert response.status == 200 and response.body().startswith(b'%PDF')
            (output / (route + '-' + lang + '.pdf')).write_bytes(response.body())
            results.append({'flow': route + ' create/submit/independent approval/scoped export', 'lang': lang})
    browser.close()

(output / 'results.json').write_text(json.dumps({'checks': results, 'javascript_errors': errors}, indent=2))
print(json.dumps({'checks': len(results), 'javascript_errors': errors}, indent=2))
assert not errors
