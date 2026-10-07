#!/usr/bin/env python3
"""Unit checks for packaging gates/privacy/determinism; synthetic inputs are NOT a game test."""
from __future__ import annotations

import contextlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import package_windows as package


class PackageTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="pokewilds-package-unit-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.report = {"status": "success", "output_sha256": "pending", "sources_sha256": "fixture-sources",
                       "resource_files_sha256": {"visual/stardew/world-atlas.json": "fixture-hash"},
                       "versions": {"python": "unit fixture"}}
        self.commit = "1234567890abcdef" * 2 + "12345678"
        jar = self.root / "dist/pokewilds-rebuilt.jar"
        jar.parent.mkdir()
        with zipfile.ZipFile(jar, "w") as archive:
            for name in package.REQUIRED_JAR:
                archive.writestr(name, b"synthetic unit test fixture, not runnable")
        self.report["output_sha256"] = package.digest(jar)
        header = bytearray(128)
        header[:2] = b"MZ"
        struct.pack_into("<I", header, 0x3c, 64)
        header[64:68] = b"PE\0\0"
        struct.pack_into("<H", header, 68, 0x8664)
        for name in ("bin/java.exe", "bin/javac.exe", "bin/server/jvm.dll"):
            self.write("toolchain/jdk-17/" + name, bytes(header))
        self.write("toolchain/jdk-17/lib/modules", b"synthetic modules")
        self.write("toolchain/jdk-17/release", b'JAVA_VERSION="17.0.1"\nOS_NAME="Windows"\nOS_ARCH="x86_64"\n')
        for name in ("run.cmd", "run-johto.cmd", "run-johto-sprites.cmd", "PROVENANCE.json",
                     "PMD-PROVENANCE.json", "GENERAZIONE-MODERNA.txt", "AGGIORNAMENTO-BIOMI.txt",
                     "art-source/reference/manifest.json", "docs/ASSET-RIGHTS.md", "licenses/NOTICE.txt"):
            self.write(name, b"synthetic package unit fixture\n")
        self.write("run-johto/personal.sav/game.json", b"must never be distributed")
        self.write("run/settings.txt", b"private settings must never be distributed")
        self.write(".env", b"private secret must never be distributed")
        checks = []
        for args in package.NATIVE:
            self.write(args[0], b"# synthetic test script\n")
            log = "build/unit-logs/" + Path(args[0]).stem + ".log"
            self.write(log, b"synthetic unit log\n")
            checks.append({"command": args, "exit_code": 0, "script_sha256": package.digest(self.root / args[0]),
                           "log": log, "log_sha256": package.digest(self.root / log)})
        self.receipt = {"status": "passed", "stage": "native", "commit": self.commit,
                        "platform": "synthetic unit fixture", "jar_sha256": self.report["output_sha256"],
                        "sources_sha256": self.report["sources_sha256"], "checks": checks}
        self.receipt_path = self.root / "build/native.json"
        self.save_receipt()
        self.addCleanup(patch.stopall)
        patch.object(package, "ROOT", self.root).start()
        patch.object(package, "verify_build", return_value=self.report).start()
        patch.object(package, "git", side_effect=lambda *args: "" if args[0] == "status" else self.commit).start()

    def write(self, name: str, data: bytes) -> None:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def save_receipt(self) -> None:
        self.receipt_path.parent.mkdir(parents=True, exist_ok=True)
        self.receipt_path.write_text(json.dumps(self.receipt), encoding="utf-8")

    def build(self) -> Path:
        with contextlib.redirect_stdout(io.StringIO()):
            return package.make_package("v0.8.11-unit.1", self.root / "build/release", [self.receipt_path])

    def test_repeatable_exact_jar_no_private_state(self) -> None:
        first = self.build()
        first_hash = package.digest(first)
        self.assertEqual(first_hash, package.digest(self.build()))
        with zipfile.ZipFile(first) as archive:
            names = archive.namelist()
            self.assertFalse(any("/run/" in name or "/run-johto/" in name or ".env" in name for name in names))
            self.assertEqual(archive.read(package.PREFIX + "dist/pokewilds-rebuilt.jar"),
                             (self.root / "dist/pokewilds-rebuilt.jar").read_bytes())
            info = json.loads(archive.read(package.PREFIX + "BUILD-INFO.json"))
            self.assertEqual(info["commit"], self.commit)
            self.assertIn("redistribution not established", info["asset_rights"]["status"])
            self.assertEqual(set(names), {package.PREFIX + name for name in info["files"]} | {package.PREFIX + "BUILD-INFO.json"})
            self.assertTrue(all(member.date_time == package.DATE for member in archive.infolist()))

    def test_wrong_jar_receipt_and_incomplete_suite_rejected(self) -> None:
        self.receipt["jar_sha256"] = "another jar"
        self.save_receipt()
        with self.assertRaisesRegex(RuntimeError, "another JAR"):
            self.build()
        self.receipt["jar_sha256"] = self.report["output_sha256"]
        self.receipt["checks"].pop()
        self.save_receipt()
        with self.assertRaisesRegex(RuntimeError, "complete required suite"):
            self.build()

    def test_dirty_checkout_wrong_architecture_and_modified_log_rejected(self) -> None:
        with patch.object(package, "git", return_value=" M src/example.java"):
            with self.assertRaisesRegex(RuntimeError, "clean working tree"):
                self.build()
        java = self.root / "toolchain/jdk-17/bin/java.exe"
        header = bytearray(java.read_bytes())
        struct.pack_into("<H", header, 68, 0x14c)
        java.write_bytes(header)
        with self.assertRaisesRegex(RuntimeError, "Windows x64"):
            self.build()
        struct.pack_into("<H", header, 68, 0x8664)
        java.write_bytes(header)
        self.write(self.receipt["checks"][0]["log"], b"tampered")
        with self.assertRaisesRegex(RuntimeError, "log changed"):
            self.build()

    def test_same_version_with_different_bytes_is_not_overwritten(self) -> None:
        original = self.build()
        original_hash = package.digest(original)
        self.write("licenses/NOTICE.txt", b"changed notice")
        with self.assertRaisesRegex(RuntimeError, "Refusing to overwrite"):
            self.build()
        self.assertEqual(original_hash, package.digest(original))


if __name__ == "__main__":
    unittest.main(verbosity=2)
