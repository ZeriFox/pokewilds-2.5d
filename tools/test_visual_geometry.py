#!/usr/bin/env python3
"""Run pure visual geometry regressions with Java 17; no GL/runtime needed."""
from pathlib import Path
import subprocess
import tempfile
from smoke_test import ROOT, find_jdk


def main() -> None:
    java, javac = find_jdk()
    with tempfile.TemporaryDirectory(prefix="pokewilds-visual-geometry-") as directory:
        subprocess.run([
            javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-d", directory,
            str(ROOT / "src/com/pkmngen/game/VisualGeometry.java"),
            str(ROOT / "tools/VisualGeometryTest.java"),
        ], check=True, timeout=60)
        subprocess.run([
            java, "-cp", directory, "com.pkmngen.game.VisualGeometryTest",
        ], check=True, timeout=30)


if __name__ == "__main__":
    main()
