"""Canonical lossless RGBA PNG encoding, independent of Pillow/zlib compression builds.

PNG filter 0 and RFC 1951 stored blocks of exactly 65535 bytes avoid compressor
heuristics. JAR/ZIP packaging still compresses these bytes. Original pixel RGBA,
including RGB under transparent pixels, is preserved without any quantization.
"""
from __future__ import annotations

import binascii
from pathlib import Path
import struct
import zlib


ENCODING = {"name": "RGBA8-filter0-stored-deflate", "version": 1, "blockBytes": 65535,
            "implementation": "tools/deterministic_png.py"}


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", binascii.crc32(kind + data) & 0xffffffff)


def rgba_png_bytes(image) -> bytes:
    rgba = image.convert("RGBA")
    width, height = rgba.size
    if width < 1 or height < 1:
        raise ValueError("PNG dimensions must be positive")
    pixels = rgba.tobytes()
    stride = width * 4
    raw = b"".join(b"\0" + pixels[y * stride:(y + 1) * stride] for y in range(height))
    # CM=DEFLATE, CINFO=32KiB, FDICT=0; 0x7801 satisfies the RFC 1950 FCHECK.
    stream = bytearray(b"\x78\x01")
    for start in range(0, len(raw), 65535):
        block = raw[start:start + 65535]
        final = start + len(block) == len(raw)
        stream.append(1 if final else 0)  # BFINAL plus BTYPE=00, byte-aligned.
        stream.extend(struct.pack("<HH", len(block), len(block) ^ 0xffff))
        stream.extend(block)
    stream.extend(struct.pack(">I", zlib.adler32(raw) & 0xffffffff))
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", header) + _chunk(b"IDAT", bytes(stream)) + _chunk(b"IEND", b"")


def save_rgba_png(image, path: Path) -> None:
    Path(path).write_bytes(rgba_png_bytes(image))
