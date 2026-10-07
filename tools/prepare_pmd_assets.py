#!/usr/bin/env python3
"""Prepare exact PMDCollab assets, preserving original PNGs, XML, credits and timings.

Python 3 + Pillow + requests. No API token or API rate allowance is required.
The repository commit is deliberately pinned: changing it is a reviewed asset update.
Re-runs reuse verified local downloads and regenerate coverage/validation reports.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import io
import json
from pathlib import Path
import re
import threading
import time
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops
import requests

ROOT = Path(__file__).resolve().parents[1]
COMMIT = "78a49e8afde071fd7585831b145fb1b189e71288"
REPO = "PMDCollab/SpriteCollab"
RAW = f"https://raw.githubusercontent.com/{REPO}/{COMMIT}/"
OUT = ROOT / "resources/visual/pmd"
CACHE = ROOT / "build/pmd-source-metadata"
ANIMATIONS = ("Idle", "Walk", "Attack", "Hurt", "Sleep")
LOCAL = threading.local()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def fetch(source, target, optional=False):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.is_file():
        return target.read_bytes()
    if not hasattr(LOCAL, "session"):
        LOCAL.session = requests.Session()
        LOCAL.session.headers["User-Agent"] = "PokeWilds-PMD-preparer/1.0"
    for attempt in range(5):
        try:
            response = LOCAL.session.get(RAW + source, timeout=(20, 90))
            if optional and response.status_code == 404:
                return None
            response.raise_for_status()
            data = response.content
            if len(data) > 16 * 1024 * 1024:
                raise ValueError("Unexpectedly large source: " + source)
            temporary = target.with_suffix(target.suffix + ".part")
            temporary.write_bytes(data)
            temporary.replace(target)
            return data
        except (requests.RequestException, OSError):
            if attempt == 4:
                raise
            time.sleep(attempt + 1)


def slug(name):
    return re.sub(r"[^a-z0-9]+", "", name.lower())


def targets(tracker):
    entries = {}
    aliases = {"nidoranf": "nidoran_f", "nidoranm": "nidoran_m", "farfetchd": "farfetch_d", "hooh": "ho_oh"}
    for dex in range(1, 1026):
        node = tracker.get(f"{dex:04d}")
        if not node:
            continue
        key = aliases.get(slug(node["name"]), slug(node["name"]))
        entries[key] = (f"{dex:04d}", node, dex, node["name"])
    exact_forms = {
        "megagengar": (94, ["0001"]),
        "araichu": (26, ["0001"]), "aexeggutor": (103, ["0001"]),
        "amarowak": (105, ["0001"]), "gcorsola": (222, ["0001"]),
        "gdarumaka": (554, ["0001"]), "gyamask": (562, ["0001"]),
        "darmanitanzen": (555, ["0001"]), "gdarmanitan": (555, ["0002"]),
        "gdarmanitanzen": (555, ["0003"]), "combee_female": (415, ["0000", "0000", "0002"]),
    }
    for index, letter in enumerate("abcdefghijklmnopqrstuvwxyz"):
        exact_forms["unown_" + letter] = (201, [] if index == 0 else [f"{index:04d}"])
    exact_forms["unown_!"] = (201, ["0026"])
    exact_forms["unown_qmark"] = (201, ["0027"])
    for key, (dex, parts) in exact_forms.items():
        node = tracker[f"{dex:04d}"]
        for part in parts:
            node = node["subgroups"][part]
        entries[key] = ("/".join([f"{dex:04d}"] + parts), node, dex, node["name"])
    # Shiny is selected explicitly and never synthesized by recoloring.
    for key, (path, node, dex, name) in list(entries.items()):
        if path.count("/") == 0:
            parent = node.get("subgroups", {}).get("0000", {})
            shiny = parent.get("subgroups", {}).get("0001")
            shiny_path = path + "/0000/0001"
        elif key == "combee_female":
            shiny = tracker["0415"]["subgroups"]["0000"]["subgroups"].get("0001", {}).get("subgroups", {}).get("0002")
            shiny_path = "0415/0000/0001/0002"
        else:
            shiny = node.get("subgroups", {}).get("0001")
            shiny_path = path + "/0001"
        if shiny:
            entries[key + "#shiny"] = (shiny_path, shiny, dex, name + " Shiny")
    return entries


def parse_xml(data):
    root = ET.fromstring(data)
    result = {}
    for node in root.find("Anims"):
        name = node.findtext("Name")
        copy = node.findtext("CopyOf")
        if copy:
            result[name] = {"copy": copy}
        else:
            result[name] = {
                "width": int(node.findtext("FrameWidth")), "height": int(node.findtext("FrameHeight")),
                "durations": [int(x.text) for x in node.find("Durations")],
                "hitFrame": int(node.findtext("HitFrame", "-1")),
                "rushFrame": int(node.findtext("RushFrame", "-1")),
                "returnFrame": int(node.findtext("ReturnFrame", "-1")),
            }
    return result


def resolved(animations, name):
    seen = set()
    while name in animations and name not in seen:
        seen.add(name)
        animation = animations[name]
        if "copy" not in animation:
            return name, animation
        name = animation["copy"]
    return None, None


def inspect_animation(path, name, meta):
    images = [Image.open(OUT / "sprite" / path / (name + suffix)).convert("RGBA") for suffix in ("-Anim.png", "-Shadow.png", "-Offsets.png")]
    anim, shadow, offsets = images
    w, h = meta["width"], meta["height"]
    durations = meta["durations"]
    if any(x.size != anim.size for x in images) or anim.width != w * len(durations) or anim.height not in (h, h * 8):
        raise ValueError(f"Invalid PMD frame grid: {path}/{name}, image {anim.size}, frame {w}x{h}, durations {len(durations)}")
    if not durations or any(d <= 0 for d in durations):
        raise ValueError("Invalid animation duration: " + path + "/" + name)
    if anim.getchannel("A").getextrema() != (0, 255):
        raise ValueError("Expected transparent and opaque sprite pixels: " + path + "/" + name)
    frames, missing_anchor, empty_frames = [], 0, 0
    white = Image.new("L", shadow.size, 255)
    for channel in shadow.split():
        white = ImageChops.multiply(white, channel.point(lambda value: 255 if value == 255 else 0))
    for row in range(anim.height // h):
        for col in range(len(durations)):
            x, y = col * w, row * h
            bounds = anim.crop((x, y, x + w, y + h)).getbbox()
            if bounds is None:
                # An intentionally empty animation frame is legal; preserve it.
                bounds = (0, 0, 1, 1)
                empty_frames += 1
            a, b, c, d = bounds
            white_bounds = white.crop((x, y, x + w, y + h)).getbbox()
            center = (white_bounds[0], white_bounds[1]) if white_bounds else None
            if center is None:
                # The format normally specifies a white ground origin. Keep an
                # explicit fallback record rather than claiming an exact anchor.
                center = (w / 2 - 0.5, h / 2 - 0.5)
                missing_anchor += 1
            frames.append([x + a, y + b, c - a, d - b, center[0] + .5 - a, d - center[1] - .5])
    return dict(meta, file="sprite/" + path + "/" + name + "-Anim.png", rows=anim.height // h,
                frames=frames, missingAnchorFrames=missing_anchor, emptyFrames=empty_frames)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workers", type=int, default=32)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    CACHE.mkdir(parents=True, exist_ok=True)
    previous_manifest = ROOT / "PMD-PROVENANCE.json"
    if previous_manifest.is_file():
        previous = json.loads(previous_manifest.read_text(encoding="utf-8"))
        if previous.get("commit") != COMMIT:
            raise ValueError("Existing PMD assets belong to another commit; use an explicit clean asset migration.")
        for source, info in previous.get("files", {}).items():
            path = OUT / source
            if path.is_file() and digest(path.read_bytes()) != info["sha256"]:
                raise ValueError("Existing pinned PMD source was modified: " + source)
    tracker_data = fetch("tracker.json", CACHE / "tracker.json")
    tracker = json.loads(tracker_data)
    selected = targets(tracker)
    licenses = ROOT / "licenses"
    licenses.mkdir(exist_ok=True)
    for source, target in [("README.md", "PMD-SpriteCollab-README.md"), ("LICENSE.md", "PMD-LICENSE.md"), ("spritebot_credits.txt", "PMD-spritebot-credits.txt")]:
        data = fetch(source, CACHE / source)
        (licenses / target).write_bytes(data)
    docs = {
        "source": "https://sprites.pmdcollab.org/", "repository": "https://github.com/" + REPO,
        "commit": COMMIT, "trackerSha256": digest(tracker_data),
        "license": "Custom artwork: CC BY-NC 4.0, attribution required. Official Chunsoft assets retain their respective rights; see upstream credits.",
        "format": "https://wiki.pmdo.pmdcollab.org/PMD_Sprite_Format",
        "directionOrderReference": "https://github.com/RogueCollab/RogueEssence/blob/master/RogueEssence/Content/CharSheet.cs (sheetIndex = (8 - Dir8) % 8)",
        "directions": ["down", "down-right", "right", "up-right", "up", "up-left", "left", "down-left"],
        "durationUnit": "1/60 second", "unmodifiedSources": True,
        "fallbackPolicy": "Missing exact species/form/shiny returns null to the original game renderer. Missing animation uses available Idle then Walk for that exact variant; recorded per entry.",
    }
    requests_map = {}
    for path, node, _, _ in selected.values():
        if node.get("sprite_files"):
            requests_map["sprite/" + path + "/AnimData.xml"] = False
        if "Normal" in node.get("portrait_files", {}):
            requests_map["portrait/" + path + "/Normal.png"] = False
    def batch(requests_map, label):
        if args.verify_only:
            missing = [name for name in requests_map if not (OUT / name).is_file()]
            if missing:
                raise ValueError(f"Missing {len(missing)} {label} files: {missing[:3]}")
            return
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            jobs = {pool.submit(fetch, name, OUT / name, optional): name for name, optional in requests_map.items()}
            for done, job in enumerate(as_completed(jobs), 1):
                job.result()
                if done % 250 == 0 or done == len(jobs):
                    print(f"{label}: {done}/{len(jobs)}", flush=True)
    batch(requests_map, "XML/portraits")
    parsed = {}
    image_requests = {}
    for path, node, _, _ in selected.values():
        if path in parsed or not node.get("sprite_files"):
            continue
        parsed[path] = parse_xml((OUT / "sprite" / path / "AnimData.xml").read_bytes())
        for name in ANIMATIONS:
            source_name, animation = resolved(parsed[path], name)
            if animation:
                for suffix in ("-Anim.png", "-Shadow.png", "-Offsets.png"):
                    image_requests["sprite/" + path + "/" + source_name + suffix] = False
    batch(image_requests, "Animation sheets")
    catalog = {"version": 1, "commit": COMMIT, "directions": docs["directions"], "species": {}}
    inspected = {}
    for path, animations in sorted(parsed.items()):
        runtime_animations = {}
        for name in ANIMATIONS:
            source_name, animation = resolved(animations, name)
            if animation and (path, source_name) not in inspected:
                inspected[(path, source_name)] = inspect_animation(path, source_name, animation)
            if animation:
                runtime_animations[name] = dict(inspected[(path, source_name)], sourceAnimation=source_name)
        (OUT / "sprite" / path / "metadata.json").write_text(json.dumps(runtime_animations, separators=(",", ":")), encoding="utf-8", newline="\n")
    provenance = dict(docs, assets=[], coverage={}, files={})
    for key, (path, node, dex, display_name) in sorted(selected.items()):
        item = {"dex": dex, "displayName": display_name, "sourcePath": path, "portrait": None, "animations": {}}
        portrait_path = "portrait/" + path + "/Normal.png"
        if (OUT / portrait_path).is_file():
            im = Image.open(OUT / portrait_path)
            if im.size != (40, 40):
                raise ValueError("Invalid portrait dimensions " + portrait_path)
            item["portrait"] = portrait_path
        for name in ANIMATIONS:
            source_name, animation = resolved(parsed.get(path, {}), name)
            if animation:
                item["animations"][name] = source_name
        fallback = next((n for n in ("Idle", "Walk") if n in item["animations"]), None)
        item["animationFallback"] = fallback
        catalog["species"][key] = item
        provenance["assets"].append({"key": key, "dex": dex, "sourcePath": path, "portrait": bool(item["portrait"]),
            "animations": list(item["animations"]), "missingAnimations": [n for n in ANIMATIONS if n not in item["animations"]],
            "spriteCredit": node["sprite_credit"], "portraitCredit": node["portrait_credit"],
            "spriteComplete": node["sprite_complete"], "portraitComplete": node["portrait_complete"]})
    all_requests = dict(requests_map, **image_requests)
    total_bytes = 0
    for source in sorted(all_requests):
        data = (OUT / source).read_bytes()
        provenance["files"][source] = {"bytes": len(data), "sha256": digest(data)}
        total_bytes += len(data)
    normal = {key: x for key, x in catalog["species"].items() if "#" not in key}
    base = {key: x for key, x in normal.items() if "/" not in x["sourcePath"] and not key.startswith("unown_")}
    provenance["coverage"] = {
        "nationalDexTargets": 1025, "baseSpeciesEntries": len(base), "basePortraits": sum(bool(x["portrait"]) for x in base.values()),
        "baseAnimatedSprites": sum(bool(x["animations"]) for x in base.values()), "variants": len(catalog["species"]),
        "missingBasePortraits": [key for key, x in base.items() if not x["portrait"]],
        "missingBaseSprites": [key for key, x in base.items() if not x["animations"]],
        "uniqueAnimationSheets": len(inspected), "sourceFiles": len(all_requests), "sourceBytes": total_bytes,
        "framesChecked": sum(len(x["frames"]) for x in inspected.values()),
        "missingAnchorFrames": sum(x["missingAnchorFrames"] for x in inspected.values()),
        "emptyFrames": sum(x["emptyFrames"] for x in inspected.values()),
    }
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "catalog.json").write_text(json.dumps(catalog, separators=(",", ":")), encoding="utf-8", newline="\n")
    (ROOT / "PMD-PROVENANCE.json").write_text(json.dumps(provenance, indent=2, ensure_ascii=True) + "\n", encoding="utf-8", newline="\n")
    (licenses / "PMD-README.txt").write_text(
        "PMDCollab SpriteCollab assets\nSource: https://sprites.pmdcollab.org/\nRepository: https://github.com/PMDCollab/SpriteCollab\nCommit: " + COMMIT +
        "\nCustom graphics: CC BY-NC 4.0 https://creativecommons.org/licenses/by-nc/4.0/\n"
        "Full unmodified upstream attribution: PMD-spritebot-credits.txt. Source policy: PMD-SpriteCollab-README.md.\n"
        "Official game graphics credited to CHUNSOFT are not relicensed as our original artwork.\n"
        "Original PNG and XML files are unchanged. Runtime only selects animation cells and clips transparent margins; no palette changes.\n"
        "Exact per-variant authors, source paths, SHA256 hashes and coverage: ../PMD-PROVENANCE.json.\n", encoding="utf-8", newline="\n")
    print(json.dumps(provenance["coverage"], indent=2), flush=True)


if __name__ == "__main__":
    main()
