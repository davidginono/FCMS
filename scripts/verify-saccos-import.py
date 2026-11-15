#!/usr/bin/env python3
"""Native SACCOS CSV workflow acceptance against an isolated loopback fixture."""
import argparse,json,os,time,uuid,urllib.request,re
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright,expect
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:8090');p.add_argument('--output',default='.verification/saccos-import-native');args=p.parse_args()
base=args.base_url.rstrip('/');assert urlparse(base).hostname in ['127.0.0.1','localhost']
root=Path(args.output);root.mkdir(parents=True,exist_ok=True);checks=[];errors=[]
metadata=json.loads(Path('.verification/category-runtime.json').read_text());sha=metadata['sha256']
for _ in range(45):
 try:
  with urllib.request.urlopen(base+'/actuator/health',timeout=2) as response:
   if response.status==200:break
 except OSError:time.sleep(1)
else:raise AssertionError('Fixture readiness timeout')
header='Saccos_Name,Entry_Date,Region,financial_year,payment_status,Amount,Annual_fee,Training_fee\r\n'
def content(name,paid='100.11',annual='200.25'):
 return f'{name},2026-11-01,Mwanza,2026,PAID,{paid},{annual},25.01\r\n'
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 page=browser.new_page(viewport={'width':1440,'height':1000});page.on('pageerror',lambda error:errors.append(str(error)))
 page.goto(base+'/login');page.locator('[name=email]').fill(os.environ['FCMS_UI_EMAIL']);page.locator('[name=password]').fill(os.environ['FCMS_UI_PASSWORD']);page.locator('button[type=submit]').click();page.wait_for_url('**/dashboard')
 for lang in ['en','sw']:
  page.goto(base+'/clients/saccos/import?lang='+lang);page.wait_for_load_state('networkidle')
  assert page.locator('h1').inner_text().strip()==('Import SACCOS' if lang=='en' else 'Ingiza SACCOS')
  page.locator('details summary').first.click()
  for width in [320,375,768,1024,1440,1920]:
   page.set_viewport_size({'width':width,'height':1000});page.wait_for_timeout(100);assert page.evaluate('document.documentElement.scrollWidth')<=width
   if width in [375,1440]:page.screenshot(path=str(root/f'upload-{lang}-{width}.png'),full_page=True)
   checks.append({'flow':'upload responsive schema and file controls','lang':lang,'width':width})
  page.set_viewport_size({'width':1440,'height':1000});page.locator('details summary').first.click()
  file=page.locator('#importFile');key=page.locator('[name=requestKey]').input_value();file.evaluate('(element)=>window.originalImportFile=element')
  file.set_input_files({'name':'invalid.csv','mimeType':'text/csv','buffer':(header+content('Synthetic Invalid','-1')).encode()})
  with page.expect_response(lambda r:r.request.method=='POST' and r.url.endswith('/clients/saccos/import')) as response:
   page.locator('form[data-csv-import] button[type=submit]').click()
  assert response.value.status==422;expect(file).to_be_enabled();expect(file).to_have_attribute('aria-invalid','true')
  assert file.evaluate('(element)=>element===window.originalImportFile && element.files[0].name==="invalid.csv"');assert page.locator('[name=requestKey]').input_value()==key
  expect(file).to_be_focused();assert page.locator('[data-import-file-error]').inner_text().strip()
  checks.append({'flow':'invalid upload preserves file, key, node and focus','lang':lang})
  page.locator('form[data-csv-import] .fcms-form-actions a').click();expect(page.locator('#fcms-discard-modal')).to_be_visible();page.locator('#fcms-discard-modal .app-modal-actions [data-fcms-keep]').click();expect(page.locator('#fcms-discard-modal')).to_be_hidden();assert file.evaluate('(element)=>element.files[0].name==="invalid.csv"')
  with page.expect_download() as download:page.locator('a[download]').click()
  assert download.value.suggested_filename.endswith('.csv');expect(page.locator('#fcms-discard-modal')).to_be_hidden();assert file.evaluate('(element)=>element.files.length===1')
  checks.append({'flow':'dirty cancel recovery and template without losing selected file','lang':lang})
  identifier='Synthetic Browser '+lang+' '+uuid.uuid4().hex[:8]
  unsafe='<svg onload="window.csvXss=true">'
  # RFC4180 quoting preserves a harmless literal rather than inserting markup.
  quoted='"'+(identifier+' '+unsafe).replace('"','""')+'"'
  data=header+content(quoted)+content(identifier+' Two','50.02','125.05')
  file.set_input_files({'name':'synthetic-clients.csv','mimeType':'text/csv','buffer':data.encode()})
  page.locator('form[data-csv-import] button[type=submit]').click();page.wait_for_url(re.compile(r'.*/clients/saccos/import/[0-9a-f-]{36}$'));page.wait_for_load_state('networkidle');preview=page.url
  assert page.locator('table tbody tr').count()==2;assert page.locator('table tbody a.resource-table-id-link').count()==0
  facts=page.locator('section').first.inner_text();assert '325.30' in facts and '150.13' in facts
  assert page.evaluate('window.csvXss') is None;assert page.locator('table svg').count()==0;assert unsafe in page.locator('table').inner_text()
  page.locator('table details summary').first.click();assert ('Not provided' if lang=='en' else 'Haijatolewa') in page.locator('table details').first.inner_text();assert '25.01' in page.locator('table details').first.inner_text()
  for width in [320,375,768,1024,1440,1920]:
   page.set_viewport_size({'width':width,'height':1000});page.wait_for_timeout(100);assert page.evaluate('document.documentElement.scrollWidth')<=width
   if width in [375,1440]:page.screenshot(path=str(root/f'preview-{lang}-{width}.png'),full_page=True)
   checks.append({'flow':'preview responsive table and expanded fees','lang':lang,'width':width})
  page.set_viewport_size({'width':1440,'height':1000});page.locator('form[data-financial-form] button[type=submit]').last.click();page.wait_for_url(preview+'?saved');expect(page.locator('form[data-financial-form]')).to_have_count(0);expect(page.locator('table tbody a.resource-table-id-link')).to_have_count(2)
  assert ('Imported' if lang=='en' else 'Imeingizwa') in page.locator('section').first.inner_text()
  target=page.locator('table tbody a.resource-table-id-link').first.get_attribute('href');page.locator('table tbody a.resource-table-id-link').first.click();page.wait_for_url('**'+target);assert identifier in page.locator('main').inner_text();assert '100.11' in page.locator('main').inner_text();assert page.evaluate('window.csvXss') is None
  checks.append({'flow':'native atomic commit and authorized destination values','lang':lang})
  page.goto(base+'/clients/saccos/import?lang='+lang);page.locator('#importFile').set_input_files({'name':'discard.csv','mimeType':'text/csv','buffer':(header+content(identifier+' Discard')).encode()});page.locator('form[data-csv-import] button[type=submit]').click();page.wait_for_url(re.compile(r'.*/clients/saccos/import/[0-9a-f-]{36}$'));draft=page.url
  page.locator('button[formaction$="/discard"]').click();page.wait_for_url(draft);expect(page.locator('form[data-financial-form]')).to_have_count(0);assert page.locator('table tbody a').count()==0
  assert ('Discarded' if lang=='en' else 'Imefutwa') in page.locator('section').first.inner_text()
  checks.append({'flow':'native discard retains totals without creating clients','lang':lang})
 # Upload and preview paging uses whole-batch facts.
 page.goto(base+'/clients/saccos/import?lang=en');data=header+''.join(content('Synthetic Paged '+str(n)) for n in range(26));page.locator('#importFile').set_input_files({'name':'paged.csv','mimeType':'text/csv','buffer':data.encode()});page.locator('form[data-csv-import] button[type=submit]').click();page.wait_for_url(re.compile(r'.*/clients/saccos/import/[0-9a-f-]{36}$'));draft=page.url
 page.goto(draft+'?size=10&page=2');page.wait_for_load_state('networkidle');assert page.locator('table tbody tr').count()==6;assert '2,602.86' in page.locator('section').first.inner_text();assert '5,206.50' in page.locator('section').first.inner_text();assert page.locator('form[data-financial-form] button[type=submit]').last.inner_text().strip()=='Import 26 clients'
 page.locator('button[formaction$="/discard"]').click();page.wait_for_url(draft);checks.append({'flow':'native page three retains totals and entire 26-row command'})
 browser.close()
assert not errors,errors
assert json.loads(Path('.verification/category-runtime.json').read_text())['sha256']==sha
(root/'results.json').write_text(json.dumps({'sha256':sha,'checks':checks,'javascript_errors':errors},indent=2));print(json.dumps({'checks':len(checks),'javascript_errors':errors}))
