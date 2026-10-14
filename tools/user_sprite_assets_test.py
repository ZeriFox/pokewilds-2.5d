#!/usr/bin/env python3
"""Verify supplied sheets through the native runtime loader (JAR by default)."""
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
    parser.add_argument('--sources',action='store_true',help='Diagnostic atlas loader override only')
    args=parser.parse_args()
    out=ROOT/'build'/('user-sprite-diagnostic' if args.sources else 'user-sprites')
    classes=out/'classes'
    if out.is_symlink() or classes.is_symlink():raise RuntimeError('Unexpected output directory')
    if out.resolve().parent!=ROOT.resolve()/'build':raise RuntimeError('Unsafe output directory')
    expected={'items-gallery.png','items-current-gallery.png','items-index-330-409.png','berries-gallery.png','balls-gallery.png','berry-growth.png'}
    # A successful proof must produce every capture during this invocation.
    for name in sorted(expected|{'receipt.json','native.log'}):
        previous=out/name
        if previous.is_symlink() or previous.resolve().parent!=out.resolve():raise RuntimeError('Unsafe previous output')
        if previous.exists():
            if not previous.is_file():raise RuntimeError('Unexpected previous output type')
            previous.unlink()
    if classes.exists():
        if classes.resolve().parent!=out.resolve() or out.resolve().parent!=ROOT.resolve()/'build':raise RuntimeError('Unsafe output cleanup')
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    sources=[ROOT/'tools/UserSpriteAssetsTest.java']
    if args.sources:sources.append(ROOT/'src/com/pkmngen/game/UserSpriteAssets.java')
    cp=os.pathsep.join(map(str,[classes,*([ROOT/'resources'] if args.sources else []),JAR]))
    java,javac=find_jdk()
    jar_hash=sha256(JAR)
    log=out/'native.log'
    with log.open('w',encoding='utf-8') as stream:
        stream.write('Source overrides='+str(args.sources)+'\nJAR SHA256 '+jar_hash+'\n')
        for index,command in enumerate(([javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),*map(str,sources)],
                        [java,'-cp',cp,'com.pkmngen.game.UserSpriteAssetsTest'])):
            if index and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    assert not any(p.relative_to(classes).as_posix() in archive.namelist() for p in classes.rglob('*.class')),'Production overrides'
            stream.flush()
            result=subprocess.run(command,cwd=out,stdout=stream,stderr=subprocess.STDOUT,creationflags=CREATE_FLAGS,timeout=180)
            if result.returncode:print('FAIL:',log);return result.returncode
    if 'USER SPRITES PASS:' not in log.read_text(encoding='utf8'):raise RuntimeError('Incomplete native evidence')
    assert all((out/name).is_file() for name in expected)
    assert sha256(JAR)==jar_hash,'Build changed during proof'
    (out/'receipt.json').write_text(json.dumps({'status':'passed','source_overrides':args.sources,'jar_sha256':jar_hash,
        'log_sha256':sha256(log),'images':{name:sha256(out/name) for name in sorted(expected)}},indent=2)+'\n',encoding='utf8')
    print('PASS:',log)
    return 0

if __name__=='__main__':raise SystemExit(main())
