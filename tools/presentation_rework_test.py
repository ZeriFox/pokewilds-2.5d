#!/usr/bin/env python3
"""Check native presentation using only the built game JAR plus the test harness."""
from pathlib import Path
import os
import subprocess
import sys
from smoke_test import ROOT,JAR,find_jdk,sha256,CREATE_FLAGS


def main():
    destination=ROOT/'build/presentation-rework'
    classes=destination/'classes'
    classes.mkdir(parents=True,exist_ok=True)
    for previous in destination.glob('*.png'):
        previous.unlink()
    java,javac=find_jdk()
    cp=os.pathsep.join((str(classes),str(JAR)))
    logpath=destination/'presentation-rework.log'
    with logpath.open('w',encoding='utf-8') as log:
        log.write('Exact JAR SHA256: '+sha256(JAR)+'\n');log.flush()
        commands=[
            [javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',str(JAR),'-d',str(classes),str(ROOT/'tools/PresentationReworkTest.java')],
            [java,'-Xmx2g','-Dfile.encoding=UTF-8','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',cp,'com.pkmngen.game.PresentationReworkTest'],
        ]
        for command in commands:
            completed=subprocess.run(command,cwd=destination,stdout=log,stderr=subprocess.STDOUT,timeout=120,creationflags=CREATE_FLAGS)
            log.flush()
            if completed.returncode:
                print(logpath.read_text(encoding='utf-8'));return completed.returncode
    content=logpath.read_text(encoding='utf-8')
    print(content)
    if 'PRESENTATION REWORK PASS:' not in content:
        raise RuntimeError('Native test completed without a success marker')
    return 0

if __name__=='__main__':sys.exit(main())
