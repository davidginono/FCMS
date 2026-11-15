#!/usr/bin/env python3
"""Native backup/download/restore of the isolated CSV import verification database."""
import argparse,http.cookiejar,json,os,re,subprocess,time,urllib.request,urllib.parse
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:8090');p.add_argument('--output',default='.verification/import-backup');args=p.parse_args();base=args.base_url.rstrip('/');assert urllib.parse.urlparse(base).hostname in ['127.0.0.1','localhost']
meta=json.loads(Path('.verification/category-runtime.json').read_text());database=meta['database'];schema=meta['schema'];assert database.startswith('fcms_codex_verify_') and re.fullmatch('[a-z0-9_]+',schema)
assert urllib.parse.urlparse(base).port==meta.get('port',8090), 'Runtime metadata must describe the tested port'
def sql(query,db=database):
 return subprocess.check_output(['docker','exec','fcms-cloud-postgres','psql','-U','fcms','-d',db,'-tAc',query],text=True).strip()
o=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def csrf(html):return re.search('name="_csrf" value="([^"]+)"',html).group(1)
login=o.open(base+'/login').read().decode();o.open(base+'/login',urllib.parse.urlencode({'email':os.environ['FCMS_UI_EMAIL'],'password':os.environ['FCMS_UI_PASSWORD'],'_csrf':csrf(login)}).encode()).read()
html=o.open(base+'/backups').read().decode();o.open(base+'/backups',urllib.parse.urlencode({'_csrf':csrf(html)}).encode()).read()
for _ in range(50):
 state=sql(f'SELECT id::text||\'|\'||status FROM {schema}.system_backups ORDER BY created_at DESC LIMIT 1');identifier,status=state.split('|')
 if status=='READY':break
 if status=='FAILED':raise AssertionError('Native backup failed')
 time.sleep(1)
else:raise AssertionError('Backup readiness timeout')
response=o.open(base+'/backups/'+identifier+'/download');data=response.read();assert data.startswith(b'PGDMP') and 'no-store' in response.headers['Cache-Control'] and 'attachment' in response.headers['Content-Disposition']
root=Path(args.output);root.mkdir(parents=True,exist_ok=True);(root/'backup.dump').write_bytes(data)
restore='fcms_codex_verify_import_restore_'+str(int(time.time()));subprocess.run(['docker','exec','fcms-cloud-postgres','createdb','-U','fcms',restore],check=True);subprocess.run(['docker','exec','-i','fcms-cloud-postgres','pg_restore','-U','fcms','--exit-on-error','--no-owner','--dbname',restore],input=data,check=True)
matched=[]
for table in ['saccos','saccos_import_batches','saccos_import_clients','audit_events','work_items','work_participants','ledger_entries','expenses','expense_events','expense_reversals','gl_journals']:
 order='batch_id,ordinal' if table=='saccos_import_clients' else 'work_id,user_id' if table=='work_participants' else 'id'
 query=f'SELECT count(*)::text||\'|\'||md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY {order})::text,\'\')) FROM {schema}.{table} t';before,after=sql(query),sql(query,restore);assert before==after,(table,before,after);matched.append({'table':table,'count_and_digest':before})
assert int(next(v['count_and_digest'] for v in matched if v['table']=='saccos_import_clients').split('|')[0])>=4
for name in ['expense-assignment','report-assignment']:
 path=Path('.verification/'+name+'-runtime.json')
 if not path.exists():continue
 fixture=json.loads(path.read_text())
 if fixture['database']!=database or fixture['sha256']!=meta['sha256']:continue
 assigned=fixture['schema'];assert re.fullmatch('[a-z0-9_]+',assigned)
 for table in ['expenses','expense_events','expense_reversals','work_items','work_participants','audit_events']:
  order='work_id,user_id' if table=='work_participants' else 'id'
  query=f'SELECT count(*)::text||\'|\'||md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY {order})::text,\'\')) FROM {assigned}.{table} t'
  before,after=sql(query),sql(query,restore);assert before==after,(assigned,table,before,after);matched.append({'schema':assigned,'table':table,'count_and_digest':before})
 query=f'SELECT version FROM {assigned}.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1'
 assert sql(query)==sql(query,restore)=='27'
result={'sha256':meta['sha256'],'native_backup':'READY','bytes':len(data),'restore_database':restore,'matched_tables':matched};(root/'results.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
