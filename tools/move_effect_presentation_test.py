#!/usr/bin/env python3
"""Exercise actual move action, original PNGs and metadata exclusively from the built JAR."""
import os
import argparse
import subprocess
from smoke_test import ROOT, JAR, find_jdk, sha256, CREATE_FLAGS


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', action='store_true', help='Diagnostic source overlays over existing JAR')
    args=parser.parse_args()
    destination = ROOT / ('build/move-effects-diagnostic' if args.sources else 'build/move-effects')
    classes = destination / 'classes'
    classes.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    sources=[ROOT/'tools/MoveEffectPresentationTest.java']
    if args.sources:
        sources += [ROOT/'src/com/pkmngen/game'/name for name in ('JohtoBattleRenderer.java','BattleScenery.java','PmdBattleSprites.java','MoveEffectPresentation.java','GhostVisualAssets.java', 'ShinyVisuals.java')]
    cp=os.pathsep.join(map(str,[classes,*([ROOT/'resources'] if args.sources else []),JAR]))
    logpath = destination / 'move-effects.log'
    with logpath.open('w', encoding='utf-8', newline='\n') as log:
        log.write('JAR SHA256: ' + sha256(JAR) + '\nProduction source overlays: '+str(args.sources)+'\n')
        log.flush()
        result = subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-proc:none', '-cp', cp,
                                 '-d', str(classes), *map(str,sources)],
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
                                     '-cp', cp, 'com.pkmngen.game.MoveEffectPresentationTest'],
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
