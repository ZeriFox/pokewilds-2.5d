#!/usr/bin/env python3
"""Rebuild the curated presentation atlas from pinned reference sheets.

Does not download, change simulation data or silently replace missing sprites.
Unchanged mechanical assets retain the audited landscape atlas and its credits.
"""
from pathlib import Path
from collections import deque
import hashlib
import json
from PIL import Image, ImageOps
from deterministic_png import ENCODING, save_rgba_png

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art-source/reference'
COMPATIBILITY = ROOT / 'art-source/compatibility'
OUTPUT = ROOT / 'resources/visual/stardew'
CONTRACT = {
    'anchorUnit': 'source-pixels',
    'crop': 'source pixels; x/y from top-left; width/height positive',
    'sample': 'source pixels; exact divisors; whole canvas for objects and UI',
    'anchor': 'source pixels from left/bottom; converted by BwAssets.pixelAnchor',
    'worldSize': 'world units; independent of source resolution',
    'offset': 'world units; applied after anchor conversion',
    'padding': '2 pixels of edge extrusion, including corners; nearest filtering',
    'legacy': 'unversioned built-in landscape anchors remain pixels; custom anchors remain normalized',
}


def isolated_component(image, seed):
    """Select one explicitly identified sheet island, without changing its origin.

    This is not a color key: all RGBA values of the chosen connected silhouette
    remain exact. It removes separate sheet decorations accidentally in its cell.
    """
    x, y = seed
    if not (0 <= x < image.width and 0 <= y < image.height) or not image.getpixel(seed)[3]:
        raise ValueError('Component seed must identify visible artwork')
    pixels = image.load()
    selected = set()
    todo = deque([seed])
    while todo:
        x, y = todo.popleft()
        if (x, y) in selected or not (0 <= x < image.width and 0 <= y < image.height):
            continue
        if not pixels[x, y][3]:
            continue
        selected.add((x, y))
        todo.extend((x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy)
    result = Image.new('RGBA', image.size)
    target = result.load()
    for pixel in selected:
        target[pixel] = pixels[pixel]
    return result


def extruded(art, padding=2):
    """Duplicate all edge pixels, including corners, without resampling art."""
    result = Image.new('RGBA', (art.width + padding * 2, art.height + padding * 2))
    result.paste(art, (padding, padding))
    for y in range(result.height):
        for x in range(result.width):
            if x < padding or x >= art.width + padding or y < padding or y >= art.height + padding:
                result.putpixel((x, y), art.getpixel((max(0, min(art.width - 1, x - padding)),
                                                     max(0, min(art.height - 1, y - padding)))))
    return result


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_text(path, value):
    path.write_text(value, encoding='utf-8', newline='\n')


def prepare(output=OUTPUT):
    manifest = json.loads((SOURCE / 'manifest.json').read_text())
    images = {}
    for name, info in manifest.items():
        path = SOURCE / (name + '.png')
        if info.get('status') != 'downloaded' or digest(path) != info['sha256']:
            raise ValueError('Reference source missing or changed: ' + name)
        images[name] = Image.open(path).convert('RGBA')
        if images[name].size != (info['width'], info['height']):
            raise ValueError('Unexpected sheet dimensions: ' + name)
    retained_sources = json.loads((COMPATIBILITY / 'manifest.json').read_text(encoding='utf-8'))
    for name, info in retained_sources.items():
        path = COMPATIBILITY / info['file']
        if digest(path) != info['sha256']:
            raise ValueError('Retained source changed: ' + name)
        images[name] = Image.open(path).convert('RGBA')
        if images[name].size != (info['width'], info['height']):
            raise ValueError('Unexpected retained source dimensions: ' + name)
        manifest[name] = info

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

    def add(key, source, box, *, prop=False, size=None, tint=None, sample=None, component=None, anchor=None):
        x, y, w, h = box
        if min(x, y) < 0 or min(w, h) <= 0 or x+w > images[source].width or y+h > images[source].height:
            raise ValueError('Out of bounds crop: ' + key)
        art = images[source].crop((x, y, x+w, y+h))
        transformations = []
        if component is not None:
            art = isolated_component(art, component)
            transformations.append({'connectedSheetIsland': {'seed': list(component), 'connectivity': 8}})
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
            # Preserve the original source canvas/origin. Per-frame alpha trimming
            # previously changed both size and contact point unpredictably.
            transformations.append({'preserveCanvas': [w, h], 'visibleBounds': list(bounds)})
        elif art.getchannel('A').getextrema() != (255,255) and source != 'party':
            raise ValueError('Surface crop contains holes: ' + key)
        materials[key] = art
        # Every replacement explicitly defines sampling. Props always retain
        # their WHOLE trimmed region, including rectangular/high-resolution PNGs.
        metadata[key] = {'sampleWidth': sample[0] if sample else art.width,
                         'sampleHeight': sample[1] if sample else art.height,
                         'kind': 'object' if prop else 'ui' if source == 'party' else 'surface',
                         'anchorUnits': 'pixels', 'sourceCanvas': [w, h]}
        if any(value <= 0 or extent % value for value, extent in
               zip((metadata[key]['sampleWidth'], metadata[key]['sampleHeight']), art.size)):
            raise ValueError('Sampling must divide the full region: ' + key)
        if prop:
            metadata[key].update(anchorX=anchor[0] if anchor else w/2, anchorY=anchor[1] if anchor else h-bounds[3])
        if size:
            scale = min(size[0]/w, size[1]/h)
            metadata[key].update(worldWidth=w*scale, worldHeight=h*scale)
        animations.pop(key, None)
        provenance[key] = {'source': source, 'page': manifest[source]['page'],
                           'sourceSha256': manifest[source]['sha256'], 'crop': list(box),
                           'transformations': transformations, 'rights': manifest[source]['rights']}
        if source in retained_sources:
            provenance[key]['retainedCompatibility'] = retained_sources[source]

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
        add('stardew_lava_cooled_'+str(i), 'volcano', (i*32,320,32,32), tint=('#391924','#b45b27'))
    alias('lava_bright','stardew_lava_0')
    animations['lava_bright'] = {'frames': ['stardew_lava_'+str(i) for i in range(4)], 'fps': 4}
    alias('lava_cooled','stardew_lava_cooled_0')
    animations['lava_cooled'] = {'frames': ['stardew_lava_cooled_'+str(i) for i in range(4)], 'fps': 4}
    add('graveyard_ground', 'spring', (48,208,16,16), tint=('#48534c','#818775'))
    add('graveyard_path', 'desert', (16,64,16,16), tint=('#656d62','#929780'))
    add('graveyard_cliff', 'mines', (144,80,32,32), tint=('#3e4a45','#828a77'))

    # Full isolated props: exclude adjacent terrain/props, preserve source canvas.
    add('tree','spring',(48,0,48,96),prop=True,size=(24,48),component=(24,48))
    add('tree_pine','spring',(160,0,48,96),prop=True,size=(22,46),component=(24,48))
    add('tree_snow','winter',(160,0,48,96),prop=True,size=(22,46),component=(24,48))
    add('tree_dry','desert',(0,112,48,80),prop=True,size=(27,45),component=(24,48))
    add('bush','spring',(208,0,48,48),prop=True,size=(19,19))
    add('rock','spring',(528,32,32,32),prop=True,size=(18,19))
    add('desert_rock','spring',(528,32,32,32),prop=True,size=(18,19),tint=('#705035','#cca071'))
    add('graveyard_rock','spring',(528,32,32,32),prop=True,size=(18,19),tint=('#44524d','#858d77'))
    add('rock_ice','spring',(528,32,32,32),prop=True,size=(18,19),tint=('#326b9e','#d0f9ff'))
    add('volcanic_rock','volcano',(48,32,16,32),prop=True,size=(14,28))
    alias('cave_stalagmite','volcanic_rock')
    add('desert_cactus','desert',(64,112,16,48),prop=True,size=(12,30))
    alias('cactus','desert_cactus')
    for key in ('forest_tree','tree_small'):
        alias(key,'tree')
    for key in ('forest_bush','deep_forest_bush'):
        alias(key,'bush')
    add('deep_forest_tree','spring',(96,0,64,112),prop=True,size=(35,51),component=(32,48))

    # House materials and full furniture silhouettes, not artificial cube skins.
    add('wood_floor','flooring',(16,16,16,16))
    add('tile_floor','flooring',(208,16,16,16))
    add('stone_floor','flooring',(80,16,16,16))
    # Each 48px wallpaper cell includes three rows of cast shadow beneath its
    # opaque 45px face (alpha 101,55,20). They are NOT part of a solid wall face.
    # Crop the actual face and baseboard; keep strict opacity validation above.
    add('wall','walls_floors',(0,0,16,45))
    add('wall_wood','walls_floors',(176,0,16,45))
    alias('wall_cap','wood_floor')
    add('chair','furniture',(0,0,16,32),prop=True,size=(12,23))
    add('couch','furniture',(0,208,48,32),prop=True,size=(29,19))
    add('desk','furniture',(0,352,32,48),prop=True,size=(22,28))
    add('table','furniture',(224,400,80,48),prop=True,size=(32,20))
    # This sheet's right-hand furniture section starts two pixels off the grid.
    # Grid-aligned x512/x592 omit the right bedpost/bookcase border.
    add('bed','furniture',(514,312,48,56),prop=True,size=(24,28))
    add('shelf','furniture',(594,0,32,32),prop=True,size=(20,26))
    # Retain the audited landscape wardrobe: the previous (512,368,32,32)
    # Stardew crop was a cut-off door, not a wardrobe.
    add('chimney','chimney',(0,0,16,16),prop=True,size=(16,16),anchor=(12,8))

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
        canvas.paste(extruded(art), (x-2,y-2))
        regions[key]={'x':x,'y':y,'width':w,'height':h,**metadata[key]}
    for name, sequence in animations.items():
        frames = sequence.get('frames', [])
        if not frames or any(frame not in regions for frame in frames):
            raise ValueError('Broken animation: ' + name)
        if sequence.get('fps', 0) <= 0 or len({materials[frame].size for frame in frames}) != 1:
            raise ValueError('Invalid animation speed or unstable canvas: ' + name)
    output.mkdir(parents=True,exist_ok=True)
    save_rgba_png(canvas, output/'world-atlas.png')
    write_text(output/'world-atlas.json', json.dumps({'schemaVersion':2, 'coordinateContract':CONTRACT,
        'regions':regions,'animations':animations},indent=2)+'\n')
    write_text(output/'PROVENANCE.json',json.dumps({'version':2,'atlasSha256':digest(output/'world-atlas.png'),
        'atlasRgbaSha256':hashlib.sha256(canvas.tobytes()).hexdigest(),
        'pngEncoding':ENCODING, 'encoderSha256':digest(ROOT/'tools/deterministic_png.py'),
        'metadataSha256':digest(output/'world-atlas.json'),
        'baseAtlasSha256':digest(base_dir/'world-atlas.png'),
        'baseMetadataSha256':digest(base_dir/'world-atlas.json'),
        'referenceManifestSha256':digest(SOURCE/'manifest.json'),
        'compatibilityManifestSha256':digest(COMPATIBILITY/'manifest.json'),
        'redistribution': 'Reference downloads are not grants of redistribution rights; see project rights review.',
        'regions':provenance},indent=2)+'\n')
    write_text(output/'CREDITS.txt','Selected environment tiles: Stardew Valley / ConcernedApe.\n'
        'Menu frames: Pokemon FireRed / LeafGreen, Nintendo / Game Freak; sheet credit Redzagoon.\n'
        'Sources: The Spriters Resource pages and exact hashes in PROVENANCE.json and art-source/reference/manifest.json.\n'
        'Adaptations: explicit crops, identified connected sheet islands, preserved canvases, palette mapping and edge-extruded atlas packing.\n'
        'These materials are not original project artwork, CC0 or MIT. All original rights remain with their holders.\n'
        'Retained mechanical assets: see visual/landscape/CREDITS.txt and its provenance.\n')
    print('REFERENCE ATLAS:',len(regions),'regions;',sum('source' in p for p in provenance.values()),'source crops;',canvas.size)
    return regions

if __name__=='__main__':
    prepare()
