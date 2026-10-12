#!/usr/bin/env python3
"""Native authored terrain topology, collision, elevation and save-format checks."""
from __future__ import annotations
import argparse
import os
import subprocess
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true')
    args = parser.parse_args()
    destination = ROOT / 'build/authored-terrain'
    classes = destination / ('source-classes' if args.sources else 'jar-classes')
    if destination.is_symlink() or classes.is_symlink(): raise RuntimeError('Test output cannot be a symlink')
    classes.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    sources = [ROOT / 'tools/AuthoredTerrainTest.java']
    if args.sources:
        sources += [ROOT / 'src/com/pkmngen/game' / name for name in
                    ('AuthoredTerrain.java', 'ModernWorldGenerator.java', 'BiomeProfiles.java', 'WorldElevation.java')]
    classpath = [str(classes)] + ([str(ROOT / 'resources')] if args.sources else []) + [str(JAR)]
    commands = [[javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-classpath', str(JAR), '-d', str(classes), *map(str, sources)],
                [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
                 '-classpath', os.pathsep.join(classpath), 'com.pkmngen.game.AuthoredTerrainTest']]
    path = destination / 'authored-terrain.log'
    with path.open('w', encoding='utf-8') as log:
        log.write(f'JAR={JAR}\nSHA256={sha256(JAR)}\nSource overrides={args.sources}\n')
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=180, creationflags=CREATE_FLAGS)
            if result.returncode:
                print(f'FAIL: {path}'); return result.returncode
    content = path.read_text(encoding='utf-8')
    if 'AUTHORED PASS:' not in content or 'Exception' in content or '\tat ' in content:
        raise RuntimeError(f'Incomplete authored terrain test: {path}')
    print(f'PASS: {path}')
    return 0

if __name__ == '__main__': raise SystemExit(main())
