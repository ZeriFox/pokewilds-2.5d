#!/usr/bin/env python3
"""Acquire only the explicitly selected public reference sheets, with provenance.

No authentication, CAPTCHA bypass or executable assets. Source artwork remains
copyright ConcernedApe / Nintendo / Game Freak; this script grants no license.
Raw sheets stay outside resources/ and are not included in the playable JAR.
"""
from __future__ import annotations
import hashlib, html, io, json, re, time, urllib.request, urllib.parse
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'art-source/reference'
BASE='https://www.spriters-resource.com'
SHEETS={
 'desert':('pc_computer/stardewvalley',88594),
 'spring':('pc_computer/stardewvalley',88632),
 'winter':('pc_computer/stardewvalley',88665),
 'interiors':('pc_computer/stardewvalley',88658),
 'walls_floors':('pc_computer/stardewvalley',88659),
 'mines':('pc_computer/stardewvalley',84485),
 'volcano':('pc_computer/stardewvalley',223937),
 'party':('game_boy_advance/pokemonfireredleafgreen',3854),
}
def fetch(url):
 req=urllib.request.Request(url,headers={'User-Agent':'PokeWilds-reference-import/1.0','Accept':'image/png,text/html;q=0.8'})
 with urllib.request.urlopen(req,timeout=40) as reply:
  data=reply.read(12*1024*1024+1)
  if len(data)>12*1024*1024:raise ValueError('Source too large')
  return data

def main():
 OUT.mkdir(parents=True,exist_ok=True)
 previous=json.loads((OUT/'manifest.json').read_text()) if (OUT/'manifest.json').exists() else {}
 results={}
 for name,(game,asset) in SHEETS.items():
  page=f'{BASE}/{game}/asset/{asset}/'
  path=OUT/(name+'.png')
  old=previous.get(name,{})
  if path.exists() and old.get('status')=='downloaded' and hashlib.sha256(path.read_bytes()).hexdigest()==old.get('sha256'):
   results[name]=old;print(name,'verified local source',flush=True);continue
  try:
   text=fetch(page).decode('utf-8')
   urls=[html.unescape(m) for m in re.findall(r'[\"\']([^\"\']*?/media/assets/[^\"\']+)[\"\']',text)]
   urls=[u for u in urls if re.search(r'/'+str(asset)+r'\.png(?:\?|$)',u)]
   if not urls:raise ValueError('No PNG image link in asset page')
   url=urllib.parse.urljoin(BASE,urls[0])
   if urllib.parse.urlparse(url).hostname!='www.spriters-resource.com':raise ValueError('Unexpected image host')
   raw=fetch(url)
   if not raw.startswith(b'\x89PNG\r\n\x1a\n'):raise ValueError('Response is not a PNG')
   im=Image.open(io.BytesIO(raw));im.load()
   path.write_bytes(raw)
   results[name]={'page':page,'image':url,'sha256':hashlib.sha256(raw).hexdigest(),'width':im.width,'height':im.height,'bytes':len(raw),'status':'downloaded','rights':'ConcernedApe / Stardew Valley' if name!='party' else 'Nintendo / Game Freak / Pokemon FireRed LeafGreen; sheet credit Redzagoon'}
   print(name,json.dumps(results[name]),flush=True)
  except Exception as e:
   results[name]={'page':page,'status':'unavailable','error':str(e)}
   print(name,'UNAVAILABLE',str(e),flush=True)
  time.sleep(.5)
 (OUT/'manifest.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
 (OUT/'RIGHTS.txt').write_text('Reference sources selected by the repository owner. Stardew Valley artwork belongs to ConcernedApe; Pokemon artwork belongs to Nintendo / Game Freak and other respective rightsholders. The Spriters Resource hosts reference rips; no MIT, CC0 or new permission is assigned to the artwork here. Keep individual source URLs and extraction credits. Review permissions before wider redistribution. These raw source sheets are outside resources/ and are not included in game builds.\n',encoding='utf-8')
 if not any(r['status']=='downloaded' for r in results.values()):raise SystemExit('No reference sheet could be retrieved')
if __name__=='__main__':main()
