#!/usr/bin/env python3
"""Crop and anchor generated clothing cutouts; no artwork is drawn by this packer."""
from pathlib import Path
import hashlib
import json
from PIL import Image
from deterministic_png import ENCODING, rgba_png_bytes
import argparse

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / 'resources/visual/wardrobe'
SOURCE = DEST / 'sources/parts.png'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    source = Image.open(SOURCE).convert('RGBA')
    output = Image.new('RGBA', (384, 128))
    dimensions = [(16, 11, 8, 17), (12, 12, 10, 16), (14, 5, 9, 26),
                  (22, 10, 5, 2), (22, 16, 5, 5), (22, 18, 5, 5)]
    # Reviewed source bands: the generated atlas has nonuniform vertical gaps.
    # Equal sixths pick up neighboring items and distort their alpha bounds.
    row_bands=[(48,200),(224,384),(402,539),(554,707),(721,879),(887,1086)]
    entries = []
    for row in range(6):
        for variant in range(2):
            component = row * 2 + variant
            for direction in range(4):
                column = variant * 4 + direction
                left, right = round(column * source.width / 8), round((column + 1) * source.width / 8)
                top, bottom = row_bands[row]
                crop = source.crop((left, top, right, bottom))
                # Ignore the almost-transparent generation fringe when finding
                # the silhouette; it otherwise shrinks real shoes inside a tall cell.
                crop.putalpha(crop.getchannel('A').point(lambda a: 255 if a >= 128 else 0))
                bounds = crop.getchannel('A').getbbox()
                if bounds is None:
                    raise ValueError(f'Missing component {row}/{column}')
                crop = crop.crop(bounds)
                width, height, x, y = dimensions[row]
                # Backpacks sit behind the shoulders. Front views show their
                # generated shoulder straps, cropped above the pack body.
                if row == 1 and direction == 0:
                    crop = crop.crop((0, 0, crop.width, max(1, round(crop.height * .34))))
                    width, height, x, y = 12, 5, 10, 17
                if row == 2 and direction >= 2:
                    width, x = 11, 10
                if row == 3:
                    # Keep the crown's volume, with the brim anchored above the face.
                    width, height, x, y = (18, 13, 7, 2) if variant == 0 else (16, 15, 8, 1)
                    if direction >= 2:
                        width, x = (21, 5) if variant == 0 else (17, 7)
                if row == 1 and direction >= 2:
                    width, x = 7, (19 if direction == 2 else 6)
                scaled = crop.resize((width, height), Image.Resampling.NEAREST)
                output.alpha_composite(scaled, (component * 32 + x, direction * 32 + y))
                entries.append(dict(component=component, direction=direction, source=[left, top, right, bottom],
                                    alpha_bounds=list(bounds), target=[x, y, width, height]))
    png=rgba_png_bytes(output)
    manifest = dict(tool='OpenAI built-in image_gen', source_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                    prompt='PROMPT.txt', sheet='parts.png', frame_size=32, directions=['down','up','left','right'],
                    png_encoding=ENCODING,components=['jacket','raincoat','daypack','hiking_pack','sneakers','boots','cap','beanie',
                                'spiky_hair','side_part','ponytail','bob'], crops=entries)
    metadata=(json.dumps(manifest, indent=2)+'\n').encode('utf-8')
    for name,data in [('parts.png',png),('parts.json',metadata)]:
        path=DEST/name
        if args.check:
            if not path.exists() or path.read_bytes()!=data:raise RuntimeError('Outdated wardrobe output: '+name)
        else:path.write_bytes(data)
    print('Packed 48 generated clothing/hair views into', DEST / 'parts.png')


if __name__ == '__main__':
    main()
