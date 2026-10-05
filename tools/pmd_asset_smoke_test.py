#!/usr/bin/env python3
"""Exercise PMD rendering on native OpenGL, including all variants and LRU eviction."""
import os
from pathlib import Path
import subprocess
import sys
from smoke_test import find_jdk, sha256

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/pmd-asset-smoke"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / "classes"
    classes.mkdir(exist_ok=True)
    java, javac = find_jdk()
    jar = ROOT / "dist/pokewilds-rebuilt.jar"
    bundled = "--bundled" in sys.argv
    production = [] if bundled else [str(ROOT / "src/com/pkmngen/game/PmdPokemonSprites.java")]
    commands = [
        [str(javac), "--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", str(jar), "-d", str(classes),
         str(ROOT / "tools/PmdAssetSmokeTest.java"), *production],
        [str(java), "-Xmx512m", "-Dfile.encoding=UTF-8", "-cp",
         os.pathsep.join(str(x) for x in ([classes, jar] if bundled else [classes, ROOT / "resources", jar])), "PmdAssetSmokeTest"],
    ]
    # Separate harness classpath avoids stale source overrides during delivered-JAR checks.
    if bundled:
        for stale in (classes / "com/pkmngen/game").glob("PmdPokemonSprites*.class"):
            stale.unlink()
    with (OUT / "pmd-asset-smoke.log").open("w", encoding="utf-8") as log:
        log.write("Mode: " + ("delivered JAR only" if bundled else "new PMD source and loose resources") + "\n")
        log.write("JAR: " + str(jar) + "\nSHA-256: " + sha256(jar) + "\nJDK: " + str(java) + "\n")
        log.flush()
        for command in commands:
            result = subprocess.run(command, cwd=OUT, stdout=log, stderr=subprocess.STDOUT, timeout=300,
                                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
            if result.returncode:
                print((OUT / "pmd-asset-smoke.log").read_text(encoding="utf-8"))
                return result.returncode
    print((OUT / "pmd-asset-smoke.log").read_text(encoding="utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
