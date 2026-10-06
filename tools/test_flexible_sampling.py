#!/usr/bin/env python3
"""Run the real VisualSampling Java code without the game runtime or OpenGL."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def jdk_tool(name: str) -> str:
    suffix = ".exe" if os.name == "nt" else ""
    bundled = ROOT / "toolchain" / "jdk-17" / "bin" / (name + suffix)
    if bundled.is_file():
        return str(bundled)
    home = os.environ.get("JAVA_HOME")
    if home:
        candidate = Path(home) / "bin" / (name + suffix)
        if not candidate.is_file():
            raise RuntimeError(f"Missing {name} in JAVA_HOME: {candidate}")
        return str(candidate)
    executable = shutil.which(name)
    if executable is None:
        raise RuntimeError(f"Install JDK 17+ and configure JAVA_HOME or PATH: {name} missing")
    return executable


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="pokewilds-sampling-") as temporary:
        subprocess.run([
            jdk_tool("javac"), "--release", "17", "-encoding", "UTF-8", "-d", temporary,
            str(ROOT / "src/com/pkmngen/game/VisualSampling.java"),
            str(ROOT / "tools/VisualSamplingTest.java"),
        ], check=True, cwd=ROOT)
        subprocess.run([
            jdk_tool("java"), "-cp", temporary, "com.pkmngen.game.VisualSamplingTest",
        ], check=True, cwd=ROOT)
    print("Sampling unit tests only: this does not compile or launch the full game.")


if __name__ == "__main__":
    main()
