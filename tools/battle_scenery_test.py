#!/usr/bin/env python3
"""Native battle orbit, projected actor and shader-state regression."""
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
    parser.add_argument('--sources', action='store_true', help='Diagnostic: compile battle scenery and actor sources with loose resources over the existing JAR')
    args = parser.parse_args()
    destination = ROOT / ('build/battle-scenery-diagnostic' if args.sources else 'build/battle-scenery')
    classes = destination / 'classes'
    if destination.is_symlink() or classes.is_symlink():
        raise RuntimeError('Test output must not be a link')
    classes.mkdir(parents=True, exist_ok=True)
    for screenshot in destination.glob('*.png'):
        if screenshot.is_symlink() or screenshot.resolve().parent != destination.resolve():
            raise RuntimeError('Unexpected image path')
        screenshot.unlink()
    java, javac = find_jdk()
    sources = [ROOT / 'tools/BattleSceneryTest.java']
    if args.sources:
        sources += [ROOT / 'src/com/pkmngen/game' / name for name in ('JohtoBattleRenderer.java', 'BattleScenery.java', 'PmdBattleSprites.java', 'GhostVisualAssets.java', 'ShinyVisuals.java', 'MoveEffectPresentation.java')]
    cp = os.pathsep.join(map(str, [classes, *([ROOT / 'resources'] if args.sources else []), JAR]))
    commands = [
        [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(classes), *map(str, sources)],
        [java, '-Xmx1g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
         '-cp', cp, 'com.pkmngen.game.BattleSceneryTest'],
    ]
    logpath, receiptpath = destination / 'battle-scenery.log', destination / 'receipt.json'
    receipt = {'status': 'running', 'jar_sha256': sha256(JAR), 'source_overrides': args.sources,
               'harness_sha256': sha256(sources[0]),
               'limits': 'Controlled unsaved scenes on the available desktop GPU; not a full campaign or frame-rate benchmark.'}
    receiptpath.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    with logpath.open('w', encoding='utf-8') as log:
        log.write(f'JAR SHA256 {receipt["jar_sha256"]}\nSource overrides={args.sources}\n')
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT, timeout=240, creationflags=CREATE_FLAGS)
            log.write(f'Exit code {result.returncode}\n')
            if result.returncode:
                receipt.update(status='failed', exit_code=result.returncode)
                receiptpath.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
                print(logpath.read_text(encoding='utf-8'))
                return result.returncode
    output = logpath.read_text(encoding='utf-8')
    if ('BATTLE SCENERY PASS:' not in output or len(list(destination.glob('*.png'))) != 41
            or re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(', output)):
        receipt.update(status='failed', reason='Incomplete native evidence')
        receiptpath.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
        raise RuntimeError(f'Incomplete native test: {logpath}')
    receipt.update(status='passed', log_sha256=sha256(logpath),
                   images={p.name: sha256(p) for p in sorted(destination.glob('*.png'))})
    receiptpath.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(f'PASS: {logpath}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
