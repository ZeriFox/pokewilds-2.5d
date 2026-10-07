#!/usr/bin/env python3
"""Sequential paired native benchmark of two exact JARs with one unchanged fixture."""
import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import statistics
import subprocess
import tempfile

from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256


def provenance(jar):
    """Do not infer a source commit just from the containing checkout's HEAD."""
    checkout = jar.parent.parent
    path = checkout / 'build/build-report.json'
    if not path.is_file():
        return {'sourceCommit': 'unavailable; JAR hash only'}
    report = json.loads(path.read_text(encoding='utf-8'))
    if report.get('output_sha256') != sha256(jar):
        raise RuntimeError('Available build report does not describe benchmark JAR')
    sources = report.get('source_files_sha256', {})
    if not sources or any(not (checkout / name).is_file() or sha256(checkout / name) != value for name, value in sources.items()):
        raise RuntimeError('Available benchmark source tree differs from its build report')
    result = {'buildReportJarMatches': True, 'sourceFilesVerified': len(sources), 'sourcesSha256': report.get('sources_sha256')}
    try:
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=checkout, text=True, stderr=subprocess.DEVNULL).strip()
        clean = not subprocess.check_output(['git', 'status', '--porcelain'], cwd=checkout, text=True).strip()
        result.update(checkoutCommit=commit, checkoutClean=clean,
                      sourceCommit=commit if clean else 'working tree; source hashes identify compiled input')
    except subprocess.CalledProcessError:
        result['sourceCommit'] = 'unavailable; verified source hashes only'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-jar', type=Path, required=True)
    parser.add_argument('--candidate-jar', type=Path, default=JAR)
    parser.add_argument('--runs', type=int, default=3)
    args = parser.parse_args()
    if not 1 <= args.runs <= 5:
        parser.error('--runs must be between 1 and 5')
    jars = {'baseline': args.baseline_jar.resolve(strict=True), 'candidate': args.candidate_jar.resolve(strict=True)}
    hashes = {label: sha256(jar) for label, jar in jars.items()}
    if hashes['baseline'] == hashes['candidate']:
        raise RuntimeError('Baseline and candidate JARs are identical')
    output = ROOT / 'build/performance-comparison'
    output.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'tools/RenderBenchmark.java'
    source_hash = sha256(source)
    java, javac = find_jdk()
    receipt = {'status': 'running', 'platform': platform.platform(), 'harness_sha256': source_hash,
               'jars': hashes, 'provenance': {key: provenance(jar) for key, jar in jars.items()},
               'results': [], 'production_overrides': False,
               'limits': 'Paired sequential diagnostic, stationary fixture, not full campaign/FPS or isolated hardware benchmark. Close competing GPU workloads before running.'}
    destination = output / 'comparison.json'
    try:
        for repeat in range(args.runs):
            # Alternate order reduces systematic warm-cache/time drift bias.
            order = ('baseline', 'candidate') if repeat % 2 == 0 else ('candidate', 'baseline')
            for label in order:
                prefix = f'{label}-{repeat + 1}'
                with tempfile.TemporaryDirectory(prefix=prefix + '-', dir=output) as folder:
                    work = Path(folder)
                    classes = work / 'classes'; classes.mkdir()
                    env = {k: v for k, v in os.environ.items() if k.upper() not in {'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH'}}
                    log_path = output / (prefix + '.log')
                    with log_path.open('w', encoding='utf-8') as log:
                        for command in (
                            [javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(jars[label]), '-d', str(classes), str(source)],
                            [java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto', '-Dpokewilds.models=off', '-cp', os.pathsep.join((str(classes), str(jars[label]))), 'com.pkmngen.game.RenderBenchmark'],
                        ):
                            result = subprocess.run(command, cwd=work, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=90, creationflags=CREATE_FLAGS)
                            if result.returncode:
                                raise RuntimeError('Native benchmark failed; see ' + str(log_path))
                    content = log_path.read_text(encoding='utf-8', errors='replace')
                    if 'RENDER BENCHMARK PASS:' not in content or '\tat ' in content or 'Exception' in content:
                        raise RuntimeError('Incomplete/exceptional benchmark: ' + str(log_path))
                    data = json.loads((work / 'metrics.json').read_text(encoding='utf-8'))
                    shutil.copy2(work / 'frame.png', output / (prefix + '.png'))
                    receipt['results'].append({'variant': label, 'run': repeat + 1, 'log_sha256': sha256(log_path), 'metrics': data})
                    if hashes[label] != sha256(jars[label]) or sha256(source) != source_hash:
                        raise RuntimeError('JAR or fixture changed during performance comparison')
                    print(f'PASS {prefix}: p95 CPU {data["cpuRenderMs"]["p95"]:.3f} ms', flush=True)
        for key in ('java', 'glVendor', 'glRenderer', 'glVersion', 'viewport', 'fixtureSeeds', 'tiles', 'playerPosition'):
            if any(r['metrics'][key] != receipt['results'][0]['metrics'][key] for r in receipt['results']):
                raise RuntimeError('Paired environment/fixture mismatch: ' + key)
        summary = {}
        for label in jars:
            rows = [r['metrics'] for r in receipt['results'] if r['variant'] == label]
            summary[label] = {'medianOfRunP95CpuMs': statistics.median(r['cpuRenderMs']['p95'] for r in rows),
                              'medianOfRunMeanCpuMs': statistics.median(r['cpuRenderMs']['mean'] for r in rows),
                              'managedTextures': [r['managedTextures'] for r in rows],
                              'managedShaders': [r['managedShaders'] for r in rows],
                              'managedTextureRgba8BaseLevelEstimateBytes': [r['managedTextureRgba8BaseLevelEstimateBytes'] for r in rows]}
        summary['candidateP95Ratio'] = summary['candidate']['medianOfRunP95CpuMs'] / summary['baseline']['medianOfRunP95CpuMs']
        receipt.update(status='passed', summary=summary)
    except Exception as error:
        receipt.update(status='failed', error=str(error))
        raise
    finally:
        destination.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('PASS:', destination)


if __name__ == '__main__':
    main()
