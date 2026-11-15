#!/usr/bin/env python3
"""Read-only EN/SW client report and payment-summary browser acceptance.

Use an isolated OperationalExportIntegrationTests fixture and FCMS_UI_EMAIL/PASSWORD.
Requires Playwright, Chromium and PyMuPDF. Does not post financial or client records.
"""
import time,urllib.request,json,os,re,argparse
from pathlib import Path
from urllib.parse import urljoin,urlparse
from playwright.sync_api import sync_playwright
import fitz
parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:8090')
parser.add_argument('--output',default='.verification/client-revenue-native')
args=parser.parse_args()
base=args.base_url.rstrip('/')
assert urlparse(base).hostname in ['127.0.0.1','localhost']
metadata=Path('.verification/category-runtime.json')
artifact_hash=json.loads(metadata.read_text())['sha256'] if metadata.exists() else None
for _ in range(30):
 try:
  with urllib.request.urlopen(base+'/actuator/health',timeout=2) as r:
   if r.status==200:break
 except OSError:time.sleep(1)
else:raise AssertionError('Fixture readiness timeout')
root=Path(args.output);root.mkdir(parents=True,exist_ok=True);checks=[];errors=[]
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);page=b.new_page(viewport={'width':1440,'height':960});page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto(base+'/login');page.locator('[name=email]').fill(os.environ['FCMS_UI_EMAIL']);page.locator('[name=password]').fill(os.environ['FCMS_UI_PASSWORD']);page.locator('button[type=submit]').click();page.wait_for_url('**/dashboard')
 for lang in ['en','sw']:
  for width in [320,375,768,1024,1440,1920]:
   page.set_viewport_size({'width':width,'height':960})
   for status in ['', '1', '2']:
    r=page.goto(base+'/clients/saccos?financialYearId=1&paymentStatusId='+status+'&lang='+lang);assert r.status==200;page.wait_for_load_state('networkidle');assert page.evaluate('document.documentElement.scrollWidth')<=width
    assert page.locator('[data-table-export=payment]').is_visible()==bool(status)
    assert page.locator('[name=paymentStatusId] option[value="1"]').inner_text().strip()==('Imelipwa' if lang=='sw' else 'Paid')
    checks.append({'flow':'payment export responsive state','lang':lang,'width':width,'status':status})
   if width in [375,1440]:
    page.goto(base+'/clients/saccos?financialYearId=1&paymentStatusId=1&lang='+lang);page.wait_for_load_state('networkidle');page.screenshot(path=str(root/('payment-export-'+lang+'-'+str(width)+'.png')),full_page=True)
  page.set_viewport_size({'width':1440,'height':960});page.goto(base+'/clients/saccos?financialYearId=1&lang='+lang);page.wait_for_load_state('networkidle')
  for status in ['1','2','']:
   page.locator('[name=paymentStatusId]').select_option(status);page.locator('form[method=get] button[type=submit]').click();page.wait_for_function('(expected)=>new URL(location.href).searchParams.get("paymentStatusId")===expected',arg=status)
   action=page.locator('[data-table-export=payment]');assert action.is_visible()==bool(status)
   if status:
    href=action.get_attribute('href');assert ('/paid/' if status=='1' else '/unpaid/') in href;assert 'financialYearId=1' in href
    r=page.request.get(urljoin(page.url,href));assert r.status==200;assert 'no-store' in r.headers['cache-control']
    with fitz.open(stream=r.body(),filetype='pdf') as doc:
     content=''.join(p.get_text() for p in doc);assert ('Imechapishwa' if lang=='sw' else 'Printed') in content;assert re.search(r'\d{2} \w+ \d{4} \d{2}:\d{2}:\d{2}',content)
     if status=='1':assert '90.09' in content and '2026' in content
     doc[0].get_pixmap(matrix=fitz.Matrix(1.25,1.25)).save(str(root/(('paid' if status=='1' else 'unpaid')+'-'+lang+'.png')))
    (root/(('paid' if status=='1' else 'unpaid')+'-'+lang+'.pdf')).write_bytes(r.body())
   checks.append({'flow':'in-place status/summary target/printed native PDF','lang':lang,'status':status})
  for category in ['saccos','amcos','unions','microfinance','others']:
   for report_type in ['full','training_fee']:
    r=page.goto(base+'/clients/'+category+'?view=revenue&financialYearId=1&reportType='+report_type+'&lang='+lang);assert r.status==200;page.wait_for_load_state('networkidle')
    href=page.locator('[data-table-export=pdf]').get_attribute('href');assert 'financialYearId=1' in href;assert 'reportType='+report_type in href
    pdf=page.request.get(urljoin(page.url,href));assert pdf.status==200;assert 'no-store' in pdf.headers['cache-control']
    with fitz.open(stream=pdf.body(),filetype='pdf') as doc:
     text=''.join(p.get_text() for p in doc);assert '2026' in text;assert 'Synthetic Export' in text;assert abs(doc[0].rect.width-841.89)<.01
     footer=''.join(p.get_text(clip=fitz.Rect(0,550,842,596)).strip() for p in doc);assert not footer,footer
     doc[0].get_pixmap(matrix=fitz.Matrix(1.25,1.25)).save(str(root/(category+'-'+report_type+'-'+lang+'.png')))
    (root/(category+'-'+report_type+'-'+lang+'.pdf')).write_bytes(pdf.body());checks.append({'flow':'native revenue/fee export link','category':category,'report_type':report_type,'lang':lang})
 b.close()
assert not errors
(root/'results.json').write_text(json.dumps({'sha256':artifact_hash,'checks':checks,'javascript_errors':errors},indent=2));print(json.dumps({'checks':len(checks),'javascript_errors':errors}))
