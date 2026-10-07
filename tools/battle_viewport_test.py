#!/usr/bin/env python3
"""GPU resize/exit checks; source overrides are explicitly development-only."""
import argparse
import os
from pathlib import Path
import subprocess
from smoke_test import CREATE_FLAGS, ROOT, JAR, find_jdk, sha256


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--classes-override", type=Path)
    args = parser.parse_args()
    out = ROOT / "build/battle-viewport"
    classes = out / ("diagnostic-classes" if args.classes_override else "bundled-classes")
    classes.mkdir(parents=True, exist_ok=True)
    runtime = [classes]
    if args.classes_override:
        runtime.extend([args.classes_override.resolve(), ROOT / "resources"])
    runtime.append(JAR)
    cp = os.pathsep.join(map(str, runtime))
    production = [ROOT / "src/com/pkmngen/game" / name for name in (
        "PmdPokemonSprites.java", "PmdBattleSprites.java", "JohtoBattleRenderer.java", "DrawBattle.java")]
    java, javac = find_jdk()
    with (out / "battle-viewport.log").open("w", encoding="utf-8") as log:
        log.write(f"Override: {args.classes_override}\nJAR: {JAR}\nSHA-256: {sha256(JAR)}\nJDK: {java}\n")
        log.flush()
        commands = [
            [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", cp, "-d", str(classes),
             str(ROOT / "tools/BattleViewportTest.java"), *map(str, production if args.classes_override else [])],
            [java, "-Xmx1g", "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto", "-Dpokewilds.models=off",
             "-cp", cp, "com.pkmngen.game.BattleViewportTest"],
        ]
        for command in commands:
            result = subprocess.run(command, cwd=out, stdout=log, stderr=subprocess.STDOUT, timeout=150, creationflags=CREATE_FLAGS)
            if result.returncode:
                print((out / "battle-viewport.log").read_text(encoding="utf-8"))
                return result.returncode
    print((out / "battle-viewport.log").read_text(encoding="utf-8"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
