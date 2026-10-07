#!/usr/bin/env python3
"""Inventory source-evidenced Tile identities through the production JAR, never source overlays."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def catalog():
    tile = (ROOT / 'src/com/pkmngen/game/Tile.java').read_text(encoding='utf-8')
    init = tile[tile.index('public void init(String tileName,'):tile.index('public boolean isWall()')]
    rows = {}
    def add(ground, upper, evidence):
        rows.setdefault((ground, upper), set()).add(evidence)
    for name in re.findall(r'\btileName\.equals\("([^"\n]+)"\)', init):
        add(name, '', 'Tile.init exact ground condition')
    for name in re.findall(r'\bthis\.nameUpper\.equals\("([^"\n]+)"\)', init):
        add('green1', name, 'Tile.init exact upper condition')
    for path in sorted((ROOT / 'src/com/pkmngen/game').rglob('*.java')):
        text = path.read_text(encoding='utf-8')
        for match in re.finditer(r'new Tile\(\s*"([^"\n]+)"\s*,\s*(?:"([^"\n]*)"\s*,)?', text):
            add(match[1], match[2] or '', path.name + ':' + str(text.count('\n', 0, match.start()) + 1))
    grammar = sorted(set(re.findall(r'(?:tileName|this\.nameUpper)\.(?:contains|startsWith)\("([^"\n]+)"\)', init)))
    return {'cases': [{'ground': ground, 'upper': upper, 'evidence': '; '.join(sorted(evidence))}
                      for (ground, upper), evidence in sorted(rows.items())],
            'dynamicNameFragmentsNotExhaustivelyEnumerated': grammar,
            'catalogSourceSha256': sha256(ROOT / 'src/com/pkmngen/game/Tile.java')}


def main():
    destination = ROOT / 'build/asset-audit'
    destination.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    source = ROOT / 'tools/AssetCoverageTest.java'
    data = catalog()
    logpath = destination / 'asset-coverage-runtime.log'
    with tempfile.TemporaryDirectory(prefix='runtime-', dir=destination) as temporary:
        run = Path(temporary)
        classes = run / 'classes'; classes.mkdir()
        (run / 'catalog-input.json').write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8', newline='\n')
        cp = os.pathsep.join(map(str, (classes, JAR)))
        with logpath.open('w', encoding='utf-8', newline='\n') as log:
            log.write(f'JAR={JAR}\nSHA256={sha256(JAR)}\nNo production source overrides\n')
            log.flush()
            subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(JAR),
                            '-d', str(classes), str(source)], check=True, stdout=log, stderr=subprocess.STDOUT,
                           creationflags=CREATE_FLAGS, timeout=90)
            previous = None
            reportfile = run / 'asset-coverage-runtime.json'
            for attempt in range(2):
                if reportfile.exists():
                    reportfile.unlink()  # Exact test-owned file; never a player's data.
                result = subprocess.run([java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto',
                                         '-Dpokewilds.models=off', '-cp', cp, 'com.pkmngen.game.AssetCoverageTest'],
                                        cwd=run, stdout=log, stderr=subprocess.STDOUT, creationflags=CREATE_FLAGS, timeout=180)
                if not reportfile.exists():
                    raise RuntimeError(f'Incomplete native catalog audit: {logpath}')
                report = json.loads(reportfile.read_text(encoding='utf-8'))
                if previous is not None and previous != report:
                    (destination / 'nondeterministic-first.json').write_text(json.dumps(previous, indent=2), encoding='utf-8', newline='\n')
                    (destination / 'nondeterministic-second.json').write_text(json.dumps(report, indent=2), encoding='utf-8', newline='\n')
                    raise RuntimeError(f'Non-deterministic catalog report: {logpath}')
                previous = report
        report.update(jarSha256=sha256(JAR), harnessSha256=sha256(source),
                      deterministicNativeRuns=2,
                      catalogSourceSha256=data['catalogSourceSha256'],
                      dynamicNameFragmentsNotExhaustivelyEnumerated=data['dynamicNameFragmentsNotExhaustivelyEnumerated'])
        (destination / reportfile.name).write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({key: value for key, value in report.items() if key not in ('rows', 'dynamicNameFragmentsNotExhaustivelyEnumerated')}, indent=2))
    print('Detailed report:', destination / 'asset-coverage-runtime.json')
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
