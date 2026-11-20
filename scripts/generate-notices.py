#!/usr/bin/env python3
"""Bundle exact packaged notices plus upstream license texts; --check is offline."""
from pathlib import Path
import argparse, hashlib, io, json, urllib.request, zipfile

parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
rows=[line.split('\t') for line in Path('app/build/release-inventory.tsv').read_text().splitlines()]
coordinates=[row[0] for row in rows]
manifest=Path('release/dependencies.json')
if args.check:
    if json.loads(manifest.read_text())['coordinates']!=coordinates: raise SystemExit('Dependency notices are stale: regenerate them.')
    print('License inventory matches resolved dependencies.');raise SystemExit(0)
sources={
 'AndroidX, Guava, Kotlin, coroutines, OkHttp and Okio — Apache 2.0':'https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/LICENSE.txt',
 'Desugared Java library — GPL v2 with Classpath Exception':'https://raw.githubusercontent.com/google/desugar_jdk_libs/master/LICENSE',
 'Desugared Java library — additional license information':'https://raw.githubusercontent.com/google/desugar_jdk_libs/master/ADDITIONAL_LICENSE_INFO',
 'Desugared Java library — assembly exception':'https://raw.githubusercontent.com/google/desugar_jdk_libs/master/ASSEMBLY_EXCEPTION',
 'Public Suffix List — Mozilla Public License 2.0':'https://raw.githubusercontent.com/publicsuffix/list/master/LICENSE',
}
sections=['Keepr — third-party software notices\n\nThis app includes the following libraries. Their authors retain their copyrights.\n\n'+'\n'.join(coordinates)]
provenance=[]
for title,url in sources.items():
    with urllib.request.urlopen(url,timeout=30) as r: data=r.read()
    sections.append(title+'\nSource: '+url+'\n\n'+data.decode())
    provenance.append({'url':url,'sha256':hashlib.sha256(data).hexdigest()})
seen=set()
def extract(coordinate,z):
    for name in z.namelist():
        if any(word in Path(name).name.lower() for word in ('license','notice','copying')) and not name.endswith('.class'):
            data=z.read(name)
            if data in seen: continue
            seen.add(data)
            sections.append(coordinate+' — '+name+'\n\n'+data.decode('utf-8'))
for coordinate,path in rows:
    with zipfile.ZipFile(path) as z:
        extract(coordinate,z)
        if 'classes.jar' in z.namelist():
            with zipfile.ZipFile(io.BytesIO(z.read('classes.jar'))) as inner: extract(coordinate,inner)
sections.append('Source code\nAndroidX: https://android.googlesource.com/platform/frameworks/support/\nKotlin: https://github.com/JetBrains/kotlin\nCoroutines: https://github.com/Kotlin/kotlinx.coroutines\nGuava: https://github.com/google/guava\nOkHttp: https://github.com/square/okhttp/tree/parent-4.12.0\nOkio: https://github.com/square/okio/tree/parent-3.6.0\njsoup: https://github.com/jhy/jsoup/tree/jsoup-1.24.1\nDesugared Java library: https://github.com/google/desugar_jdk_libs\nPublic Suffix List: https://publicsuffix.org/list/public_suffix_list.dat\nNo library source modifications are made by Keepr.')
Path('app/src/main/res/raw/third_party_notices.txt').write_text('\n\n'+'\n\n'+'\n\n'.join(sections)+'\n')
manifest.write_text(json.dumps({'coordinates':coordinates,'license_sources':provenance},indent=2)+'\n')
print('Bundled third-party license texts and packaged notices for',len(rows),'artifacts.')
