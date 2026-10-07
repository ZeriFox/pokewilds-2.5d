#!/usr/bin/env python3
"""Check complete Game.render field actions against only the delivered JAR."""
import os, subprocess
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256

def main():
    dest=ROOT/'build/field-scenes'
    if dest.is_symlink(): raise RuntimeError('Test destination must not be a link')
    classes=dest/'classes'; classes.mkdir(parents=True,exist_ok=True)
    java,javac=find_jdk(); cp=os.pathsep.join(map(str,(classes,JAR)))
    path=dest/'field-scenes.log'
    with path.open('w',encoding='utf-8') as log:
        log.write(f'JAR SHA256 {sha256(JAR)}\n');log.flush()
        for cmd in ([javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),str(ROOT/'tools/FieldSceneTest.java')],
                    [java,'-Xmx2g','-Dfile.encoding=UTF-8','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',cp,'com.pkmngen.game.FieldSceneTest']):
            result=subprocess.run(cmd,cwd=dest,stdout=log,stderr=subprocess.STDOUT,timeout=110,creationflags=CREATE_FLAGS)
            log.flush()
            if result.returncode: print(path); return result.returncode
    output=path.read_text(encoding='utf-8')
    if 'FIELD PASS:' not in output or 'Exception' in output or '\tat ' in output: raise RuntimeError('Field test incomplete: '+str(path))
    print('Field actions PASS:',path)
    return 0
if __name__=='__main__': raise SystemExit(main())
