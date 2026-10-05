#!/usr/bin/env python3
"""Native B/W landscape, PMD sprites, relief, day/night and travel smoke test."""
import argparse,os,re,subprocess,sys
from pathlib import Path
from smoke_test import CREATE_FLAGS,JAR,ROOT,find_jdk,sha256

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classes-override',type=Path,action='append',default=[])
    args=parser.parse_args()
    destination=ROOT/'build'/'landscape-smoke';destination.mkdir(parents=True,exist_ok=True)
    if destination.is_symlink():raise RuntimeError('Test destination must not be a link')
    classes=destination/'classes';classes.mkdir(exist_ok=True)
    java,javac=find_jdk()
    runtime=[classes,*[p.resolve() for p in args.classes_override]]
    if args.classes_override:runtime.append(ROOT/'resources')
    runtime.append(JAR);cp=os.pathsep.join(map(str,runtime))
    logpath=destination/'landscape-smoke.log'
    with logpath.open('w',encoding='utf-8') as log:
        log.write(f'JAR SHA256 {sha256(JAR)}\nOverrides {args.classes_override}\n');log.flush()
        commands=[
            [javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),str(ROOT/'tools/LandscapeSmokeTest.java')],
            [java,'-Xmx2g','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-Dfile.encoding=UTF-8','-cp',cp,'com.pkmngen.game.LandscapeSmokeTest']]
        for command in commands:
            result=subprocess.run(command,cwd=destination,stdout=log,stderr=subprocess.STDOUT,timeout=120,creationflags=CREATE_FLAGS)
            log.write(f'Exit code {result.returncode}\n');log.flush()
            if result.returncode:print(logpath);return result.returncode
    content=logpath.read_text(encoding='utf-8')
    if re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(',content):raise RuntimeError('Exception logged: '+str(logpath))
    if 'LANDSCAPE PASS:' not in content:raise RuntimeError('Missing native success marker')
    print('Landscape native PASS:',destination)
    return 0
if __name__=='__main__':sys.exit(main())
