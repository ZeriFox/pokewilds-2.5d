#!/usr/bin/env python3
"""Native collision/topology fixtures plus a real generated island and save/load.

--sources compiles only the generator changes over the current JAR for development.
Omit it to test exclusively the delivered JAR. All state stays in build/modern-world.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", action="store_true")
    parser.add_argument("--fixtures-only", action="store_true")
    args = parser.parse_args()
    destination = ROOT / "build" / "modern-world"
    classes = destination / ("source-classes" if args.sources else "jar-classes")
    if destination.is_symlink() or classes.is_symlink():
        raise RuntimeError("Test output must not be a symlink")
    classes.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    sources = [ROOT / "tools" / "ModernWorldGenerationTest.java", ROOT / "tools" / "WorldSmokeTest.java"]
    if args.sources:
        sources += [ROOT / "src/com/pkmngen/game" / name for name in ("ModernWorldGenerator.java", "GenIsland1.java", "BiomeProfiles.java", "WildSpawnRules.java", "Pokemon.java", "WorldElevation.java")]
    log_path = destination / "modern-world.log"
    commands = [[javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-classpath", str(JAR),
                 "-d", str(classes), *map(str, sources)]]
    base = [java, "-Xmx2g", "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto", "-Dpokewilds.models=off",
            "-classpath", os.pathsep.join([str(classes)] + ([str(ROOT / "resources")] if args.sources else []) + [str(JAR)])]
    commands.append(base + ["com.pkmngen.game.ModernWorldGenerationTest"])
    if not args.fixtures_only:
        commands.append(base + ["com.pkmngen.game.WorldSmokeTest"])
    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"JAR={JAR}\nSHA256={sha256(JAR)}\nSource overrides={args.sources}\n")
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=300, creationflags=CREATE_FLAGS)
            if result.returncode:
                print(f"FAIL: {log_path}")
                return result.returncode
    output = log_path.read_text(encoding="utf-8")
    if "TOPOLOGY PASS:" not in output or not args.fixtures_only and "WORLD PASS:" not in output:
        raise RuntimeError(f"Missing PASS result: {log_path}")
    reports = re.findall(r"WORLDGEN v1 .*?dryComponents=(\d+)->(\d+).*?volcanic=(\d+).*?lava=(\d+)", output)
    if not reports or any(int(after) > int(before) or int(volcanic) < 100 or int(lava) < 20
                          for before, after, volcanic, lava in reports):
        raise RuntimeError(f"World topology regression: {reports}")
    print(f"PASS: {log_path}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
