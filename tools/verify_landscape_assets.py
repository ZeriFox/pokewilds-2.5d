#!/usr/bin/env python3
"""Check atlas bounds, alpha, animation data and pinned generated-output hashes."""
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'resources/visual/landscape'
MATERIALS = ('grass grass_light grass_dry path dirt snow sand stone_floor mountain cliff '
             'cliff_dark cliff_earth cliff_volcano cave_floor wood_floor tile_floor wall '
             'wall_wood roof roof_green rug steps basalt volcano_path water water_shallow lava').split()
MATERIALS += ('desert_ground desert_path desert_cliff desert_cliff_cap desert_ramp '
              'volcanic_basalt volcanic_cracked volcanic_ash volcanic_obsidian volcanic_cliff '
              'volcanic_cliff_cap volcanic_ramp graveyard_ground graveyard_path graveyard_cliff '
              'graveyard_cliff_cap graveyard_ramp cliff_snow lava_bright lava_cooled').split()
OBJECTS = ('tree tree_small tree_pine tree_dry tree_snow bush cactus aloe rock rock_ice '
           'forest_tree forest_tree_willow door door_locked door_open machine machine_active '
           'berry_empty berry_full berry_growing seedling').split()
OBJECTS += ('desert_rock desert_decor desert_cactus desert_dead_tree volcanic_rock volcanic_crystal '
            'volcanic_charred_tree volcanic_dead_shrub graveyard_rock graveyard_dead_tree '
            'graveyard_weeds graveyard_tomb graveyard_tomb_square').split()

def brightness(im):
 pixels=[px for px in im.getdata() if px[3]]
 return sum(.2126*p[0]+.7152*p[1]+.0722*p[2] for p in pixels)/len(pixels)

def color_spread(im):
 pixels=[px for px in im.getdata() if px[3]]
 return sum(max(p[:3])-min(p[:3]) for p in pixels)/len(pixels)

def main():
 atlas=Image.open(ASSETS/'world-atlas.png').convert('RGBA')
 meta=json.loads((ASSETS/'world-atlas.json').read_text())
 provenance=json.loads((ASSETS/'PROVENANCE.json').read_text())
 samples={}
 for name,r in meta['regions'].items():
  x,y,w,h=(r[k] for k in ('x','y','width','height'))
  assert min(x,y)>=0 and min(w,h)>0 and x+w<=atlas.width and y+h<=atlas.height,name
  im=atlas.crop((x,y,x+w,y+h));assert im.getchannel('A').getbbox(),name
  assert 0<=r['anchorX']<=w and 0<=r['anchorY']<=h,name
  assert name in provenance['regions'],name
  samples[name]=im
 for name in MATERIALS:
  assert samples[name].getchannel('A').getextrema()==(255,255),('nonopaque material',name)
 for name in OBJECTS:
  assert samples[name].getchannel('A').getextrema()[0]==0,('opaque background',name)
  bounds=samples[name].getchannel('A').getbbox()
  assert bounds[3]==samples[name].height,('empty pixels below foot anchor',name)
 for name,animation in meta['animations'].items():
  frames=[samples[f] for f in animation['frames']]
  assert len(set(im.size for im in frames))==1,('frame size jitter',name)
  assert len(set(hashlib.sha256(im.tobytes()).hexdigest() for im in frames))==len(frames),('duplicate animation frame',name)
  assert animation['fps']>0
 assert samples['berry_full'].tobytes()!=samples['berry_empty'].tobytes(),'growth state is invisible'
 assert samples['door_locked'].tobytes()!=samples['door_open'].tobytes(),'door state is invisible'
 profiles=json.loads((ROOT/'resources/visual/biomes/profiles.json').read_text())['profiles']
 for biome,profile in profiles.items():
  for section in ['terrain','cliff','fluid','decor']:
   for role,key in profile[section].items():
    assert key in samples,('missing biome asset',biome,section,role,key)
    if section!='decor':
     assert samples[key].getchannel('A').getextrema()==(255,255),('transparent biome surface',biome,section,role,key)
 for biome,prefix in [('desert','desert_'),('volcano','volcanic_'),('graveyard','graveyard_')]:
  family=meta['biomeFamilies'][biome]
  assert len(family)>=6,(biome,'incomplete art family')
  for section in ['terrain','cliff']:
   for role,key in profiles[biome][section].items():
    assert key.startswith(prefix),('generic material leaked into biome',biome,section,role,key)
  assert samples[profiles[biome]['cliff']['front']].tobytes()!=samples['cliff'].tobytes(),('global cliff copy',biome)
 # Distinct material roles and intentionally restrained graveyard saturation.
 assert len({samples[k].tobytes() for k in ['volcanic_basalt','volcanic_cracked','volcanic_ash','volcanic_obsidian']})==4,'volcanic materials collapsed'
 assert brightness(samples['lava_bright'])>brightness(samples['lava_cooled'])>brightness(samples['volcanic_obsidian']),'lava heat hierarchy'
 assert color_spread(samples['graveyard_ground'])<color_spread(samples['grass']),'graveyard ground must be desaturated'
 assert provenance['sources']['graveyard']['license']=='https://creativecommons.org/publicdomain/zero/1.0/','unverified cemetery license'
 assert provenance['regions']['graveyard_tomb']['source']=='graveyard','placeholder tomb'
 assert provenance['sources']['volcanic_lava']['license']=='https://creativecommons.org/licenses/by/4.0/','unverified volcanic license'
 for key in ['lava_bright','lava_cooled']:
  assert len(meta['animations'][key]['frames'])==16,('incomplete fluid cycle',key)
  assert provenance['regions'][key+'_0']['source']=='volcanic_lava',('bubble or symbol crop instead of lava',key)
 for name,expected in provenance['outputs'].items():
  raw=(ASSETS/name).read_bytes();assert hashlib.sha256(raw).hexdigest()==expected['sha256'],name
 report={'status':'PASS','regions':len(samples),'animations':len(meta['animations']),
         'opaque_materials':len(MATERIALS),'transparent_objects':len(OBJECTS),
         'biome_profiles':len(profiles),'dedicated_biome_families':list(meta['biomeFamilies']),
         'checks':['region bounds','nonempty sprites','anchors','provenance and output SHA-256',
                   'opaque surface coverage','transparent object backgrounds','distinct animation frames',
                   'stable animation dimensions','visible plant and door states','all biome role assets exist',
                   'no transparent terrain or generic desert/volcano/graveyard cliff leakage',
                   'prop foot anchors touch visible pixels','volcanic heat/material hierarchy',
                   'graveyard desaturation and CC0 gravestone provenance'],
         'limits':'Asset data check only; full game rendering and input require native smoke tests.'}
 (ROOT/'build').mkdir(exist_ok=True)
 (ROOT/'build/landscape-assets-check.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
 print(json.dumps(report,indent=2))

if __name__=='__main__':main()
