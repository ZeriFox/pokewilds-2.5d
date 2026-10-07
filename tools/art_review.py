#!/usr/bin/env python3
"""Small review previews of the acquired game artwork, not runtime assets."""
from pathlib import Path
import base64
import io
import json
from PIL import Image
ROOT = Path(__file__).resolve().parent.parent
out = ROOT / 'tools/art-review'
out.mkdir(exist_ok=True)
for source in sorted((ROOT / 'resources/visual/stardew/sources').glob('*.png')):
    image = Image.open(source).convert('RGBA')
    background = Image.new('RGBA', image.size, (64, 64, 64, 255))
    background.alpha_composite(image)
    preview = background.convert('RGB')
    preview = preview.resize((image.width // 4, image.height // 4), Image.Resampling.NEAREST)
    preview = preview.quantize(colors=24, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    buffer = io.BytesIO()
    preview.save(buffer, format='PNG', optimize=True)
    text = base64.b64encode(buffer.getvalue()).decode('ascii')
    (out / (source.stem + '.b64')).write_text(text + '\n')
    print(source.stem, image.size, preview.size, len(buffer.getvalue()))
atlas = json.loads((ROOT / 'resources/visual/landscape/world-atlas.json').read_text())
lines = [name + ' ' + json.dumps(entry, separators=(',', ':')) for name, entry in atlas.get('regions', atlas).items()]
(out / 'landscape-mapping.txt').write_text('\n'.join(lines) + '\n')
(out / 'biome-files.txt').write_text('\n'.join(str(p.relative_to(ROOT)) for p in (ROOT / 'resources/visual/biomes').rglob('*') if p.is_file()) + '\n')
