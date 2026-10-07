#!/usr/bin/env python3
"""Test a copied preexisting save with old/new JARs; persist only hashes and counts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256


def manifest(folder):
    result = {}
    for path in sorted(folder.rglob('*')):
        if path.is_symlink():
            raise RuntimeError('Save copy refuses symbolic links')
        if path.is_file():
            result[path.relative_to(folder).as_posix()] = sha256(path)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--save-directory', required=True, type=Path)
    parser.add_argument('--baseline-jar', required=True, type=Path)
    args = parser.parse_args()
    source_save = args.save_directory.resolve(strict=True)
    if not source_save.is_dir() or source_save.suffix != '.sav':
        raise RuntimeError('Explicit .sav directory is required')
    original = manifest(source_save)
    if not original:
        raise RuntimeError('Empty save directory')
    output = ROOT / 'build/save-compatibility'; output.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    source = ROOT / 'tools/SaveCompatibilityTest.java'
    jars = {'baseline': args.baseline_jar.resolve(strict=True), 'candidate': JAR.resolve(strict=True)}
    receipt = {'status': 'running', 'scope': 'Preexisting save copied into isolated folders; original never passed to JVM; private data/logs/screenshots excluded',
               'source_files': len(original), 'source_content_sha256': hashlib.sha256(json.dumps(original, sort_keys=True).encode()).hexdigest(),
               'harness_sha256': sha256(source), 'jars': {key: sha256(jar) for key, jar in jars.items()}, 'results': {}}
    try:
        for label, jar in jars.items():
            with tempfile.TemporaryDirectory(prefix='copied-save-', dir=output) as temporary:
                work = Path(temporary); classes = work / 'classes'; classes.mkdir()
                shutil.copytree(source_save, work / 'compatibility.sav')
                env = {k: v for k, v in os.environ.items() if k.upper() not in {'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH'}}
                log_path = work / 'private-load.log'
                with log_path.open('w', encoding='utf-8') as log:
                    for command in (
                        [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(jar), '-d', str(classes), str(source)],
                        [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off', '-cp', os.pathsep.join((str(classes), str(jar))), 'com.pkmngen.game.SaveCompatibilityTest'],
                    ):
                        result = subprocess.run(command, cwd=work, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=120, creationflags=CREATE_FLAGS)
                        if result.returncode:
                            # Retain no private save log; expose only our own assertion categories.
                            content = log_path.read_text(encoding='utf-8', errors='replace')
                            reasons = [line for line in content.splitlines() if 'IllegalStateException:' in line or 'error:' in line]
                            raise RuntimeError(label + ' compatibility test failed: ' + '; '.join(reasons[:3]))
                content = log_path.read_text(encoding='utf-8', errors='replace')
                if 'SAVE COMPATIBILITY PASS:' not in content or '\tat ' in content or 'Exception' in content:
                    raise RuntimeError(label + ' compatibility run incomplete/exceptional')
                receipt['results'][label] = json.loads((work / 'compatibility-result.json').read_text(encoding='utf-8'))
                if sha256(jar) != receipt['jars'][label] or sha256(source) != receipt['harness_sha256']:
                    raise RuntimeError('JAR/harness changed during save compatibility test')
        if receipt['results']['baseline']['persistentData'] != receipt['results']['candidate']['persistentData']:
            before, after = (receipt['results'][key]['persistentData'] for key in ('baseline', 'candidate'))
            raise RuntimeError('Cross-version save data differs in categories: ' + ', '.join(key for key in before if before[key] != after[key]))
        receipt.update(status='passed', cross_version_data_preserved=True)
    except Exception as error:
        receipt.update(status='failed', error=str(error))
        raise
    finally:
        receipt['original_unchanged'] = original == manifest(source_save)
        (output / 'save-compatibility.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
        if not receipt['original_unchanged']:
            raise RuntimeError('Original save changed during verification; possible concurrent game use')
    print('PASS:', output / 'save-compatibility.json')


if __name__ == '__main__':
    main()
