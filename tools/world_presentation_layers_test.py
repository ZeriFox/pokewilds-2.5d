#!/usr/bin/env python3
"""Verify material roles and modest ambient overlays with the exact built game JAR."""
from pathlib import Path
import json
import os
import subprocess
import sys
from smoke_test import CREATE_FLAGS, ROOT, JAR, find_jdk, sha256


def main() -> int:
    out = ROOT / 'build/world-presentation-layers'
    classes = out / 'classes'
    if out.is_symlink() or classes.is_symlink():
        raise RuntimeError('Test output must be a local directory')
    classes.mkdir(parents=True, exist_ok=True)
    for p in out.glob('*.png'):
        p.unlink()
    java, javac = find_jdk()
    cp = os.pathsep.join(map(str, (classes, JAR)))
    log = out / 'world-presentation-layers.log'
    receipt = {'jar_sha256': sha256(JAR), 'status': 'running', 'platform': sys.platform,
               'limits': 'Seven controlled on/off scenes; not a complete manual playthrough or all GPUs.'}
    path = out / 'receipt.json'
    path.write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf-8')
    with log.open('w', encoding='utf-8') as output:
        commands = [
            [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(JAR), '-d', str(classes),
             str(ROOT / 'tools/WorldPresentationLayersTest.java')],
            [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
             '-cp', cp, 'com.pkmngen.game.WorldPresentationLayersTest'],
        ]
        for command in commands:
            result = subprocess.run(command, cwd=out, stdout=output, stderr=subprocess.STDOUT,
                                    timeout=150, creationflags=CREATE_FLAGS)
            output.flush()
            if result.returncode:
                receipt.update(status='failed', exit_code=result.returncode)
                path.write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf-8')
                print(log.read_text(encoding='utf-8', errors='replace'))
                return result.returncode
    text = log.read_text(encoding='utf-8')
    if 'WORLD PRESENTATION LAYERS PASS:' not in text or len(list(out.glob('*.png'))) != 14:
        raise RuntimeError('Incomplete native evidence: '+str(log))
    receipt.update(status='passed', log_sha256=sha256(log),
                   images={p.name: sha256(p) for p in sorted(out.glob('*.png'))})
    path.write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf-8')
    print(text)
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
