#!/usr/bin/env python3
"""Render every real encounter entry frame and verify reduced-motion/full-window coverage."""
import argparse
import os
from pathlib import Path
import subprocess
from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes-override", type=Path, help="Diagnostic compiled sources; omit for delivered-JAR validation")
    args = parser.parse_args()
    target = ROOT / "build/battle-entry"
    classes = target / ("diagnostic-test-classes" if args.classes_override else "bundled-test-classes")
    if target.is_symlink() or classes.is_symlink():
        raise RuntimeError("Refuse a linked test directory")
    classes.mkdir(parents=True, exist_ok=True)
    runtime = [classes]
    if args.classes_override:
        runtime.extend([args.classes_override.resolve(), ROOT / "resources"])
    runtime.append(JAR)
    cp = os.pathsep.join(map(str, runtime))
    java, javac = find_jdk()
    log_path = target / "battle-entry.log"
    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"JAR: {JAR}\nSHA256: {sha256(JAR)}\nProduction override: {args.classes_override}\n")
        commands = [
            [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", cp, "-d", str(classes), str(ROOT / "tools/BattleEntryTest.java")],
            [java, "-Xmx2g", "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto", "-Dpokewilds.models=off", "-cp", cp, "com.pkmngen.game.BattleEntryTest"],
        ]
        for command in commands:
            log.flush()
            result = subprocess.run(command, cwd=target, stdout=log, stderr=subprocess.STDOUT, timeout=180, creationflags=CREATE_FLAGS)
            if result.returncode:
                print(log_path.read_text(encoding="utf-8", errors="replace"))
                return result.returncode
    output = log_path.read_text(encoding="utf-8", errors="replace")
    if "BATTLE ENTRY PASS" not in output:
        raise RuntimeError(f"Incomplete native test: {log_path}")
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
