#!/usr/bin/env python3
"""Native GL checks for merged built-in/custom asset loading; isolated test files only."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true', help='Explicitly test current asset/renderer/sampling/geometry sources over the JAR')
    args = parser.parse_args()
    destination = ROOT / 'build' / 'bw-assets-integration'
    destination.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    logpath = destination / 'bw-assets-integration.log'
    with tempfile.TemporaryDirectory(prefix='run-', dir=destination) as temporary:
        run = Path(temporary)
        classes = run / 'classes'
        classes.mkdir()
        runtime = [classes]
        if args.sources:
            runtime.append(ROOT / 'resources')
        runtime.append(JAR)
        cp = os.pathsep.join(map(str, runtime))
        sources = [ROOT / 'tools' / 'BwAssetsIntegrationTest.java']
        if args.sources:
            sources += [ROOT / 'src/com/pkmngen/game' / name for name in ('BwAssets.java', 'VisualSampling.java', 'JohtoRenderer.java', 'VisualGeometry.java')]
        with logpath.open('w', encoding='utf-8') as log:
            log.write(f'JAR={JAR}\nSHA256={sha256(JAR)}\nSource overrides={args.sources}\n')
            for source in sources:
                log.write(f'Source SHA256 {source.name}={sha256(source)}\n')
            log.flush()
            commands = [
                [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(classes), *map(str, sources)],
                [java, '-Dfile.encoding=UTF-8', '-cp', cp, 'com.pkmngen.game.BwAssetsIntegrationTest'],
            ]
            for command in commands:
                result = subprocess.run(command, cwd=run, stdout=log, stderr=subprocess.STDOUT,
                                        timeout=90, creationflags=CREATE_FLAGS)
                log.flush()
                if result.returncode:
                    raise RuntimeError(f'Native asset test failed: {logpath}')
        content = logpath.read_text(encoding='utf-8')
        if 'BW ASSETS PASS:' not in content or re.search(r'(?m)^\s*at\s+[\w.$]+\(', content):
            raise RuntimeError(f'Missing success or native failure: {logpath}')
        for name in ('builtin-materials.png', 'custom-sampling.png'):
            (destination / name).write_bytes((run / name).read_bytes())
    print('BwAssets native integration PASS:', logpath)


if __name__ == '__main__':
    main()
