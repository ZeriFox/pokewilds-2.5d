#!/usr/bin/env python3
"""Pixel/data regressions for the recovered atlas; no OpenGL claim is implied."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image

import prepare_stardew_presentation as generator


class StardewAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='pokewilds-stardew-')
        cls.output = Path(cls.temp.name) / 'first'
        generator.prepare(cls.output)
        cls.meta = json.loads((cls.output / 'world-atlas.json').read_text())
        cls.provenance = json.loads((cls.output / 'PROVENANCE.json').read_text())
        cls.image = Image.open(cls.output / 'world-atlas.png').convert('RGBA')

    @classmethod
    def tearDownClass(cls):
        cls.image.close()
        cls.temp.cleanup()

    def region(self, name):
        r = self.meta['regions'][name]
        return self.image.crop((r['x'], r['y'], r['x'] + r['width'], r['y'] + r['height']))

    def test_wall_excludes_sheet_cast_shadow_and_preserves_face(self):
        source = Image.open(generator.SOURCE / 'walls_floors.png').convert('RGBA')
        # Reproduce the exact cause of the previous failure, not just a size check.
        self.assertEqual([source.getpixel((0, y))[3] for y in (45, 46, 47)], [101, 55, 20])
        self.assertNotEqual(source.crop((0, 0, 16, 48)).getchannel('A').getextrema(), (255, 255))
        for name, x in [('wall', 0), ('wall_wood', 176)]:
            result = self.region(name)
            self.assertEqual(result.size, (16, 45))
            self.assertEqual(result.tobytes(), source.crop((x, 0, x + 16, 45)).tobytes())
            self.assertEqual(result.getchannel('A').getextrema(), (255, 255))

    def test_every_opaque_surface_is_opaque_and_every_frame_exists(self):
        for name, r in self.meta['regions'].items():
            with self.subTest(region=name):
                self.assertIsNotNone(self.region(name).getchannel('A').getbbox())
                if r.get('kind') == 'surface':
                    self.assertEqual(self.region(name).getchannel('A').getextrema(), (255, 255))
                if 'sampleWidth' in r:
                    self.assertEqual(r['width'] % r['sampleWidth'], 0)
                    self.assertEqual(r['height'] % r['sampleHeight'], 0)
                self.assertIn(name, self.provenance['regions'])
        for name, animation in self.meta['animations'].items():
            with self.subTest(animation=name):
                frames = [self.region(key) for key in animation['frames']]
                self.assertGreater(animation['fps'], 0)
                self.assertEqual(len({frame.size for frame in frames}), 1)
                self.assertEqual(len({frame.tobytes() for frame in frames}), len(frames))
        self.assertEqual(len(self.meta['animations']), 6, 'Retain all mechanical animation roles')
        for name in ('lava_bright', 'lava_cooled'):
            self.assertEqual(len(self.meta['animations'][name]['frames']), 4)

    def test_complete_furniture_and_rocks_preserve_all_source_pixels(self):
        for name, source_name, box in [
            ('bed', 'furniture', (514, 312, 562, 368)),
            ('shelf', 'furniture', (594, 0, 626, 32)),
            ('table', 'furniture', (224, 400, 304, 448)),
            ('chair', 'furniture', (0, 0, 16, 32)),
            ('rock', 'spring', (528, 32, 560, 64)),
            ('desert_cactus', 'desert', (64, 112, 80, 160)),
            ('volcanic_rock', 'volcano', (48, 32, 64, 64)),
        ]:
            with self.subTest(region=name):
                source = Image.open(generator.SOURCE / (source_name + '.png')).convert('RGBA')
                expected = source.crop(box)
                actual = self.region(name)
                self.assertEqual(actual.size, expected.size)
                self.assertEqual(actual.tobytes(), expected.tobytes())
                r = self.meta['regions'][name]
                self.assertEqual((r['sampleWidth'], r['sampleHeight']), actual.size)
                self.assertEqual(r['anchorUnits'], 'pixels')
                self.assertAlmostEqual(r['worldWidth'] / r['worldHeight'], actual.width / actual.height)

    def test_isolated_tree_keeps_trunk_without_adjacent_terrain(self):
        tree = self.region('tree')
        palm = self.region('tree_dry')
        self.assertEqual(tree.size, (48, 96))
        self.assertEqual(palm.size, (48, 80))
        # The old 48px palm crop omitted its bottom 32px of trunk entirely.
        self.assertIsNotNone(palm.crop((0, 48, 48, 80)).getchannel('A').getbbox())
        # Bottom-left/right remain empty instead of displaying the ground strip.
        self.assertEqual(tree.getpixel((0, 95))[3], 0)
        self.assertEqual(tree.getpixel((47, 95))[3], 0)
        pine = self.region('tree_pine')
        self.assertEqual(pine.size, (48, 96))
        self.assertEqual(pine.getpixel((5, 95))[3], 0)

    def test_padding_extrudes_edges_and_corners(self):
        for name, r in self.meta['regions'].items():
            with self.subTest(region=name):
                x, y, w, h = (r[key] for key in ('x', 'y', 'width', 'height'))
                for dx, dy in [(-2, -2), (w + 1, -2), (-2, h + 1), (w + 1, h + 1),
                               (-1, h // 2), (w, h // 2), (w // 2, -1), (w // 2, h)]:
                    self.assertEqual(self.image.getpixel((x + dx, y + dy)),
                                     self.image.getpixel((x + max(0, min(w - 1, dx)),
                                                          y + max(0, min(h - 1, dy)))))

    def test_output_is_deterministic_and_hashes_match(self):
        second = Path(self.temp.name) / 'second'
        generator.prepare(second)
        for file in self.output.iterdir():
            self.assertEqual(file.read_bytes(), (second / file.name).read_bytes(), file.name)
        self.assertEqual(self.provenance['atlasSha256'], generator.digest(self.output / 'world-atlas.png'))
        self.assertEqual(self.provenance['metadataSha256'], generator.digest(self.output / 'world-atlas.json'))
        self.assertEqual(self.meta['schemaVersion'], 2)
        self.assertEqual(self.meta['coordinateContract']['anchorUnit'], 'source-pixels')

    def test_modified_reference_is_rejected_before_writing(self):
        destination = Path(self.temp.name) / 'bad-reference'
        with patch.object(generator, 'digest', return_value='corrupted input'):
            with self.assertRaisesRegex(ValueError, 'Reference source missing or changed'):
                generator.prepare(destination)
        self.assertFalse(destination.exists())

    def test_invalid_component_seed_cannot_silently_hide_an_object(self):
        with self.assertRaisesRegex(ValueError, 'visible artwork'):
            generator.isolated_component(Image.new('RGBA', (4, 4)), (2, 2))

    def test_retained_chimney_preserves_authentic_pixels_and_origin(self):
        source = Image.open(generator.COMPATIBILITY / 'chimney.png').convert('RGBA')
        self.assertEqual(self.region('chimney').tobytes(), source.tobytes())
        region = self.meta['regions']['chimney']
        self.assertEqual((region['anchorX'], region['anchorY']), (12, 8))
        self.assertIn('retainedCompatibility', self.provenance['regions']['chimney'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
