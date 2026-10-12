#!/usr/bin/env python3
"""Native desktop menu event/focus proof; defaults to the final JAR without overrides."""
import argparse
import json
import os
import re
import shutil
import subprocess
import zipfile
from smoke_test import ROOT,JAR,find_jdk,sha256,CREATE_FLAGS

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sources',action='store_true');args=p.parse_args()
    target=ROOT/'build'/('desktop-menu-flow-diagnostic' if args.sources else 'desktop-menu-flow');classes=target/'classes'
    if target.is_symlink() or classes.is_symlink() or target.resolve()!=ROOT.resolve()/'build'/target.name:raise RuntimeError('Unexpected output path')
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir(parents=True,exist_ok=True)
    for path in list(target.glob('*.png'))+list(target.glob('appearance-profiles.json*'))+list(target.glob('interface-settings.json*')):
        if path.is_symlink() or path.resolve().parent!=target.resolve():raise RuntimeError('Unexpected fixture output')
        path.unlink()
    java,javac=find_jdk();sources=[ROOT/'tools/DesktopMenuFlowTest.java']
    if args.sources:sources+=sorted((ROOT/'src').rglob('*.java'))
    response=target/'sources.txt';response.write_text('\n'.join('"'+str(s).replace('\\','/')+'"' for s in sources),encoding='utf-8')
    cp=os.pathsep.join(map(str,[classes]+([ROOT/'resources'] if args.sources else [])+[JAR]));logpath=target/'desktop-menu-flow.log'
    receipt={'status':'running','jar_sha256':sha256(JAR),'source_overrides':args.sources,'harness_sha256':sha256(sources[0])}
    (target/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
    commands=[[javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),'@'+str(response)],
              [java,'-Xmx2g','-Dfile.encoding=UTF-8','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',cp,'com.pkmngen.game.DesktopMenuFlowTest']]
    with logpath.open('w',encoding='utf-8') as log:
        log.write(f'JAR SHA256 {receipt["jar_sha256"]}\nSource overrides={args.sources}\n')
        for i,command in enumerate(commands):
            if i and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    overlap=[f.relative_to(classes).as_posix() for f in classes.rglob('*.class') if f.relative_to(classes).as_posix() in archive.namelist()]
                if overlap:raise RuntimeError('Harness shadows production classes: '+str(overlap))
            log.flush();result=subprocess.run(command,cwd=target,stdout=log,stderr=subprocess.STDOUT,timeout=240,creationflags=CREATE_FLAGS)
            if result.returncode:raise RuntimeError(f'Desktop menu flow failed: {logpath}')
    output=logpath.read_text(encoding='utf-8');evidence=re.search(r'DESKTOP MENU FLOW PASS: (\d+) assertions; (\d+) captures;',output)
    images=sorted(target.glob('*.png'))
    if not evidence or int(evidence[2])!=10 or len(images)!=10 or sha256(JAR)!=receipt['jar_sha256']:raise RuntimeError('Incomplete desktop menu evidence')
    receipt.update(status='passed',checks=int(evidence[1]),captures=10,log_sha256=sha256(logpath),images={f.name:sha256(f) for f in images})
    (target/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8');print('PASS:',logpath)

if __name__=='__main__':main()
