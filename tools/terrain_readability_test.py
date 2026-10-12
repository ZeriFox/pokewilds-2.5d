#!/usr/bin/env python3
"""Unsaved native fixture: stair cues, material boundaries and actual sprite depth."""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import subprocess
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true')
    parser.add_argument('--classes-override', type=Path, action='append', default=[])
    args = parser.parse_args()
    destination = ROOT / 'build/terrain-readability'
    if destination.is_symlink():
        raise RuntimeError('Test output must not be a link')
    destination.mkdir(parents=True, exist_ok=True)
    classes = destination / ('source-classes' if args.sources else 'jar-classes')
    classes.mkdir(exist_ok=True)
    java, javac = find_jdk()
    sources = [ROOT / 'tools/TerrainReadabilityTest.java']
    if args.sources:
        sources += sorted((ROOT / 'src').rglob('*.java'))
    response = destination / 'sources.txt'
    response.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf-8')
    classpath = [classes, *[p.resolve() for p in args.classes_override]]
    if args.sources or args.classes_override:
        classpath.append(ROOT / 'resources')
    classpath.append(JAR)
    cp = os.pathsep.join(map(str, classpath))
    commands = [
        [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(classes), '@' + str(response)],
        [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
         '-cp', cp, 'com.pkmngen.game.TerrainReadabilityTest'],
    ]
    logpath = destination / 'terrain-readability.log'
    with logpath.open('w', encoding='utf-8') as log:
        log.write(f'JAR SHA256 {sha256(JAR)}\nSource overrides={args.sources}; class overrides={args.classes_override}\n')
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=180, creationflags=CREATE_FLAGS)
            log.write(f'Exit code {result.returncode}\n')
            if result.returncode:
                print(f'FAIL: {logpath}')
                return result.returncode
    output = logpath.read_text(encoding='utf-8')
    if 'TERRAIN READABILITY PASS:' not in output or re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(', output):
        raise RuntimeError(f'Incomplete native test: {logpath}')
    print(f'PASS: {logpath}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
