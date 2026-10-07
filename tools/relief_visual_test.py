#!/usr/bin/env python3
"""Render and walk real terrain relief in an isolated copy of the caldera regression save."""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256


def hashes(folder: Path) -> dict[str, str]:
    return {p.relative_to(folder).as_posix(): sha256(p) for p in folder.rglob('*') if p.is_file()}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true')
    args = parser.parse_args()
    destination = ROOT / 'build/relief-visual'
    saved = destination / 'smoke-world.sav'
    if destination.is_symlink() or saved.is_symlink(): raise RuntimeError('Test output cannot be a symlink')
    destination.mkdir(parents=True, exist_ok=True)
    if not saved.exists(): shutil.copytree(ROOT / 'build/world-reload/smoke-world.sav', saved)
    before = hashes(saved)
    classes = destination / ('source-classes' if args.sources else 'jar-classes')
    classes.mkdir(exist_ok=True)
    java, javac = find_jdk()
    sources = [ROOT / 'tools/ReliefVisualTest.java']
    if args.sources: sources += sorted((ROOT / 'src').rglob('*.java'))
    response = destination / 'sources.txt'
    response.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf-8')
    classpath = [str(classes)] + ([str(ROOT / 'resources')] if args.sources else []) + [str(JAR)]
    commands = [[javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-classpath', str(JAR), '-d', str(classes), '@'+str(response)],
                [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
                 '-classpath', os.pathsep.join(classpath), 'com.pkmngen.game.ReliefVisualTest']]
    path = destination / 'relief-visual.log'
    code = 0
    with path.open('w', encoding='utf-8') as log:
        log.write(f'JAR={JAR}\nSHA256={sha256(JAR)}\nSource overrides={args.sources}\n')
        if args.sources:
            for name in ('WorldElevation.java', 'JohtoRenderer.java'):
                log.write(f'Source SHA256 {name}={sha256(ROOT / "src/com/pkmngen/game" / name)}\n')
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=160, creationflags=CREATE_FLAGS)
            if result.returncode: code = result.returncode; break
    if before != hashes(saved): raise RuntimeError('Relief test changed the saved fixture')
    with path.open('a', encoding='utf-8') as log: log.write(f'SAVE HASH PASS: all {len(before)} saved files unchanged\n')
    if code: print(f'FAIL: {path}'); return code
    output = path.read_text(encoding='utf-8')
    if 'RELIEF VISUAL PASS:' not in output or re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(', output):
        raise RuntimeError(f'Incomplete relief test: {path}')
    print(f'PASS: {path}')
    return 0


if __name__ == '__main__': raise SystemExit(main())
