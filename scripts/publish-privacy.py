#!/usr/bin/env python3
"""Publish only the reviewed privacy documents on an isolated GitHub Pages branch."""
from pathlib import Path
import json,subprocess
REPO='deborahfam/Keepr'
def api(path,method='GET',payload=None):
    command=['gh','api','repos/'+REPO+'/'+path,'--method',method]
    if payload is not None: command+=['--input','-']
    result=subprocess.run(command,input=json.dumps(payload) if payload is not None else None,text=True,capture_output=True,check=True)
    return json.loads(result.stdout) if result.stdout.strip() else None
files=[]
for name in ['index.html','privacy.txt']:
    files.append({'path':name,'mode':'100644','type':'blob','content':Path('docs/privacy',name).read_text()})
files.append({'path':'.nojekyll','mode':'100644','type':'blob','content':''})
try: parent=api('git/ref/heads/privacy-policy')['object']['sha']
except subprocess.CalledProcessError as error:
    if '404' not in error.stderr: raise
    parent=None
tree=api('git/trees','POST',{'tree':files})
author={'name':'deborahfam','email':'61991769+deborahfam@users.noreply.github.com'}
commit=api('git/commits','POST',{'message':'Publish Keepr privacy policy','tree':tree['sha'],'parents':[parent] if parent else [],'author':author,'committer':author})
if parent: api('git/refs/heads/privacy-policy','PATCH',{'sha':commit['sha'],'force':False})
else: api('git/refs','POST',{'ref':'refs/heads/privacy-policy','sha':commit['sha']})
try: page=api('pages')
except subprocess.CalledProcessError as error:
    if '404' not in error.stderr: raise
    page=api('pages','POST',{'source':{'branch':'privacy-policy','path':'/'}})
if page.get('source',{}).get('branch')!='privacy-policy':
    raise SystemExit('Existing Pages configuration differs; refusing to replace it automatically.')
print(json.dumps({'url':page.get('html_url'),'branch':'privacy-policy','commit':commit['sha']}))
