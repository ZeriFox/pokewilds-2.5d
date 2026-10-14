#!/usr/bin/env python3
"""Real capture animations, exact action schedules and outcomes across a full battle orbit."""
import argparse,os,subprocess
from smoke_test import ROOT,JAR,find_jdk,CREATE_FLAGS,sha256

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--sources',action='store_true');args=parser.parse_args()
    dest=ROOT/('build/battle-capture-diagnostic' if args.sources else 'build/battle-capture');classes=dest/'classes';classes.mkdir(parents=True,exist_ok=True)
    if dest.is_symlink() or classes.is_symlink():raise RuntimeError('Capture test directories must not be links')
    for previous in classes.rglob('*.class'):
        if previous.is_symlink() or not previous.resolve().is_relative_to(classes.resolve()):raise RuntimeError('Unexpected capture class path')
        if not args.sources and not (previous.name=='BattleCaptureOrbitTest.class' or previous.name.startswith('BattleCaptureOrbitTest$')):
            raise RuntimeError('Production override found in bundled capture fixture: '+str(previous))
        previous.unlink()
    for image in dest.glob('*.png'):
        if image.is_symlink() or image.resolve().parent!=dest.resolve():raise RuntimeError('Unexpected capture image path')
        image.unlink()
    sources=[ROOT/'tools/BattleCaptureOrbitTest.java']
    if args.sources:
        names=['BattleScenery','JohtoBattleRenderer','PmdBattleSprites','MoveEffectPresentation','GhostVisualAssets','ShinyVisuals','UserSpriteAssets','CaptureBallVisual','ThrowPokeball','CatchPokemonWobblesThenCatch','CatchPokemonWobbles3Times']
        sources += [ROOT/'src/com/pkmngen/game'/(n+'.java') for n in names]
    cp=os.pathsep.join(map(str,[classes,*([ROOT/'resources'] if args.sources else []),JAR]));java,javac=find_jdk()
    logfile=dest/'battle-capture.log'
    with logfile.open('w',encoding='utf-8') as log:
        log.write('JAR SHA256 '+sha256(JAR)+'\nSource overlays '+str(args.sources)+'\n');log.flush()
        for cmd in [[javac,'--release','17','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes),*map(str,sources)],
                    [java,'-Xmx2g','-Dpokewilds.visual=johto','-Dpokewilds.models=off','-cp',cp,'com.pkmngen.game.BattleCaptureOrbitTest']]:
            result=subprocess.run(cmd,cwd=dest,stdout=log,stderr=subprocess.STDOUT,timeout=180,creationflags=CREATE_FLAGS);log.flush()
            if result.returncode:print(logfile.read_text(encoding='utf-8'));return result.returncode
    text=logfile.read_text(encoding='utf-8');print(text)
    if 'BATTLE CAPTURE ORBIT PASS:' not in text:raise RuntimeError('Incomplete capture evidence')
    return 0
if __name__=='__main__':raise SystemExit(main())
