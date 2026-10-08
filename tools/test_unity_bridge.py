#!/usr/bin/env python3
"""Bridge guard tests. Full source import is exercised separately in CI."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('bridge',Path(__file__).with_name('prepare_unity_assets.py'))
bridge=importlib.util.module_from_spec(spec);spec.loader.exec_module(bridge)

class BridgeGuards(unittest.TestCase):
    def test_invalid_numbers(self):
        for value in (float('nan'),float('inf'),True,'16',None):
            with self.assertRaises(ValueError):bridge.number(value)
    def test_signed_origins(self):
        self.assertEqual(bridge.number(-7.5),-7.5)
    def test_extent_contract(self):
        self.assertEqual(bridge.number(48,integer=True,positive=True),48)
        for value in (0,-1,3.5):
            with self.assertRaises(ValueError):bridge.number(value,integer=True,positive=True)
    def test_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'ok.png').write_bytes(b'fixture')
            self.assertEqual(bridge.inside(root,'ok.png'),root/'ok.png')
            for name in ('../ok.png','/etc/passwd','C:/file','..\\file','missing.png'):
                with self.assertRaises(ValueError):bridge.inside(root,name)
    def test_unowned_destination(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out=root/'unity/Assets/PokeWilds/Resources/Imported';out.mkdir(parents=True)
            marker=out/'user-file.txt';marker.write_text('keep')
            with self.assertRaises(ValueError):bridge.prepare(root)
            self.assertEqual(marker.read_text(),'keep')

if __name__=='__main__':unittest.main()
