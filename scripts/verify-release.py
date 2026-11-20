#!/usr/bin/env python3
"""Fail release packaging when Android security flags or sensitive file checks fail."""
import os,re,subprocess,sys,zipfile
from pathlib import Path
apk=sys.argv[1]
aapt=str(Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0/aapt')
manifest=subprocess.check_output([aapt,'dump','xmltree',apk,'AndroidManifest.xml'],text=True)
badging=subprocess.check_output([aapt,'dump','badging',apk],text=True)
assert 'application-debuggable' not in badging,'Debuggable APK'
for flag in ('usesCleartextTraffic','allowBackup'):
    assert re.search(r'android:'+flag+r'\([^\n]+\)=\(type 0x12\)0x0',manifest),flag+' must be false'
patterns=[rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',rb'gh[pousr]_[A-Za-z0-9]{30,}',rb'github_pat_[A-Za-z0-9_]{40,}',rb'AKIA[0-9A-Z]{16}',rb'/(?:home|Users)/[A-Za-z0-9_.-]+/']
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        assert not name.lower().endswith(('.keystore','.jks','.p12','.db','.keeprbackup','.pen')), 'Sensitive file type in APK: '+name
        data=z.read(name)
        assert not any(re.search(p,data) for p in patterns),'Sensitive pattern in APK entry: '+name
    contents=[z.read(n) for n in z.namelist() if n.startswith('res/')]
    for expected in (b'Keepr privacy policy',b'Apache License',b'third-party software notices'):
        assert any(expected in data for data in contents),'Missing legal resource'
print('Release APK passed manifest, legal-resource and sensitive-pattern checks.')
