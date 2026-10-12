#!/usr/bin/env python3
"""Native nature animation proof: actual shaders, fixed times, seams and safe state."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true', help='Diagnostic only: compile current sources and load source resources')
    args = parser.parse_args()
    destination = ROOT / 'build/nature-animation'
    classes = destination / ('source-classes' if args.sources else 'jar-classes')
    if destination.is_symlink() or classes.is_symlink():
        raise RuntimeError('Test output must not be a link')
    classes.mkdir(parents=True, exist_ok=True)
    expected_output = (ROOT / 'build/nature-animation').resolve()
    for image in destination.glob('*.png'):
        if image.is_symlink() or image.resolve().parent != expected_output:
            raise RuntimeError('Unexpected image path in test output')
        image.unlink()
    java, javac = find_jdk()
    sources = [ROOT / 'tools/NatureAnimationTest.java']
    if args.sources:
        sources += sorted((ROOT / 'src').rglob('*.java'))
    response = destination / 'sources.txt'
    response.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf-8')
    classpath = [classes]
    if args.sources:
        classpath.append(ROOT / 'resources')
    classpath.append(JAR)
    cp = os.pathsep.join(map(str, classpath))
    commands = [
        [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(classes), '@' + str(response)],
        [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
         '-cp', cp, 'com.pkmngen.game.NatureAnimationTest'],
    ]
    logpath = destination / 'nature-animation.log'
    receipt_path = destination / 'receipt.json'
    receipt = {'status': 'running', 'jar_sha256': sha256(JAR), 'source_overrides': args.sources,
               'harness_sha256': sha256(ROOT / 'tools/NatureAnimationTest.java'),
               'limits': 'Controlled unsaved scenes on the available GPU; not a full playthrough or performance benchmark.'}
    receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    with logpath.open('w', encoding='utf-8') as log:
        log.write(f'JAR SHA256 {receipt["jar_sha256"]}\nSource overrides={args.sources}\n')
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=240, creationflags=CREATE_FLAGS)
            log.write(f'Exit code {result.returncode}\n')
            if result.returncode:
                receipt.update(status='failed', exit_code=result.returncode)
                receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
                print(f'FAIL: {logpath}')
                return result.returncode
    output = logpath.read_text(encoding='utf-8')
    if ('NATURE ANIMATION PASS:' not in output or len(list(destination.glob('*.png'))) != 33
            or re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(', output)):
        receipt.update(status='failed', reason='Incomplete native evidence')
        receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
        raise RuntimeError(f'Incomplete native test: {logpath}')
    receipt.update(status='passed', log_sha256=sha256(logpath),
                   images={p.name: sha256(p) for p in sorted(destination.glob('*.png'))})
    receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(f'PASS: {logpath}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
