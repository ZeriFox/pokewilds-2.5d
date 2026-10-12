#!/usr/bin/env python3
"""Prepare the reviewed generated panoramas with a reproducible nearest resize.

The source illustrations are preserved byte-exact. This performs no drawing,
generation, inpainting, color replacement or semantic changes to the images.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image
from deterministic_png import ENCODING, rgba_png_bytes

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / 'resources/visual/battle-scenes'
SCENES = ('beach', 'cave', 'desert', 'forest', 'graveyard', 'snow', 'volcano')


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    provenance = dict(schema=1, preparation='Nearest-neighbor resize to 768x432; full opaque continuous arenas without platforms. Original generated images and prompts retained.',
                      reference='User-provided pixel-art forest clearing', png_encoding=ENCODING, scenes={})
    outputs = {}
    for name in SCENES:
        source = DEST / 'sources' / (name + '.png')
        metadata = DEST / 'sources' / (name + '.json')
        info = json.loads(metadata.read_text(encoding='utf-8'))
        if info['tool'] != 'built-in image_gen':
            raise ValueError('Unexpected image provenance: ' + name)
        with Image.open(source) as image:
            image = image.convert('RGBA')
            if image.getchannel('A').getextrema() != (255, 255):
                raise ValueError('Arena contains uncovered transparent pixels: ' + name)
            runtime = rgba_png_bytes(image.resize((768, 432), Image.Resampling.NEAREST))
        outputs[name + '.png'] = runtime
        provenance['scenes'][name] = dict(source='sources/' + name + '.png', source_sha256=digest(source.read_bytes()),
            metadata_sha256=digest(metadata.read_bytes()), runtime=name + '.png', runtime_sha256=digest(runtime),
            tool=info['tool'], prompt=info['prompt'])
    outputs['PROVENANCE.json'] = (json.dumps(provenance, indent=2) + '\n').encode('utf-8')
    for name, content in outputs.items():
        path = DEST / name
        if args.check:
            if not path.is_file() or path.read_bytes() != content:
                raise RuntimeError('Outdated prepared arena: ' + name)
        else:
            path.write_bytes(content)
    print(('Verified' if args.check else 'Prepared') + ' seven opaque battle panoramas, source provenance and deterministic PNG bytes.')


if __name__ == '__main__':
    main()
