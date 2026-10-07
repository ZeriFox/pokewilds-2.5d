#!/usr/bin/env python3
"""Capture the saved volcanic-mansion regression island without altering its save.

The first run snapshots build/modern-world/smoke-world.sav into build/world-reload.
Later runs retain that exact fixture even after the generation test creates a new island.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import shutil
import subprocess
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256

def hashes(folder: Path) -> dict[str, str]:
    return {p.relative_to(folder).as_posix(): sha256(p) for p in folder.rglob('*') if p.is_file()}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true')
    args = parser.parse_args()
    destination = ROOT / 'build/world-reload'
    saved = destination / 'smoke-world.sav'
    if destination.is_symlink() or saved.is_symlink(): raise RuntimeError('Test output cannot be a symlink')
    destination.mkdir(parents=True, exist_ok=True)
    if not saved.exists(): shutil.copytree(ROOT / 'build/modern-world/smoke-world.sav', saved)
    before = hashes(saved)
    classes = destination / ('source-classes' if args.sources else 'jar-classes')
    classes.mkdir(exist_ok=True)
    java, javac = find_jdk()
    sources = [ROOT / 'tools/WorldReloadVisualTest.java']
    if args.sources:
        sources += [ROOT / 'src/com/pkmngen/game' / name for name in ('BiomeProfiles.java', 'BwAssets.java', 'JohtoRenderer.java')]
    classpath = [str(classes)] + ([str(ROOT / 'resources')] if args.sources else []) + [str(JAR)]
    commands = [[javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-classpath', str(JAR), '-d', str(classes), *map(str, sources)],
                [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
                 '-classpath', os.pathsep.join(classpath), 'com.pkmngen.game.WorldReloadVisualTest']]
    path = destination / 'world-reload.log'
    with path.open('w', encoding='utf-8') as log:
        log.write(f'JAR={JAR}\nSHA256={sha256(JAR)}\nSource overrides={args.sources}\n')
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=120, creationflags=CREATE_FLAGS)
            if result.returncode:
                print(f'FAIL: {path}'); return result.returncode
    if before != hashes(saved): raise RuntimeError('Read-only visual test changed the save')
    output = path.read_text(encoding='utf-8')
    if 'RELOAD VISUAL PASS:' not in output or 'Exception' in output or '\tat ' in output:
        raise RuntimeError(f'Incomplete saved scene test: {path}')
    with path.open('a', encoding='utf-8') as log:
        log.write(f'SAVE HASH PASS: all {len(before)} saved files unchanged\n')
    print(f'PASS: {path}')
    return 0

if __name__ == '__main__': raise SystemExit(main())
