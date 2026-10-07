#!/usr/bin/env python3
"""Run tracked-source checks and native tests, with receipts bound to the built JAR.

No production classes or loose resources are placed before the delivered JAR.
Preparation is explicit; it must reproduce the assets already in the checkout.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/rework-verification"
JAR = ROOT / "dist/pokewilds-rebuilt.jar"
PURE = [
    "tools/test_flexible_sampling.py", "tools/test_visual_geometry.py",
    "tools/verify_visual_scope.py", "tools/verify_scope_test.py",
    "tools/verify_landscape_assets.py",
    "tools/test_stardew_assets.py",
    "tools/test_pmd_compatibility.py",
    "tools/test_packaging.py",
]
NATIVE = [
    ["tools/smoke_test.py"], ["tools/landscape_smoke_test.py"],
    ["tools/pmd_battle_visual_test.py"], ["tools/presentation_rework_test.py"],
    ["tools/bw_assets_integration_test.py"], ["tools/world_elevation_test.py"],
    ["tools/biome_scene_test.py"], ["tools/biome_habitat_test.py"],
    ["tools/field_scene_test.py"], ["tools/desktop_controls_test.py"],
    ["tools/ui_layout_test.py"], ["tools/battle_viewport_test.py"],
    ["tools/modern_ui_smoke_test.py"], ["tools/modern_world_test.py"],
    ["tools/expansion_dex_test.py"], ["tools/pmd_asset_smoke_test.py", "--bundled"],
    ["tools/asset_coverage_test.py"], ["tools/habitat_coverage.py"],
    ["tools/ghost_diagnostic_test.py"], ["tools/vertical_slice_test.py"],
    ["tools/move_effect_presentation_test.py"],
]


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def files(folder: Path) -> dict[str, str]:
    return {p.relative_to(ROOT).as_posix(): digest(p)
            for p in sorted(folder.rglob("*")) if p.is_file()}


def png_fingerprint(path: Path) -> dict:
    from PIL import Image
    with Image.open(path) as image:
        rgba = image.convert("RGBA")
        return {"size": list(rgba.size), "rgba_sha256": hashlib.sha256(rgba.tobytes()).hexdigest()}


def preparation_diff(before: dict, after: dict, before_pixels: dict, receipt: dict) -> None:
    from PIL import Image, features
    receipt["image_backend"] = {"pillow": Image.__version__, "zlib": features.version_codec("zlib")}
    receipt["asset_differences"] = []
    for name in sorted(set(before) | set(after)):
        if before.get(name) == after.get(name):
            continue
        row = {"file": name, "before_sha256": before.get(name), "after_sha256": after.get(name)}
        if name in before_pixels:
            row["before_png"] = before_pixels[name]
        source = ROOT / name
        if source.is_file():
            target = OUT / "preparation-changed" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            if source.suffix.lower() == ".png":
                row["after_png"] = png_fingerprint(source)
                row["pixels_equal"] = row.get("before_png") == row["after_png"]
        receipt["asset_differences"].append(row)
    print(json.dumps({"image_backend": receipt["image_backend"], "asset_differences": receipt["asset_differences"]}, indent=2), flush=True)


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def verify_build() -> dict:
    report = json.loads((ROOT / "build/build-report.json").read_text(encoding="utf-8"))
    if report.get("status") != "success" or report.get("output_sha256") != digest(JAR):
        raise RuntimeError("Missing successful report or stale/mismatched built JAR")
    source_hashes = {p.relative_to(ROOT).as_posix(): digest(p)
                     for p in sorted((ROOT / "src/com/pkmngen").rglob("*.java"))}
    resource_hashes = {p.relative_to(ROOT / "resources").as_posix(): digest(p)
                       for p in sorted((ROOT / "resources").rglob("*"))
                       if p.is_file() and (report.get("include_optional_3d_models") or p.suffix.lower() != ".glb")}
    if source_hashes != report["source_files_sha256"] or resource_hashes != report["resource_files_sha256"]:
        raise RuntimeError("Sources/assets changed after compilation; rebuild before verification or packaging")
    for name, expected in {"lib/upstream-runtime.jar": report["runtime_sha256"],
                           **report["additional_libraries_sha256"]}.items():
        if digest(ROOT / name) != expected:
            raise RuntimeError("Build dependency changed: " + name)
    return report


def run_stage(stage: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    receipt = {"schema": 1, "stage": stage, "status": "running", "commit": git("rev-parse", "HEAD"),
               "platform": platform.platform(), "python": platform.python_version(), "checks": [],
               "limitations": "Automated fixtures, not a complete manual campaign or multiplayer playthrough."}
    path = OUT / (stage + ".json")

    def save() -> None:
        path.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")

    save()
    try:
        before = {**files(ROOT / "resources/visual/stardew"), **files(ROOT / "resources/pokemon")} if stage == "prepare" else None
        before_pixels = {name: png_fingerprint(ROOT / name) for name in (before or {}) if name.endswith(".png")}
        if stage == "native":
            if git("status", "--porcelain", "--untracked-files=all"):
                raise RuntimeError("Acceptance requires a clean committed checkpoint; use individual native scripts for development diagnostics")
            report = verify_build()
            receipt.update(jar_sha256=report["output_sha256"], sources_sha256=report["sources_sha256"])
        commands = ([["tools/prepare_stardew_presentation.py"], ["tools/prepare_pmd_compatibility.py"]] if stage == "prepare" else
                    [[name] for name in PURE] + [["build.py"]] if stage == "build" else NATIVE)
        for args in commands:
            if not (ROOT / args[0]).is_file():
                raise RuntimeError("Required verification is absent: " + args[0])
            log_path = OUT / (Path(args[0]).stem + ".log")
            script_hash = digest(ROOT / args[0])
            print("RUN:", " ".join(args), flush=True)
            with log_path.open("w", encoding="utf-8") as log:
                result = subprocess.run([sys.executable, *args], cwd=ROOT, stdout=log,
                                        stderr=subprocess.STDOUT, timeout=900)
            check = {"command": args, "exit_code": result.returncode,
                     "script_sha256": script_hash, "log_sha256": digest(log_path),
                     "log": log_path.relative_to(ROOT).as_posix()}
            receipt["checks"].append(check)
            save()
            if script_hash != digest(ROOT / args[0]):
                raise RuntimeError("Test script changed while it was running: " + args[0])
            if result.returncode:
                print(log_path.read_text(encoding="utf-8", errors="replace"))
                raise RuntimeError("Verification failed: " + args[0])
        if stage == "prepare":
            after = {**files(ROOT / "resources/visual/stardew"), **files(ROOT / "resources/pokemon")}
            if not before or before != after:
                preparation_diff(before or {}, after, before_pixels, receipt)
                save()
                raise RuntimeError("Asset preparation differs from tracked input; review and commit generated assets first")
            tracked = set(git("ls-files", "resources/visual/stardew", "resources/pokemon").splitlines())
            if not set(after).issubset(tracked):
                raise RuntimeError("Generated assets are not all tracked")
            receipt["asset_files_sha256"] = after
        else:
            report = verify_build()
            if stage == "native" and receipt["jar_sha256"] != report["output_sha256"]:
                raise RuntimeError("JAR changed while native verification was running")
            receipt.update(jar_sha256=report["output_sha256"], sources_sha256=report["sources_sha256"])
        if git("rev-parse", "HEAD") != receipt["commit"]:
            raise RuntimeError("HEAD changed while verification was running; rerun at the final checkpoint")
        if stage == "native" and git("status", "--porcelain", "--untracked-files=all"):
            raise RuntimeError("Working tree changed during native verification; commit and rerun the final checkpoint")
        receipt["status"] = "passed"
    except Exception as error:
        receipt.update(status="failed", error=str(error))
        raise
    finally:
        save()
    print("PASS:", path, flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", choices=("prepare", "build", "native", "all"), default="all")
    args = parser.parse_args()
    for stage in (("prepare", "build", "native") if args.stage == "all" else (args.stage,)):
        run_stage(stage)


if __name__ == "__main__":
    main()
