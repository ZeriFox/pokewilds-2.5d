#!/usr/bin/env python3
"""Ricompila PokeWilds usando soltanto Python e un JDK locale."""

from __future__ import annotations

import hashlib
from contextlib import ExitStack
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
import zlib


ROOT = Path(__file__).resolve().parent
SOURCE_ROOT = ROOT / "src" / "com" / "pkmngen"
RESOURCE_ROOT = ROOT / "resources"
RUNTIME = ROOT / "lib" / "upstream-runtime.jar"
BUILD = ROOT / "build"
CLASSES = BUILD / "classes"
OUTPUT = ROOT / "dist" / "pokewilds-rebuilt.jar"
REPORT = BUILD / "build-report.json"
MAIN_CLASS = "com.pkmngen.game.desktop.DesktopLauncher"
ZIP_DATE = (1980, 1, 1, 0, 0, 0)


def digest_file(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def jdk_tool(name: str) -> str:
    suffix = ".exe" if os.name == "nt" else ""
    bundled = ROOT / "toolchain" / "jdk-17" / "bin" / (name + suffix)
    if bundled.is_file():
        return str(bundled)
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / (name + suffix)
        if not candidate.is_file():
            raise RuntimeError(f"JAVA_HOME non contiene {name}: {candidate}")
        return str(candidate)
    executable = shutil.which(name)
    if executable is None:
        raise RuntimeError(
            f"{name} non trovato. Installa un JDK 17 o successivo e configura JAVA_HOME o PATH."
        )
    return executable


def tool_version(executable: str) -> str:
    completed = subprocess.run(
        [executable, "-version" if Path(executable).stem != "jar" else "--version"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    return completed.stdout.strip()


def excluded_entry(name: str) -> bool:
    upper = name.upper()
    if upper == "META-INF/MANIFEST.MF":
        return True
    if upper.startswith("META-INF/"):
        leaf = upper[len("META-INF/") :]
        if "/" not in leaf:
            return leaf.endswith((".SF", ".RSA", ".DSA", ".EC")) or leaf.startswith("SIG-")
    return False


def zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
    info.create_system = 3
    info.external_attr = ((0o40755 if name.endswith("/") else 0o100644) << 16)
    if name.endswith("/"):
        info.external_attr |= 0x10
    info.compress_type = zipfile.ZIP_DEFLATED
    return info


def write_report(report: dict) -> None:
    REPORT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def clean_classes() -> None:
    # Elimina ricorsivamente soltanto la directory esatta build/classes del progetto.
    expected = ROOT / "build" / "classes"
    if BUILD.is_symlink() or CLASSES.is_symlink():
        raise RuntimeError("Rifiuto di pulire build/classes: il percorso contiene un collegamento simbolico.")
    resolved = CLASSES.resolve()
    if resolved != expected or not resolved.is_relative_to(ROOT):
        raise RuntimeError(f"Percorso di pulizia non sicuro: {resolved}")
    if CLASSES.exists():
        shutil.rmtree(CLASSES)
    CLASSES.mkdir(parents=True)


def build() -> int:
    if not RUNTIME.is_file():
        raise RuntimeError(
            "Manca lib/upstream-runtime.jar. Occorre il runtime originale con le classi "
            "com/pkmngen rimosse; questa build non scarica dipendenze."
        )
    sources = sorted(SOURCE_ROOT.rglob("*.java"))
    if not sources:
        raise RuntimeError("Nessun sorgente Java trovato in src/com/pkmngen.")
    executables = {name: jdk_tool(name) for name in ("javac", "java", "jar")}
    versions = {name: tool_version(path) for name, path in executables.items()}
    versions.update(python=sys.version, zlib=zlib.ZLIB_RUNTIME_VERSION)
    with zipfile.ZipFile(RUNTIME) as runtime:
        names = runtime.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError("Il runtime contiene entry ZIP duplicate.")
        forbidden = [name for name in names if name.startswith("com/pkmngen/") and name.endswith(".class")]
        if forbidden:
            raise RuntimeError(f"Il runtime contiene ancora {len(forbidden)} classi del gioco originale.")
        runtime_entries = sorted(name for name in names if not excluded_entry(name))
    addon_libraries = sorted(path for path in (ROOT / "lib").glob("*.jar") if path != RUNTIME)
    addon_entries: dict[str, Path] = {}
    with zipfile.ZipFile(RUNTIME) as runtime:
        for library in addon_libraries:
            with zipfile.ZipFile(library) as archive:
                entries = archive.namelist()
                if len(entries) != len(set(entries)):
                    raise RuntimeError(f"Entry duplicate nella libreria: {library.name}")
                for name in entries:
                    if name.endswith("/") or excluded_entry(name):
                        continue
                    if name.startswith("com/pkmngen/"):
                        raise RuntimeError(f"Classi del gioco nella libreria aggiuntiva: {library.name}")
                    if name in runtime_entries or name in addon_entries:
                        raise RuntimeError(f"Collisione tra dipendenze: {name}")
                    addon_entries[name] = library
    source_hashes = {path.relative_to(ROOT).as_posix(): digest_file(path) for path in sources}
    resource_entries = {
        path.relative_to(RESOURCE_ROOT).as_posix(): path
        for path in sorted(RESOURCE_ROOT.rglob("*")) if path.is_file()
    }
    resource_hashes = {name: digest_file(path) for name, path in resource_entries.items()}
    if set(resource_entries).intersection(set(runtime_entries) | set(addon_entries)):
        raise RuntimeError("Una risorsa nuova sovrascriverebbe il runtime originale.")
    combined_sources_hash = hashlib.sha256(
        json.dumps(source_hashes, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    clean_classes()
    report = {
        "status": "compiling",
        "main_class": MAIN_CLASS,
        "java_release": 17,
        "versions": versions,
        "executables": executables,
        "source_count": len(sources),
        "sources_sha256": combined_sources_hash,
        "source_files_sha256": source_hashes,
        "resource_files_sha256": resource_hashes,
        "runtime_sha256": digest_file(RUNTIME),
        "runtime_entry_count": len(names),
        "runtime_entries_copied": len(runtime_entries),
        "runtime_game_class_count": len(forbidden),
        "additional_libraries_sha256": {p.relative_to(ROOT).as_posix(): digest_file(p) for p in addon_libraries},
        "additional_library_entries": len(addon_entries),
    }
    write_report(report)
    # Un argfile evita il limite di lunghezza della riga di comando su Windows.
    arguments = BUILD / "sources.args"
    arguments.write_text(
        "\n".join('"' + path.as_posix().replace('"', '\\"') + '"' for path in sources) + "\n",
        encoding="utf-8",
    )
    command = [
        executables["javac"], "-J-Dfile.encoding=UTF-8", "--release", "17", "-encoding", "UTF-8", "-proc:none",
        "-classpath", os.pathsep.join(str(p) for p in [RUNTIME, *addon_libraries]),
        "-d", str(CLASSES), "@" + str(arguments),
    ]
    print(f"Compilazione di {len(sources)} sorgenti Java...", flush=True)
    compiled = subprocess.run(
        command, cwd=ROOT, check=False, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace",
    )
    (BUILD / "build.log").write_text(compiled.stdout, encoding="utf-8")
    if compiled.stdout:
        print(compiled.stdout, end="" if compiled.stdout.endswith("\n") else "\n")
    if compiled.returncode != 0:
        report.update(status="compile_failed", javac_exit_code=compiled.returncode)
        write_report(report)
        print("Compilazione fallita. Nessun nuovo JAR creato.", file=sys.stderr)
        return compiled.returncode
    class_files = sorted(CLASSES.rglob("*.class"))
    class_entries = {path.relative_to(CLASSES).as_posix(): path for path in class_files}
    expected_main = MAIN_CLASS.replace(".", "/") + ".class"
    if expected_main not in class_entries:
        raise RuntimeError("La compilazione non ha prodotto la classe DesktopLauncher.")
    collisions = (set(runtime_entries) | set(addon_entries) | set(resource_entries)).intersection(class_entries)
    if collisions:
        raise RuntimeError("Il runtime contiene classi che sovrascriverebbero la compilazione: " + ", ".join(sorted(collisions)))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temporary_output = OUTPUT.with_suffix(".jar.tmp")
    manifest = (
        "Manifest-Version: 1.0\r\n"
        f"Main-Class: {MAIN_CLASS}\r\n"
        "\r\n"
    ).encode("ascii")
    try:
        with ExitStack() as library_stack, zipfile.ZipFile(RUNTIME) as runtime, zipfile.ZipFile(
            temporary_output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
        ) as destination:
            libraries = {path: library_stack.enter_context(zipfile.ZipFile(path)) for path in addon_libraries}
            destination.writestr(zip_info("META-INF/MANIFEST.MF"), manifest, compresslevel=9)
            for name in sorted(set(runtime_entries) | set(addon_entries) | set(class_entries) | set(resource_entries)):
                if name in class_entries:
                    content = class_entries[name].read_bytes()
                elif name in resource_entries:
                    content = resource_entries[name].read_bytes()
                elif name in addon_entries:
                    content = libraries[addon_entries[name]].read(name)
                else:
                    content = runtime.read(name)
                destination.writestr(zip_info(name), content, compresslevel=9)
        with zipfile.ZipFile(temporary_output) as rebuilt:
            corrupt_entry = rebuilt.testzip()
            if corrupt_entry is not None:
                raise RuntimeError(f"JAR prodotto non valido: {corrupt_entry}")
            output_names = set(rebuilt.namelist())
            if not set(class_entries).issubset(output_names):
                raise RuntimeError("Il JAR prodotto non contiene tutte le classi compilate.")
            for name, path in class_entries.items():
                if rebuilt.read(name) != path.read_bytes():
                    raise RuntimeError(f"Classe compilata differente nel JAR: {name}")
        temporary_output.replace(OUTPUT)
    finally:
        if temporary_output.exists():
            temporary_output.unlink()
    report.update(
        status="success",
        compiled_class_count=len(class_entries),
        output_entry_count=len(output_names),
        output_sha256=digest_file(OUTPUT),
        output_size_bytes=OUTPUT.stat().st_size,
        output=OUTPUT.relative_to(ROOT).as_posix(),
        deterministic_zip_timestamp="1980-01-01T00:00:00",
        verification="CRC ZIP e contenuto di tutte le classi compilate verificati",
    )
    write_report(report)
    print(f"Build completata: {OUTPUT}")
    print(f"SHA-256: {report['output_sha256']}")
    print(f"Report: {REPORT}")
    print("Avvio: run-johto.cmd (2.5D), oppure run.cmd (grafica classica).")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(build())
    except (OSError, RuntimeError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"Errore di build: {error}", file=sys.stderr)
        sys.exit(1)
