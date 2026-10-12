#!/usr/bin/env python3
"""Data/pixel checks for the new terrain treatment; not an in-game visual review."""
import json, statistics, unittest
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]

class ReadabilityAssets(unittest.TestCase):
    def test_calm_surfaces(self):
        root=ROOT/'resources/visual/stardew'
        meta=json.loads((root/'world-atlas.json').read_text())
        provenance=json.loads((root/'PROVENANCE.json').read_text())
        with Image.open(root/'world-atlas.png') as atlas:
            for key in ('grass','grass_light','grass_dry','path'):
                with self.subTest(key=key):
                    r=meta['regions'][key];image=atlas.crop((r['x'],r['y'],r['x']+r['width'],r['y']+r['height'])).convert('RGBA')
                    self.assertEqual(image.size,(64,64));self.assertEqual((r['sampleWidth'],r['sampleHeight']),(16,16))
                    self.assertEqual(image.getchannel('A').getextrema(),(255,255))
                    self.assertLess(statistics.pstdev(list(image.getchannel('G').getdata())),6,'Lawn is still high-contrast')
                    self.assertNotEqual(image.crop((0,0,16,16)).tobytes(),image.crop((16,16,32,32)).tobytes(),'Every world tile repeats the same stamp')
                    pixels=image.load()
                    horizontal=max(abs(pixels[0,y][c]-pixels[63,y][c]) for y in range(64) for c in range(3))
                    self.assertLessEqual(horizontal,8,'Periodic texture has an obvious outer seam')
                    self.assertTrue(any('calmPeriodicSurface' in t for t in provenance['regions'][key]['transformations']))

    def test_original_sheets_not_repainted(self):
        import hashlib
        source=ROOT/'art-source/reference'
        for key,info in json.loads((source/'manifest.json').read_text()).items():
            self.assertEqual(hashlib.sha256((source/(key+'.png')).read_bytes()).hexdigest(),info['sha256'])

if __name__=='__main__':unittest.main(verbosity=2)
