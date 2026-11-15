#!/usr/bin/env python3
"""Read-only source parity acceptance against DashboardParityIntegrationTests fixtures."""
import argparse,json,os,time,urllib.request
from pathlib import Path
from urllib.parse import urljoin,urlparse,parse_qs
from playwright.sync_api import sync_playwright,expect
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:8092');args=p.parse_args();base=args.base_url.rstrip('/');assert urlparse(base).hostname in ['127.0.0.1','localhost']
root=Path('.verification/dashboard-parity-native');root.mkdir(parents=True,exist_ok=True);checks=[];errors=[];meta=json.loads(Path('.verification/dashboard-parity-runtime.json').read_text())
for _ in range(45):
 try:
  with urllib.request.urlopen(base+'/actuator/health',timeout=2) as response:
   if response.status==200:break
 except OSError:time.sleep(1)
else:raise AssertionError('Fixture readiness timeout')
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 def login(email,password):
  page=browser.new_page(viewport={'width':1440,'height':1000});page.on('pageerror',lambda error:errors.append(str(error)))
  page.goto(base+'/login');page.locator('[name=email]').fill(email);page.locator('[name=password]').fill(password);page.locator('button[type=submit]').click();page.wait_for_url('**/dashboard');page.wait_for_load_state('networkidle');return page
 page=login(os.environ['FCMS_UI_EMAIL'],os.environ['FCMS_UI_PASSWORD'])
 for lang in ['en','sw']:
  for year in ['2026','2025']:
   page.goto(base+'/dashboard?lang='+lang);page.wait_for_load_state('networkidle')
   if year=='2025':page.locator('[name=financialYearId]').select_option(label=year);page.locator('.dashboard-year-form button[type=submit]').click();page.wait_for_url('**/dashboard?financialYearId=*');page.wait_for_load_state('networkidle')
   selected=page.locator('[name=financialYearId]').input_value();assert page.locator('[name=financialYearId] option:checked').inner_text().strip()==year
   assert page.locator('[data-dashboard-client]').count()==5;assert page.locator('[data-dashboard-cloud]').count()==1
   for expected,key in [('70.07' if year=='2026' else '999.99','income'),('150.23' if year=='2026' else '0.00','bulk'),('699.99','petty'),('100.10' if year=='2026' else '900.09','expenses')]:assert page.locator('[data-dashboard-money='+key+'] .dashboard-value').inner_text().strip()==expected
   assert page.locator('[data-dashboard-client=saccos] .dashboard-value').inner_text().strip()==('3' if year=='2026' else '1')
   assert ('140.11' if year=='2026' else '-11.00') in page.locator('[data-dashboard-client=saccos]').inner_text()
   assert ('75.03' if year=='2026' else '0.00') in page.locator('[data-dashboard-client=saccos]').inner_text()
   for width in [320,375,768,1024,1440,1920]:
    page.set_viewport_size({'width':width,'height':1000});page.wait_for_timeout(100);assert page.evaluate('document.documentElement.scrollWidth')<=width
    if width in [375,1440]:page.screenshot(path=str(root/f'dashboard-{lang}-{year}-{width}.png'),full_page=True)
    checks.append({'flow':'actual scoped dashboard values, year and responsive widgets','lang':lang,'year':year,'width':width})
   for selector in ['[data-dashboard-client] a[href]','[data-dashboard-cloud] a[href]','[data-dashboard-money] a[href]','[data-dashboard-work] a[href]']:
    hrefs=list(dict.fromkeys(page.locator(selector).evaluate_all('(nodes)=>nodes.map(n=>n.getAttribute("href"))')))
    for href in hrefs:
     uri=urlparse(urljoin(base,href));assert uri.hostname=='127.0.0.1';query=parse_qs(uri.query)
     if uri.path.startswith('/clients/'):assert query['financialYearId']==[selected]
     elif uri.path.startswith('/finance/cloud-backup/billing'):assert query['year']==[year]
     elif uri.path.startswith('/finance/expenses'):assert query['year']==[year]
     elif uri.path in ['/finance/income','/finance/bulk-sms']:assert query['from']==[year+'-01-01'] and query['to']==[year+'-12-31']
     elif uri.path=='/reports':assert query['year']==[year]
     response=page.request.get(uri.geturl());assert response.status==200,(uri.geturl(),response.status)
     checks.append({'flow':'native authorized metric destination retains query scope','lang':lang,'year':year,'path':uri.path,'query':uri.query})
   page.set_viewport_size({'width':1440,'height':1000})
  page.goto(base+'/dashboard?lang='+lang);page.wait_for_load_state('networkidle');fee=page.locator('[data-dashboard-client=saccos] a[href*="reportType=training_fee"]').first;fee.click();page.wait_for_url('**/clients/saccos?**');expect(page.locator('[name=reportType]')).to_have_value('training_fee');expect(page.locator('[name=financialYearId]')).to_have_value('1');checks.append({'flow':'native fee drill opens selected report in the same year','lang':lang})
 page.close()
 for user,portfolios,money,work in [('dashboard-owner',5,1,3),('dashboard-finance',0,3,0),('dashboard-report',0,1,0),('dashboard-client',5,0,0),('dashboard-task',0,0,1)]:
  page=login(user+'@test.invalid',os.environ['FCMS_PARITY_PASSWORD']);assert page.locator('[data-dashboard-client]').count()==portfolios;assert page.locator('[data-dashboard-money]').count()==money;assert page.locator('[data-dashboard-work]').count()==work;assert page.locator('[data-dashboard-cloud]').count()==0
  if user=='dashboard-owner':assert page.locator('[data-dashboard-money=expenses] .dashboard-value').inner_text().strip()=='40.04'
  checks.append({'flow':'role-independent claims and owner scope','user':user});page.close()
 browser.close()
assert not errors,errors
assert json.loads(Path('.verification/dashboard-parity-runtime.json').read_text())['sha256']==meta['sha256']
(root/'results.json').write_text(json.dumps({'sha256':meta['sha256'],'checks':checks,'javascript_errors':errors},indent=2));print(json.dumps({'checks':len(checks),'javascript_errors':errors}))
