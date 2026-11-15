#!/usr/bin/env python3
"""Render isolated original PHP presentation with synthetic fixtures, then compare exactly.

Run the relevant Java PDF fixture tests first. Source snapshot is mounted read-only;
containers have no network and never execute database/authentication entrypoints.
"""
import json,subprocess
from pathlib import Path
image='php@sha256:d63e8b80cd4c6cfd01cfa7c910522e299cdc7ca1a2b6d6563db48df3c17e6c55'
reference=Path('.verification/legacy-php/fcms').resolve();scripts=Path('scripts').resolve();assert reference.is_dir()
run='foreach(array_slice($argv,2) as $case){passthru("php ".escapeshellarg($argv[1])." ".escapeshellarg($case),$status);if($status)exit($status);}'
results=[]
for directory,harness in [('operational-exports','reference-operational-exports.php'),('client-exports','reference-client-exports.php'),('client-revenue-exports','reference-client-revenues.php'),('client-empty-exports','reference-client-exports.php'),('client-empty-revenues','reference-client-revenues.php'),('cloud-billing-exports','reference-cloud-billing.php')]:
 root=(Path('.verification')/directory).resolve();cases=[p.stem[len('source-'):] for p in sorted(root.glob('source-*.json'))];assert cases,directory
 subprocess.run(['docker','run','--rm','--network','none','-v',str(reference)+':/reference:ro','-v',str(scripts)+':/scripts:ro','-v',str(root)+':/proof',image,'php','-r',run,'/scripts/'+harness,*cases],check=True,capture_output=True,text=True)
 r=subprocess.run(['python3','scripts/compare-operational-stationery.py','--directory',str(root),'--cases',*cases],capture_output=True,text=True)
 assert r.returncode==0,(directory,r.stdout,r.stderr);results.append({'directory':directory,'exact_cases':len(cases)});print(directory,len(cases),'exact cases passed',flush=True)
Path('.verification/client-export-original-comparison.json').write_text(json.dumps(results,indent=2));print('Total exact cases:',sum(x['exact_cases'] for x in results))
