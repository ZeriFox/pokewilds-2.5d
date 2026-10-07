#!/usr/bin/env python3
"""Validate species identity/provenance and unchanged PMD sources for legacy Tile images."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from PIL import Image

from prepare_pmd_compatibility import PINNED, ROOT, prepare


class PmdCompatibilityTest(unittest.TestCase):
    def test_complete_distinct_poses_and_repeatable_export(self):
        with tempfile.TemporaryDirectory(prefix='pokewilds-pmd-compat-') as temporary:
            first, second = Path(temporary) / 'first', Path(temporary) / 'second'
            provenance = prepare(first)
            prepare(second)
            signatures = set()
            for species, (dex, expected, crop) in PINNED.items():
                name = species + '_overw1.png'
                path = ROOT / 'resources/visual/pmd/sprite' / dex
                self.assertEqual(hashlib.sha256((path / 'Idle-Anim.png').read_bytes()).hexdigest(), expected)
                meta = json.loads((path / 'metadata.json').read_text(encoding='utf-8'))
                self.assertEqual(meta['Idle']['frames'][0][:4], crop, species)
                art = Image.open(first / name).convert('RGBA')
                self.assertEqual(art.size, (16, 16))
                bounds = art.getchannel('A').getbbox()
                self.assertIsNotNone(bounds, species)
                self.assertEqual(bounds[3], 16, species)
                self.assertEqual(art.getchannel('A').getextrema(), (0, 255), species)
                signatures.add(art.tobytes())
                self.assertEqual(provenance['files'][name]['sourceSha256'], expected)
                self.assertEqual(provenance['files'][name]['sha256'], hashlib.sha256((first / name).read_bytes()).hexdigest())
            self.assertEqual(len(signatures), 3, 'Different species must never share a generic replacement')
            for file in first.iterdir():
                self.assertEqual(file.read_bytes(), (second / file.name).read_bytes(), file.name)


if __name__ == '__main__':
    unittest.main(verbosity=2)
