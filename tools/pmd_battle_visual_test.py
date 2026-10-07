#!/usr/bin/env python3
"""Capture original presentation actions; --classes-override is diagnostic only."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
from smoke_test import CREATE_FLAGS, ROOT, JAR, find_jdk, sha256


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--classes-override",type=Path)
    args = parser.parse_args()
    out = ROOT / "build/pmd-battle-visual"
    classes = out / ("diagnostic-classes" if args.classes_override else "bundled-classes"); classes.mkdir(parents=True,exist_ok=True)
    runtime = [classes]
    if args.classes_override: runtime.extend([args.classes_override.resolve(),ROOT / "resources"])
    runtime.append(JAR)
    cp = os.pathsep.join(map(str,runtime))
    production = [str(ROOT / "src/com/pkmngen/game" / name) for name in (
        "PmdPokemonSprites.java", "PmdBattleSprites.java", "JohtoBattleRenderer.java", "DrawBattle.java",
        "EvolutionAnim.java", "EggHatchAnim.java", "PokemonFrame.java",
        "FriendlyFaint.java", "EnemyFaint.java", "ThrowOutPokemonCrystal.java", "util/SpriteProxy.java")]
    java,javac = find_jdk()
    with (out / "pmd-battle-visual.log").open("w",encoding="utf-8") as log:
        log.write("Override: " + str(args.classes_override) + "\nJAR: " + str(JAR) + "\nSHA-256: " + sha256(JAR) + "\nJDK: " + str(java) + "\n"); log.flush()
        for command in [
            [javac,"--release","17","-encoding","UTF-8","-proc:none","-cp",cp,"-d",str(classes),str(ROOT / "tools/PmdBattleVisualTest.java"),*(production if args.classes_override else [])],
            [java,"-Xmx1g","-Dfile.encoding=UTF-8","-Dpokewilds.visual=johto","-Dpokewilds.models=off","-cp",cp,"com.pkmngen.game.PmdBattleVisualTest"],
        ]:
            result = subprocess.run(command,cwd=out,stdout=log,stderr=subprocess.STDOUT,timeout=120,creationflags=CREATE_FLAGS)
            if result.returncode:
                print((out / "pmd-battle-visual.log").read_text(encoding="utf-8"));return result.returncode
    print((out / "pmd-battle-visual.log").read_text(encoding="utf-8"));return 0


if __name__ == "__main__": sys.exit(main())
