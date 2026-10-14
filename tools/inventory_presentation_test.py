#!/usr/bin/env python3
"""Verify real inventory UI and shiny effects through the packaged game."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources',action='store_true',help='Diagnostic current production source overrides')
    args=parser.parse_args()
    out=ROOT/'build'/('inventory-presentation-diagnostic' if args.sources else 'inventory-presentation')
    classes=out/'classes'
    if out.is_symlink() or classes.is_symlink():raise RuntimeError('Unexpected output directory')
    if classes.exists():
        if classes.resolve().parent!=out.resolve() or out.resolve().parent!=ROOT.resolve()/'build':raise RuntimeError('Unsafe output cleanup')
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    expected={'bag-pocket-0.png','bag-pocket-1.png','bag-pocket-2.png','party-status.png','summary-shiny.png'}
    if out.resolve().parent!=ROOT.resolve()/'build':raise RuntimeError('Unexpected fixture path')
    for name in expected:
        image=out/name
        if image.is_symlink():raise RuntimeError('Linked screenshot path')
        image.unlink(missing_ok=True)
    sources=[ROOT/'tools/InventoryPresentationTest.java']
    if args.sources:sources+=sorted((ROOT/'src').rglob('*.java'))
    cp=os.pathsep.join(map(str,[classes,*([ROOT/'resources'] if args.sources else []),JAR]))
    java,javac=find_jdk()
    response=out/'sources.txt'
    response.write_text('\n'.join(chr(34)+str(p).replace(chr(92),'/')+chr(34) for p in sources),encoding='utf8')
    jar_hash=sha256(JAR)
    log=out/'native.log'
    with log.open('w',encoding='utf-8') as stream:
        stream.write('Source overrides='+str(args.sources)+'\nJAR SHA256 '+jar_hash+'\n')
        for index,command in enumerate(([javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),'@'+str(response)],
                        [java,'-Xmx2g','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',cp,'com.pkmngen.game.InventoryPresentationTest'])):
            if index and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    assert not any(p.relative_to(classes).as_posix() in archive.namelist() for p in classes.rglob('*.class')),'Production overrides'
            stream.flush()
            result=subprocess.run(command,cwd=out,stdout=stream,stderr=subprocess.STDOUT,creationflags=CREATE_FLAGS,timeout=180)
            if result.returncode:print('FAIL:',log);return result.returncode
    if 'INVENTORY PRESENTATION PASS:' not in log.read_text(encoding='utf8'):raise RuntimeError('Incomplete native evidence')
    assert all((out/name).is_file() for name in expected)
    assert sha256(JAR)==jar_hash,'Build changed during proof'
    (out/'receipt.json').write_text(json.dumps({'status':'passed','source_overrides':args.sources,'jar_sha256':jar_hash,
        'log_sha256':sha256(log),'images':{name:sha256(out/name) for name in sorted(expected)}},indent=2)+'\n',encoding='utf8')
    print('PASS:',log)
    return 0

if __name__=='__main__':raise SystemExit(main())
