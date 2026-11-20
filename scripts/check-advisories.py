#!/usr/bin/env python3
"""Check exact resolved release dependencies against OSV; fail closed on outages/findings."""
import json
from pathlib import Path
import urllib.request

inventory = Path('app/build/release-inventory.tsv')
packages = [line.split('\t')[0] for line in inventory.read_text().splitlines()]
queries = []
for coordinate in packages:
    group, artifact, version = coordinate.split(':')
    queries.append({'package': {'ecosystem': 'Maven', 'name': group + ':' + artifact}, 'version': version})
request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
    data=json.dumps({'queries': queries}).encode(), headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(request, timeout=90) as response:
    results = json.load(response)['results']
if len(results) != len(packages):
    raise SystemExit('Incomplete OSV response')
findings = {package: [v['id'] for v in result.get('vulns', [])] for package, result in zip(packages, results) if result.get('vulns')}
report = {'source': 'https://osv.dev', 'packages': packages, 'findings': findings}
Path('app/build/advisories.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'checked': len(packages), 'findings': findings}, indent=2))
raise SystemExit(bool(findings))
