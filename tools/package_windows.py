#!/usr/bin/env python3
"""Package the verified built JAR and a portable Windows x64 JDK; never compile or download."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

from verify_rework import NATIVE, ROOT, digest, git, verify_build

DATE = (1980, 1, 1, 0, 0, 0)
PREFIX = "PokeWilds-2.5D/"
NATIVES = ("gdx64.dll", "gdx-freetype64.dll", "gdx-box2d64.dll", "jamepad64.dll",
           "windows/x64/org/lwjgl/lwjgl.dll", "windows/x64/org/lwjgl/glfw/glfw.dll",
           "windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll", "windows/x64/org/lwjgl/openal/OpenAL.dll",
           "windows/x64/org/lwjgl/stb/lwjgl_stb.dll", "windows/x64/org/lwjgl/jemalloc/jemalloc.dll")
REQUIRED_JAR = (*NATIVES, "com/pkmngen/game/desktop/DesktopLauncher.class",
                "com/pkmngen/game/StartupVerification.class",
                "com/pkmngen/game/JohtoRenderer.class", "com/pkmngen/game/JohtoBattleRenderer.class",
                "com/pkmngen/game/PmdBattleSprites.class", "visual/pmd/catalog.json",
                "visual/stardew/world-atlas.png", "visual/stardew/world-atlas.json",
                "visual/stardew/PROVENANCE.json", "visual/biomes/profiles.json")


def windows_x64(path: Path) -> None:
    data = path.read_bytes()
    if len(data) < 64 or data[:2] != b"MZ":
        raise RuntimeError("Not a Windows PE executable: " + str(path))
    offset = struct.unpack_from("<I", data, 0x3C)[0]
    if offset + 6 > len(data) or data[offset:offset + 4] != b"PE\0\0" or struct.unpack_from("<H", data, offset + 4)[0] != 0x8664:
        raise RuntimeError("Portable runtime must be Windows x64: " + str(path))


def read_receipt(path: Path, report: dict, commit: str) -> dict:
    result = json.loads(path.read_text(encoding="utf-8"))
    if result.get("status") != "passed" or result.get("jar_sha256") != report["output_sha256"]:
        raise RuntimeError("Test receipt is unsuccessful or belongs to another JAR: " + str(path))
    if result.get("sources_sha256") != report["sources_sha256"] or result.get("commit") != commit:
        raise RuntimeError("Test receipt belongs to another source checkpoint: " + str(path))
    if result.get("stage") == "native":
        if [check.get("command") for check in result.get("checks", [])] != NATIVE:
            raise RuntimeError("Native verification receipt does not cover the complete required suite")
    for check in result.get("checks", []):
        if check.get("exit_code") != 0:
            raise RuntimeError("Receipt contains a failed check")
        if digest(ROOT / check["command"][0]) != check["script_sha256"]:
            raise RuntimeError("Verification script changed since test: " + check["command"][0])
        if digest(ROOT / check["log"]) != check["log_sha256"]:
            raise RuntimeError("Verification log changed since test: " + check["log"])
    return result


def make_package(version: str, destination: Path, receipts: list[Path]) -> Path:
    if not re.fullmatch(r"v\d+\.\d+\.\d+-[a-z0-9]+\.\d+", version):
        raise RuntimeError("Use a new version such as v0.8.11-johto.4")
    if git("status", "--porcelain", "--untracked-files=all"):
        raise RuntimeError("Commit reviewed sources/assets first: packaging requires a clean working tree")
    commit = git("rev-parse", "HEAD")
    report = verify_build()
    verification = [read_receipt(path, report, commit) for path in receipts]
    if not any(r.get("stage") == "native" and r.get("checks") for r in verification):
        raise RuntimeError("A successful exact-JAR native verification receipt is required")
    jar = ROOT / "dist/pokewilds-rebuilt.jar"
    with zipfile.ZipFile(jar) as archive:
        missing = set(REQUIRED_JAR) - set(archive.namelist())
        if missing or archive.testzip():
            raise RuntimeError("Incomplete/corrupt playable JAR: " + ", ".join(sorted(missing)))
    runtime = ROOT / "toolchain/jdk-17"
    if runtime.is_symlink():
        raise RuntimeError("Portable runtime cannot be a symbolic link")
    for name in ("bin/java.exe", "bin/javac.exe", "bin/server/jvm.dll", "lib/modules", "release"):
        if not (runtime / name).is_file():
            raise RuntimeError("Missing portable runtime file: " + name)
    windows_x64(runtime / "bin/java.exe")
    windows_x64(runtime / "bin/server/jvm.dll")
    release = (runtime / "release").read_text(encoding="utf-8")
    if 'JAVA_VERSION="17.' not in release or 'OS_NAME="Windows"' not in release or 'OS_ARCH="x86_64"' not in release:
        raise RuntimeError("Expected portable Windows x64 Java 17")

    payload: dict[str, Path | bytes] = {}

    def add(path: Path, target: str) -> None:
        if not path.is_file() or path.is_symlink():
            raise RuntimeError("Missing or linked package input: " + str(path))
        if target in payload:
            raise RuntimeError("Duplicate package path: " + target)
        payload[target] = path

    add(jar, "dist/pokewilds-rebuilt.jar")
    for path in sorted(runtime.rglob("*")):
        if path.is_symlink():
            raise RuntimeError("Runtime cannot contain links: " + str(path))
        if path.is_file():
            add(path, "toolchain/jdk-17/" + path.relative_to(runtime).as_posix())
    for name in ("run.cmd", "run-johto.cmd", "run-johto-sprites.cmd"):
        payload[name] = (ROOT / name).read_text(encoding="utf-8").replace("\r\n", "\n").replace("\n", "\r\n").encode("utf-8")
    payload["GIOCA.cmd"] = b'@echo off\r\ncall "%~dp0run-johto.cmd" %*\r\nexit /b %errorlevel%\r\n'
    for path in sorted((ROOT / "licenses").rglob("*")):
        if path.is_file():
            add(path, path.relative_to(ROOT).as_posix())
    for name in ("PROVENANCE.json", "PMD-PROVENANCE.json", "GENERAZIONE-MODERNA.txt", "AGGIORNAMENTO-BIOMI.txt"):
        add(ROOT / name, "documentazione/" + name)
    for path in sorted((ROOT / "resources/visual").rglob("*")):
        if path.is_file() and (path.name.startswith(("LICENSE", "CREDITS", "NOTICE", "PROVENANCE"))):
            add(path, "documentazione/" + path.relative_to(ROOT / "resources").as_posix())
    add(ROOT / "art-source/reference/manifest.json", "documentazione/reference-manifest.json")
    add(ROOT / "docs/ASSET-RIGHTS.md", "documentazione/ASSET-RIGHTS.md")
    for name in ("REWORK-STATUS.md", "ASSET-COVERAGE.md", "REWORK-TESTS.md", "PACKAGING.md", "VERTICAL-SLICE.md"):
        path = ROOT / "docs" / name
        if path.is_file():
            add(path, "documentazione/" + name)
    for index, receipt in enumerate(verification):
        payload[f"documentazione/verification-{index + 1}.json"] = (json.dumps(receipt, indent=2) + "\n").encode()
    payload["LEGGIMI.txt"] = f'''POKEWILDS 2.5D - {version} - WINDOWS X64

AVVIO
Estrai tutto lo ZIP in una cartella scrivibile e apri GIOCA.cmd.
Non aprire il launcher dentro lo ZIP e non spostarlo da solo.
Java 17 e le librerie native sono inclusi: non occorrono Git, Python,
Java installato o download di asset. Il gioco puo avviarsi offline.

COMANDI
WASD: movimento. Mouse sinistro: conferma/azione.
Mouse destro: annulla; tienilo premuto per la corsa dove prevista.
Invio: menu Start. F11: schermo intero. I comandi personalizzati
gia salvati vengono rispettati; consulta il menu Comandi.

SALVATAGGI E AGGIORNAMENTO
La cartella run-johto viene creata accanto a GIOCA.cmd al primo avvio.
Contiene impostazioni e cartelle *.sav. Chiudi sempre il gioco prima
di copiare o spostare una partita. Conserva una copia della vecchia
run-johto, poi copiala accanto al nuovo GIOCA.cmd. Non sovrascrivere
partite nuove senza backup. Il pacchetto non contiene partite personali.
Il renderer usa anche le isole salvate: non le rigenera automaticamente.
Le modifiche alla generazione interessano soltanto nuove isole/aree;
consulta documentazione/REWORK-STATUS.md per i limiti verificati.
run.cmd avvia la modalita classica diagnostica con salvataggi in run;
GIOCA.cmd e run-johto.cmd avviano la presentazione moderna.

VERIFICHE E CREDITI
BUILD-INFO.json identifica commit, JAR, asset, runtime e test automatici.
Le ricevute documentano soltanto le verifiche effettivamente eseguite;
non sostituiscono una campagna manuale completa o prove multiplayer.
Un eventuale rapporto di avvio Windows e distribuito separatamente
come windows-package-verification.json, con hash dello ZIP provato.
Crediti e condizioni originali: licenses/ e documentazione/.
Non attribuiamo a noi diritti su Pokemon o Stardew Valley.
In caso di errore conserva il messaggio del launcher.
'''.encode("utf-8")

    def content(value: Path | bytes) -> bytes:
        return value.read_bytes() if isinstance(value, Path) else value

    file_manifest = {name: {"sha256": digest(value) if isinstance(value, Path) else hashlib.sha256(value).hexdigest(),
                            "bytes": value.stat().st_size if isinstance(value, Path) else len(value)}
                     for name, value in sorted(payload.items())}
    asset_hashes = {name: value for name, value in report["resource_files_sha256"].items()
                    if name.startswith("visual/") and name.endswith((".json", "CREDITS.txt"))}
    info = {"schema": 1, "version": version, "commit": commit, "platform": "Windows x64",
            "jar_sha256": report["output_sha256"], "sources_sha256": report["sources_sha256"],
            "build_tool_versions": report["versions"], "runtime_release": release,
            "asset_metadata_sha256": asset_hashes, "files": file_manifest,
            "asset_rights": {"status": "local-user-supplied-reference-assets; redistribution not established",
                             "details": "documentazione/ASSET-RIGHTS.md",
                             "publication": "No automatic public release. Rightsholder clearance is required before redistributing reference art."},
            "verification": [{"stage": r["stage"], "platform": r["platform"],
                              "checks": [c["command"] for c in r["checks"]]} for r in verification],
            "limitations": "Automated fixtures only. Packaged Windows startup is recorded in the external receipt bound to ZIP SHA-256."}
    payload["BUILD-INFO.json"] = (json.dumps(info, indent=2, sort_keys=True) + "\n").encode("utf-8")
    destination.mkdir(parents=True, exist_ok=True)
    target = destination / f"pokewilds-2.5d-windows-x64-{version}.zip"
    temporary = target.with_suffix(".zip.tmp")
    if temporary.exists():
        raise RuntimeError("Preserve or remove previous incomplete archive before retrying: " + str(temporary))
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, value in sorted(payload.items()):
            entry = zipfile.ZipInfo(PREFIX + name, DATE)
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, content(value), compresslevel=6)
    with zipfile.ZipFile(temporary) as archive:
        if archive.testzip():
            raise RuntimeError("Portable ZIP failed CRC validation")
        if hashlib.sha256(archive.read(PREFIX + "dist/pokewilds-rebuilt.jar")).hexdigest() != info["jar_sha256"]:
            raise RuntimeError("Packaged JAR differs from verified JAR")
    if target.exists():
        if digest(target) != digest(temporary):
            raise RuntimeError("Refusing to overwrite a different package with the same version: " + str(target))
        temporary.unlink()
    else:
        temporary.replace(target)
    (destination / "SHA256SUMS.txt").write_text(digest(target) + "  " + target.name + "\n", encoding="ascii")
    (destination / "BUILD-INFO.json").write_bytes(payload["BUILD-INFO.json"])
    print(json.dumps({"zip": str(target), "zip_sha256": digest(target), "jar_sha256": info["jar_sha256"], "commit": commit}, indent=2))
    return target


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "build/release")
    parser.add_argument("--verification", type=Path, action="append")
    args = parser.parse_args()
    make_package(args.version, args.output, args.verification or [ROOT / "build/rework-verification/native.json"])


if __name__ == "__main__":
    main()
