#!/usr/bin/env python3
"""Prepare verified Black/White source textures, without generating substitute artwork.

Source downloads can be supplied with --source-dir after normal browser download.
The server currently rejects automated requests with 403; this script does not try
alternate identities or bypass that restriction. It accepts only pinned SHA-256s.
"""
from __future__ import annotations
import argparse, hashlib, io, json, math, shutil, zipfile
from pathlib import Path
from urllib.request import urlopen
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'resources/visual/unova'
SOURCES={
 'battle-stages.png': {'url':'https://textures.spriters-resource.com/media/assets/366/368759.png?updated=1755520282','page':'https://textures.spriters-resource.com/ds_dsi/pokemonblackwhite/asset/368759/','uploader':'tsuka','sha256':'d0988fb559d00e4be6c10ee93891798ae40718872b14c8b286c5466b7e3d1d27'},
 'heroes.png': {'url':'https://www.spriters-resource.com/media/assets/31/34024.png?updated=1755472687','page':'https://www.spriters-resource.com/ds_dsi/pokemonblackwhite/asset/34024/','uploader':'Barubary','sha256':'2c13ddba92ac514d0cb08d70ae556d47d034d95e1659ce9f3227db5d409cae0d'},
 'environment.zip': {'url':'https://textures.spriters-resource.com/media/assets/372/375513.zip?updated=1755522024','page':'https://textures.spriters-resource.com/ds_dsi/pokemonblackwhite/asset/375513/','uploader':'Brom','submitted':'2018-11-05','sha256':'0a224ec81da7bc2382e56f6edfda96ca96220cdc067c4818944ec18a6c3b44b8'},
}
MATERIALS={
 'grass':'summer/grass01ax.png','grass_light':'spring/grass01ax.png','grass_dry':'autumn/grass01ax.png','snow':'winter/snow/grass01ax.png',
 'path':'summer/michi01b.png','path_dry':'autumn/michi01b.png','sand':'sabaku01.png','sand_shore':'sea_jimen.png',
 'stone_floor':'grass_ishi.png','mountain':'gake_michi.png','basalt':'d0iwa_01.png','volcano':'out57_yuka02.png','volcano_path':'out57_yuka01.png',
 'water':'sea_mizu1.png','water_shallow':'sea_mizu1_1.png','river':'kawa_soko.png','water_ripple':'kawa01b.png',
 'cliff':'yamagake01.png','cliff_cap':'gake_futa.png','cliff_grass':'summer/gake1_1.png','cliff_volcano':'out57_gake01a.png','cliff_dark':'d0gake_001.png',
 'steps':'kaidan01a.png','wood_floor':'cafe_tile03.png','tile_floor':'cafe_tile01.png','tile_pale':'cafe_tile02.png','brick_floor':'yuka.png',
 'wall':'cafe_kabe02.png','wall_wood':'cafe_kabe01.png','door':'cabin_door.png','door_red':'door_c14_1.png','fence':'c13_saku01.png','fence_wood':'fence_01.png',
 'tree':'summer/54ki02ax.png','tree_pine':'summer/ki02ax.png','tree_dry':'autumn/ki02ax1.png','tree_snow':'winter/snow/ki03ax.png','tree_small':'summer/ki03ax.png',
 'tree_top':'summer/54ki02bx.png','palm_trunk':'ki04ax.png','palm_top':'ki04dx.png','rock':'rock01.png','rock_small':'rock02.png','rock_ice':'in30_rock01.png',
 'grass_tuft':'summer/ue_grass01.png','grass_dry_tuft':'autumn/ue_grass01.png','flowers':'summer/hana01.2.png','flowers_fairy':'spring/kisetu_hana.png','plant':'plant_tex4_lm1.png',
 'bench':'bench.png','chair':'isu.png','desk':'desk01.png','pot':'in66_plant.png','lamp':'in66_light02.png','couch':'chushion01a_1.png','cushion':'chushion01a_2.png',
 'bridge':'hashi01a.png','hole':'mori01s.png','ruin_wall':'sio_fe01.png','ruin_floor':'out18_tile03.png','rug':'cafe_mat01.png',
}

def digest(b):return hashlib.sha256(b).hexdigest()
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source-dir',type=Path);args=parser.parse_args()
 (DEST/'sources').mkdir(parents=True,exist_ok=True)
 provenance={'schema':1,'source_game':'Pokemon Black / White, Nintendo DS','rights':'Original game artwork: Nintendo / Game Freak / The Pokemon Company. Public extraction catalog, not an assertion of an open-source artwork license. Preserve uploader credits; do not claim authorship.','sources':SOURCES,'preparation':'Exact PNG copies / crops and background-key transparency for hero cells; no generated replacement art. Runtime lava coloration and geometric architecture are presentation adaptations.','regions':{},'outputs':{}}
 data={}
 for name,meta in SOURCES.items():
  candidate=(args.source_dir/name if args.source_dir else DEST/'sources'/name)
  if candidate.exists():raw=candidate.read_bytes()
  else:
   try:
    with urlopen(meta['url'],timeout=30) as response:raw=response.read()
   except Exception as e:raise RuntimeError(f'Download {name} from its linked catalog page in a browser and pass --source-dir. No bypass attempted: {e}') from e
  if digest(raw)!=meta['sha256']:raise RuntimeError('Pinned source hash mismatch: '+name)
  (DEST/'sources'/name).write_bytes(raw);data[name]=raw
 z=zipfile.ZipFile(io.BytesIO(data['environment.zip']));images={}
 for key,name in MATERIALS.items():
  member='sourceimages/'+name;raw=z.read(member);images[key]=Image.open(io.BytesIO(raw)).convert('RGBA')
  provenance['regions'][key]={'source':'environment.zip','member':member,'member_sha256':digest(raw),'source_size':list(images[key].size)}
 heroes=Image.open(io.BytesIO(data['heroes.png'])).convert('RGBA')
 stages=Image.open(io.BytesIO(data['battle-stages.png'])).convert('RGBA')
 for row in range(6):
  for col in range(4 if row==5 else 6):
   key=f'battle_{row}_{col}';x=8+40*col;y=8+40*row;images[key]=stages.crop((x,y,x+32,y+32))
   provenance['regions'][key]={'source':'battle-stages.png','crop':[x,y,32,32]}
 for gender,origin_y in [('male',4),('female',144)]:
  for motion,origin_x in [('walk',4),('run',112)]:
   for direction,row in [('up',0),('down',1),('left',2),('right',3)]:
    for frame in range(3):
     x=origin_x+frame*32;y=origin_y+row*32;cell=heroes.crop((x,y,x+32,y+32));background=cell.getpixel((0,0))[:3]
     cell.putdata([(r,g,b,0 if (r,g,b)==background else a) for r,g,b,a in cell.getdata()])
     key=f'trainer_{gender}_{motion}_{direction}_{frame}';images[key]=cell
     provenance['regions'][key]={'source':'heroes.png','crop':[x,y,32,32],'transparent_color':list(background),'anchor':[16,29]}
 cell=68;cols=16;rows=math.ceil(len(images)/cols);height=2**math.ceil(math.log2(rows*cell));atlas=Image.new('RGBA',(2048,height))
 regions={}
 for i,(key,im) in enumerate(images.items()):
  if im.width>64 or im.height>64:raise RuntimeError('Unexpected size '+key)
  x=i%cols*cell+2;y=i//cols*cell+2;atlas.paste(im,(x,y));regions[key]={'x':x,'y':y,'width':im.width,'height':im.height}
  # One-pixel edge extrusion prevents sampling an adjacent transparent texel.
  atlas.paste(im.crop((0,0,im.width,1)),(x,y-1));atlas.paste(im.crop((0,im.height-1,im.width,im.height)),(x,y+im.height))
  atlas.paste(im.crop((0,0,1,im.height)),(x-1,y));atlas.paste(im.crop((im.width-1,0,im.width,im.height)),(x+im.width,y))
 atlas.save(DEST/'world-atlas.png',optimize=False)
 (DEST/'world-atlas.json').write_text(json.dumps(regions,indent=2)+'\n',encoding='utf-8')
 for name in ['world-atlas.png','world-atlas.json']:provenance['outputs'][name]={'sha256':digest((DEST/name).read_bytes()),'bytes':(DEST/name).stat().st_size}
 (DEST/'PROVENANCE.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8')
 (DEST/'CREDITS.txt').write_text('Pokemon Black / White original graphics: Nintendo, Game Freak, The Pokemon Company.\nHero (M/F) Overworld extraction: Barubary, The Spriters Resource, asset 34024.\nMain Environment extraction: Brom, The Textures Resource, asset 375513 (2018-11-05).\nSource pages and file SHA-256 values are recorded in PROVENANCE.json.\nOriginal ripped artwork is not described as MIT, CC0 or freely licensed.\nOriginal sheets/ZIP are preserved with their embedded credits in sources/.\nAtlas preparation: deterministic packing, source crops, hero background transparency.\nLava colors, cliff geometry and modular architectural composition are renderer adaptations, not original B/W screenshots.\n',encoding='utf-8')
 with (DEST/'CREDITS.txt').open('a',encoding='utf-8') as credit: credit.write('Battle Stages extraction: tsuka, The Textures Resource, asset 368759. Credit appreciated in source sheet.\n')
 print(f'Prepared {len(regions)} verified regions in {atlas.width}x{atlas.height} atlas.')
if __name__=='__main__':main()
