#!/usr/bin/env python3
"""Build the local landscape atlas from pinned, unmodified source downloads.

No network, generated art, executable plugin, or source-pack redistribution.
Requires Pillow. Originals remain outside this project in work/landscape-assets.
"""
from __future__ import annotations
import argparse, hashlib, io, json, math, zipfile
from collections import deque
from pathlib import Path
from PIL import Image, ImageChops, ImageOps

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'resources/visual/landscape'
SOURCES = {
 'crawler': {'file':'Pixel Crawler - Free Pack 2.11.zip','sha256':'b7b228ca232da9958f191b01db87764dddb67610116151e06e8dd8ade6de94b8','author':'Anokolisa','page':'https://anokolisa.itch.io/free-pixel-art-asset-pack-topdown-tileset-rpg-16x16-sprites','terms':'Embedded Terms.txt allows game projects and modifications; assets cannot be sold as a standalone final product.'},
 'woods': {'file':'pixel_16_woods v2 free.zip','sha256':'85411ba94b2ddebb9c1384c4b8c932812127b6e5ad8da4f2a3166db9870eb045','author':'zedpxl','page':'https://zedpxl.itch.io/pixelart-forest-asset-pack','terms':'Author page allows commercial/noncommercial games and modification; forbids resale/redistribution of the asset pack, AI and NFT use. No license file in supplied archive.'},
 'cave': {'file':'Cold Cave FREE.zip','sha256':'55ea764ac030ae0decc3f98261ac1c56461f02c652dc7651761203df25e21cb8','author':'Asset Alliance','page':'https://gif-superretroworld.itch.io/cold-cave','terms':'Embedded LICENSE.txt allows games and modification; forbids direct asset distribution/sale, claiming authorship, NFT/metaverse/AI projects.'},
 'forest': {'file':'Forest_1.png','sha256':'42c2fbe8929964d6aaba1aa15298e51619042dfe072a4ee389811a8ea4dbcd84','author':'Haydeos','page':'https://haydeos.itch.io/fantasy-forest-rpg-maker-tileset','terms':'Author page allows games and modification across engines; forbids standalone redistribution, AI training, NFT/blockchain use. Author discloses AI-assisted base forms and manual refinement.'},
 'graveyard': {'file':'graveyard-marionline.png','sha256':'1529ee5c3b927107178ef7ed7a3af02ac93a60860543c227678ff3ceb3c5d094','author':'marionline','page':'https://opengameart.org/content/forest-graveyard-tileset','download_url':'https://opengameart.org/sites/default/files/tileset_48.png','terms':'CC0 1.0; public-domain dedication, modification and redistribution permitted. Attribution retained voluntarily. Source/licensing checked 2026-10-07.','license':'https://creativecommons.org/publicdomain/zero/1.0/'},
 'desert': {'file':'desert-scratchio.png','sha256':'373c83623b9ac0a776120abb89c25b7b6d33acd0b2a4d052f717083563c005bf','author':'ScratchIO','page':'https://opengameart.org/content/desert-level-decorations-pixel-art','download_url':'https://opengameart.org/sites/default/files/desert_decorations_0.png','terms':'CC0 1.0; public-domain dedication, modification and redistribution permitted. One cactus crop used with the same dry-vegetation palette as the main pack. Source/licensing checked 2026-10-07.','license':'https://creativecommons.org/publicdomain/zero/1.0/'},
 'volcanic_lava': {'file':'lava-dark-stone_large.png','sha256':'5186e497358a2b3b62599f278704897ef50001b56d6edab737b1fc79fd5240c2','author':'Sevarihk','page':'https://opengameart.org/content/stone-and-lava-ground-tiles','download_url':'https://opengameart.org/sites/default/files/lava-dark-stone_large.png','terms':'CC-BY 4.0. Attribution: Stone and Lava Ground Tiles by Sevarihk, linked creator page. Modified: cropped continuous lava, luminance palette for hot/cooled states, toroidal frame motion. Source/licensing checked 2026-10-07.','license':'https://creativecommons.org/licenses/by/4.0/'},
 'volcanic_stone': {'file':'stone_terrain_addon.png','sha256':'1179ce4c2998b7e4a9c4e0ed728bff1c5f70b6ff45b14a90e1801587c6bfcaa5','author':'Sevarihk','page':'https://opengameart.org/content/stone-and-lava-ground-tiles','download_url':'https://opengameart.org/sites/default/files/stone_terrain_addon.png','terms':'CC-BY 4.0. Attribution: Stone and Lava Ground Tiles by Sevarihk, linked creator page. Modified: exact material crops and cool basalt luminance palette. Source/licensing checked 2026-10-07.','license':'https://creativecommons.org/licenses/by/4.0/'},
}
CR = 'Pixel Crawler - Free Pack/Environment/'
SHEETS = {
 'floor':('crawler',CR+'Tilesets/Floors_Tiles.png'), 'water':('crawler',CR+'Tilesets/Water_tiles.png'),
 'wall':('crawler',CR+'Tilesets/Wall_Tiles.png'), 'dungeon':('crawler',CR+'Tilesets/Dungeon_Tiles.png'),
 'props':('crawler',CR+'Structures/Buildings/Props.png'), 'roof':('crawler',CR+'Structures/Buildings/Roofs.png'),
 'inside':('crawler',CR+'Structures/Buildings/Interior/Interior_Props_01.png'),
 'veg':('crawler',CR+'Props/Static/Vegetation.png'), 'rocks':('crawler',CR+'Props/Static/Rocks.png'),
 'resources':('crawler',CR+'Props/Static/Resources.png'),
 'tree1':('crawler',CR+'Props/Static/Trees/Model_01/Size_02.png'),
 'tree2':('crawler',CR+'Props/Static/Trees/Model_02/Size_02.png'),
 'tree3':('crawler',CR+'Props/Static/Trees/Model_03/Size_02.png'),
 'fire':('crawler',CR+'Structures/Stations/Bonfire/Fire_01-Sheet.png'),
 'woods':('woods','pixel_16_woods v2 free/free_pixel_16_woods.png'),
 'cave':('cave','Tileset_FREE.png'), 'forest':('forest',None),
 'graveyard':('graveyard',None),
 'desert':('desert',None),
 'volcanic_lava':('volcanic_lava',None), 'volcanic_stone':('volcanic_stone',None),
}
def sha(raw): return hashlib.sha256(raw).hexdigest()

def atomic_write(path, data):
 """Readers must never observe a partially written atlas or metadata file."""
 temporary=path.with_name(path.name+'.tmp')
 temporary.write_bytes(data)
 temporary.replace(path)

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--source-dir',type=Path,default=ROOT.parents[1]/'work/landscape-assets/sources')
 args=p.parse_args(); DEST.mkdir(parents=True,exist_ok=True)
 archives={}; images={}; entries={}; metadata={}; regions={}; animations={}
 for key,s in SOURCES.items():
  raw=(args.source_dir/s['file']).read_bytes()
  if sha(raw)!=s['sha256']:raise ValueError('Source hash mismatch: '+s['file'])
  archives[key]=zipfile.ZipFile(io.BytesIO(raw)) if s['file'].endswith('.zip') else raw
 for key,(source,member) in SHEETS.items():
  raw=archives[source].read(member) if member else archives[source]
  images[key]=Image.open(io.BytesIO(raw)).convert('RGBA')
  entries[key]={'source':source,'member':member,'member_sha256':sha(raw)}
 def add(key,sheet,box,*,trim=False,palette=None,opaque=None,foliage_palette=None,normalize=False,colorkey=None):
  x,y,w,h=box; original=images[sheet]
  assert x>=0 and y>=0 and x+w<=original.width and y+h<=original.height,(key,box,original.size)
  im=original.crop((x,y,x+w,y+h)); op=[]
  if colorkey is not None:
   im.putdata([(*px[:3],0 if px[:3]==colorkey else px[3]) for px in im.getdata()])
   op.append({'transparent_color':colorkey})
  if sheet=='forest':
   # Supplied Forest_1 has opaque white outside its objects. Remove only the
   # border-connected neutral background, keeping enclosed pale highlights.
   pix=im.load();q=deque();seen=set()
   for px in range(w):q.extend([(px,0),(px,h-1)])
   for py in range(h):q.extend([(0,py),(w-1,py)])
   while q:
    px,py=q.popleft()
    if (px,py) in seen or not (0<=px<w and 0<=py<h):continue
    seen.add((px,py));r,g,b,a=pix[px,py]
    if a and (min(r,g,b)<240 or max(r,g,b)-min(r,g,b)>10):continue
    pix[px,py]=(r,g,b,0)
    q.extend([(px+1,py),(px-1,py),(px,py+1),(px,py-1)])
   op.append({'border_connected_white_alpha':{'minimum_channel':240,'maximum_channel_spread':10}})
  if opaque:
   base=Image.new('RGBA',im.size,opaque);base.alpha_composite(im);im=base;op.append({'opaque_underlay':opaque})
  if palette:
   alpha=im.getchannel('A');gray=ImageOps.grayscale(im)
   if normalize:
    levels=[value for value,a in zip(gray.getdata(),alpha.getdata()) if a]
    low,high=min(levels),max(levels)
    if high>low:gray=gray.point(lambda value:max(0,min(255,round((value-low)*255/(high-low)))))
    op.append({'visible_luminance_range':[low,high]})
   im=ImageOps.colorize(gray,*palette).convert('RGBA');im.putalpha(alpha);op.append({'luminance_palette':palette})
  if foliage_palette:
   recolored=ImageOps.colorize(ImageOps.grayscale(im),*foliage_palette).convert('RGBA')
   src=im.load();dst=recolored.load()
   for py in range(im.height):
    for px in range(im.width):
     r,g,b,a=src[px,py]
     if a and g>r and g>b:src[px,py]=(*dst[px,py][:3],a)
   op.append({'green_foliage_luminance_palette':foliage_palette,'mask':'green > red and green > blue; bark unchanged'})
  if trim:
   bounds=im.getchannel('A').getbbox()
   if not bounds:raise ValueError('Empty region '+key)
   im=im.crop(bounds);op.append({'alpha_trim':list(bounds)})
  if not im.getchannel('A').getbbox():raise ValueError('Empty region '+key)
  regions[key]=im;metadata[key]={**entries[sheet],'crop':[x,y,w,h],'operations':op,'anchorX':im.width/2,'anchorY':0}
 def alias(key,base):
  regions[key]=regions[base];metadata[key]={'alias':base,'anchorX':regions[key].width/2,'anchorY':0}
 # Opaque, internally seamless portions of the original tile patterns.
 for key,sheet,box in [
  ('grass','floor',(16,160,32,32)),('path','floor',(96,160,32,32)),('dirt','floor',(176,160,32,32)),
  ('snow','floor',(16,352,32,32)),('sand','floor',(96,352,32,32)),('stone_floor','floor',(256,16,32,32)),
  ('mountain','wall',(112,32,32,32)),('cliff','wall',(112,96,32,32)),('cliff_earth','wall',(16,96,32,32)),
  ('cliff_dark','cave',(0,32,16,16)),('cave_floor','cave',(16,80,16,16)),
  ('wood_floor','dungeon',(224,16,32,32)),('tile_floor','dungeon',(64,0,32,32)),
  ('wall','dungeon',(0,16,32,32)),('wall_wood','dungeon',(224,16,32,32)),
  ('roof','roof',(48,160,32,32)),('roof_green','roof',(176,160,32,32)),
  ('rug','inside',(340,340,32,32)),('steps','wall',(112,32,16,16)),
 ]:add(key,sheet,box,opaque=(55,49,56,255) if key=='cliff_dark' else None)
 add('grass_light','floor',(16,160,32,32),palette=('#33592b','#8cad46'))
 add('grass_dry','floor',(16,160,32,32),palette=('#726334','#c0b15a'))
 add('basalt','wall',(112,32,32,32),palette=('#28262e','#706c73'))
 add('cliff_volcano','wall',(112,96,32,32),palette=('#23212a','#67616b'))
 add('volcano_path','floor',(96,160,32,32),palette=('#4f423e','#a18060'))
 for a,b in {'volcano':'basalt','cliff_cap':'mountain','cliff_grass':'cliff_earth','ramp':'steps','ruin_floor':'tile_floor','ruin_wall':'wall','tile_pale':'snow','brick_floor':'roof','sand_shore':'sand','path_dry':'dirt','bridge':'wood_floor'}.items():alias(a,b)
 # Four explicitly different source water frames, exported side by side by Anokolisa.
 for i in range(4):
  box=(80+96*i,16,16,16)
  add('water_'+str(i),'water',box)
  add('water_shallow_'+str(i),'water',box,palette=('#237e8d','#9fd6c7'))
  add('lava_'+str(i),'water',box,palette=('#ab2521','#ffd171'))
 for key in ['water','water_shallow','lava']:
  animations[key]={'fps':5,'frames':[key+'_'+str(i) for i in range(4)]};alias(key,key+'_0')
 alias('river','water_shallow');alias('water_ripple','water_shallow')
 # Each special biome owns a coherent set, including both cliff faces and caps.
 # Do not use a global rock face or a sand tile as a desert prop. Geometry still
 # follows the original height/collision data; these are only presentation assets.
 for key,sheet,box,palette in [
  ('desert_ground','floor',(96,352,32,32),('#bc8d55','#e6c587')),
  ('desert_path','floor',(176,160,32,32),('#9e743e','#ccaa6c')),
  ('desert_cliff','wall',(16,96,32,32),('#71523b','#c59659')),
  ('desert_cliff_cap','wall',(16,32,32,32),('#b3834e','#c89a5c')),
  ('desert_ramp','wall',(16,256,32,32),('#87623f','#cc9b5d')),
  ('volcanic_basalt','wall',(112,32,32,32),('#30313b','#41424d')),
  ('volcanic_cracked','volcanic_stone',(32,32,32,32),('#252530','#51515e')),
  ('volcanic_ash','floor',(96,352,32,32),('#62606c','#76727c')),
  ('volcanic_obsidian','wall',(112,32,32,32),('#1e2130','#34364a')),
  ('volcanic_cliff','volcanic_stone',(32,96,32,32),('#181b27','#53525e')),
  ('volcanic_cliff_cap','volcanic_stone',(32,32,32,32),('#343440','#4b4a58')),
  ('volcanic_ramp','wall',(112,256,32,32),('#232530','#62606d')),
  ('graveyard_ground','floor',(16,160,32,32),('#404c45','#596557')),
  ('graveyard_path','floor',(176,160,32,32),('#555950','#737568')),
  ('graveyard_cliff','wall',(112,96,32,32),('#323d39','#6e7b6d')),
  ('graveyard_cliff_cap','wall',(112,32,32,32),('#526054','#636c5d')),
  ('graveyard_ramp','wall',(112,256,32,32),('#39473e','#7b8575')),
  ('cliff_snow','wall',(112,96,32,32),('#668797','#c5e0e6')),
 ]:add(key,sheet,box,palette=palette,normalize=True)
 for i in range(16):
  # Sevarihk's organic lava is the secondary volcanic material family. The
  # continuous 64px center has no shore rocks, symbols, grids or bubble icons.
  # Hot and cooled states retain exactly the same crust/fissure geometry.
  for key,palette in [('lava_bright',('#60242a','#ffca73')),('lava_cooled',('#292a35','#bf6944'))]:
   name=key+'_'+str(i)
   add(name,'volcanic_lava',(160,32,64,64),palette=palette,normalize=True)
   regions[name]=ImageChops.offset(regions[name],i*4,0)
   metadata[name]['operations'].append({'toroidal_flow_offset':[i*4,0]})
 for key in ['lava_bright','lava_cooled']:
  animations[key]={'fps':5,'frames':[key+'_'+str(i) for i in range(16)]};alias(key,key+'_0')
 # Source sprite crops retain real alpha; trimming gives each sprite a visible foot origin.
 for key,sheet,box in [
  ('tree','woods',(272,0,64,80)),('tree_small','woods',(272,80,32,48)),('tree_pine','woods',(304,80,32,48)),
  ('tree_dry','tree1',(96,64,32,64)),('tree_snow','tree1',(224,0,32,64)),('tree_oak','tree3',(0,0,32,48)),
  ('bush','veg',(0,0,32,32)),('bush_dry','veg',(96,0,32,32)),('tree_dead','veg',(192,64,48,32)),
  ('cactus','tree2',(0,0,32,48)),('aloe','veg',(80,144,32,32)),
  ('rock','rocks',(128,16,32,32)),('rock_small','rocks',(64,16,16,16)),('rock_ice','rocks',(160,272,32,32)),
  ('grass_tuft','woods',(224,48,16,16)),('grass_dry_tuft','veg',(96,272,16,16)),
  ('flowers','woods',(80,80,16,16)),('flowers_fairy','woods',(256,48,16,16)),('mushroom','woods',(224,64,16,16)),
  ('seedling','veg',(0,144,16,16)),('plant_growing','veg',(0,176,16,16)),('berry_fruit','veg',(0,352,16,16)),
  ('stump','woods',(224,80,32,16)),('cave_stalagmite','cave',(112,16,16,16)),
  ('cave_crystal','rocks',(144,256,16,48)),('fence_wood','props',(32,176,16,16)),
  ('door','props',(160,16,32,48)),('door_locked','props',(128,16,32,48)),('door_open','props',(64,16,32,48)),
  ('window','props',(64,64,32,32)),('window_dark','props',(192,64,32,32)),
  ('chair','inside',(64,32,16,32)),('desk','inside',(112,64,32,32)),('table','inside',(80,0,32,48)),
  ('bench','props',(16,128,32,16)),('bed','inside',(0,288,32,64)),('couch','inside',(272,0,48,48)),
  ('shelf','inside',(48,96,48,48)),('wardrobe','inside',(432,0,64,48)),('pot','inside',(32,352,16,32)),
  ('chest','inside',(144,64,32,32)),('machine','inside',(112,0,32,48)),('machine_active','inside',(144,0,32,48)),
  ('kiln','inside',(272,48,48,96)),('sign','inside',(224,256,32,32)),('lamp','inside',(208,64,32,48)),
  ('key','resources',(96,48,16,16)),('item','resources',(96,16,16,16)),('fossil','resources',(16,16,32,32)),
  ('ruin_gate','dungeon',(32,112,32,48)),('spikes','dungeon',(64,112,16,48)),('hole','dungeon',(0,160,32,48)),
  ('forest_tree','forest',(350,304,130,245)),('forest_tree_willow','forest',(480,144,96,112)),
 ]:add(key,sheet,box,trim=True)
 # Detailed supplied Forest_1 trees are the world defaults. Previous source
 # variants retain their own exact crop records, not aliases to replaced names.
 for key in ['tree','tree_small','tree_pine','tree_dry','tree_snow']:
  regions['alternate_'+key]=regions[key]
  metadata['alternate_'+key]=dict(metadata[key])
 for key,box in [('tree',(144,16,96,128)),('tree_small',(48,160,48,80)),
                 ('tree_pine',(256,0,80,144)),('tree_dry',(256,144,64,96)),
                 ('tree_oak',(624,0,144,144))]:add(key,'forest',box,trim=True)
 add('tree_snow','forest',(448,16,64,128),trim=True,foliage_palette=('#557f98','#eef8fc'))
 for key,sheet,box,palette in [
  ('desert_rock','rocks',(128,16,32,32),('#705238','#d6ab6d')),
  ('desert_decor','veg',(96,272,16,16),('#715b3b','#bcad68')),
  ('desert_cactus','desert',(152,16,32,64),('#485439','#909969')),
  ('desert_dead_tree','veg',(192,96,48,48),('#544533','#a28b62')),
  ('volcanic_rock','rocks',(128,16,32,32),('#1d202b','#62616f')),
  ('volcanic_crystal','rocks',(144,256,16,48),('#171c29','#777891')),
  ('volcanic_charred_tree','veg',(192,96,48,48),('#1f222d','#69616a')),
  ('volcanic_dead_shrub','veg',(192,64,48,32),('#2d2b34','#82727a')),
  ('graveyard_rock','rocks',(128,16,32,32),('#38473f','#828e7b')),
  ('graveyard_dead_tree','veg',(192,96,48,48),('#2c3830','#798171')),
  ('graveyard_weeds','veg',(96,272,16,16),('#39483a','#798469')),
 ]:add(key,sheet,box,trim=True,palette=palette,normalize=True)
 # Only two small CC0 gravestones join the otherwise user-supplied art family.
 # Source black is a colour key, not a black rectangle behind the object.
 add('graveyard_tomb','graveyard',(80,140,20,20),trim=True,colorkey=(0,0,0),palette=('#46524d','#aab5a7'),normalize=True)
 add('graveyard_tomb_square','graveyard',(140,140,20,20),trim=True,colorkey=(0,0,0),palette=('#46524d','#aab5a7'),normalize=True)
 add('grass_patch','woods',(160,32,32,32))
 add('warp','dungeon',(96,304,16,16))
 add('pressure_plate','floor',(256,16,16,16))
 add('cracked','wall',(112,96,16,16))
 add('statue','cave',(112,0,16,32),trim=True)
 for a,b in {'tree_top':'bush','plant':'bush','palm_top':'tree_oak','palm_trunk':'tree_dead','fence':'fence_wood','door_red':'door_locked','cushion':'rug','gravestone':'statue','pedestal':'statue','doll':'berry_fruit','switch':'machine_active','pokeball':'berry_fruit','ultraball':'item','berry_empty':'tree_small','berry_growing':'plant_growing','berry_full':'tree_small'}.items():alias(a,b)
 # Berry fruit state is an exact source-fruit overlay, never a state change to Tile.
 fruit=regions['berry_fruit'];base=regions['berry_full'].copy();base.alpha_composite(fruit,(max(0,(base.width-fruit.width)//2),max(0,base.height//3-fruit.height//2)))
 regions['berry_full']=base;metadata['berry_full']={'composition':['tree_small','berry_fruit'],'anchorX':base.width/2,'anchorY':0}
 for i in range(4):add('fire_'+str(i),'fire',(i*32,0,32,48),trim=False)
 boxes=[regions['fire_'+str(i)].getchannel('A').getbbox() for i in range(4)]
 fire_bounds=(min(b[0] for b in boxes),min(b[1] for b in boxes),max(b[2] for b in boxes),max(b[3] for b in boxes))
 for i in range(4):
  key='fire_'+str(i);regions[key]=regions[key].crop(fire_bounds)
  metadata[key]['operations'].append({'shared_animation_alpha_bounds':list(fire_bounds)})
  metadata[key]['anchorX']=regions[key].width/2
 animations['fire']={'fps':8,'frames':['fire_'+str(i) for i in range(4)]};alias('fire','fire_0')
 # Fixed-width shelf packing with two transparent padding pixels. No filtering bleed.
 width=1024;x=2;y=2;rowh=0;coords={}
 for key,im in regions.items():
  if x+im.width+2>width:x=2;y+=rowh+4;rowh=0
  coords[key]={'x':x,'y':y,'width':im.width,'height':im.height,'anchorX':metadata[key]['anchorX'],'anchorY':metadata[key]['anchorY']}
  x+=im.width+4;rowh=max(rowh,im.height)
 height=2**math.ceil(math.log2(y+rowh+2));atlas=Image.new('RGBA',(width,height))
 for key,im in regions.items():atlas.paste(im,(coords[key]['x'],coords[key]['y']))
 buffer=io.BytesIO();atlas.save(buffer,format='PNG',optimize=False)
 atomic_write(DEST/'world-atlas.png',buffer.getvalue())
 families={family:[key for key in regions if key.startswith(prefix)] for family,prefix in [('desert','desert_'),('volcano','volcanic_'),('graveyard','graveyard_')]}
 families['volcano'] += [key for key in regions if key.startswith('lava_bright') or key.startswith('lava_cooled')]
 atomic_write(DEST/'world-atlas.json',(json.dumps({'regions':coords,'animations':animations,'biomeFamilies':families},indent=2)+'\n').encode('utf-8'))
 provenance={'schema':2,'scope':'Local game integration from user-supplied downloads, CC-BY volcanic materials and three CC0 prop crops; no standalone asset-pack redistribution.','source_storage':'work/landscape-assets/sources, outside game project','sources':SOURCES,'regions':metadata,'animations':animations,'biomeFamilies':families,'outputs':{}}
 for n in ['world-atlas.png','world-atlas.json']:provenance['outputs'][n]={'sha256':sha((DEST/n).read_bytes()),'bytes':(DEST/n).stat().st_size}
 atomic_write(DEST/'PROVENANCE.json',(json.dumps(provenance,indent=2)+'\n').encode('utf-8'))
 credits=['LOCAL LANDSCAPE ASSETS','Original downloaded packs are preserved outside the project. This atlas is an integration for this local game, not an asset pack.','Pixel Crawler supplies the common material/object language; Woods and supplied Forest_1 supply nature; Cold Cave supplies cave details. Sevarihk supplies the secondary volcanic stone/lava material set. Two CC0 marionline gravestones and one CC0 ScratchIO cactus provide missing silhouettes. Desert, volcano and graveyard use dedicated source-derived palette families.','']
 for s in SOURCES.values():credits += [s['author']+' - '+s['file'],s['page'],s['terms'],'']
 credits += ['Preparation: exact crops, alpha trimming, listed source-sprite compositions and explicit luminance palette variants. No generative edits.','RW_TileAnimations.js was inspected as text only, is not executed or redistributed; Java animation uses atlas frame indices.','B/W player sheets retain their existing separate visual/unova provenance.']
 atomic_write(DEST/'CREDITS.txt',('\n'.join(credits)+'\n').encode('utf-8'))
 for key,member,out in [('crawler','Pixel Crawler - Free Pack/Terms.txt','LICENSE-Anokolisa.txt'),('cave','LICENSE.txt','LICENSE-Asset-Alliance.txt')]:
  atomic_write(DEST/out,archives[key].read(member))
 atomic_write(DEST/'LICENSE-marionline-CC0.txt',('Forest / Graveyard tileset by marionline\nhttps://opengameart.org/content/forest-graveyard-tileset\nPublished 2018-01-14; license verified 2026-10-07.\nCC0 1.0 Universal public-domain dedication\nhttps://creativecommons.org/publicdomain/zero/1.0/\nSource image: https://opengameart.org/sites/default/files/tileset_48.png\nTwo gravestone crops are used with transparent black and a documented gray-green palette.\n').encode('utf-8'))
 atomic_write(DEST/'LICENSE-ScratchIO-CC0.txt',('Desert Level Decorations (Pixel-Art) by ScratchIO\nhttps://opengameart.org/content/desert-level-decorations-pixel-art\nPublished 2020-11-30; license verified 2026-10-07.\nCC0 1.0 Universal public-domain dedication\nhttps://creativecommons.org/publicdomain/zero/1.0/\nSource image: https://opengameart.org/sites/default/files/desert_decorations_0.png\nOne cactus crop is used with the documented dry-vegetation palette.\n').encode('utf-8'))
 atomic_write(DEST/'LICENSE-Sevarihk-CC-BY.txt',('Stone and Lava Ground Tiles by Sevarihk\nhttps://opengameart.org/content/stone-and-lava-ground-tiles\nPublished 2023-04-14; license verified 2026-10-07.\nCreative Commons Attribution 4.0 International\nhttps://creativecommons.org/licenses/by/4.0/\nSources: lava-dark-stone_large.png and stone_terrain_addon.png from the linked author page.\nChanges: exact crops; cool basalt, bright lava and cooled lava luminance palettes; 16 toroidal animation frames from the 64px continuous lava crop.\nNo endorsement by the original author is implied. Full input hashes and per-region changes are in PROVENANCE.json.\n').encode('utf-8'))
 print(f'Prepared {len(regions)} regions, {len(animations)} animations: {width}x{height}; source archives remain outside project.')
if __name__=='__main__':main()
