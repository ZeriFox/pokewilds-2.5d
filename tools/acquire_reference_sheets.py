#!/usr/bin/env python3
"""Acquire user-selected public reference sheets with hashes; never run by the game.
Raw sheets are not built into the game. Artwork remains with its rightsholders;
The Spriters Resource is a reference archive, not a license grant. No login or
CAPTCHA bypass; only verified PNGs are retained, not page scripts or credentials.
"""
from __future__ import annotations
import hashlib,html,io,json,re,time,urllib.request,urllib.parse
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'art-source/reference'
BASE='https://www.spriters-resource.com'
SHEETS={name:('pc_computer/stardewvalley',number) for name,number in {
 'desert':88594,'spring':88632,'winter':88665,'interiors':88658,
 'walls_floors':88659,'mines':84485,'volcano':223937,
 'furniture':126521,'flooring':169045,'chairs':169570}.items()}
SHEETS['party']=('game_boy_advance/pokemonfireredleafgreen',3854)
def fetch(url):
 request=urllib.request.Request(url,headers={'User-Agent':'PokeWilds-reference-import/1.0','Accept':'image/png,text/html;q=0.8'})
 with urllib.request.urlopen(request,timeout=40) as reply:
  data=reply.read(12*1024*1024+1)
  if len(data)>12*1024*1024:raise ValueError('Source too large')
  return data

def main():
 OUT.mkdir(parents=True,exist_ok=True)
 previous=json.loads((OUT/'manifest.json').read_text()) if (OUT/'manifest.json').exists() else {}
 results={}
 for name,(game,asset) in SHEETS.items():
  page=f'{BASE}/{game}/asset/{asset}/';path=OUT/(name+'.png');old=previous.get(name,{})
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
   image=Image.open(io.BytesIO(raw));image.load();path.write_bytes(raw)
   results[name]={'page':page,'image':url,'sha256':hashlib.sha256(raw).hexdigest(),'width':image.width,'height':image.height,'bytes':len(raw),'status':'downloaded','rights':'ConcernedApe / Stardew Valley' if name!='party' else 'Nintendo / Game Freak / Pokemon FireRed LeafGreen; sheet credit Redzagoon'}
   print(name,json.dumps(results[name]),flush=True)
  except Exception as error:
   results[name]={'page':page,'status':'unavailable','error':str(error)};print(name,'UNAVAILABLE',str(error),flush=True)
  time.sleep(.5)
 (OUT/'manifest.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
 (OUT/'RIGHTS.txt').write_text('Reference sources selected by the repository owner. Stardew Valley artwork belongs to ConcernedApe; Pokemon artwork belongs to Nintendo / Game Freak and other respective rightsholders. The Spriters Resource hosts reference rips; no MIT, CC0 or new permission is assigned to the artwork here. Keep individual source URLs and extraction credits. Review permissions before wider redistribution. These raw source sheets are outside resources/ and are not included in game builds.\n',encoding='utf-8')
 if not all(result['status']=='downloaded' for result in results.values()):raise SystemExit('Some sources unavailable; inspect manifest before using')
if __name__=='__main__':main()
