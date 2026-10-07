#!/usr/bin/env python3
"""Exercise actual move action, original PNGs and metadata exclusively from the built JAR."""
import os
import subprocess
from smoke_test import ROOT, JAR, find_jdk, sha256, CREATE_FLAGS


def main():
    destination = ROOT / 'build/move-effects'
    classes = destination / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    logpath = destination / 'move-effects.log'
    with logpath.open('w', encoding='utf-8', newline='\n') as log:
        log.write('Exact JAR SHA256: ' + sha256(JAR) + '\nNo production source overlays\n')
        log.flush()
        result = subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', str(JAR),
                                 '-d', str(classes), str(ROOT / 'tools/MoveEffectPresentationTest.java')],
                                stdout=log, stderr=subprocess.STDOUT, timeout=90, creationflags=CREATE_FLAGS)
        if result.returncode:
            print(logpath.read_text(encoding='utf-8'));return result.returncode
        for width, height in ((1280, 720), (1024, 768), (1600, 600)):
            output = destination / f'{width}x{height}'
            output.mkdir(exist_ok=True)
            for previous in output.glob('*.png'):
                previous.unlink()
            log.write(f'\nNative viewport {width}x{height}\n');log.flush()
            result = subprocess.run([java, '-Xmx2g', '-Dfile.encoding=UTF-8', '-Dpokewilds.visual=johto',
                                     '-Dpokewilds.models=off', f'-Deffects.width={width}', f'-Deffects.height={height}',
                                     '-cp', os.pathsep.join((str(classes), str(JAR))), 'com.pkmngen.game.MoveEffectPresentationTest'],
                                    cwd=output, stdout=log, stderr=subprocess.STDOUT, timeout=240, creationflags=CREATE_FLAGS)
            log.flush()
            if result.returncode:
                print(logpath.read_text(encoding='utf-8'));return result.returncode
    content = logpath.read_text(encoding='utf-8')
    print(content)
    if content.count('MOVE EFFECT PRESENTATION PASS:') != 3:
        raise RuntimeError('Incomplete native move effects verification')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
