#!/usr/bin/env python3
"""Rebuild the curated presentation atlas from pinned reference sheets.

Does not download, change simulation data or silently replace missing sprites.
Unchanged mechanical assets retain the audited landscape atlas and its credits.
"""
from pathlib import Path
import hashlib
import json
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art-source/reference'
OUTPUT = ROOT / 'resources/visual/stardew'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare():
    manifest = json.loads((SOURCE / 'manifest.json').read_text())
    images = {}
    for name, info in manifest.items():
        path = SOURCE / (name + '.png')
        if info.get('status') != 'downloaded' or digest(path) != info['sha256']:
            raise ValueError('Reference source missing or changed: ' + name)
        images[name] = Image.open(path).convert('RGBA')
        if images[name].size != (info['width'], info['height']):
            raise ValueError('Unexpected sheet dimensions: ' + name)

    base_dir = ROOT / 'resources/visual/landscape'
    base_image = Image.open(base_dir / 'world-atlas.png').convert('RGBA')
    original = json.loads((base_dir / 'world-atlas.json').read_text())
    entries = original['regions']
    materials, metadata, provenance = {}, {}, {}
    for key, entry in entries.items():
        x, y, w, h = (entry[k] for k in ('x', 'y', 'width', 'height'))
        materials[key] = base_image.crop((x, y, x+w, y+h))
        metadata[key] = {k: v for k, v in entry.items() if k not in ('x', 'y', 'width', 'height', 'texture')}
        provenance[key] = {'retainedFrom': 'visual/landscape/world-atlas.json', 'region': key}
    animations = dict(original.get('animations', {}))

    def add(key, source, box, *, prop=False, size=None, tint=None, sample=None):
        x, y, w, h = box
        if min(x, y) < 0 or min(w, h) <= 0 or x+w > images[source].width or y+h > images[source].height:
            raise ValueError('Out of bounds crop: ' + key)
        art = images[source].crop((x, y, x+w, y+h))
        if source == 'party':
            pixels = list(art.getdata())
            art.putdata([(r,g,b,0 if (r,g,b)==(0,128,0) else a) for r,g,b,a in pixels])
        transformations = []
        if tint:
            alpha = art.getchannel('A')
            gray = ImageOps.grayscale(art)
            art = ImageOps.colorize(gray, tint[0], tint[1]).convert('RGBA')
            art.putalpha(alpha)
            transformations.append({'grayscaleColorMap': list(tint)})
        if prop:
            bounds = art.getchannel('A').getbbox()
            if bounds is None:
                raise ValueError('Empty prop: ' + key)
            art = art.crop(bounds)
            transformations.append({'alphaTrim': list(bounds)})
        elif art.getchannel('A').getextrema() != (255,255) and source != 'party':
            raise ValueError('Surface crop contains holes: ' + key)
        materials[key] = art
        # Every replacement explicitly defines sampling. Props always retain
        # their WHOLE trimmed region, including rectangular/high-resolution PNGs.
        metadata[key] = {'sampleWidth': sample[0] if sample else art.width,
                         'sampleHeight': sample[1] if sample else art.height}
        if prop:
            metadata[key].update(anchorX=art.width/2, anchorY=0)
        if size:
            metadata[key].update(worldWidth=size[0], worldHeight=size[1])
        animations.pop(key, None)
        provenance[key] = {'source': source, 'page': manifest[source]['page'],
                           'sourceSha256': manifest[source]['sha256'], 'crop': list(box),
                           'transformations': transformations, 'rights': manifest[source]['rights']}

    def alias(new, old):
        materials[new] = materials[old].copy()
        metadata[new] = dict(metadata[old])
        provenance[new] = {'aliasOf': old}
        animations.pop(new, None)

    # Entire opaque terrain cells: never crop an edge or object out of a map sheet.
    add('grass', 'spring', (48,208,16,16))
    add('grass_light', 'spring', (48,208,16,16), tint=('#315631','#94b756'))
    add('grass_dry', 'spring', (48,208,16,16), tint=('#76633a','#bea965'))
    add('path', 'spring', (224,368,16,16))
    alias('dirt','path')
    add('mountain', 'mines', (128,144,32,32), tint=('#4e4b41','#95856a'), sample=(16,16))
    add('cliff', 'mines', (144,80,32,32), tint=('#454035','#a18d65'))
    add('cliff_dark', 'mines', (656,80,32,32), tint=('#20202c','#686070'))
    add('cave_floor', 'mines', (640,144,32,32), sample=(16,16))
    add('snow', 'winter', (48,208,16,16))
    add('cliff_snow', 'mines', (400,80,32,32))
    add('desert_ground', 'desert', (16,16,16,16))
    add('desert_path', 'desert', (16,64,16,16))
    add('desert_cliff', 'desert', (128,48,32,48))
    alias('desert_cliff_cap','desert_ground')
    alias('sand','desert_path')
    alias('sand_shore','desert_path')
    add('volcanic_basalt', 'volcano', (16,0,16,16))
    add('volcanic_obsidian', 'volcano', (16,0,16,16), tint=('#171722','#33303e'))
    add('volcanic_ash', 'mines', (640,144,32,32), tint=('#514e57','#86808a'), sample=(16,16))
    add('volcanic_cracked', 'volcano', (32,0,16,16))
    add('volcanic_cliff', 'volcano', (64,160,16,32))
    alias('basalt','volcanic_basalt')
    alias('volcano','volcanic_basalt')
    alias('volcano_path','volcanic_ash')
    for i in range(4):
        add('stardew_lava_'+str(i), 'volcano', (i*32,320,32,32))
    alias('lava_bright','stardew_lava_0')
    animations['lava_bright'] = {'frames': ['stardew_lava_'+str(i) for i in range(4)], 'fps': 4}
    add('lava_cooled', 'volcano', (0,320,32,32), tint=('#391924','#b45b27'))
    add('graveyard_ground', 'spring', (48,208,16,16), tint=('#48534c','#818775'))
    add('graveyard_path', 'desert', (16,64,16,16), tint=('#656d62','#929780'))
    add('graveyard_cliff', 'mines', (144,80,32,32), tint=('#3e4a45','#828a77'))

    # Crop distinct props from isolated sheet islands, trim alpha, keep proportions.
    add('tree','spring',(0,0,48,112),prop=True,size=(24,48))
    add('tree_pine','spring',(160,0,48,112),prop=True,size=(22,46))
    add('tree_snow','winter',(160,0,48,112),prop=True,size=(22,46))
    add('tree_dry','desert',(0,112,48,48),prop=True,size=(27,34))
    add('bush','spring',(208,0,48,48),prop=True,size=(19,19))
    add('rock','spring',(528,32,32,32),prop=True,size=(18,19))
    add('desert_rock','spring',(528,32,32,32),prop=True,size=(18,19),tint=('#705035','#cca071'))
    add('graveyard_rock','spring',(528,32,32,32),prop=True,size=(18,19),tint=('#44524d','#858d77'))
    add('rock_ice','spring',(528,32,32,32),prop=True,size=(18,19),tint=('#326b9e','#d0f9ff'))
    add('volcanic_rock','volcano',(32,32,32,32),prop=True,size=(19,24))
    alias('cave_stalagmite','volcanic_rock')
    add('desert_cactus','desert',(48,112,32,48),prop=True,size=(18,27))
    alias('cactus','desert_cactus')
    for key in ('forest_tree','tree_small'):
        alias(key,'tree')
    for key in ('forest_bush','deep_forest_bush'):
        alias(key,'bush')
    add('deep_forest_tree','spring',(96,0,64,112),prop=True,size=(35,51))

    # House materials and full furniture silhouettes, not artificial cube skins.
    add('wood_floor','flooring',(16,16,16,16))
    add('tile_floor','flooring',(208,16,16,16))
    add('stone_floor','flooring',(80,16,16,16))
    add('wall','walls_floors',(0,0,16,48))
    add('wall_wood','walls_floors',(32,0,16,48))
    alias('wall_cap','wood_floor')
    add('chair','furniture',(0,0,16,32),prop=True,size=(12,23))
    add('couch','furniture',(0,208,48,32),prop=True,size=(29,19))
    add('desk','furniture',(0,352,32,32),prop=True,size=(22,22))
    add('table','furniture',(224,400,80,48),prop=True,size=(32,20))
    add('bed','furniture',(512,320,32,48),prop=True,size=(20,30))
    add('shelf','furniture',(592,0,32,32),prop=True,size=(20,26))
    add('wardrobe','furniture',(512,368,32,32),prop=True,size=(18,27))

    # This sheet is Pokemon Menu (party), not the FRLG bag. Use its reusable
    # frames for both interfaces without baking labels, HP values or Pokemon.
    add('ui_frlg_background','party',(22,12,220,145))
    add('ui_frlg_row','party',(347,16,140,19))
    add('ui_frlg_leader','party',(261,32,73,46))

    # Shelf packing with duplicated padding prevents neighbouring images leaking.
    atlas_width = 2048
    x = y = 2
    row_height = 0
    layout = {}
    for key in sorted(materials):
        art = materials[key]
        if art.width+4 > atlas_width:
            raise ValueError('Region too wide: ' + key)
        if x+art.width+2 > atlas_width:
            x=2; y+=row_height+4; row_height=0
        layout[key]=(x,y,art.width,art.height)
        x+=art.width+4; row_height=max(row_height,art.height)
    height=y+row_height+2
    if height > 4096:
        raise ValueError('Atlas exceeds supported safety bound')
    canvas=Image.new('RGBA',(atlas_width,height))
    regions={}
    for key, art in materials.items():
        x,y,w,h=layout[key]
        canvas.paste(art,(x,y))
        canvas.paste(art.crop((0,0,1,h)).resize((2,h)),(x-2,y))
        canvas.paste(art.crop((w-1,0,w,h)).resize((2,h)),(x+w,y))
        canvas.paste(art.crop((0,0,w,1)).resize((w,2)),(x,y-2))
        canvas.paste(art.crop((0,h-1,w,h)).resize((w,2)),(x,y+h))
        regions[key]={'x':x,'y':y,'width':w,'height':h,**metadata[key]}
    for name, sequence in animations.items():
        if any(frame not in regions for frame in sequence['frames']):
            raise ValueError('Broken animation: ' + name)
    OUTPUT.mkdir(parents=True,exist_ok=True)
    canvas.save(OUTPUT/'world-atlas.png',optimize=True)
    (OUTPUT/'world-atlas.json').write_text(json.dumps({'regions':regions,'animations':animations},indent=2)+'\n')
    (OUTPUT/'PROVENANCE.json').write_text(json.dumps({'version':1,'atlasSha256':digest(OUTPUT/'world-atlas.png'),
        'baseAtlasSha256':digest(base_dir/'world-atlas.png'),'regions':provenance},indent=2)+'\n')
    (OUTPUT/'CREDITS.txt').write_text('Selected environment tiles: Stardew Valley / ConcernedApe.\n'
        'Menu frames: Pokemon FireRed / LeafGreen, Nintendo / Game Freak; sheet credit Redzagoon.\n'
        'Sources: The Spriters Resource pages and exact hashes in PROVENANCE.json and art-source/reference/manifest.json.\n'
        'Adaptations: explicit crops, alpha-only trimming, palette mapping and atlas packing.\n'
        'These materials are not original project artwork, CC0 or MIT. All original rights remain with their holders.\n'
        'Retained mechanical assets: see visual/landscape/CREDITS.txt and its provenance.\n')
    print('REFERENCE ATLAS:',len(regions),'regions;',sum('source' in p for p in provenance.values()),'source crops;',canvas.size)
    return regions

if __name__=='__main__':
    prepare()
