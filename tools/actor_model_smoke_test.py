#!/usr/bin/env python3
"""Verify real 3D player/Machop models in an isolated deterministic desktop fixture.

Writes untouched game framebuffer captures for four idle directions, four walking
directions, and after an original save/load round trip. This test does not prove
procedural generation: use world_smoke_test.py for that separate integration check.
"""
from __future__ import annotations

import os
import subprocess
import sys

from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def main() -> int:
    destination = ROOT / "build" / "actor-model-smoke"
    if destination.resolve() != destination or destination.is_symlink():
        raise RuntimeError("Il test richiede build/actor-model-smoke locale, senza collegamenti.")
    destination.mkdir(parents=True, exist_ok=True)
    classes = destination / "classes"
    if classes.is_symlink():
        raise RuntimeError("La cartella classes non puo essere un collegamento.")
    classes.mkdir(exist_ok=True)
    screenshots = [destination / f"actor-{state}-{direction}.png"
                   for state in ("idle", "walk") for direction in ("down", "right", "up", "left")]
    screenshots.append(destination / "actor-loaded.png")
    for screenshot in screenshots:
        screenshot.unlink(missing_ok=True)
    if not JAR.is_file():
        raise RuntimeError("Manca dist/pokewilds-rebuilt.jar: esegui prima build.cmd.")
    java, javac = find_jdk()
    log_path = destination / "actor-model-smoke.log"
    print(f"Actor model smoke test: {log_path}", flush=True)
    with log_path.open("w", encoding="utf-8", newline="\n") as log:
        log.write(f"JAR: {JAR}\nSHA-256: {sha256(JAR)}\nVisual: johto; models: actual 3D\n")
        commands = [
            ("Compile harness", [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none",
                "-classpath", str(JAR), "-d", str(classes), str(ROOT / "tools" / "ActorModelSmokeTest.java")]),
            ("Render models and verify original movement/save/load", [java, "-Xmx2g", "-Dfile.encoding=UTF-8",
                "-Dpokewilds.visual=johto", "-Dpokewilds.models=on", "-classpath", os.pathsep.join((str(classes), str(JAR))),
                "com.pkmngen.game.ActorModelSmokeTest"]),
        ]
        for label, command in commands:
            log.write(f"\n{label}\n")
            log.flush()
            try:
                result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                    timeout=120, creationflags=CREATE_FLAGS, check=False)
            except subprocess.TimeoutExpired:
                log.write("ACTOR FAIL: timeout esterno (120s)\n")
                print(f"Timeout. Dettagli: {log_path}", file=sys.stderr)
                return 124
            log.write(f"Exit code: {result.returncode}\n")
            log.flush()
            if result.returncode:
                print(f"Test fallito: {label}. Dettagli: {log_path}", file=sys.stderr)
                return result.returncode
        if not all(screenshot.is_file() for screenshot in screenshots):
            raise RuntimeError("Mancano screenshot nativi delle pose richieste.")
    print("Verificati modelli 3D, quattro direzioni, movimento originale e salvataggio/caricamento.")
    print(f"Screenshot: {destination}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError) as error:
        print(f"Actor model smoke test: {error}", file=sys.stderr)
        sys.exit(1)
