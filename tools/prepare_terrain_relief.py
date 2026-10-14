"""Import the original image_gen material PNGs without creatively modifying them.

The only transformation is a deterministic centre-sampled nearest-neighbour
conversion to the game's 128 x 128 pixel texture resolution. Original PNGs,
exact prompts, source hashes and the lossless encoding specification remain
in resources/visual/terrain-relief. --check verifies byte-for-byte reproducibility.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image

from deterministic_png import ENCODING, rgba_png_bytes


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "resources" / "visual" / "terrain-relief"
SIZE = 128


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalize(source: Path) -> Image.Image:
    with Image.open(source) as opened:
        rgba = opened.convert("RGBA")
        if rgba.width != rgba.height or rgba.width < SIZE:
            raise ValueError(f"Expected a square source of at least {SIZE} px: {source}")
        if rgba.getextrema()[3] != (255, 255):
            raise ValueError(f"Terrain texture must be fully opaque: {source}")
        # Integer pixel-centre sampling is independent of resampler versions.
        px = rgba.load()
        normalized = Image.new("RGBA", (SIZE, SIZE))
        normalized.putdata([
            px[min(rgba.width - 1, (2 * x + 1) * rgba.width // (2 * SIZE)),
               min(rgba.height - 1, (2 * y + 1) * rgba.height // (2 * SIZE))]
            for y in range(SIZE) for x in range(SIZE)
        ])
        return normalized


def edge_metrics(im: Image.Image) -> dict[str, float]:
    pixels = im.load()

    def distance(a, b):
        return sum(abs(a[c] - b[c]) for c in range(3)) / 3

    return {
        "leftRightMeanRgbDifference": round(
            sum(distance(pixels[0, y], pixels[SIZE - 1, y]) for y in range(SIZE)) / SIZE, 4),
        "topBottomMeanRgbDifference": round(
            sum(distance(pixels[x, 0], pixels[x, SIZE - 1]) for x in range(SIZE)) / SIZE, 4),
        "horizontalNeighborMeanRgbDifference": round(
            sum(distance(pixels[x, y], pixels[x + 1, y])
                for y in range(SIZE) for x in range(SIZE - 1)) / (SIZE * (SIZE - 1)), 4),
        "verticalNeighborMeanRgbDifference": round(
            sum(distance(pixels[x, y], pixels[x, y + 1])
                for y in range(SIZE - 1) for x in range(SIZE)) / (SIZE * (SIZE - 1)), 4),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify checked-in PNGs instead of writing.")
    args = parser.parse_args()
    manifest_path = ASSETS / "PROVENANCE.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["normalization"]["encoding"] != ENCODING:
        raise ValueError("Manifest lossless encoding differs from the canonical importer")
    for asset in manifest["assets"]:
        source = ASSETS / asset["source"]
        source_data = source.read_bytes()
        if digest(source_data) != asset["sourceSha256"]:
            raise ValueError(f"Source changed: {source}")
        prompt = ASSETS / asset["prompt"]
        if digest(prompt.read_bytes()) != asset["promptSha256"]:
            raise ValueError(f"Exact generation prompt changed: {prompt}")
        normalized = normalize(source)
        pixels = list(normalized.getdata())
        lumas = [(p[0] * 299 + p[1] * 587 + p[2] * 114) / 1000 for p in pixels]
        # Lava deliberately contains dark cooled crust beside luminous magma.
        # Require a broad hot surface, rather than applying the dry-face floor.
        lava = asset["file"] == "lava-surface.png"
        if lava and sum(p[0] > 180 and p[0] > p[2] * 2 for p in pixels) / len(pixels) < .30:
            raise ValueError(f"Lava lacks a visible molten surface: {source}")
        if min(lumas) < (12 if lava else 25) or not 65 <= sum(lumas) / len(lumas) <= 190:
            raise ValueError(f"Material has a near-black or unreadable palette: {source}")
        data = rgba_png_bytes(normalized)
        target = ASSETS / asset["file"]
        if args.check:
            if target.read_bytes() != data:
                raise ValueError(f"Output is not reproducible: {target}")
            if digest(data) != asset["sha256"]:
                raise ValueError(f"Output hash differs from provenance: {target}")
            if edge_metrics(normalized) != asset["edgeMetrics"]:
                raise ValueError(f"Edge continuity metrics differ: {target}")
        else:
            if target.exists() and target.read_bytes() != data:
                raise ValueError(f"Refusing to overwrite a different existing asset: {target}")
            target.write_bytes(data)
            asset["sha256"] = digest(data)
            asset["edgeMetrics"] = edge_metrics(normalized)
        print(f"{'PASS' if args.check else 'WROTE'} {asset['file']} 128 x 128 opaque RGBA {digest(data)}")
    if not args.check:
        manifest_path.write_bytes((json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))


if __name__ == "__main__":
    main()
