#!/usr/bin/env python3
"""Verify every expansion species with real game data, GL sprites, audio and save/load."""
from __future__ import annotations
import argparse
import os
import subprocess
import sys
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", action="store_true", help="Development: compile dex sources and include resources over current JAR")
    args = parser.parse_args()
    destination = ROOT / "build/expansion-dex-test"
    classes = destination / ("source-classes" if args.sources else "jar-classes")
    if destination.is_symlink() or classes.is_symlink(): raise RuntimeError("Test folder must not be a symlink")
    classes.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    sources = [ROOT / "tools/ExpansionDexTest.java"]
    if args.sources:
        sources += [ROOT / "src/com/pkmngen/game" / name for name in
                    ("ExpansionDex.java", "Specie.java", "Pokemon.java", "Route.java", "PlaySound.java", "PlayMusic.java", "PmdPokemonSprites.java")]
    classpath = [str(classes)]
    if args.sources: classpath.append(str(ROOT / "resources"))
    classpath.append(str(JAR))
    log_path = destination / "expansion-dex-test.log"
    commands = [[javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-classpath", str(JAR),
                 "-d", str(classes), *map(str, sources)],
                [java, "-Xmx3g", "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto", "-Dpokewilds.models=off",
                 "-classpath", os.pathsep.join(classpath), "com.pkmngen.game.ExpansionDexTest"]]
    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"JAR={JAR}\nSHA256={sha256(JAR)}\nSource overrides={args.sources}\n")
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT, timeout=300, creationflags=CREATE_FLAGS)
            if result.returncode:
                print(f"FAIL: {log_path}"); return result.returncode
    output = log_path.read_text(encoding="utf-8")
    if "EXPANSION PASS:" not in output or "Exception" in output or "\tat " in output:
        raise RuntimeError(f"Incomplete test or swallowed exception: {log_path}")
    print(f"PASS: {log_path}")
    return 0

if __name__ == "__main__": sys.exit(main())
