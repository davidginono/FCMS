#!/usr/bin/env python3
"""Native bilingual cloud billing controls and PDF typography against a loopback fixture."""
import argparse,json,os,re,time,urllib.request
from pathlib import Path
from urllib.parse import urljoin,urlparse
from playwright.sync_api import sync_playwright
import fitz
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:8090');p.add_argument('--output',default='.verification/final-billing-native');args=p.parse_args();base=args.base_url.rstrip('/');assert urlparse(base).hostname in ['127.0.0.1','localhost']
root=Path(args.output);root.mkdir(parents=True,exist_ok=True);checks=[];errors=[];sha=json.loads(Path('.verification/category-runtime.json').read_text())['sha256']
for _ in range(40):
 try:
  with urllib.request.urlopen(base+'/actuator/health',timeout=2) as r:
   if r.status==200:break
 except OSError:time.sleep(1)
else:raise AssertionError('Fixture readiness timeout')
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);page=browser.new_page(viewport={'width':1440,'height':960});page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto(base+'/login');page.locator('[name=email]').fill(os.environ['FCMS_UI_EMAIL']);page.locator('[name=password]').fill(os.environ['FCMS_UI_PASSWORD']);page.locator('button[type=submit]').click();page.wait_for_url('**/dashboard')
 for lang in ['en','sw']:
  for state in ['','PAID','UNPAID','EMPTY']:
   path='/finance/cloud-backup/billing?year=2026&lang='+lang+('&search=absent-synthetic-billing' if state=='EMPTY' else '&status='+state)
   assert page.goto(base+path).status==200;page.wait_for_load_state('networkidle');href=page.locator('[data-table-export=pdf]').get_attribute('href');assert 'year=2026' in href
   response=page.request.get(urljoin(page.url,href));assert response.status==200 and 'no-store' in response.headers['cache-control'];assert 'cloud-billing.pdf' in response.headers['content-disposition']
   with fitz.open(stream=response.body(),filetype='pdf') as doc:
    text=''.join(p.get_text() for p in doc);assert '2026' in text;assert ('Imechapishwa' if lang=='sw' else 'Printed') in text;assert re.search(r'\d{2} [A-Za-z]{3} \d{4} \d{2}:\d{2}:\d{2}',text)
    spans=[s for p in doc for block in p.get_text('dict')['blocks'] for line in block.get('lines',[]) for s in line['spans']]
    sizes={round(s['size'],2) for s in spans};assert {16.0,11.0,9.0}.issubset(sizes),sizes
    if state in ['','PAID']:assert '12.34' in text and '1.01' in text and 'Synthetic Export Client' in text;assert 10.0 in sizes
    if state=='EMPTY':assert 'Synthetic Export Client' not in text;assert '0.00' in text
    if state in ['PAID','EMPTY']:doc[0].get_pixmap(matrix=fitz.Matrix(1.25,1.25)).save(str(root/f'billing-{state}-{lang}.png'))
   (root/f'billing-{state or "ALL"}-{lang}.pdf').write_bytes(response.body());checks.append({'flow':'native billing controls, exact values, typography and printed footer','lang':lang,'state':state or 'ALL'})
 browser.close()
assert not errors,errors
assert json.loads(Path('.verification/category-runtime.json').read_text())['sha256']==sha
(root/'results.json').write_text(json.dumps({'sha256':sha,'checks':checks,'javascript_errors':errors},indent=2));print(json.dumps({'checks':len(checks),'javascript_errors':errors}))
