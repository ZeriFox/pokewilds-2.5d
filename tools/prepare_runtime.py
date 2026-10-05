#!/usr/bin/env python3
"""Ricrea il runtime di supporto dal JAR originale PokeWilds 0.8.11."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "lib" / "upstream-runtime.jar"
EXPECTED_SHA256 = "0a72e17bf5cc3bd14ac97a6d001fe16c32cf8bd2d6caf1271bb2d3f234a6f248"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def prepare(original: Path) -> None:
    original = original.resolve()
    if original == OUTPUT.resolve():
        raise RuntimeError("Il JAR originale deve essere distinto da lib/upstream-runtime.jar.")
    original_hash = sha256(original)
    if original_hash != EXPECTED_SHA256:
        raise RuntimeError(
            "Il JAR originale non corrisponde alla versione verificata.\n"
            f"SHA-256 atteso:   {EXPECTED_SHA256}\n"
            f"SHA-256 ricevuto: {original_hash}"
        )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT.with_suffix(".jar.tmp")
    kept = 0
    removed = 0
    try:
        with zipfile.ZipFile(original) as source, zipfile.ZipFile(temporary, "w") as destination:
            for info in source.infolist():
                if info.filename.startswith("com/pkmngen/"):
                    removed += 1
                    continue
                # Conserva ordine, metadati e tipo di compressione del JAR originale.
                destination.writestr(info, source.read(info))
                kept += 1
        with zipfile.ZipFile(temporary) as result:
            if any(name.startswith("com/pkmngen/") for name in result.namelist()):
                raise RuntimeError("Il runtime contiene ancora elementi del namespace com/pkmngen.")
            invalid = result.testzip()
            if invalid is not None:
                raise RuntimeError(f"Verifica ZIP fallita: {invalid}")
        temporary.replace(OUTPUT)
    finally:
        if temporary.exists():
            temporary.unlink()
    print(f"Runtime creato: {OUTPUT}")
    print(f"Elementi mantenuti: {kept}; elementi del gioco rimossi: {removed}.")
    print(f"SHA-256: {sha256(OUTPUT)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original_jar", type=Path, help="Percorso del JAR originale PokeWilds 0.8.11 verificato")
    arguments = parser.parse_args()
    try:
        prepare(arguments.original_jar)
    except (OSError, RuntimeError, zipfile.BadZipFile) as error:
        print(f"Errore: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
