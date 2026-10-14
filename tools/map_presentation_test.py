#!/usr/bin/env python3
"""Native MAP menu input, actual pixels and resize checks in an unsaved fixture."""
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
    args = parser.parse_args()
    out = ROOT / 'build' / ('map-presentation-diagnostic' if args.sources else 'map-presentation')
    classes = out / 'classes'
    if out.is_symlink() or classes.is_symlink() or out.resolve().parent != ROOT.resolve() / 'build':
        raise RuntimeError('Unsafe native map output path')
    if classes.exists():
        if classes.resolve().parent != out.resolve():
            raise RuntimeError('Unsafe class cleanup')
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    expected = ('01-map-open.png', '02-map-pan-east.png', '03-map-pan-north.png', '04-map-drag.png',
                '05-map-reopened.png', '06-map-wide.png', '07-map-portrait.png', '08-map-portrait-reopened.png',
                '09-map-warp-question.png', '10-map-warp-cancelled.png')
    for path in [*out.glob('*.png'), out / 'receipt.json']:
        if path.is_symlink():
            raise RuntimeError('Linked map evidence path')
        path.unlink(missing_ok=True)
    if list(out.glob('*.sav')):
        raise RuntimeError('Native MAP fixture must not contain saves')
    harness = ROOT / 'tools/MapPresentationTest.java'
    sources = [harness]
    if args.sources:
        sources += sorted((ROOT / 'src').rglob('*.java'))
    response = out / 'sources.txt'
    response.write_text('\n'.join('"' + str(path).replace('\\', '/') + '"' for path in sources), encoding='utf-8', newline='\n')
    cp = os.pathsep.join(map(str, [classes, *([ROOT / 'resources'] if args.sources else []), JAR]))
    java, javac = find_jdk()
    runner = Path(__file__)
    jar_hash, harness_hash, runner_hash = sha256(JAR), sha256(harness), sha256(runner)
    log = out / 'native.log'
    with log.open('w', encoding='utf-8') as stream:
        stream.write(f'Source overrides={args.sources}\nJAR SHA256 {jar_hash}\n')
        commands = [[javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', cp,
                     '-d', str(classes), '@' + str(response)],
                    [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
                     '-cp', cp, 'com.pkmngen.game.MapPresentationTest']]
        for index, command in enumerate(commands):
            if index and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    packaged = set(archive.namelist())
                if any(path.relative_to(classes).as_posix() in packaged for path in classes.rglob('*.class')):
                    raise RuntimeError('Production class override in packaged MAP verification')
            stream.flush()
            result = subprocess.run(command, cwd=out, stdout=stream, stderr=subprocess.STDOUT,
                                    timeout=180, creationflags=CREATE_FLAGS)
            if result.returncode:
                print('FAIL:', log)
                return result.returncode
    content = log.read_text(encoding='utf-8')
    evidence = re.search(r'MAP PRESENTATION PASS: checks=(\d+), captures=(\d+), frames=(\d+),', content)
    if not evidence or int(evidence[2]) != len(expected) or int(evidence[3]) != 290 or re.search(
            r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(', content):
        raise RuntimeError('Incomplete native MAP evidence: ' + str(log))
    if not all((out / name).is_file() for name in expected) or list(out.glob('*.sav')):
        raise RuntimeError('Missing MAP captures or unexpected save output')
    if sha256(JAR) != jar_hash or sha256(harness) != harness_hash or sha256(runner) != runner_hash:
        raise RuntimeError('JAR or MAP test changed during verification')
    receipt = {'status': 'passed', 'production_overrides': args.sources, 'checks': int(evidence[1]),
               'frames': int(evidence[3]), 'captures': len(expected), 'jar_sha256': jar_hash,
               'harness_sha256': harness_hash, 'runner_sha256': runner_hash, 'log_sha256': sha256(log),
               'images': {name: sha256(out / name) for name in expected},
               'limits': 'Disposable unsaved fixture; original teleport confirmation is cancelled, no campaign save is opened.'}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8', newline='\n')
    print('PASS:', log)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
