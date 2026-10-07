#!/usr/bin/env python3
"""Native biome materials/elevation/atmosphere and real ghost lifecycle in an isolated directory."""
import argparse
import os
import re
import subprocess
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources",action="store_true",help="Compile production sources over the current runtime JAR")
    args=parser.parse_args()
    destination=ROOT/"build/biome-scenes"
    classes=destination/("source-classes" if args.sources else "jar-classes")
    if destination.is_symlink() or classes.is_symlink():raise RuntimeError("Test directories must not be symlinks")
    classes.mkdir(parents=True,exist_ok=True)
    java,javac=find_jdk()
    sources=[ROOT/"tools/BiomeSceneTest.java"]
    if args.sources:sources+=sorted((ROOT/"src").rglob("*.java"))
    response=destination/"sources.txt"
    response.write_text("\n".join('"'+str(path).replace('\\','/')+'"' for path in sources),encoding="utf-8")
    runtime=[classes]
    if args.sources:runtime.append(ROOT/"resources")
    runtime.append(JAR)
    cp=os.pathsep.join(map(str,runtime))
    logpath=destination/"biome-scenes.log"
    with logpath.open("w",encoding="utf-8",newline="\n") as log:
        log.write(f"JAR={JAR}\nSHA256={sha256(JAR)}\nSource overrides={args.sources}\n")
        commands=[
            [javac,"--release","17","-encoding","UTF-8","-proc:none","-cp",str(JAR),"-d",str(classes),"@"+str(response)],
            [java,"-Xmx2g","-Dfile.encoding=UTF-8","-Dpokewilds.visual=johto","-Dpokewilds.models=off","-cp",cp,"com.pkmngen.game.BiomeSceneTest"]]
        for command in commands:
            log.flush()
            result=subprocess.run(command,cwd=destination,stdout=log,stderr=subprocess.STDOUT,timeout=150,creationflags=CREATE_FLAGS)
            if result.returncode:print("FAIL:",logpath);return result.returncode
    output=logpath.read_text(encoding="utf-8")
    if "BIOME SCENE PASS:" not in output or re.search(r"(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(",output):
        raise RuntimeError("Incomplete native test: "+str(logpath))
    print("PASS:",logpath)
    return 0
if __name__=="__main__":raise SystemExit(main())
