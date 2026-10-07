#!/usr/bin/env python3
"""Restore three missing legacy Tile paths from the unchanged, pinned PMD species.

The modern renderer still uses the original full-resolution PMD animations.
These complete 16px miniatures only satisfy the classic Tile constructor contract.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PINNED = {
    'raikou': ('0243', '76f1cb3ed29f46d70eb32ac1d7f00871e41a42f76efdb4ffa3cdd7bf09ef9806', [17, 1, 23, 31]),
    'entei': ('0244', 'ad961a2b960bedbf2a608964e75f1fb7ce03eb3c2e6498984dca6297115713e6', [10, 5, 29, 31]),
    'suicune': ('0245', '3ff451f131dd998aa70b918d5612062a1c2f9b5c874dd92d3ee88441b39355a2', [9, 2, 30, 31]),
}


def prepare(output=None):
    output = output or ROOT / 'resources/pokemon'
    output.mkdir(parents=True, exist_ok=True)
    provenance = {'version': 1, 'sourceProvenance': 'PMD-PROVENANCE.json',
                  'rights': 'Existing PMDCollab credits and rights remain applicable; no new rights grant.',
                  'modernRendering': 'Original PMD sheets and metadata remain unchanged.', 'files': {}}
    for species, (dex, expected, crop) in PINNED.items():
        source = ROOT / 'resources/visual/pmd/sprite' / dex / 'Idle-Anim.png'
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if digest != expected:
            raise ValueError('PMD source changed: ' + species)
        image = Image.open(source).convert('RGBA')
        x, y, w, h = crop
        art = image.crop((x, y, x + w, y + h))
        if art.getchannel('A').getbbox() is None:
            raise ValueError('Empty PMD pose: ' + species)
        scale = 16 / max(w, h)
        resized = (max(1, round(w * scale)), max(1, round(h * scale)))
        art = art.resize(resized, Image.Resampling.NEAREST)
        miniature = Image.new('RGBA', (16, 16))
        miniature.paste(art, ((16 - art.width) // 2, 16 - art.height))
        destination = output / (species + '_overw1.png')
        miniature.save(destination, optimize=True)
        provenance['files'][destination.name] = {
            'source': str(source.relative_to(ROOT)).replace('\\', '/'), 'sourceSha256': digest,
            'pose': 'Idle, down, frame 0', 'crop': crop, 'scaledTo': list(resized),
            'filter': 'nearest', 'canvas': [16, 16], 'anchor': 'bottom center',
            'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()}
    (output / 'PMD-COMPATIBILITY.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('PMD compatibility: 3 species-specific full silhouettes; original PMD assets unchanged')
    return provenance


if __name__ == '__main__':
    prepare()
