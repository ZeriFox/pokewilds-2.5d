#!/usr/bin/env python3
"""Native special-encounter presentation test; never changes battle rules."""
import argparse
import os
from pathlib import Path
import subprocess
from smoke_test import CREATE_FLAGS, ROOT, JAR, find_jdk, sha256


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--classes-override",type=Path);args=parser.parse_args()
    out=ROOT/"build/boss-battle-visual";classes=out/("diagnostic-classes" if args.classes_override else "bundled-classes")
    classes.mkdir(parents=True,exist_ok=True)
    runtime=[classes]
    if args.classes_override:runtime.extend([args.classes_override.resolve(),ROOT/"resources"])
    runtime.append(JAR);cp=os.pathsep.join(map(str,runtime));java,javac=find_jdk()
    source=[ROOT/"src/com/pkmngen/game"/name for name in (
        "PmdPokemonSprites.java","PmdBattleSprites.java","JohtoBattleRenderer.java","RegigigasBattle.java",
        "SpecialBattleMewtwo.java","SpecialBattleMegaGengar.java","SpecialMegaGengar1.java","Specie.java","ThrowOutPokemon.java")]
    with(out/"boss-battle-visual.log").open("w",encoding="utf-8")as log:
        log.write(f"Override: {args.classes_override}\nJAR: {JAR}\nSHA-256: {sha256(JAR)}\nJDK: {java}\n");log.flush()
        for command in [
            [javac,"--release","17","-encoding","UTF-8","-proc:none","-cp",cp,"-d",str(classes),str(ROOT/"tools/BossBattleVisualTest.java"),*map(str,source if args.classes_override else [])],
            [java,"-Xmx1g","-Dfile.encoding=UTF-8","-Dpokewilds.visual=johto","-Dpokewilds.models=off","-cp",cp,"com.pkmngen.game.BossBattleVisualTest"],
        ]:
            result=subprocess.run(command,cwd=out,stdout=log,stderr=subprocess.STDOUT,timeout=150,creationflags=CREATE_FLAGS)
            if result.returncode:print((out/"boss-battle-visual.log").read_text(encoding="utf-8"));return result.returncode
    print((out/"boss-battle-visual.log").read_text(encoding="utf-8"));return 0


if __name__=="__main__":raise SystemExit(main())
