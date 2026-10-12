#!/usr/bin/env python3
"""Real-GL readability fixtures against the exact game JAR, not replaced classes."""
from pathlib import Path
import os, subprocess
from smoke_test import ROOT, JAR, find_jdk, sha256, CREATE_FLAGS

def main():
    target=ROOT/'build/world-readability';classes=target/'classes'
    if target.is_symlink() or classes.is_symlink(): raise RuntimeError('Linked test output refused')
    classes.mkdir(parents=True,exist_ok=True)
    java,javac=find_jdk()
    commands=[
        [javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',str(JAR),'-d',str(classes),str(ROOT/'tools/WorldReadabilityTest.java')],
        [java,'-Xmx2g','-Dfile.encoding=UTF-8','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',os.pathsep.join((str(classes),str(JAR))),'com.pkmngen.game.WorldReadabilityTest']]
    path=target/'world-readability.log'
    with path.open('w',encoding='utf-8') as log:
        log.write('Built JAR SHA256: '+sha256(JAR)+'\nProduction overrides: none\n')
        for command in commands:
            log.flush();result=subprocess.run(command,cwd=target,stdout=log,stderr=subprocess.STDOUT,timeout=180,creationflags=CREATE_FLAGS)
            if result.returncode:
                print(path.read_text(encoding='utf-8',errors='replace'));raise RuntimeError('Native readability verification failed')
    text=path.read_text(encoding='utf-8',errors='replace')
    if 'READABILITY PASS:' not in text: raise RuntimeError('Missing completion marker')
    print(text)

if __name__=='__main__': main()
