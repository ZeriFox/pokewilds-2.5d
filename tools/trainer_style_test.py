#!/usr/bin/env python3
"""Hidden native trainer verification against the packaged JAR, with source diagnostics isolated."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true', help='Diagnostic only: compile current production sources and use source resources')
    args = parser.parse_args()
    destination = ROOT / 'build' / ('trainer-style-diagnostic' if args.sources else 'trainer-style')
    classes = destination / ('source-classes' if args.sources else 'jar-classes')
    expected = ROOT.resolve() / 'build' / destination.name
    if destination.is_symlink() or classes.is_symlink() or destination.resolve() != expected:
        raise RuntimeError('Trainer proof output must be an ordinary project build folder')
    # These are this harness's generated outputs only; validate all targets before cleanup.
    for folder in (classes, destination / 'mods'):
        if folder.exists():
            if folder.is_symlink() or folder.resolve().parent != expected:
                raise RuntimeError('Unexpected generated fixture path')
            shutil.rmtree(folder)
    classes.mkdir(parents=True, exist_ok=True)
    for path in list(destination.glob('*.png')) + [destination / 'interface-settings.json', destination / 'interface-settings.json.bak']:
        if path.exists():
            if path.is_symlink() or path.resolve().parent != expected:
                raise RuntimeError('Unexpected generated fixture file')
            path.unlink()
    java, javac = find_jdk()
    sources = [ROOT / 'tools/TrainerStyleTest.java']
    if args.sources:
        sources += sorted((ROOT / 'src').rglob('*.java'))
    response = destination / 'sources.txt'
    response.write_text('\n'.join('"' + str(path).replace('\\', '/') + '"' for path in sources), encoding='utf-8')
    classpath = [classes]
    if args.sources:
        classpath.append(ROOT / 'resources')
    classpath.append(JAR)
    cp = os.pathsep.join(map(str, classpath))
    commands = [
        [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(classes), '@' + str(response)],
        [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off', '-cp', cp, 'com.pkmngen.game.TrainerStyleTest'],
    ]
    logpath = destination / 'trainer-style.log'
    receipt_path = destination / 'receipt.json'
    receipt = {'status': 'running', 'jar_sha256': sha256(JAR), 'source_overrides': args.sources,
               'harness_sha256': sha256(ROOT / 'tools/TrainerStyleTest.java'),
               'runner_sha256': sha256(Path(__file__)),
               'limits': 'Controlled unsaved native GL scenes and fixture mods; style quality still requires visual review. No personal save is loaded.'}
    def save() -> None:
        receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    save()
    with logpath.open('w', encoding='utf-8') as log:
        log.write(f'JAR SHA256 {receipt["jar_sha256"]}\nSource overrides={args.sources}\n')
        for index, command in enumerate(commands):
            if index and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    collisions = sorted(p.relative_to(classes).as_posix() for p in classes.rglob('*.class')
                                        if p.relative_to(classes).as_posix() in archive.namelist())
                if collisions:
                    receipt.update(status='failed', reason='Compiled harness overrides packaged production classes', collisions=collisions)
                    save()
                    raise RuntimeError('Production class override in JAR-only proof')
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=240, creationflags=CREATE_FLAGS)
            log.write(f'Exit code {result.returncode}\n')
            if result.returncode:
                receipt.update(status='failed', exit_code=result.returncode)
                save()
                print(f'FAIL: {logpath}')
                return result.returncode
    output = logpath.read_text(encoding='utf-8')
    evidence = re.search(r'TRAINER STYLE PASS: checks=(\d+), identities=(\d+), walkFrames=(\d+), worldPoses=(\d+), captures=(\d+)', output)
    expected_images = {f'0{i+1}-cast-walk-{direction}.png' for i, direction in enumerate(('down', 'up', 'left', 'right'))}
    expected_images |= {'05-cast-running.png', '06-selector-page-1.png', '07-selector-page-2.png',
                        'world-gold.png', 'world-hilda.png', 'world-lyra.png'}
    images = sorted(destination.glob('*.png'))
    if (not evidence or tuple(map(int, evidence.groups()[1:])) != (19, 228, 76, 10)
            or {p.name for p in images} != expected_images
            or sha256(JAR) != receipt['jar_sha256']
            or re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(', output)):
        receipt.update(status='failed', reason='Incomplete native evidence or changed artifact')
        save()
        raise RuntimeError(f'Incomplete native trainer proof: {logpath}')
    receipt.update(status='passed', checks=int(evidence[1]), identities=19, walking_frames=228, world_poses=76, captures=10,
                   log_sha256=sha256(logpath), images={p.name: sha256(p) for p in images})
    save()
    print(f'PASS: {logpath}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
