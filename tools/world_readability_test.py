#!/usr/bin/env python3
"""Unsaved native world regression. --sources compiles only this patch's renderer helpers."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
from smoke_test import CREATE_FLAGS, ROOT, JAR, find_jdk, sha256

SCREENSHOTS=('01-jump-ledges-and-cut-tiers.png','02-animated-shiny-field-companion.png','03-textured-interior.png')

def output_directory(source_mode):
    build=(ROOT/'build').resolve()
    out=ROOT/'build'/('world-readability-sources' if source_mode else 'world-readability')
    # Bound every temporary directory and unlink operation to this runner's
    # dedicated output. Reject directory junctions as well as symlinks.
    if out.is_symlink() or (hasattr(out,'is_junction') and out.is_junction()) or out.resolve().parent!=build:
        raise RuntimeError('Local bounded output directory required')
    out.mkdir(parents=True,exist_ok=True)
    for name in SCREENSHOTS:
        image=out/name
        if image.is_symlink() or image.resolve().parent!=out.resolve():
            raise RuntimeError('Screenshot path must remain inside test output')
        if image.exists():image.unlink()
    return out

def verify_test_classes(classes):
    emitted=[p.relative_to(classes).as_posix() for p in classes.rglob('*.class')]
    if not emitted or any(not re.fullmatch(r'com/pkmngen/game/WorldReadabilityTest(?:\$[^/]+)?\.class',name) for name in emitted):
        raise RuntimeError('Exact-JAR test produced a production class override: '+str(emitted))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources',action='store_true')
    args=parser.parse_args()
    out=output_directory(args.sources)
    sources=[ROOT/'tools/WorldReadabilityTest.java']
    if args.sources:
        sources += [ROOT/'src/com/pkmngen/game'/f'{name}.java' for name in
                    ('JohtoRenderer','WorldReadability','WorldElevation','WorldMaterials','UserSpriteAssets')]
    java,javac=find_jdk();jar_hash=sha256(JAR)
    log=out/'world-readability.log'
    # A unique empty class directory prevents stale source-mode classes from
    # shadowing the production JAR. The temporary directory owns only this run.
    with tempfile.TemporaryDirectory(prefix='native-classes-',dir=out) as temporary,log.open('w',encoding='utf-8') as stream:
        classes=Path(temporary).resolve()
        if classes.parent!=out.resolve():raise RuntimeError('Unexpected classes output path')
        empty_sourcepath=classes/'empty-sourcepath';empty_sourcepath.mkdir()
        cp=os.pathsep.join(map(str,[classes,*([ROOT/'resources'] if args.sources else []),JAR]))
        stream.write(f'JAR SHA256 {jar_hash}\nSource overrides={args.sources}\nRuntime classpath={cp}\n');stream.flush()
        commands=([javac,'--release','17','-encoding','UTF-8','-proc:none','-implicit:none','-sourcepath',str(empty_sourcepath),'-cp',str(JAR),'-d',str(classes),*map(str,sources)],
                  [java,'-Xmx2g','-Dfile.encoding=UTF-8','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',cp,'com.pkmngen.game.WorldReadabilityTest'])
        for index,command in enumerate(commands):
            result=subprocess.run(command,cwd=out,stdout=stream,stderr=subprocess.STDOUT,timeout=100,creationflags=CREATE_FLAGS)
            stream.flush()
            if result.returncode:print('FAIL:',log);return result.returncode
            if index==0 and not args.sources:verify_test_classes(classes)
    text=log.read_text(encoding='utf-8')
    if 'WORLD READABILITY PASS:' not in text or re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(',text):
        raise RuntimeError('Incomplete test: '+str(log))
    if sha256(JAR)!=jar_hash:raise RuntimeError('Production JAR changed during test')
    if any(not(out/name).is_file() or (out/name).stat().st_size==0 for name in SCREENSHOTS):
        raise RuntimeError('Missing screenshot from this test run')
    print('PASS:',log);return 0

if __name__=='__main__':raise SystemExit(main())
