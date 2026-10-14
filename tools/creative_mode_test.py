#!/usr/bin/env python3
"""Native creative catalog, inventory, multiplayer guards and real disposable save round-trip."""
import argparse
import json
import os
import re
import shutil
import subprocess
import zipfile
from smoke_test import ROOT,JAR,find_jdk,sha256,CREATE_FLAGS

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--sources',action='store_true');args=parser.parse_args()
    target=ROOT/'build'/('creative-mode-diagnostic' if args.sources else 'creative-mode');classes=target/'classes'
    if target.is_symlink() or classes.is_symlink() or target.resolve()!=ROOT.resolve()/'build'/target.name:raise RuntimeError('Unexpected fixture path')
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir(parents=True,exist_ok=True)
    for entry in target.iterdir():
        if entry.name.startswith('creative-fixture.sav') or entry.name in ('creative-catalog.png','interface-settings.json'):
            if entry.is_symlink() or entry.resolve().parent!=target.resolve():raise RuntimeError('Unexpected fixture output')
            if entry.is_dir():shutil.rmtree(entry)
            else:entry.unlink()
    java,javac=find_jdk();sources=[ROOT/'tools/CreativeModeTest.java']
    if args.sources:sources+=sorted((ROOT/'src').rglob('*.java'))
    response=target/'sources.txt';response.write_text('\n'.join('"'+str(s).replace('\\','/')+'"' for s in sources),encoding='utf-8')
    cp=os.pathsep.join(map(str,[classes]+([ROOT/'resources'] if args.sources else [])+[JAR]));logpath=target/'creative-mode.log'
    receipt={'status':'running','jar_sha256':sha256(JAR),'source_overrides':args.sources,'harness_sha256':sha256(sources[0])}
    (target/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    commands=[[javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),'@'+str(response)],
              [java,'-Xmx2g','-Dfile.encoding=UTF-8','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',cp,'com.pkmngen.game.CreativeModeTest']]
    with logpath.open('w',encoding='utf-8') as log:
        for i,command in enumerate(commands):
            if i and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    overlap=[f.relative_to(classes).as_posix() for f in classes.rglob('*.class') if f.relative_to(classes).as_posix() in archive.namelist()]
                if overlap:raise RuntimeError('Harness shadows production classes: '+str(overlap))
            result=subprocess.run(command,cwd=target,stdout=log,stderr=subprocess.STDOUT,timeout=240,creationflags=CREATE_FLAGS)
            if result.returncode:raise RuntimeError(f'Creative proof failed: {logpath}')
    output=logpath.read_text(encoding='utf-8');evidence=re.search(r'CREATIVE MODE PASS: checks=(\d+), captures=(\d+)',output)
    if not evidence or int(evidence[2])!=1 or sha256(JAR)!=receipt['jar_sha256']:raise RuntimeError('Incomplete creative evidence')
    receipt.update(status='passed',checks=int(evidence[1]),captures=1,log_sha256=sha256(logpath),image_sha256=sha256(target/'creative-catalog.png'))
    (target/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8');print('PASS:',logpath)

if __name__=='__main__':main()
