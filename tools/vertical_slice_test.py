#!/usr/bin/env python3
"""Native mountain/house/inventory/battle/save slice against the exact built JAR."""
from pathlib import Path
import json
import os
import platform
import subprocess
import tempfile
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256


def main():
    output = ROOT / 'build/vertical-slice'
    output.mkdir(parents=True, exist_ok=True)
    if output.is_symlink():
        raise RuntimeError('Verification output cannot be a symbolic link')
    java, javac = find_jdk()
    jar_hash = sha256(JAR)
    source = ROOT / 'tools/VerticalSliceTest.java'
    source_hash = sha256(source)
    log_path = output / 'vertical-slice.log'
    receipt = {'status': 'running', 'platform': platform.platform(), 'jar_sha256': jar_hash,
               'harness_sha256': source_hash, 'production_overrides': False,
               'limits': 'Fixed disposable scene; no historical performance baseline; host/GPU may be shared with concurrent tests.'}
    receipt_path = output / 'vertical-slice-verification.json'
    receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    try:
        with tempfile.TemporaryDirectory(prefix='fixture-', dir=output) as temporary:
            run = Path(temporary)
            classes = run / 'classes'
            classes.mkdir()
            with log_path.open('w', encoding='utf-8') as log:
                log.write(f'Exact JAR SHA256: {jar_hash}\nHarness SHA256: {source_hash}\nJDK: {java}\n')
                commands = [
                    [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(JAR), '-d', str(classes), str(source)],
                    [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off',
                     '-cp', os.pathsep.join((str(classes), str(JAR))), 'com.pkmngen.game.VerticalSliceTest'],
                ]
                try:
                    for command in commands:
                        log.flush()
                        result = subprocess.run(command, cwd=run, stdout=log, stderr=subprocess.STDOUT,
                                                timeout=300, creationflags=CREATE_FLAGS)
                        if result.returncode:
                            raise RuntimeError('Native vertical slice failed; see ' + str(log_path))
                finally:
                    artifacts = {}
                    for pattern in ('*.png', '*metrics.json', 'frame-times.csv'):
                        for artifact in run.glob(pattern):
                            (output / artifact.name).write_bytes(artifact.read_bytes())
                            artifacts[artifact.name] = sha256(output / artifact.name)
                    receipt['artifacts_sha256'] = artifacts
            content = log_path.read_text(encoding='utf-8', errors='replace')
            if 'VERTICAL SLICE PASS:' not in content or '\tat ' in content or 'Exception' in content:
                raise RuntimeError('Incomplete/exceptional native vertical slice: ' + str(log_path))
            if sha256(JAR) != jar_hash or sha256(source) != source_hash:
                raise RuntimeError('JAR or harness changed while the native test was running')
            receipt.update(status='passed', metrics='vertical-slice-metrics.json')
    except Exception as error:
        receipt.update(status='failed', error=str(error))
        raise
    finally:
        receipt_path.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('PASS:', receipt_path)


if __name__ == '__main__':
    main()
