#!/usr/bin/env python3
"""Retrieve the specific public art sources requested for the landscape/UI revision.
No game state or renderer is changed by this acquisition step. Downloads are bounded,
PNG signatures/dimensions are checked, and the original source/rights are recorded.
"""
from __future__ import annotations
import hashlib, html, json, re, struct, time, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = 'https://www.spriters-resource.com'
SOURCES = [
    ('spring-outdoors', 'pc_computer/stardewvalley', 88632, 'Stardew Valley', 'Mr Mad Mothy; marios1999'),
    ('winter-outdoors', 'pc_computer/stardewvalley', 88665, 'Stardew Valley', 'Mr Mad Mothy; marios1999'),
    ('desert', 'pc_computer/stardewvalley', 88594, 'Stardew Valley', 'Mr Mad Mothy; marios1999'),
    ('volcano', 'pc_computer/stardewvalley', 223937, 'Stardew Valley', 'marios1999'),
    ('interiors', 'pc_computer/stardewvalley', 88658, 'Stardew Valley', 'Mr Mad Mothy; marios1999'),
    ('walls-floors', 'pc_computer/stardewvalley', 88659, 'Stardew Valley', 'Mr Mad Mothy; marios1999'),
    ('flooring', 'pc_computer/stardewvalley', 169045, 'Stardew Valley', 'Babycake'),
    ('furniture', 'pc_computer/stardewvalley', 126521, 'Stardew Valley', 'Babycake; BlobfishCultist; marios1999'),
    ('frlg-menu', 'game_boy_advance/pokemonfireredleafgreen', 3854, 'Pokemon FireRed / LeafGreen', 'Redzagoon'),
]

def retrieve(url: str) -> bytes:
    request = urllib.request.Request(url, headers={'User-Agent': 'PokeWilds-asset-import/1.0', 'Referer': SITE + '/'})
    with urllib.request.urlopen(request, timeout=35) as response:
        data = response.read(8 * 1024 * 1024 + 1)
    if len(data) > 8 * 1024 * 1024:
        raise ValueError('Asset exceeds the 8 MiB import limit')
    return data

def main() -> None:
    out = ROOT / 'resources/visual/stardew/sources'
    out.mkdir(parents=True, exist_ok=True)
    report = {'status': 'acquisition-only', 'rights': 'Original game artwork. Stardew Valley: ConcernedApe. Pokemon: Nintendo / Game Freak / Creatures. Public hosting does not grant a new redistribution or commercial license. Preserve the original rights and credits.', 'sources': []}
    for name, game_path, asset_id, game, submitter in SOURCES:
        page = f'{SITE}/{game_path}/asset/{asset_id}/'
        record = {'name': name, 'page': page, 'game': game, 'sheetCredits': submitter}
        try:
            body = retrieve(page).decode('utf-8')
            # Follow the full-size image actually published by the selected page.
            candidates = re.findall(r'(?:src|href|data-src)=[\"\']([^\"\']+)[\"\']', body)
            candidates = [html.unescape(x) for x in candidates if re.search(r'/(?:media/assets|resources/sheets)/\d+/' + str(asset_id) + r'\.png(?:\?|$)', x)]
            if not candidates:
                raise ValueError('No full-size PNG link in the requested asset page')
            url = urllib.parse.urljoin(SITE, candidates[0])
            parsed = urllib.parse.urlsplit(url)
            if parsed.scheme != 'https' or parsed.hostname not in ('www.spriters-resource.com', 'spriters-resource.com'):
                raise ValueError('Unexpected image host')
            data = retrieve(url)
            if data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
                raise ValueError('Download is not a PNG image')
            width, height = struct.unpack('>II', data[16:24])
            if width <= 0 or height <= 0 or width * height > 16000000:
                raise ValueError('Unexpected image dimensions')
            destination = out / (name + '.png')
            destination.write_bytes(data)
            record.update(image=url, file=destination.relative_to(ROOT).as_posix(), width=width, height=height, sha256=hashlib.sha256(data).hexdigest(), status='downloaded')
            print(f'ART OK {name} {width}x{height} {len(data)} bytes', flush=True)
        except Exception as error:
            record.update(status='unavailable', error=str(error))
            print(f'ART UNAVAILABLE {name}: {error}', flush=True)
        report['sources'].append(record)
        time.sleep(.3)
    (out.parent / 'SOURCE-REPORT.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)
    if not any(x['status'] == 'downloaded' for x in report['sources']):
        raise SystemExit('No source was retrieved; no art can be integrated from this run.')

if __name__ == '__main__':
    main()
