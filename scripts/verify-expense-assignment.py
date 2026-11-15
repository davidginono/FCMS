#!/usr/bin/env python3
"""Native on-behalf expense lifecycle against the isolated assignment fixture."""
import argparse,json,os,re,time,urllib.request,uuid
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright,expect
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:8093');args=p.parse_args();base=args.base_url.rstrip('/');assert urlparse(base).hostname in ['127.0.0.1','localhost']
root=Path('.verification/expense-assignment-native');root.mkdir(parents=True,exist_ok=True);meta=json.loads(Path('.verification/expense-assignment-runtime.json').read_text());fixture=json.loads(Path('.verification/expense-assignment-ui-fixture.json').read_text());checks=[];errors=[]
for _ in range(45):
 try:
  with urllib.request.urlopen(base+'/actuator/health',timeout=2) as response:
   if response.status==200:break
 except OSError:time.sleep(1)
else:raise AssertionError('Fixture readiness timeout')
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 def login(user,lang='en'):
  page=browser.new_page(viewport={'width':1440,'height':1000});page.on('pageerror',lambda e:errors.append(str(e)));page.goto(base+'/login?lang='+lang);page.locator('[name=email]').fill(user+'@test.invalid');page.locator('[name=password]').fill(os.environ['FCMS_PARITY_PASSWORD']);page.locator('button[type=submit]').click();page.wait_for_url('**/dashboard');return page
 def widths(page,flow,lang):
  for width in [320,375,768,1024,1440,1920]:
   page.set_viewport_size({'width':width,'height':1000});page.wait_for_timeout(100);assert page.evaluate('document.documentElement.scrollWidth')<=width
   if width in [375,1440]:page.screenshot(path=str(root/f'{flow}-{lang}-{width}.png'),full_page=True)
   checks.append({'flow':flow,'lang':lang,'width':width})
  page.set_viewport_size({'width':1440,'height':1000})
 def command(page,action,reason='Synthetic independent review',reference='SYN-PAID'):
  form=page.locator('form[action$="/actions/'+action+'"]');expect(form).to_have_count(1)
  details=form.locator('xpath=ancestor::details[1]')
  if details.count() and not details.get_attribute('open'):details.locator('summary').click()
  if form.locator('[name=comment]').count():form.locator('[name=comment]').fill(reason)
  if form.locator('[name=reference]').count():form.locator('[name=reference]').fill(reference)
  with page.expect_navigation():form.locator('button[type=submit]').first.click()
  page.wait_for_load_state('networkidle');assert page.locator('[data-message-type=success]').count()==1
 for lang in ['en','sw']:
  maker=login('assign-maker',lang);preparer=login('assign-preparer',lang);reviewer=login('assign-reviewer',lang);owner=login('assign-owner',lang)
  maker.goto(base+'/finance/expenses/new?lang='+lang);maker.wait_for_load_state('networkidle');name='Synthetic browser assignment '+lang+' '+uuid.uuid4().hex[:8]
  maker.locator('[name=ownerId]').select_option(str(fixture['assign-owner']));maker.locator('[name=financialYearId]').select_option(str(fixture['year2025']));maker.locator('[name=date]').fill('2026-11-01');maker.locator('[name=category]').fill(name);maker.locator('[name=description]').fill('Synthetic on-behalf lifecycle');maker.locator('[name=payee]').fill('Synthetic payee');maker.locator('[name=amount]').fill('123.45');key=maker.locator('[name=requestKey]').input_value();widths(maker,'create-filled',lang)
  maker.locator('[name=ownerId]').evaluate('(element,id)=>element.add(new Option("Synthetic assign-inactive",String(id)))',fixture['assign-inactive']);maker.locator('[name=ownerId]').select_option(str(fixture['assign-inactive']))
  with maker.expect_navigation():maker.locator('form.fcms-form button[type=submit]').click()
  maker.wait_for_load_state('networkidle');assert maker.locator('[name=ownerId]').input_value()==str(fixture['assign-inactive']);assert maker.locator('[name=financialYearId]').input_value()==str(fixture['year2025']);assert maker.locator('[name=requestKey]').input_value()==key;assert maker.locator('[name=amount]').input_value()=='123.45';expect(maker.locator('.fcms-error')).to_contain_text('Chagua' if lang=='sw' else 'active');widths(maker,'invalid-owner-recovery',lang)
  maker.locator('[name=ownerId]').select_option(str(fixture['assign-owner']));maker.locator('form.fcms-form button[type=submit]').click();maker.wait_for_url(re.compile(r'.*/finance/expenses/\d+\?saved$'));path=urlparse(maker.url).path;assert 'assign-owner' in maker.locator('main').inner_text();assert 'assign-maker' in maker.locator('main').inner_text();assert '2025' in maker.locator('main').inner_text();expect(maker.locator('[data-message-type=success]')).to_be_visible();widths(maker,'created-details',lang)
  preparer.goto(base+path+'/edit?lang='+lang);preparer.wait_for_load_state('networkidle');assert preparer.locator('[name=ownerId]').input_value()==str(fixture['assign-owner']);assert preparer.locator('[name=financialYearId]').input_value()==str(fixture['year2025']);preparer.locator('[name=amount]').fill('124.56');widths(preparer,'preparer-edit',lang);preparer.locator('form.fcms-form button[type=submit]').click();preparer.wait_for_url(re.compile(r'.*/finance/expenses/\d+\?saved$'))
  submitter=maker if lang=='en' else owner;submitter.goto(base+path+'?lang='+lang);command(submitter,'submit')
  for page in [maker,preparer,owner]:
   page.goto(base+path+'?lang='+lang);assert page.locator('form[action$="/approve"],form[action$="/reject"]').count()==0
  reviewer.goto(base+path+'?lang='+lang);command(reviewer,'approve');command(reviewer,'pay');assert '124.56' in reviewer.locator('main').inner_text();widths(reviewer,'paid-details',lang)
  for page in [maker,preparer,owner]:page.goto(base+path+'?lang='+lang);assert page.locator('form[action$="/reverse"]').count()==0
  command(reviewer,'reverse','Synthetic independent correction');expect(reviewer.locator('table tbody tr')).to_have_count(4);widths(reviewer,'reversed-history',lang)
  for page in [maker,preparer,owner,reviewer]:page.close()
  checks.append({'flow':'creator owner preparer reviewer native submit approval payment reversal and history','lang':lang})
 page=login('assign-limited');page.goto(base+'/finance/expenses/new');page.locator('[name=ownerId]').select_option(str(fixture['assign-other']));page.locator('[name=category]').fill('Synthetic limited creator '+uuid.uuid4().hex[:8]);page.locator('[name=description]').fill('Synthetic scoped assignment');page.locator('[name=payee]').fill('Synthetic');page.locator('[name=amount]').fill('25.01');page.locator('form.fcms-form button[type=submit]').click();page.wait_for_url(re.compile(r'.*/finance/expenses/\d+\?saved$'));assert 'assign-other' in page.locator('main').inner_text();assert page.locator('form[action$="/approve"]').count()==0;checks.append({'flow':'custom assignment claim reads own assigned result without global approval scope'});page.close()
 page=login('assign-owner');page.goto(base+'/finance/expenses/'+str(fixture['legacyUnknown'])+'/edit');expect(page.locator('[name=financialYearId]')).to_have_value('');assert 'UNMAPPED' in page.locator('[name=financialYearId] option:checked').inner_text();page.locator('[name=description]').fill('Synthetic retained unknown year');page.locator('form.fcms-form button[type=submit]').click();page.wait_for_url(re.compile(r'.*/finance/expenses/\d+\?saved$'));assert 'UNMAPPED' in page.locator('main').inner_text();assert ('Unavailable' in page.locator('main').inner_text()) or ('Haipatikani' in page.locator('main').inner_text());checks.append({'flow':'unknown legacy year and creator remain honest through native edit'});page.close()
 page=login('assign-owner');page.goto(base+'/finance/expenses?year=2025&category=Travel%26Training&lang=en');page.get_by_role('link',name='Kiswahili',exact=True).click();expect(page.locator('[name=year]')).to_have_value('2025');expect(page.locator('[name=category]')).to_have_value('Travel&Training');href=page.locator('[data-table-export=pdf]').get_attribute('href');assert 'year=2025' in href and 'category=Travel%26Training' in href;checks.append({'flow':'language switch preserves expense year, literal category and native PDF context'});page.close();browser.close()
assert not errors,errors
assert json.loads(Path('.verification/expense-assignment-runtime.json').read_text())['sha256']==meta['sha256']
(root/'results.json').write_text(json.dumps({'sha256':meta['sha256'],'checks':checks,'javascript_errors':errors},indent=2));print(json.dumps({'checks':len(checks),'javascript_errors':errors}))
