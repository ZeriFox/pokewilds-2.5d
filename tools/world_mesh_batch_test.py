#!/usr/bin/env python3
"""Real OpenGL batch parity/lifecycle tests against the pinned pre-refactor batch.

Only test classes and a renamed reference renderer precede the built game JAR.
No production class or loose production asset is compiled as an override.
Whole-scene historical pixel parity is opt-in: the authored-terrain update
intentionally changes stairs, palette and billboard depth. New scene acceptance
is covered by terrain_readability_test; batch CPU/GPU byte parity stays mandatory.
"""
import argparse
from pathlib import Path
import hashlib
import os
import re
import subprocess
import sys
from smoke_test import CREATE_FLAGS, ROOT, JAR, find_jdk, sha256


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--historical-parity', action='store_true',
                        help='Also require the old step-1 scene pixels; only applicable to that historical refactor')
    args = parser.parse_args()
    if not JAR.is_file():
        raise RuntimeError('Build the game first with python build.py')
    out = ROOT / 'build/world-mesh-batch'
    if out.is_symlink():
        raise RuntimeError('Test output must be a local directory')
    out.mkdir(parents=True, exist_ok=True)
    classes = out / 'classes'
    classes.mkdir(exist_ok=True)
    for image in out.glob('*.png'):
        image.unlink()
    frozen = ROOT / 'baseline/java-mesh-step1/JohtoRenderer.java'
    raw = frozen.read_bytes()
    blob = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    if blob != 'ab51bc9c15c2312901da9e53ff1173f36c06e221':
        raise RuntimeError('Pre-refactor comparison source was changed')
    reference = out / 'ReferenceJohtoRenderer.java'
    reference.write_text(re.sub(r'\bJohtoRenderer\b', 'ReferenceJohtoRenderer', raw.decode('utf-8')), encoding='utf-8')
    java, javac = find_jdk()
    cp = os.pathsep.join(map(str, (classes, JAR)))
    log_path = out / 'world-mesh-batch.log'
    commands = [
        [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(JAR), '-d', str(classes),
         str(reference), str(ROOT / 'tools/WorldMeshBatchTest.java'), str(ROOT / 'tools/WorldRendererParityTest.java')],
        [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off', '-Dpokewilds.ambientOverlays=off', '-cp', cp,
         'com.pkmngen.game.WorldMeshBatchTest'],
    ]
    if args.historical_parity:
        commands.append([java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
                         '-Dpokewilds.ambientOverlays=off', '-cp', cp, 'com.pkmngen.game.WorldRendererParityTest'])
    with log_path.open('w', encoding='utf-8') as log:
        log.write('JAR SHA256 ' + sha256(JAR) + '\nPinned baseline blob ' + blob
                  + '\nBatch CPU/GPU parity required; historical whole-scene parity=' + str(args.historical_parity) + '\n')
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=out, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=180, creationflags=CREATE_FLAGS)
            log.write('Exit code ' + str(result.returncode) + '\n')
            log.flush()
            if result.returncode:
                print(log_path.read_text(encoding='utf-8', errors='replace'))
                return result.returncode
    content = log_path.read_text(encoding='utf-8')
    for marker in (('WORLD MESH BATCH PASS', 'WORLD RENDERER PARITY PASS') if args.historical_parity else ('WORLD MESH BATCH PASS',)):
        if marker not in content:
            raise RuntimeError('Missing native success marker: ' + marker)
    print(content)
    return 0


if __name__ == '__main__':
    sys.exit(main())
