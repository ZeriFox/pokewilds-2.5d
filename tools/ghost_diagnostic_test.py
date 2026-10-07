#!/usr/bin/env python3
"""Original night-event trigger and identity regression; no production class overrides."""
import os
import subprocess
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256

def main():
    dest = ROOT / 'build/ghost-diagnostic'
    classes = dest / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    cp = os.pathsep.join(map(str, (classes, JAR)))
    path = dest / 'ghost-diagnostic.log'
    with path.open('w', encoding='utf-8') as log:
        log.write('JAR SHA256 ' + sha256(JAR) + '\n'); log.flush()
        for command in ([javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(JAR), '-d', str(classes), str(ROOT / 'tools/GhostDiagnosticTest.java')],
                        [java, '-Xmx2g', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off', '-cp', cp, 'com.pkmngen.game.GhostDiagnosticTest']):
            subprocess.run(command, cwd=dest, stdout=log, stderr=subprocess.STDOUT, timeout=90, creationflags=CREATE_FLAGS, check=True)
    output = path.read_text(encoding='utf-8')
    if 'GHOST DIAGNOSTIC PASS:' not in output or 'Exception' in output or '\tat ' in output:
        raise RuntimeError('Ghost diagnosis incomplete: ' + str(path))
    print('Ghost diagnostic PASS:', path)
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
