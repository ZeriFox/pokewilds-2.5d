#!/usr/bin/env python3
"""Rebuild the reviewed unsigned-index splitter into the pinned gdx-gltf JAR.

The original dependency is preserved and verified before any replacement.
Only MeshSpliter and its possible nested classes may differ from upstream.
Run from any directory; Python's standard library and bundled JDK 17 suffice.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / "third_party/gdx-gltf"
ORIGINAL_SHA256 = "c728f2009ad916c9a3ad882e41f9e7e505d7ff78f36201ccd04c3aca4f3a1809"
ORIGINAL = VENDOR / "gltf-2.1.0-original.jar"
DESTINATION = ROOT / "lib/gltf-2.1.0.jar"
RUNTIME = ROOT / "lib/upstream-runtime.jar"
CLASS_PREFIX = "net/mgsx/gltf/loaders/shared/geometry/MeshSpliter"
SOURCE = VENDOR / "src" / (CLASS_PREFIX + ".java")
TEST = ROOT / "tools/MeshSplitterInvariantTest.java"
REPORT = VENDOR / "PATCH-PROVENANCE.json"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def patch_class(name: str) -> bool:
    return name == CLASS_PREFIX + ".class" or (
        name.startswith(CLASS_PREFIX + "$") and name.endswith(".class")
    )


def run(arguments: list[str]) -> str:
    result = subprocess.run(arguments, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    output = result.stdout + result.stderr
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {arguments[0]}\n{output}")
    return output.strip()


def main() -> None:
    VENDOR.mkdir(parents=True, exist_ok=True)
    if not ORIGINAL.is_file():
        original = DESTINATION.read_bytes()
        if digest(original) != ORIGINAL_SHA256:
            raise ValueError("Installed dependency is not the expected original; refusing to create an untrusted backup")
        # Exclusive create prevents overwriting a backup from another invocation.
        with ORIGINAL.open("xb") as file:
            file.write(original)
    original = ORIGINAL.read_bytes()
    if digest(original) != ORIGINAL_SHA256:
        raise ValueError("Original gdx-gltf dependency backup does not match its pinned SHA256")
    extension = ".exe" if os.name == "nt" else ""
    javac = ROOT / f"toolchain/jdk-17/bin/javac{extension}"
    java = ROOT / f"toolchain/jdk-17/bin/java{extension}"
    compiler_version = run([str(javac), "-version"])
    if not compiler_version.startswith("javac 17."):
        raise ValueError(f"Expected bundled JDK17, found {compiler_version}")
    build = ROOT / "build/gltf-loader-patch"
    build.mkdir(parents=True, exist_ok=True)
    classpath = os.pathsep.join(map(str, [ORIGINAL, RUNTIME]))
    with tempfile.TemporaryDirectory(prefix="compile-", dir=build) as directory:
        output = Path(directory)
        classes = output / "patch"
        tests = output / "tests"
        classes.mkdir()
        tests.mkdir()
        run([str(javac), "--release", "17", "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes), str(SOURCE)])
        replacements = {path.relative_to(classes).as_posix(): path.read_bytes() for path in classes.rglob("*.class")}
        if CLASS_PREFIX + ".class" not in replacements or any(not patch_class(name) for name in replacements):
            raise ValueError("Compilation produced classes outside the permitted MeshSpliter override")
        test_classpath = os.pathsep.join([str(classes), classpath])
        run([str(javac), "--release", "17", "-encoding", "UTF-8", "-cp", test_classpath, "-d", str(tests), str(TEST)])
        test_output = run([
            str(java), "-cp", os.pathsep.join([str(tests), test_classpath]),
            "net.mgsx.gltf.loaders.shared.geometry.MeshSplitterInvariantTest",
            str(ROOT / "resources/visual/johto/models/pokemon/chimecho.glb"),
        ])
        entries = {}
        with zipfile.ZipFile(ORIGINAL) as archive:
            if len(archive.namelist()) != len(set(archive.namelist())):
                raise ValueError("Original archive contains duplicate entry names")
            if CLASS_PREFIX + ".class" not in archive.namelist():
                raise ValueError("Expected upstream class is absent")
            for entry in archive.infolist():
                name = entry.filename
                if name.startswith("META-INF/") and name.upper().endswith((".SF", ".RSA", ".DSA", ".EC")):
                    raise ValueError("Refusing to modify a signed JAR")
                if not patch_class(name):
                    entries[name] = archive.read(name)
        entries.update(replacements)
        candidate = output / "gltf-2.1.0.jar"
        with zipfile.ZipFile(candidate, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name in sorted(entries):
                entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.create_system = 3
                entry.external_attr = (0o40755 if name.endswith("/") else 0o100644) << 16
                archive.writestr(entry, entries[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        with zipfile.ZipFile(candidate) as archive:
            if archive.testzip() is not None or len(archive.namelist()) != len(entries):
                raise ValueError("Generated JAR failed structural verification")
            for name, expected in entries.items():
                if archive.read(name) != expected:
                    raise ValueError(f"Generated JAR changed entry content: {name}")
        patched = candidate.read_bytes()
        report = {
            "schema_version": 1,
            "upstream_repository": "https://github.com/mgsx-dev/gdx-gltf",
            "upstream_tag": "2.1.0",
            "upstream_sha256": ORIGINAL_SHA256,
            "original_jar": ORIGINAL.relative_to(ROOT).as_posix(),
            "patched_jar": DESTINATION.relative_to(ROOT).as_posix(),
            "patched_jar_sha256": digest(patched),
            "patch_source": SOURCE.relative_to(ROOT).as_posix(),
            "patch_source_sha256": digest(SOURCE.read_bytes()),
            "patch_tool_sha256": digest(Path(__file__).read_bytes()),
            "compiler": compiler_version,
            "release": 17,
            "replaced_classes": {name: digest(data) for name, data in sorted(replacements.items())},
            "other_entries_byte_identical_to_upstream": True,
            "test_source": TEST.relative_to(ROOT).as_posix(),
            "test_source_sha256": digest(TEST.read_bytes()),
            "invariant_test_output": test_output.splitlines(),
        }
        # Tests and all comparison checks finish before the installed JAR changes.
        temporary = DESTINATION.with_suffix(".jar.part")
        temporary.write_bytes(patched)
        temporary.replace(DESTINATION)
        temporary_report = REPORT.with_suffix(".json.part")
        temporary_report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        temporary_report.replace(REPORT)
    print(test_output)
    print(f"Patched JAR SHA256: {digest(patched)}")
    print(f"Provenance: {REPORT.relative_to(ROOT).as_posix()}")


if __name__ == "__main__":
    main()
