#!/usr/bin/env python3
"""Invisible real procedural world check; outputs only under build/world-smoke.

Default: Johto presentation. --classic: unchanged rendering. Uses the original
S generator, real InputProcessor, and original save/load paths. Requires desktop
OpenGL. Does not exercise battles, building, interiors, or extended gameplay.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys

from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classic", action="store_true")
    args = parser.parse_args()
    mode = "classic" if args.classic else "johto"
    destination = ROOT / "build" / "world-smoke" / mode
    if destination.resolve() != destination or destination.is_symlink():
        raise RuntimeError("Il test richiede una directory locale build/world-smoke senza collegamenti.")
    destination.mkdir(parents=True, exist_ok=True)
    classes = destination / "classes"
    classes.mkdir(exist_ok=True)
    if classes.is_symlink():
        raise RuntimeError("La directory classes non puo essere un collegamento.")
    screenshots = [destination / name for name in ("world-generated.png", "world-loaded.png")]
    if not args.classic:
        screenshots.append(destination / "world-emote.png")
    for screenshot in screenshots:
        screenshot.unlink(missing_ok=True)
    if not JAR.is_file():
        raise RuntimeError("Esegui prima build.cmd: dist/pokewilds-rebuilt.jar assente.")
    java, javac = find_jdk()
    log_path = destination / "world-smoke.log"
    print(f"World smoke test {mode}: {log_path}", flush=True)
    with log_path.open("w", encoding="utf-8", newline="\n") as log:
        log.write(f"JAR: {JAR}\nSHA-256: {sha256(JAR)}\nVisual: {mode}\n")
        commands = [
            ("Compile harness", [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none",
                "-classpath", str(JAR), "-d", str(classes), str(ROOT / "tools" / "WorldSmokeTest.java")]),
            ("Generate/render/move/save/load", [java, "-Xmx2g", "-Dfile.encoding=UTF-8",
                f"-Dpokewilds.visual={mode}", "-classpath", os.pathsep.join((str(classes), str(JAR))),
                "com.pkmngen.game.WorldSmokeTest"]),
        ]
        for label, command in commands:
            log.write(f"\n{label}\n")
            log.flush()
            try:
                result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                    timeout=300, creationflags=CREATE_FLAGS, check=False)
            except subprocess.TimeoutExpired:
                log.write("WORLD FAIL: timeout esterno (300s)\n")
                print(f"Timeout. Dettagli: {log_path}", file=sys.stderr)
                return 124
            log.write(f"Exit code: {result.returncode}\n")
            log.flush()
            if result.returncode:
                print(f"Test fallito: {label}. Dettagli: {log_path}", file=sys.stderr)
                return result.returncode
        if not all(screenshot.is_file() for screenshot in screenshots):
            raise RuntimeError("Screenshot del mondo assenti.")
    print("Verificati: generazione S, rendering, un movimento, salvataggio/caricamento.")
    print(f"Screenshot finale: {destination / 'world-loaded.png'}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError) as error:
        print(f"World smoke test: {error}", file=sys.stderr)
        sys.exit(1)
