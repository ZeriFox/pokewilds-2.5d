#!/usr/bin/env python3
"""Native sealed relief, textured cliff and directional stair verification."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true', help='Diagnostic production source overrides')
    parser.add_argument('--before', action='store_true', help='Capture old JAR; report previous corner metrics without accepting the new visual contract')
    args = parser.parse_args()
    if args.before and args.sources:
        parser.error('--before captures only the original packaged JAR')
    label = 'relief-before' if args.before else 'relief-presentation-diagnostic' if args.sources else 'relief-presentation'
    out = ROOT / 'build' / label
    classes = out / 'classes'
    if out.is_symlink() or classes.is_symlink() or out.resolve().parent != ROOT.resolve() / 'build':
        raise RuntimeError('Unsafe output path')
    if classes.exists():
        if classes.resolve().parent != out.resolve():
            raise RuntimeError('Unsafe class cleanup')
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    expected = {f'{i * 2 + night + 1:02d}-{biome}-{"day" if night == 0 else "night"}.png'
                for i, biome in enumerate(('mountain', 'desert', 'snow', 'forest')) for night in range(2)}
    expected.update(f'stairs-direction-{direction}.png' for direction in range(4))
    for name in expected:
        path = out / name
        if path.is_symlink():
            raise RuntimeError('Linked screenshot path')
        path.unlink(missing_ok=True)
    harness = ROOT / 'tools/ReliefPresentationTest.java'
    sources = [harness]
    if args.sources:
        sources += sorted((ROOT / 'src').rglob('*.java'))
    response = out / 'sources.txt'
    response.write_text('\n'.join('"' + str(path).replace('\\', '/') + '"' for path in sources), encoding='utf-8', newline='\n')
    cp = os.pathsep.join(map(str, [classes, *([ROOT / 'resources'] if args.sources else []), JAR]))
    java, javac = find_jdk()
    jar_hash, source_hash = sha256(JAR), sha256(harness)
    log = out / 'native.log'
    with log.open('w', encoding='utf-8') as stream:
        stream.write(f'Source overrides={args.sources}\nBefore diagnostic={args.before}\nJAR SHA256 {jar_hash}\n')
        commands = [[javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', cp,
                     '-d', str(classes), '@' + str(response)],
                    [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
                     '-Drelief.before=' + str(args.before).lower(), '-cp', cp, 'com.pkmngen.game.ReliefPresentationTest']]
        for index, command in enumerate(commands):
            if index and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    packaged = set(archive.namelist())
                if any(path.relative_to(classes).as_posix() in packaged for path in classes.rglob('*.class')):
                    raise RuntimeError('Production class override in packaged verification')
            stream.flush()
            result = subprocess.run(command, cwd=out, stdout=stream, stderr=subprocess.STDOUT,
                                    timeout=240, creationflags=CREATE_FLAGS)
            if result.returncode:
                print('FAIL:', log)
                return result.returncode
    content = log.read_text(encoding='utf-8')
    if 'RELIEF PRESENTATION PASS:' not in content or re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(', content):
        raise RuntimeError('Incomplete native evidence: ' + str(log))
    if not all((out / name).is_file() for name in expected):
        raise RuntimeError('Missing relief captures')
    if sha256(JAR) != jar_hash or sha256(harness) != source_hash:
        raise RuntimeError('JAR or harness changed during verification')
    receipt = {'status': 'before_diagnostic' if args.before else 'passed', 'production_overrides': args.sources,
               'jar_sha256': jar_hash, 'harness_sha256': source_hash, 'log_sha256': sha256(log),
               'images': {name: sha256(out / name) for name in sorted(expected)},
               'limits': 'Disposable unsaved scenes; this is not a full manual campaign.'}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('BASELINE CAPTURE:' if args.before else 'PASS:', log)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
