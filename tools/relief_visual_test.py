#!/usr/bin/env python3
"""Render/walk isolated terrain relief; bootstrap only automated build fixtures if missing."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256


def hashes(folder: Path) -> dict[str, str]:
    if folder.is_symlink() or any(p.is_symlink() for p in folder.rglob('*')):
        raise RuntimeError('Fixture cannot contain filesystem links')
    return {p.relative_to(folder).as_posix(): sha256(p) for p in folder.rglob('*') if p.is_file()}


def prepare_fixture(source_saved: Path, destination: Path) -> None:
    """Generate a missing test island, then snapshot its world-reload fixture.

    Both helpers operate solely under build/. Personal game saves are never read.
    """
    generated = ROOT / 'build/modern-world/smoke-world.sav'
    for folder in (generated.parent, source_saved.parent):
        if folder.is_symlink() or folder.resolve().parent != ROOT.resolve() / 'build':
            raise RuntimeError('Unexpected automated fixture directory')
    for folder in (generated, source_saved):
        if folder.is_symlink() or folder.resolve().parent != folder.parent.resolve():
            raise RuntimeError('Unexpected automated saved fixture path')
        if folder.exists(): hashes(folder)
    if source_saved.is_dir(): return
    preparation_log = destination / 'fixture-preparation.log'
    if preparation_log.is_symlink(): raise RuntimeError('Unexpected fixture preparation log')
    scripts = []
    if not generated.is_dir(): scripts.append(ROOT / 'tools/modern_world_test.py')
    scripts.append(ROOT / 'tools/world_reload_visual_test.py')
    with preparation_log.open('w', encoding='utf-8') as log:
        for script in scripts:
            log.write(f'Preparing automated fixture with {script.name}\n')
            log.flush()
            result = subprocess.run([sys.executable, str(script)], cwd=ROOT, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=600, creationflags=CREATE_FLAGS)
            if result.returncode:
                raise RuntimeError(f'Automated fixture preparation failed: {preparation_log}')
    if not source_saved.is_dir():
        raise RuntimeError(f'world_reload_visual_test.py did not create its automated fixture: {preparation_log}')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true')
    args = parser.parse_args()
    destination = ROOT / 'build' / ('relief-visual-sources' if args.sources else 'relief-visual')
    saved = destination / 'smoke-world.sav'
    classes = destination / 'classes'
    source_saved = ROOT / 'build/world-reload/smoke-world.sav'
    if destination.is_symlink() or saved.is_symlink() or classes.is_symlink():
        raise RuntimeError('Test output cannot be a symlink')
    if destination.resolve().parent != ROOT.resolve() / 'build':
        raise RuntimeError('Unexpected output path')
    destination.mkdir(parents=True, exist_ok=True)
    prepare_fixture(source_saved, destination)
    if not source_saved.is_dir() or source_saved.resolve().parent != ROOT.resolve() / 'build/world-reload':
        raise RuntimeError('Missing automated world-reload fixture; run world_reload_visual_test.py first')
    source_before = hashes(source_saved)
    # Fresh, bounded fixture/classes prevent old saves, PNGs or compiled source
    # overrides from supplying evidence to a later packaged-JAR run.
    for folder in (saved, classes):
        if folder.resolve().parent != destination.resolve(): raise RuntimeError('Unsafe output cleanup')
        if folder.exists():
            hashes(folder)
            shutil.rmtree(folder)
    shutil.copytree(source_saved, saved)
    before = hashes(saved)
    classes.mkdir()
    expected = {'00-saved-caldera.png', '00a-saved-ramp-entry.png', '00b-saved-ramp-middle.png',
                '00b-saved-plateau-top.png', '01-closed-ramp-entry.png', '02-closed-ramp-middle.png',
                '02-closed-plateau-top.png', '03-open-ramp-entry.png', '04-open-ramp-middle.png',
                '04-open-plateau-top.png', '05-opening-entry.png', '06-opening-ramp-middle.png',
                '06-opening-plateau-top.png'}
    for image in destination.glob('*.png'):
        if image.is_symlink() or image.resolve().parent != destination.resolve():
            raise RuntimeError('Unexpected screenshot path')
        image.unlink()
    receipt = destination / 'receipt.json'
    if receipt.is_symlink(): raise RuntimeError('Unexpected receipt path')
    receipt.unlink(missing_ok=True)
    java, javac = find_jdk()
    harness = ROOT / 'tools/ReliefVisualTest.java'
    harness_hash = sha256(harness)
    runner_hash = sha256(Path(__file__))
    jar_hash = sha256(JAR)
    sources = [harness]
    if args.sources: sources += sorted((ROOT / 'src').rglob('*.java'))
    source_hashes = {p.relative_to(ROOT).as_posix(): sha256(p) for p in sources}
    response = destination / 'sources.txt'
    response.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf-8')
    classpath = [str(classes)] + ([str(ROOT / 'resources')] if args.sources else []) + [str(JAR)]
    commands = [[javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-classpath', str(JAR), '-d', str(classes), '@'+str(response)],
                [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
                 '-classpath', os.pathsep.join(classpath), 'com.pkmngen.game.ReliefVisualTest']]
    path = destination / 'relief-visual.log'
    code = 0
    with path.open('w', encoding='utf-8') as log:
        log.write(f'JAR={JAR}\nSHA256={jar_hash}\nSource overrides={args.sources}\nHarness SHA256={harness_hash}\n')
        if args.sources:
            for name in ('WorldElevation.java', 'JohtoRenderer.java'):
                log.write(f'Source SHA256 {name}={sha256(ROOT / "src/com/pkmngen/game" / name)}\n')
        for index, command in enumerate(commands):
            if index and not args.sources:
                with zipfile.ZipFile(JAR) as archive:
                    production = set(archive.namelist())
                overlap = sorted(p.relative_to(classes).as_posix() for p in classes.rglob('*.class')
                                 if p.relative_to(classes).as_posix() in production)
                if overlap: raise RuntimeError(f'Production overrides in packaged-JAR proof: {overlap}')
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=160, creationflags=CREATE_FLAGS)
            if result.returncode: code = result.returncode; break
    if before != hashes(saved): raise RuntimeError('Relief test changed the saved fixture')
    if source_before != hashes(source_saved): raise RuntimeError('Relief test changed its automated source fixture')
    if sha256(JAR) != jar_hash: raise RuntimeError('Packaged game changed during proof')
    if sha256(harness) != harness_hash or sha256(Path(__file__)) != runner_hash:
        raise RuntimeError('Relief harness/runner changed during proof')
    if source_hashes != {p.relative_to(ROOT).as_posix(): sha256(p) for p in sources}:
        raise RuntimeError('Compiled sources changed during proof')
    with path.open('a', encoding='utf-8') as log: log.write(f'SAVE HASH PASS: all {len(before)} saved files unchanged\n')
    if code: print(f'FAIL: {path}'); return code
    output = path.read_text(encoding='utf-8')
    if 'RELIEF VISUAL PASS:' not in output or re.search(r'(?m)^\s*(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?:[:\s]|$)|^\s*at\s+[\w.$]+\(', output):
        raise RuntimeError(f'Incomplete relief test: {path}')
    if not all((destination / name).is_file() for name in expected):
        raise RuntimeError('Missing new framebuffer evidence')
    receipt.write_text(json.dumps({'status': 'passed', 'source_overrides': args.sources,
        'jar_sha256': jar_hash, 'harness_sha256': harness_hash, 'runner_sha256': runner_hash,
        'source_sha256': source_hashes, 'fixture_sha256': before, 'log_sha256': sha256(path),
        'images': {name: sha256(destination / name) for name in sorted(expected)}}, indent=2) + '\n', encoding='utf-8')
    print(f'PASS: {path}')
    return 0


if __name__ == '__main__': raise SystemExit(main())
