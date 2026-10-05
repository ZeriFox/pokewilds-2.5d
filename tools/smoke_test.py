#!/usr/bin/env python3
"""Compile and run the invisible desktop menu check against the rebuilt JAR.

Requires Python 3 and a desktop with an OpenGL driver. Prefer the included
portable JDK 17; otherwise use JAVA_HOME, then a matching java/javac on PATH.
Writes build/smoke/smoke.log and build/smoke/smoke-menu.png. This does not
exercise world generation, controls, battles or save/load.
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
JAR = ROOT / "dist" / "pokewilds-rebuilt.jar"
SOURCE = ROOT / "tools" / "PcSmokeTest.java"
SMOKE = ROOT / "build" / "smoke"
CLASSES = SMOKE / "classes"
LOG = SMOKE / "smoke.log"
SCREENSHOT = SMOKE / "smoke-menu.png"
TIMEOUT = 55
CREATE_FLAGS = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def find_jdk() -> tuple[str, str]:
    suffix = ".exe" if os.name == "nt" else ""
    portable = ROOT / "toolchain" / "jdk-17"
    homes = [portable]
    configured = os.environ.get("JAVA_HOME")
    if configured:
        homes.append(Path(configured))
    for home in homes:
        java = home / "bin" / ("java" + suffix)
        javac = home / "bin" / ("javac" + suffix)
        if java.is_file() and javac.is_file():
            return str(java), str(javac)
        if home.exists() or str(home) == configured:
            raise RuntimeError(f"JDK incompleto: servono java e javac in {home / 'bin'}")
    javac_path = shutil.which("javac")
    if javac_path:
        javac = Path(javac_path).resolve()
        java = javac.with_name("java" + suffix)
        if java.is_file():
            return str(java), str(javac)
    raise RuntimeError("JDK non trovato: usa toolchain/jdk-17 oppure configura JAVA_HOME con un JDK 17.")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    if not JAR.is_file():
        raise RuntimeError("Manca dist/pokewilds-rebuilt.jar: esegui prima build.cmd o build.py.")
    if not SOURCE.is_file():
        raise RuntimeError(f"Manca il test: {SOURCE}")
    java, javac = find_jdk()
    # Keep generated test files inside this project's exact build/smoke path.
    if SMOKE.resolve() != ROOT / "build" / "smoke" or CLASSES.is_symlink():
        raise RuntimeError("build/smoke deve essere una directory locale del progetto, senza collegamenti.")
    CLASSES.mkdir(parents=True, exist_ok=True)
    # Remove only the previous image, so a startup failure cannot appear to have
    # produced a successful screenshot from an earlier run.
    if SCREENSHOT.exists():
        SCREENSHOT.unlink()
    print(f"Smoke test PC: log in {LOG}", flush=True)
    with LOG.open("w", encoding="utf-8", newline="\n") as log:
        log.write(f"JAR: {JAR}\nSHA-256: {sha256(JAR)}\nJDK: {java}\n")
        log.write(f"Timeout esterno: {TIMEOUT}s; interno Java: 45s; frame: 120\n")
        log.flush()
        commands = [
            ("Java version", [java, "-version"]),
            ("Compile harness", [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none",
                                 "-classpath", str(JAR), "-d", str(CLASSES), str(SOURCE)]),
            ("Run rebuilt game smoke test", [java, "-Dfile.encoding=UTF-8", "-Dsmoke.frames=120",
                                              "-Dsmoke.timeoutSeconds=45", "-classpath",
                                              os.pathsep.join((str(CLASSES), str(JAR))), "PcSmokeTest"]),
        ]
        for label, command in commands:
            log.write(f"\n{label}\n")
            log.flush()
            try:
                completed = subprocess.run(
                    command, cwd=SMOKE, stdout=log, stderr=subprocess.STDOUT,
                    timeout=TIMEOUT, check=False, creationflags=CREATE_FLAGS,
                )
            except subprocess.TimeoutExpired:
                log.write(f"SMOKE FAIL: timeout esterno di {TIMEOUT}s durante {label}\n")
                print(f"Test scaduto dopo {TIMEOUT}s. Dettagli: {LOG}", file=sys.stderr)
                return 124
            log.write(f"Exit code: {completed.returncode}\n")
            log.flush()
            if completed.returncode:
                print(f"Test fallito durante {label}. Dettagli: {LOG}", file=sys.stderr)
                return completed.returncode
        if not SCREENSHOT.is_file():
            log.write("SMOKE FAIL: nessuno screenshot prodotto\n")
            print(f"Test fallito: screenshot assente. Dettagli: {LOG}", file=sys.stderr)
            return 1
    print(f"Smoke test superato. Menu: {SCREENSHOT}")
    print("Verificati avvio, menu, rendering e chiusura; mondo e gameplay non ancora verificati.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError) as error:
        print(f"Smoke test: {error}", file=sys.stderr)
        sys.exit(1)
