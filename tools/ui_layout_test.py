#!/usr/bin/env python3
"""Render real native menus at desktop scaling/aspect cases, without opening a visible window."""
import argparse
import os
import subprocess
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", action="store_true", help="Compile only changed UI sources over the current JAR")
    args = parser.parse_args()
    destination = ROOT / "build/ui-layout"
    classes = destination / ("source-classes" if args.sources else "jar-classes")
    if destination.is_symlink() or classes.is_symlink():
        raise RuntimeError("Test directories must not be symlinks")
    classes.mkdir(parents=True, exist_ok=True)
    java, javac = find_jdk()
    sources = [ROOT / "tools/UiLayoutTest.java"]
    if args.sources:
        sources += [ROOT / "src/com/pkmngen/game" / (name + ".java") for name in
                    ("DesktopControls", "ModernPartyUi", "ModernUi", "ModernInventoryUi", "DrawControls",
                     "DrawUseTossMenu", "DrawPokemonMenu", "TrainerTipsTile")]
    log_path = destination / "ui-layout.log"
    commands = [
        [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-classpath", str(JAR), "-d", str(classes), *map(str, sources)],
        [java, "-Xmx2g", "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto", "-Dpokewilds.models=off",
         "-Dui.sourceMode=" + str(args.sources).lower(), "-classpath", os.pathsep.join((str(classes), str(JAR))),
         "com.pkmngen.game.UiLayoutTest"]]
    with log_path.open("w", encoding="utf-8", newline="\n") as log:
        log.write(f"JAR={JAR}\nSHA256={sha256(JAR)}\nSource overrides={args.sources}\n")
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=120, creationflags=CREATE_FLAGS)
            if result.returncode:
                print(f"FAIL: {log_path}")
                return result.returncode
    output = log_path.read_text(encoding="utf-8")
    if "UI LAYOUT PASS:" not in output or "Exception" in output or "\tat " in output:
        raise RuntimeError(f"Incomplete native test: {log_path}")
    print(f"PASS: {log_path}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
