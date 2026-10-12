#!/usr/bin/env python3
"""Pack the reviewed, preserved trainer redraws; never synthesize character artwork.

Each source is a transparent image_gen sheet and an accompanying prompt/layout JSON.
Preparation only crops cells, normalizes their scale, samples existing pixels and
marks a restricted clothing accent for the existing costume-color selection.
"""
from __future__ import annotations

import argparse
import colorsys
import hashlib
import json
from pathlib import Path

from PIL import Image
from deterministic_png import ENCODING, rgba_png_bytes, save_rgba_png

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "resources/visual/trainers-bw"
CAST = ("leaf", "gold", "kris", "brendan", "may", "rosa", "calem", "serena",
        "chase", "elaine", "gloria", "mark", "mint", "lunick", "summer", "kellyn", "lyra")
ACCENTS = {"leaf": "blue", "gold": "red", "kris": "pink", "brendan": "red",
           "may": "red", "rosa": "pink", "calem": "blue", "serena": "red",
           "chase": "blue", "elaine": "blue", "gloria": "gray", "mark": "red",
           "mint": "blue", "lunick": "red", "summer": "red", "kellyn": "red", "lyra": "red"}
MASK_BOUNDS = {identity: (10, 22, 22, 27) for identity in CAST}
# Lower center only: this character's blue hair shares the outfit hue.
MASK_BOUNDS["mint"] = (12, 25, 20, 28)
MASK_BOUNDS["lunick"] = (10, 23, 22, 28)
PARAMETERS = {"cellSize": [32, 32], "columns": 3, "rows": 4,
              "directions": ["down", "up", "left", "right"],
              "frames": ["left-step", "idle", "right-step"],
              "scale": "one uniform factor per identity; fit max height 28 and width 26",
              "resample": "nearest", "alphaThreshold": 128,
              "sourceRows": "recorded alpha128 content bands, otherwise density>=20 with gaps<=20 merged; six-pixel padding",
              "alignment": "horizontal silhouette center at x16; lowest visible row y30",
              "anchor": [16, 30], "maskBodyBounds": MASK_BOUNDS,
              "mask": "binary alpha; saturated character-specific clothing hue inside body bounds"}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def hard_alpha(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    image.putdata([(r, g, b, 255) if a >= 128 else (0, 0, 0, 0)
                   for r, g, b, a in image.getdata()])
    return image


def clothing(r: int, g: int, b: int, accent: str) -> bool:
    hue, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    degrees = hue * 360
    if accent == "gray":
        return saturation < .18 and .24 <= value <= .9
    if saturation < .30 or value < .22:
        return False
    # Exclude orange/brown skin and hair even in the restricted torso band.
    if accent == "red":
        return degrees <= 12 or degrees >= 340
    if accent == "pink":
        return 290 <= degrees <= 355
    if accent == "blue":
        return 190 <= degrees <= 250
    raise ValueError(f"Unreviewed clothing accent: {accent}")


def content_bands(source: Image.Image) -> list[list[int]]:
    alpha = source.getchannel("A")
    occupied = [y for y in range(source.height)
                if alpha.crop((0, y, source.width, y + 1)).histogram()[255] >= 20]
    bands = []
    for y in occupied:
        if not bands or y - bands[-1][1] > 20:
            bands.append([y, y])
        else:
            bands[-1][1] = y
    if len(bands) != 4:
        raise ValueError(f"Expected four distinct trainer content rows, found {bands}")
    return bands


def prepare(identity: str) -> tuple[Image.Image, Image.Image, dict]:
    source_path = DEST / "sources" / (identity + ".png")
    metadata_path = source_path.with_suffix(".json")
    raw, raw_metadata = source_path.read_bytes(), metadata_path.read_bytes()
    metadata = json.loads(raw_metadata)
    if metadata.get("id", metadata.get("character")) != identity:
        raise ValueError(f"Metadata identity mismatch: {identity}")
    if metadata.get("source_sha256", sha(raw)) != sha(raw):
        raise ValueError(f"Preserved source differs from its recorded hash: {identity}")
    layout = metadata["layout"]
    if layout.get("columns") != 3 or layout.get("rows") != 4:
        raise ValueError(f"Source grid must be 3x4: {identity}")
    cell_map = layout.get("cellMap", [[row * 3 + col for col in range(3)] for row in range(4)])
    indices = [index for row in cell_map for index in row]
    if len(cell_map) != 4 or any(len(row) != 3 for row in cell_map) or sorted(indices) != list(range(12)):
        raise ValueError(f"Cell map must be a permutation of the 12 source cells: {identity}")
    with Image.open(source_path) as opened:
        source = hard_alpha(opened)
    bands = metadata.get("content_row_bands_alpha128") or content_bands(source)
    if len(bands) != 4 or any(len(band) != 2 or band[0] < 0 or band[1] >= source.height or band[1] <= band[0] for band in bands):
        raise ValueError(f"Invalid source content rows: {identity}")
    if any(bands[row][1] + 12 >= bands[row + 1][0] for row in range(3)):
        raise ValueError(f"Source content rows do not have safe transparent separation: {identity}")
    crops, boxes = [], []
    for index in indices:
        col, row = index % 3, index // 3
        box = (source.width * col // 3, max(0, bands[row][0] - 6),
               source.width * (col + 1) // 3, min(source.height, bands[row][1] + 7))
        cell = source.crop(box)
        bbox = cell.getbbox()
        if bbox is None:
            raise ValueError(f"Empty source cell {index}: {identity}")
        crops.append(cell.crop(bbox))
        boxes.append({"sourceCell": index, "cellBounds": list(box), "silhouetteBounds": list(bbox)})
    factor = min(28 / max(cell.height for cell in crops), 26 / max(cell.width for cell in crops))
    sheet = Image.new("RGBA", (96, 128))
    for i, crop in enumerate(crops):
        width, height = max(1, round(crop.width * factor)), max(1, round(crop.height * factor))
        cell = hard_alpha(crop.resize((width, height), Image.Resampling.NEAREST))
        # Match the original B/W sheets' lowest visible pixel at local row 30.
        x, y = (32 - width) // 2, 31 - height
        sheet.paste(cell, (i % 3 * 32 + x, i // 3 * 32 + y))
        boxes[i].update({"sampledSize": [width, height], "outputOffset": [x, y]})
    mask = Image.new("RGBA", sheet.size)
    counts = []
    for i in range(12):
        count = 0
        x0, y0, x1, y1 = MASK_BOUNDS[identity]
        for y in range(y0, y1):
            for x in range(x0, x1):
                px, py = i % 3 * 32 + x, i // 3 * 32 + y
                r, g, b, alpha = sheet.getpixel((px, py))
                if alpha and clothing(r, g, b, ACCENTS[identity]):
                    mask.putpixel((px, py), (255, 255, 255, 255))
                    count += 1
        counts.append(count)
    if not sum(counts):
        raise ValueError(f"No safe clothing accent pixels: {identity}")
    record = {"source": "sources/" + source_path.name,
              "sourceSha256": sha(raw), "sourceSize": list(source.size),
              "metadata": "sources/" + metadata_path.name, "metadataSha256": sha(raw_metadata),
              "kind": metadata.get("kind", metadata.get("authorship")),
              "tool": metadata.get("tool", metadata.get("generation_tool")),
              "prompt": metadata.get("prompt"), "references": metadata.get("references", metadata.get("reference_images")),
              "layout": layout, "contentRowBands": bands, "uniformScale": factor, "cells": boxes,
              "costumeAccent": ACCENTS[identity], "maskedPixelsByCell": counts,
              "outputs": {identity + ".png": sha(rgba_png_bytes(sheet)),
                          identity + "-mask.png": sha(rgba_png_bytes(mask))}}
    return sheet, mask, record


def credits() -> str:
    return ("Pokemon characters: Nintendo / Game Freak / The Pokemon Company.\n"
            "Seventeen B/W-style trainer sheets are AI-assisted fan-art redraws generated with image_gen, "
            "using the existing character identities and costumes as references. They are not extracted official B/W sprites.\n"
            "Hilbert and Hilda still use the original Pokemon Black/White overworld sheets in visual/unova; "
            "extracted by Barubary, The Spriters Resource, asset 34024. Their source credits remain in visual/unova/CREDITS.txt.\n"
            "The original generated images and exact prompts/layout notes are preserved in sources/.\n"
            "Preparation: deterministic source-cell packing, nearest-neighbor size normalization, binary alpha, "
            "and clothing-only costume masks. No new character pixels are drawn by the preparation script.\n"
            "Character artwork and references are not declared MIT, CC0 or freely licensed.\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", nargs="+", choices=CAST, help="Prepare a subset for visual diagnostics; final build requires all 17")
    parser.add_argument("--check", action="store_true", help="Verify deterministic outputs without writing files")
    args = parser.parse_args()
    selected = [identity for identity in CAST if not args.only or identity in args.only]
    outputs, records = {}, {}
    for identity in selected:
        sheet, mask, record = prepare(identity)
        outputs[identity + ".png"] = sheet
        outputs[identity + "-mask.png"] = mask
        records[identity] = record
        print(f"{identity}: 12 frames, {sum(record['maskedPixelsByCell'])} clothing pixels, scale={record['uniformScale']:.8f}")
    provenance = {"schema": 1, "complete": len(selected) == len(CAST),
                  "preparation": "tools/prepare_trainer_assets.py", "encoding": ENCODING,
                  "parameters": PARAMETERS, "identities": records,
                  "originalBWHeroes": {"ids": ["hilbert", "hilda"], "provenance": "../unova/PROVENANCE.json"}}
    text_outputs = {"PROVENANCE.json": json.dumps(provenance, indent=2, ensure_ascii=False) + "\n",
                    "CREDITS.txt": credits()}
    for name, image in outputs.items():
        path = DEST / name
        if args.check:
            if not path.is_file() or path.read_bytes() != rgba_png_bytes(image):
                raise RuntimeError("Prepared sprite differs: " + name)
        else:
            save_rgba_png(image, path)
    for name, content in text_outputs.items():
        path = DEST / name
        if args.check:
            if not path.is_file() or path.read_text(encoding="utf-8") != content:
                raise RuntimeError("Prepared metadata differs: " + name)
        else:
            path.write_text(content, encoding="utf-8", newline="\n")
    print(f"TRAINER ASSET {'CHECK' if args.check else 'PREPARATION'} PASS: {len(selected)} identities / {len(outputs)} PNGs")


if __name__ == "__main__":
    main()
