#!/usr/bin/env python3
"""Verify the opening and spirit artwork in an isolated hidden native window."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources',action='store_true')
    args=parser.parse_args()
    dest=ROOT/'build'/('cinematic-opening-diagnostic' if args.sources else 'cinematic-opening')
    classes=dest/('source-classes' if args.sources else 'jar-classes')
    classes.mkdir(parents=True,exist_ok=True)
    java,javac=find_jdk()
    sources=[ROOT/'tools/CinematicOpeningTest.java']
    if args.sources:sources+=sorted((ROOT/'src').rglob('*.java'))
    response=dest/'sources.txt'
    response.write_text('\n'.join('"'+p.as_posix()+'"' for p in sources),encoding='utf-8')
    cp=os.pathsep.join(map(str,[classes]+([ROOT/'resources'] if args.sources else [])+[JAR]))
    jar_hash=sha256(JAR)
    (dest/'receipt.json').write_text(json.dumps({'status':'running','jar_sha256':jar_hash,'source_overrides':args.sources},indent=2)+'\n',encoding='utf-8')
    logpath=dest/'cinematic-opening.log'
    with logpath.open('w',encoding='utf-8') as log:
        log.write(f'JAR SHA256 {jar_hash}\nSource overrides={args.sources}\n');log.flush()
        for i,cmd in enumerate([[javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),'@'+str(response)],
                              [java,'-Xmx2g','-Dfile.encoding=UTF-8','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',cp,'com.pkmngen.game.CinematicOpeningTest']]):
            if i and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    names=set(archive.namelist())
                    if any(p.relative_to(classes).as_posix() in names for p in classes.rglob('*.class')):raise RuntimeError('Production override in JAR test')
            result=subprocess.run(cmd,cwd=dest,stdout=log,stderr=subprocess.STDOUT,creationflags=CREATE_FLAGS,timeout=240)
            log.write(f'Exit code {result.returncode}\n');log.flush()
            if result.returncode:print(f'FAIL: {logpath}');return result.returncode
    if sha256(JAR)!=jar_hash or 'CINEMATIC OPENING PASS:' not in logpath.read_text(encoding='utf-8'):raise RuntimeError('Incomplete cinematic proof')
    images=sorted(dest.glob('*.png'))
    if len(images)!=8:raise RuntimeError('Expected three opening, one menu and four ghost captures')
    (dest/'receipt.json').write_text(json.dumps({'status':'passed','jar_sha256':jar_hash,'source_overrides':args.sources,'log_sha256':sha256(logpath),'images':{p.name:sha256(p) for p in images}},indent=2)+'\n',encoding='utf-8')
    print(f'PASS: {logpath}');return 0

if __name__=='__main__':raise SystemExit(main())
