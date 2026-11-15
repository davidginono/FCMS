#!/usr/bin/env python3
"""Native staff-report assignment/group review and task date-basis acceptance on synthetic data."""
import json,os,re,time,urllib.request,uuid
from pathlib import Path
from urllib.parse import urlparse,urlencode,parse_qs
import fitz
from playwright.sync_api import sync_playwright,expect
base='http://127.0.0.1:8094'
root=Path('.verification/report-assignment-native');root.mkdir(parents=True,exist_ok=True)
meta=json.loads(Path('.verification/report-assignment-runtime.json').read_text())
fixture=json.loads(Path('.verification/report-assignment-ui-fixture.json').read_text());checks=[];errors=[]
for _ in range(45):
 try:
  if urllib.request.urlopen(base+'/actuator/health',timeout=2).status==200:break
 except OSError:time.sleep(1)
else:raise AssertionError('Runtime readiness timeout')
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 def login(name,lang):
  page=browser.new_page(viewport={'width':1440,'height':1000});page.on('pageerror',lambda e:errors.append(str(e)))
  page.goto(base+'/login?lang='+lang);page.locator('[name=email]').fill(name+'@test.invalid');page.locator('[name=password]').fill(os.environ['FCMS_PARITY_PASSWORD']);page.locator('button[type=submit]').click();page.wait_for_url('**/dashboard');return page
 def widths(page,flow,lang):
  for width in [320,375,768,1024,1440,1920]:
   page.set_viewport_size({'width':width,'height':1000});page.wait_for_timeout(80);assert page.evaluate('document.documentElement.scrollWidth')<=width,(flow,width)
   if width in [375,1440]:page.screenshot(path=str(root/f'{flow}-{lang}-{width}.png'),full_page=True)
   checks.append({'flow':flow,'lang':lang,'width':width})
  page.set_viewport_size({'width':1440,'height':1000})
 def lookup(page,name,text):
  select=page.locator(name);wrap=select.locator('xpath=ancestor::div[contains(@class,"neo-select")][1]')
  button=wrap.locator('.neo-select-button');button.click();menu=page.locator('#'+button.get_attribute('aria-controls')).locator('xpath=..');menu.locator('input[type=search]').fill(text)
  menu.locator('.neo-select-option').filter(has_text=text).click();expect(select).not_to_have_value('')
 def save(page):
  page.locator('form.fcms-form button[type=submit]').click();page.wait_for_url(re.compile(r'.*/staff-reports/\d+\?saved$'));expect(page.locator('[data-message-type=success]')).to_be_visible()
 def command(page,action):
  if action in ['approve','reject']:
   page.locator('form[action$="/actions/approve"] [name=comment]').fill('Synthetic independent native review')
   button=page.locator('form[action$="/actions/approve"] button'+('[formaction]' if action=='reject' else ':not([formaction])'))
  else:button=page.locator('form[action$="/actions/'+action+'"] button')
  with page.expect_navigation():button.click()
  expect(page.locator('[data-message-type=success]')).to_be_visible()
 def pdf(page,url):
  r=page.request.get(url);assert r.status==200 and r.headers['content-type'].startswith('application/pdf')
  document=fitz.open(stream=r.body(),filetype='pdf');return '\n'.join(p.get_text() for p in document)
 for lang in ['en','sw']:
  maker=login('report-maker',lang);preparer=login('report-preparer',lang);reviewer=login('report-reviewer',lang);owner=login('report-owner',lang);member=login('report-member',lang)
  maker.goto(base+'/staff-reports/new?lang='+lang);maker.wait_for_load_state('networkidle');name='Native group '+lang+' '+uuid.uuid4().hex[:8]
  maker.locator('[name=subject]').fill(name);maker.locator('[name=description]').fill('Synthetic selected-staff group activity')
  lookup(maker,'[name=ownerId]','report-owner');maker.locator('[name=workType]').select_option('GROUP')
  lookup(maker,'#participantChoice','report-member');maker.locator('[data-team-add]').click();expect(maker.locator('[name=participants]:checked')).to_have_count(1)
  assert maker.locator('[data-team-picker]').get_attribute('data-team-owner')==str(fixture['report-owner'])
  maker.locator('[name=startAt]').fill('2026-11-01T09:00');maker.locator('[name=endAt]').fill('2026-11-01T17:00');maker.locator('[name=outcome]').select_option('INCOMPLETE');maker.locator('[name=explanation]').fill('Synthetic retained result');key=maker.locator('[name=requestKey]').input_value();widths(maker,'group-create-filled',lang)
  maker.locator('[name=endAt]').fill('2026-10-01T17:00')
  with maker.expect_navigation():maker.locator('form.fcms-form button[type=submit]').click()
  expect(maker.locator('[name=ownerId]')).to_have_value(str(fixture['report-owner']));expect(maker.locator('[name=participants]:checked')).to_have_count(1);assert maker.locator('[name=requestKey]').input_value()==key;assert maker.locator('[name=explanation]').input_value()=='Synthetic retained result';widths(maker,'group-error-recovery',lang)
  maker.locator('[name=endAt]').fill('2026-11-01T17:00');save(maker);path=urlparse(maker.url).path;assert 'report-owner' in maker.locator('main').inner_text();assert 'report-member' in maker.locator('main').inner_text();assert 'report-maker' in maker.locator('main').inner_text();widths(maker,'group-created-details',lang)
  preparer.goto(base+path+'/edit?lang='+lang);preparer.wait_for_load_state('networkidle');expect(preparer.locator('[name=ownerId]')).to_have_value(str(fixture['report-owner']));preparer.locator('[name=description]').fill('Synthetic independently prepared group');save(preparer)
  maker.goto(base+path+'?lang='+lang);command(maker,'submit')
  for page in [maker,preparer,owner,member]:
   page.goto(base+path+'?lang='+lang);assert page.locator('form[action$="/actions/approve"]').count()==0
  reviewer.goto(base+path+'?lang='+lang);widths(reviewer,'independent-group-review',lang);command(reviewer,'reject')
  owner.goto(base+path+'/edit?lang='+lang);expect(owner.locator('[name=ownerId]')).to_have_value(str(fixture['report-owner']));owner.locator('[name=outcome]').select_option('COMPLETED');save(owner);command(owner,'submit')
  reviewer.goto(base+path+'?lang='+lang);command(reviewer,'approve');widths(reviewer,'approved-group-history',lang)
  filters={'ownerId':fixture['report-member'],'search':name,'from':'2026-11-01','to':'2026-11-01','lang':lang}
  reviewer.goto(base+'/staff-reports?'+urlencode(filters));reviewer.wait_for_load_state('networkidle');expect(reviewer.locator('[name=ownerId]')).to_have_value(str(fixture['report-member']));assert name in reviewer.locator('tbody').inner_text();widths(reviewer,'participant-filter-register',lang)
  href=reviewer.locator('[data-table-export=pdf]').get_attribute('href');assert parse_qs(urlparse(href).query)['ownerId']==[str(fixture['report-member'])];text=pdf(reviewer,base+href);assert 'report-member' in text
  filters.pop('ownerId');text=pdf(reviewer,base+'/staff-reports/print.pdf?'+urlencode(filters));assert 'report-member' in ''.join(text.split()) and 'report-owner' in ''.join(text.split())
  for page in [maker,preparer,reviewer,owner,member]:page.close()
  page=login('report-maker',lang);page.goto(base+'/staff-reports/new?lang='+lang);lookup(page,'[name=ownerId]','report-owner');page.locator('[name=subject]').fill('Native individual '+uuid.uuid4().hex[:8]);page.locator('[name=description]').fill('Synthetic individual activity');page.locator('[name=workType]').select_option('INDIVIDUAL');assert not page.locator('[data-team-picker]').is_visible();save(page);assert 'report-owner' in page.locator('main').inner_text();assert page.locator('[data-dismiss-notice]').inner_text()=='×';individual=urlparse(page.url).path;page.close()
  page=login('report-owner',lang);page.goto(base+individual+'?lang='+lang);withdraw=page.locator('form[action$="/actions/cancel"]');withdraw.locator('xpath=..').locator('summary').click();withdraw.locator('[name=comment]').fill(('Synthetic duplicate individual report '+'W'*1000)[:1000]);widths(page,'withdraw-report-confirmation',lang)
  with page.expect_navigation():withdraw.locator('button[type=submit]').click()
  expect(page.locator('[data-message-type=success]')).to_be_visible();assert page.locator('form[action$="/actions/cancel"]').count()==0;assert 'Synthetic duplicate individual report' in page.locator('main').inner_text();assert 'report-maker' in page.locator('main').inner_text();widths(page,'withdrawn-report-details',lang);page.close();checks.append({'flow':'individual/group assignment, creator/preparer, owner correction, independent review, member-aware filters/PDF','lang':lang})
  page=login('report-maker',lang);task=fixture['task'];params={'dateBasis':'CREATED','from':'2026-11-01','to':'2026-11-01','search':task['subject'],'lang':lang}
  page.goto(base+'/tasks?'+urlencode(params));page.wait_for_load_state('networkidle');assert task['subject'] in page.locator('tbody').inner_text();expect(page.locator('[name=dateBasis]')).to_have_value('CREATED');page.get_by_role('link',name=('Kiswahili' if lang=='en' else 'English'),exact=True).click();expect(page.locator('[name=dateBasis]')).to_have_value('CREATED');assert parse_qs(urlparse(page.url).query)['from']==['2026-11-01'];page.get_by_role('link',name=('English' if lang=='en' else 'Kiswahili'),exact=True).click();widths(page,'task-created-date-filter',lang)
  link=page.locator('[data-table-export=pdf]').get_attribute('href');assert parse_qs(urlparse(link).query)['dateBasis']==['CREATED'];text=pdf(page,base+link);assert ('Created date' in text if lang=='en' else 'Tarehe ya kuundwa' in text)
  page.locator('[name=dateBasis]').select_option('START')
  with page.expect_navigation():page.locator('form[method=get] button[type=submit]').click()
  assert task['subject'] not in page.locator('tbody').inner_text();page.close();checks.append({'flow':'creation versus start date filter with native PDF context','lang':lang})
 page=login('report-limited','en');page.goto(base+'/staff-reports/new');assert page.locator('select[name=clientId]').count()==0;lookup(page,'[name=ownerId]','report-owner');page.locator('[name=subject]').fill('Native minimal-claim '+uuid.uuid4().hex[:8]);page.locator('[name=description]').fill('Synthetic scoped assignment without client directory access');save(page);assert 'report-owner' in page.locator('main').inner_text();page.close();checks.append({'flow':'custom report assignment claim completes native form and result without client directory access'})
 browser.close()
assert not errors,errors
assert json.loads(Path('.verification/report-assignment-runtime.json').read_text())['sha256']==meta['sha256']
(root/'results.json').write_text(json.dumps({'sha256':meta['sha256'],'checks':checks,'javascript_errors':errors},indent=2))
print(json.dumps({'checks':len(checks),'javascript_errors':errors}))
