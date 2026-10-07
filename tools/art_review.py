#!/usr/bin/env python3
"""Generate small review-only previews; never used as production artwork."""
from pathlib import Path
import base64, io, json
from PIL import Image
ROOT=Path(__file__).resolve().parent.parent
out=ROOT/'tools/art-review';out.mkdir(exist_ok=True)
for source in sorted((ROOT/'resources/visual/stardew/sources').glob('*.png')):
    im=Image.open(source).convert('RGBA')
    factor=2 if im.width<550 and im.height<650 else 4
    small=im.resize((im.width//factor,im.height//factor),Image.Resampling.NEAREST)
    preview=small.quantize(colors=32,method=Image.Quantize.FASTOCTREE)
    data=io.BytesIO();preview.save(data,format='PNG',optimize=True)
    (out/(source.stem+'.b64')).write_text(base64.b64encode(data.getvalue()).decode()+'\n')
    print(source.stem,im.size,'preview',small.size,len(data.getvalue()))
atlas=json.loads((ROOT/'resources/visual/landscape/world-atlas.json').read_text())
lines=[]
for name,entry in atlas.get('regions',atlas).items():
    lines.append(name+' '+json.dumps(entry,separators=(',',':')))
(out/'landscape-mapping.txt').write_text('\n'.join(lines)+'\n')
(out/'biome-files.txt').write_text('\n'.join(str(p.relative_to(ROOT)) for p in (ROOT/'resources/visual/biomes').rglob('*') if p.is_file())+'\n')
