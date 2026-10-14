#!/usr/bin/env python3
"""Pack generated headless mounted-body cutouts; no pixels are painted by this importer."""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image
from deterministic_png import ENCODING, rgba_png_bytes

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / 'resources/visual/mounted-trainers'
SOURCE = DEST / 'sources/mounted-bodies.png'
MODES = ('ride', 'surf', 'fly')
DIRECTIONS = ('down', 'up', 'left', 'right')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    source = Image.open(SOURCE).convert('RGBA')
    packed = Image.new('RGBA', (96, 128))
    # Reviewed generation gaps; the lower rows are not evenly spaced. Equal
    # quarters would include the following right-facing neckline in row three.
    row_bands = ((0, 360), (360, 710), (710, 1015), (1015, 1448))
    crops = []
    for row, direction in enumerate(DIRECTIONS):
        for column, mode in enumerate(MODES):
            left, right = round(column * source.width / 3), round((column + 1) * source.width / 3)
            top, bottom = row_bands[row]
            crop = source.crop((left, top, right, bottom))
            crop.putalpha(crop.getchannel('A').point(lambda alpha: 255 if alpha >= 128 else 0))
            bounds = crop.getchannel('A').getbbox()
            if bounds is None:
                raise ValueError('Missing generated body: ' + mode + '/' + direction)
            crop = crop.crop(bounds)
            width = (18 if column == 0 else 14 if column == 1 else 15) if row < 2 else (15 if column < 2 else 17)
            height = 13
            scaled = crop.resize((width, height), Image.Resampling.NEAREST)
            x, y = (32 - width) // 2, 18
            packed.alpha_composite(scaled, (column * 32 + x, row * 32 + y))
            crops.append(dict(mode=mode, direction=direction, source=[left, top, right, bottom],
                              alpha_bounds=list(bounds), target=[x, y, width, height]))
    metadata = dict(schema=1, tool='OpenAI built-in image_gen', source_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                    prompt='PROMPT.txt', sheet='bodies.png', frame_size=32, directions=list(DIRECTIONS),
                    modes=list(MODES), png_encoding=ENCODING, crops=crops,
                    composition='Generated seated/folded bodies beneath resolved idle identity head; runtime material recoloring and outfit sampling. No walking frames or trainer leg crops.')
    outputs = {'bodies.png': rgba_png_bytes(packed), 'PROVENANCE.json': (json.dumps(metadata, indent=2) + '\n').encode('utf-8')}
    for name, data in outputs.items():
        path = DEST / name
        if args.check:
            if not path.exists() or path.read_bytes() != data:
                raise RuntimeError('Outdated mounted trainer asset: ' + name)
        else:
            path.write_bytes(data)
    print('Mounted trainer body assets PASS: 3 poses x 4 directions, deterministic alpha crops')


if __name__ == '__main__':
    main()
