#!/usr/bin/env python3
"""Read-only Java asset bridge. No downloads, Java rewrites or save conversion.

Output is a generated, disposable Unity Resources subtree. The original art,
including signed PMD origins and per-frame durations, is retained byte-for-byte.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import tempfile
from PIL import Image

DEFAULT_SPECIES = ('pikachu', 'machop', 'milotic')
REQUIRED = ('grass_light', 'path', 'cliff', 'mountain', 'snow', 'water', 'lava_bright',
            'desert_ground', 'desert_cliff', 'volcanic_basalt', 'volcanic_cliff',
            'graveyard_ground', 'graveyard_cliff', 'tree', 'tree_snow',
            'desert_rock', 'volcanic_rock', 'graveyard_rock',
            'trainer_male_walk_down_1')


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inside(root: Path, name: str) -> Path:
    if not isinstance(name, str) or '\\' in name or ':' in name:
        raise ValueError('Invalid internal asset path')
    relative = Path(name)
    if relative.is_absolute() or '..' in relative.parts:
        raise ValueError('Asset path leaves the repository: ' + name)
    path = root / relative
    if path.is_symlink() or not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Unsafe asset path: ' + name)
    if not path.is_file():
        raise ValueError('Missing source file: ' + name)
    return path


def number(value, *, integer=False, positive=False):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError('Expected a finite numeric value')
    if integer and int(value) != value or positive and value <= 0:
        raise ValueError('Invalid numeric extent/timing')
    return int(value) if integer else float(value)


def prepare(root: Path, species=DEFAULT_SPECIES, *, check=False):
    root = root.resolve()
    output = root / 'unity/Assets/PokeWilds/Resources/Imported'
    # Refuse symlinked parents and protect an existing hand-written directory.
    for p in (root/'unity', *list(output.parents)[:-1], output):
        if p.is_symlink():
            raise ValueError('Refusing a symlinked output path')
    if output.exists() and not (output/'import-receipt.json').is_file():
        raise ValueError('Existing Imported directory has no ownership receipt')
    resources = root/'resources'
    build = root/'build/unity-import'
    build.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='stage-', dir=build) as temp:
        stage = Path(temp)
        texture_info, hashes, regions, animations = {}, {}, {}, []
        def texture(path):
            key = path.relative_to(root).as_posix()
            if key not in texture_info:
                with Image.open(path) as im:
                    im.verify()
                with Image.open(path) as im:
                    width, height = im.size
                if max(width, height) > 8192:
                    raise ValueError('Texture exceeds bridge import limit: ' + key)
                digest = sha(path)
                target = 'textures/' + digest[:24]
                dest = stage/(target+'.png'); dest.parent.mkdir(exist_ok=True)
                shutil.copyfile(path, dest)
                hashes[key] = digest
                texture_info[key] = (target, width, height)
            return texture_info[key]
        def frame(path, x, y, width, height, ax, ay, **layout):
            target, tw, th = texture(path)
            x, y, width, height = [number(v, integer=True) for v in (x, y, width, height)]
            if min(x, y) < 0 or min(width, height) <= 0 or x+width > tw or y+height > th:
                raise ValueError('Source rectangle out of bounds: ' + str(path))
            return dict(texture=target, x=x, y=y, width=width, height=height,
                        anchorX=number(ax), anchorY=number(ay), **layout)
        for folder, trainers in (('stardew', False), ('unova', True)):
            atlas_path = inside(resources, f'visual/{folder}/world-atlas.json')
            data = json.loads(atlas_path.read_text(encoding='utf-8'))
            hashes[atlas_path.relative_to(root).as_posix()] = sha(atlas_path)
            if data.get('schemaVersion', 1) not in (1, 2):
                raise ValueError('Unsupported source material schema')
            if data.get('schemaVersion') == 2 and data.get('coordinateContract', {}).get('anchorUnit') != 'source-pixels':
                raise ValueError('Built-in source anchors must be in pixels')
            entries = data.get('regions', data)
            for key, r in sorted(entries.items()):
                if trainers and not key.startswith('trainer_'):
                    continue
                path = inside(resources, r.get('texture', data.get('texture', f'visual/{folder}/world-atlas.png')))
                w, h = r['width'], r['height']
                sw = number(r.get('sampleWidth', min(16, w)), integer=True, positive=True)
                sh = number(r.get('sampleHeight', min(16, h)), integer=True, positive=True)
                gw, gh = w, h
                if 'sampleWidth' not in r and 'sampleHeight' not in r:
                    gw, gh = max(1, w//16)*sw, max(1, h//16)*sh
                if gw % sw or gh % sh or gw*gh//(sw*sh) > 1048576:
                    raise ValueError('Invalid sampling: '+key)
                layout = {k: number(r[k], positive=k in ('worldWidth','worldHeight'))/16.0
                          for k in ('worldWidth','worldHeight','offsetX','offsetY','elevation') if k in r}
                f = frame(path, r['x'], r['y'], w, h, r.get('anchorX', w*.5), r.get('anchorY', 0), **layout)
                regions[key] = dict(name=key, frame=f, sampleWidth=sw, sampleHeight=sh, gridWidth=gw, gridHeight=gh)
            if not trainers:
                for key, a in sorted(data.get('animations', {}).items()):
                    if not a['frames'] or any(f not in regions for f in a['frames']):
                        raise ValueError('Invalid terrain animation: '+key)
                    animations.append(dict(name=key, frames=a['frames'], fps=number(a['fps'], positive=True)))
        for name in REQUIRED:
            if name not in regions:
                raise ValueError('Demo requires material: '+name)
        catalog_path = inside(resources, 'visual/pmd/catalog.json')
        source_catalog = json.loads(catalog_path.read_text(encoding='utf-8'))['species']
        hashes[catalog_path.relative_to(root).as_posix()] = sha(catalog_path)
        selected = sorted(source_catalog) if species is None else sorted(set(species))
        pokemon = []
        for name in selected:
            entry = source_catalog.get(name)
            if not entry: raise ValueError('Unknown PMD identity: '+name)
            meta_path = inside(resources, 'visual/pmd/sprite/'+entry['sourcePath']+'/metadata.json')
            meta = json.loads(meta_path.read_text(encoding='utf-8'))
            hashes[meta_path.relative_to(root).as_posix()] = sha(meta_path)
            clips = []
            for clip_name in ('Idle', 'Walk'):
                if clip_name not in meta: continue
                c = meta[clip_name]
                durations = [number(v, integer=True, positive=True) for v in c['durations']]
                rows = number(c['rows'], integer=True)
                if rows not in (1, 8) or not durations or len(c['frames']) != rows*len(durations):
                    raise ValueError('Invalid PMD timing/grid: '+name)
                path = inside(resources, 'visual/pmd/'+c['file'])
                frames = []
                for f in c['frames']:
                    if len(f) != 6: raise ValueError('Unexpected PMD frame layout: '+name)
                    # Negative anchors encode authored jumps. Do not clamp or re-center.
                    frames.append(frame(path, *f))
                clips.append(dict(name=clip_name, rows=rows, durations=durations, frames=frames))
            if not any(c['name']=='Idle' for c in clips):
                raise ValueError('Initial bridge needs an Idle clip: '+name)
            pokemon.append(dict(name=name, clips=clips))
        try:
            commit = subprocess.check_output(['git','rev-parse','HEAD'], cwd=root, stderr=subprocess.DEVNULL, text=True).strip()
        except (OSError, subprocess.CalledProcessError):
            commit = 'unversioned-checkout'
        payload = dict(schemaVersion=1, sourceCommit=commit, regions=[regions[k] for k in sorted(regions)],
                       animations=animations, pokemon=pokemon)
        (stage/'catalog.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        notices = stage/'notices'; notices.mkdir()
        for rel in ('resources/visual/stardew/CREDITS.txt','resources/visual/stardew/PROVENANCE.json',
                    'resources/visual/unova/CREDITS.txt','docs/ASSET-RIGHTS.md'):
            p = root/rel
            if p.is_file(): shutil.copyfile(p, notices/(rel.replace('/','_')))
        (notices/'PMD.txt').write_text('PMD artwork: original repository resources/visual/pmd, PMD-PROVENANCE.json and licenses/PMD-*.\n'
            'Only a format bridge; no new rights or ownership are granted. Original source art is unchanged.\n', encoding='utf-8')
        # Preserve attribution in the self-contained bridge output, not only URLs.
        for p in sorted((root/'licenses').glob('PMD-*')):
            if p.is_file() and p.suffix.lower() in ('.txt','.md','.json'):
                shutil.copyfile(p, notices/p.name)
        receipt = dict(schemaVersion=1, sourceCommit=commit, sourceSha256=hashes,
            files={p.relative_to(stage).as_posix():sha(p) for p in sorted(stage.rglob('*')) if p.is_file()},
            species=selected, regionCount=len(regions), editorVerified=False,
            scope='M0 bridge / fixture only; no battle rules, procedural-world equivalence or save migration')
        (stage/'import-receipt.json').write_text(json.dumps(receipt, indent=2, sort_keys=True)+'\n', encoding='utf-8')
        generated = {p.relative_to(stage) for p in stage.rglob('*') if p.is_file()}
        if check:
            for rel in generated:
                if not (output/rel).is_file() or sha(output/rel) != sha(stage/rel):
                    raise ValueError('Generated bridge differs: '+str(rel))
            extras = {p.relative_to(output) for p in output.rglob('*') if p.is_file() and p.suffix!='.meta'} - generated
            if extras: raise ValueError('Stale bridge output: '+str(extras))
        else:
            output.mkdir(parents=True, exist_ok=True)
            # Preserve Unity GUID .meta files on deterministic re-import.
            for p in sorted(output.rglob('*'), reverse=True):
                if p.is_symlink(): raise ValueError('Refusing symlink inside generated output')
                if p.is_file() and p.suffix!='.meta' and p.relative_to(output) not in generated:
                    p.unlink(); p.with_name(p.name+'.meta').unlink(missing_ok=True)
            for rel in generated:
                dest=output/rel; dest.parent.mkdir(parents=True, exist_ok=True)
                if not dest.exists() or sha(dest)!=sha(stage/rel): shutil.copyfile(stage/rel,dest)
        print(f'UNITY BRIDGE: {len(regions)} regions, {len(pokemon)} Pokemon, {len(texture_info)} source textures; check={check}')
        return receipt

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--species', nargs='+', default=list(DEFAULT_SPECIES))
    parser.add_argument('--all-pokemon', action='store_true', help='Import all catalog Idle/Walk clips; intentionally not the battle rules.')
    parser.add_argument('--check', action='store_true')
    args=parser.parse_args()
    prepare(args.root, None if args.all_pokemon else args.species, check=args.check)
