import binascii
import hashlib
import io
from pathlib import Path
import struct
import unittest
from unittest.mock import patch
import zlib
from PIL import Image
from deterministic_png import rgba_png_bytes, save_rgba_png


class DeterministicPngTest(unittest.TestCase):
    def chunks(self, encoded):
        self.assertEqual(encoded[:8], b"\x89PNG\r\n\x1a\n")
        pos = 8; result = []
        while pos < len(encoded):
            length = struct.unpack(">I", encoded[pos:pos+4])[0]
            kind = encoded[pos+4:pos+8]; data = encoded[pos+8:pos+8+length]
            expected = struct.unpack(">I", encoded[pos+8+length:pos+12+length])[0]
            self.assertEqual(expected, binascii.crc32(kind+data) & 0xffffffff)
            result.append((kind, data)); pos += 12+length
        self.assertEqual(pos, len(encoded))
        self.assertEqual([kind for kind, _ in result], [b"IHDR", b"IDAT", b"IEND"])
        return dict(result)

    def test_transparency_palette_and_native_decoder(self):
        source = Image.frombytes("RGBA", (2, 2), bytes([255,0,0,255, 0,255,0,128, 0,0,255,0, 255,255,255,255]))
        before = source.tobytes()
        with patch("zlib.compress", side_effect=AssertionError("Platform compressor must not run")):
            data = rgba_png_bytes(source)
        self.assertEqual(source.tobytes(), before)
        decoded = Image.open(io.BytesIO(data)).convert("RGBA")
        self.assertEqual(decoded.tobytes(), before)
        chunks = self.chunks(data)
        self.assertEqual(zlib.decompress(chunks[b"IDAT"]), b"\0"+before[:8]+b"\0"+before[8:])
        self.assertEqual(hashlib.sha256(data).hexdigest(), "7d3ec8122d0108a7c5c5355f39202ff6986fb0c45769036bd891cb8cca8b64d0")
        palette = Image.new("P", (1,1)); palette.putpalette([255,0,255]+[0,0,0]*255); palette.info["transparency"]=0
        self.assertEqual(Image.open(io.BytesIO(rgba_png_bytes(palette))).getpixel((0,0)), (255,0,255,0))

    def test_fixed_stored_blocks_and_real_atlas(self):
        root = Path(__file__).resolve().parents[1]
        for source in (root/"resources/visual/stardew/world-atlas.png", root/"resources/pokemon/suicune_overw1.png"):
            image = Image.open(source).convert("RGBA")
            encoded = rgba_png_bytes(image); stream = self.chunks(encoded)[b"IDAT"]
            self.assertEqual(stream[:2], b"\x78\x01")
            pos = 2; blocks = 0; raw = bytearray()
            while True:
                final = stream[pos]; self.assertIn(final, (0,1))
                length, inverse = struct.unpack("<HH", stream[pos+1:pos+5])
                self.assertEqual(length ^ inverse, 0xffff)
                if not final: self.assertEqual(length, 65535)
                raw.extend(stream[pos+5:pos+5+length]); pos += 5+length; blocks += 1
                if final: break
            self.assertEqual(len(stream)-pos, 4)
            self.assertEqual(bytes(raw), zlib.decompress(stream))
            decoded = Image.open(io.BytesIO(encoded))
            self.assertEqual(decoded.size, image.size)
            self.assertEqual(decoded.tobytes(), image.tobytes())
            if image.width > 16: self.assertGreater(blocks, 60)
            self.assertEqual(encoded, rgba_png_bytes(image))

    def test_empty_rejected(self):
        with self.assertRaisesRegex(ValueError, "positive"):
            rgba_png_bytes(Image.new("RGBA", (0,1)))


if __name__ == "__main__":
    unittest.main()
