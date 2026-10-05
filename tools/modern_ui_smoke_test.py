#!/usr/bin/env python3
"""Exercise native modern UI and original battle rules in an isolated fixture."""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import subprocess
import sys
from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes-override", type=Path, help="Diagnostic compiled production classes; omit for a delivered-JAR test")
    args = parser.parse_args()
    destination = ROOT / "build" / "ui-battle-smoke"
    if destination.resolve() != destination or destination.is_symlink():
        raise RuntimeError("build/ui-battle-smoke deve essere locale, senza collegamenti.")
    destination.mkdir(parents=True, exist_ok=True)
    classes = destination / "classes"
    if classes.is_symlink():
        raise RuntimeError("classes non puo essere un collegamento.")
    classes.mkdir(exist_ok=True)
    for image in destination.glob("*.png"):
        image.unlink()
    java, javac = find_jdk()
    runtime = [classes]
    if args.classes_override:
        runtime.append(args.classes_override.resolve())
        runtime.append(ROOT / "resources")
    runtime.append(JAR)
    cp = os.pathsep.join(map(str, runtime))
    log_path = destination / "modern-ui-smoke.log"
    print(f"Modern UI smoke test: {log_path}", flush=True)
    with log_path.open("w", encoding="utf-8", newline="\n") as log:
        log.write(f"JAR: {JAR}\nSHA-256: {sha256(JAR)}\nOverride: {args.classes_override}\n")
        for name, command in [
            ("Compile harness", [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-classpath", cp,
             "-d", str(classes), str(ROOT / "tools" / "ModernUiSmokeTest.java")]),
            ("Native UI and battle integration", [java, "-Xmx2g", "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto",
             "-Dpokewilds.models=off", "-classpath", cp, "com.pkmngen.game.ModernUiSmokeTest"]),
        ]:
            log.write(f"\n{name}\n"); log.flush()
            try:
                result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                    timeout=300, creationflags=CREATE_FLAGS, check=False)
            except subprocess.TimeoutExpired:
                log.write("UI FAIL: external timeout 300s\n")
                return 124
            log.write(f"Exit code: {result.returncode}\n"); log.flush()
            if result.returncode:
                print(f"Test fallito: {name}. Log: {log_path}", file=sys.stderr)
                return result.returncode
    if not (destination / "25-loaded.png").is_file():
        raise RuntimeError("Manca schermata finale del round trip UI/battle/save/load.")
    print(f"UI, crafting, lotta, fuga, cattura e salvataggio verificati. Screenshot: {destination}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError) as error:
        print(f"Modern UI smoke test: {error}", file=sys.stderr)
        sys.exit(1)
