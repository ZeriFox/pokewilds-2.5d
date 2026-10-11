#!/usr/bin/env python3
"""Exercise the modern menu event/focus layer against the actual built game JAR."""
from pathlib import Path
import os
import subprocess
from smoke_test import ROOT, JAR, find_jdk, sha256, CREATE_FLAGS


def main() -> None:
    target = ROOT / "build/desktop-menu-flow"
    classes = target / "classes"
    if target.is_symlink() or classes.is_symlink():
        raise RuntimeError("Refuse a linked test directory")
    classes.mkdir(parents=True, exist_ok=True)
    # These files are owned by this isolated fixture; never use the player's run directory.
    for name in ("interface-settings.json", "interface-settings.json.bak", "interface-settings.json.tmp"):
        (target / name).unlink(missing_ok=True)
    java, javac = find_jdk()
    commands = [
        [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", str(JAR), "-d", str(classes), str(ROOT / "tools/DesktopMenuFlowTest.java")],
        [java, "-Xmx2g", "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto", "-Dpokewilds.models=off", "-cp", os.pathsep.join((str(classes), str(JAR))), "com.pkmngen.game.DesktopMenuFlowTest"],
    ]
    log_path = target / "desktop-menu-flow.log"
    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"Built JAR: {JAR}\nSHA256: {sha256(JAR)}\nProduction overrides: none\n")
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=target, stdout=log, stderr=subprocess.STDOUT, timeout=120, creationflags=CREATE_FLAGS)
            if result.returncode:
                print(log_path.read_text(encoding="utf-8", errors="replace"))
                raise RuntimeError("Desktop menu flow failed")
    output=log_path.read_text(encoding="utf-8")
    if "DESKTOP MENU FLOW PASS:" not in output:
        raise RuntimeError("Missing native completion marker")
    print(output)


if __name__ == "__main__":
    main()
