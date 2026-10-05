#!/usr/bin/env python3
"""Load and GPU-render every manifest GLB using the real actor shader pipeline.

Reads resources directly rather than unpacking the game JAR. Writes per-species
TSV results and native captures for six representative/regression species to
build/model-catalog-smoke. Requires an actual desktop OpenGL driver.
"""
from __future__ import annotations

import json
import hashlib
import os
import subprocess
import sys
import zipfile

from smoke_test import CREATE_FLAGS, JAR, ROOT, find_jdk, sha256


def main() -> int:
    models = ROOT / "resources" / "visual" / "johto" / "models" / "pokemon"
    files = sorted(models.glob("*.glb"))
    provenance_path = ROOT / "MODELS-PROVENANCE.json"
    provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    expected_count = provenance["installed_model_count"]
    expected_models = {entry["species"] + ".glb": entry for entry in provenance["models"]}
    if len(files) != expected_count or {path.name for path in files} != set(expected_models):
        raise RuntimeError(f"GLB e manifest non corrispondono: attesi {expected_count}, trovati {len(files)}")
    destination = ROOT / "build" / "model-catalog-smoke"
    if destination.resolve() != destination or destination.is_symlink():
        raise RuntimeError("Il test richiede build/model-catalog-smoke locale, senza collegamenti.")
    destination.mkdir(parents=True, exist_ok=True)
    classes = destination / "classes"
    if classes.is_symlink():
        raise RuntimeError("classes non puo essere un collegamento.")
    classes.mkdir(exist_ok=True)
    if not JAR.is_file():
        raise RuntimeError("Manca dist/pokewilds-rebuilt.jar: esegui prima build.cmd.")
    screenshots = [destination / f"{species}-{pose}.png" for species in
                   ("machop", "pikachu", "charizard", "gyarados", "pidgey", "chimecho") for pose in ("idle", "walk")]
    for screenshot in screenshots:
        screenshot.unlink(missing_ok=True)
    (destination / "catalog-results.tsv").unlink(missing_ok=True)
    manifest = {"shader": "DefaultShader", "numBones": 128, "directionalLights": 1,
                "pointLights": 0, "spotLights": 0, "materialConverter": True,
                "dependencies": {JAR.name: sha256(JAR)},
                "models": {path.name: {"bytes": path.stat().st_size, "sha256": sha256(path)} for path in files}}
    for name, actual in manifest["models"].items():
        expected = expected_models[name]
        if actual["bytes"] != expected["bytes"] or actual["sha256"] != expected["sha256"]:
            raise RuntimeError(f"Asset non corrisponde al manifest: {name}")
    # Render loose resources for quick access, but prove that each byte is the
    # same as the delivered fat JAR. No external library overrides are allowed.
    with zipfile.ZipFile(JAR) as bundle:
        prefix = "visual/johto/models/pokemon/"
        bundled = {name[len(prefix):] for name in bundle.namelist()
                   if name.startswith(prefix) and name.endswith(".glb")}
        if bundled != set(manifest["models"]):
            raise RuntimeError("Il catalogo GLB nel JAR non corrisponde al manifest.")
        for name, actual in manifest["models"].items():
            entry = bundle.getinfo(prefix + name)
            digest = hashlib.sha256()
            with bundle.open(entry) as model:
                for chunk in iter(lambda: model.read(1024 * 1024), b""):
                    digest.update(chunk)
            if entry.file_size != actual["bytes"] or digest.hexdigest() != actual["sha256"]:
                raise RuntimeError(f"Asset nel JAR differente dalla risorsa verificata: {name}")
    manifest["installed_model_count"] = expected_count
    manifest["provenance_sha256"] = sha256(provenance_path)
    manifest["bundled_models_verified"] = True
    (destination / "catalog-inputs.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    java, javac = find_jdk()
    log_path = destination / "model-catalog-smoke.log"
    print(f"Catalog GPU smoke test ({expected_count} GLB): {log_path}", flush=True)
    with log_path.open("w", encoding="utf-8", newline="\n") as log:
        log.write(f"Models: {models}\nJDK: {java}\nAssets: {expected_count}; verified manifest sha256 in catalog-inputs.json\n")
        commands = [
            ("Compile GPU harness", [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none",
                "-classpath", str(JAR), "-d", str(classes), str(ROOT / "tools" / "ModelCatalogSmokeTest.java")]),
            ("Load and render catalog", [java, "-Xmx2g", "-Dfile.encoding=UTF-8", "-classpath",
                os.pathsep.join(map(str, [classes, JAR])), "ModelCatalogSmokeTest", str(models), str(expected_count)]),
        ]
        for label, command in commands:
            log.write(f"\n{label}\n")
            log.flush()
            try:
                result = subprocess.run(command, cwd=destination, stdout=log, stderr=subprocess.STDOUT,
                    timeout=300, creationflags=CREATE_FLAGS, check=False)
            except subprocess.TimeoutExpired:
                log.write("CATALOG FAIL: timeout esterno 300s\n")
                print(f"Timeout. Dettagli: {log_path}", file=sys.stderr)
                return 124
            log.write(f"Exit code: {result.returncode}\n")
            log.flush()
            if result.returncode:
                print(f"Test fallito: {label}. Dettagli: {log_path}", file=sys.stderr)
                return result.returncode
        if not all(path.is_file() for path in screenshots):
            raise RuntimeError("Mancano screenshot campione del catalogo.")
    print(f"Catalogo GPU verificato: {expected_count} GLB. Risultati: {destination / 'catalog-results.tsv'}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, RuntimeError) as error:
        print(f"Catalog smoke test: {error}", file=sys.stderr)
        sys.exit(1)
