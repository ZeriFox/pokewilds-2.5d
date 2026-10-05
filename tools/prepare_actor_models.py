#!/usr/bin/env python3
"""Download pinned, verified actor GLBs using Python's standard library.

No original game resources are altered. Species names come from the recovered
runtime JAR. Model rights are separate from the source repository's code license;
see licenses/*-models.txt. Re-runs verify existing files before downloading.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import time
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "06wj/pokemon"
COMMIT = "00d96f7f18894055e7f1db44fa0df6462e5e4c8a"
SOURCES = {
    "06wj": {"repository": REPOSITORY, "commit": COMMIT, "documents": ["README.md", "LICENSE"]},
    "legacy": {
        "repository": "Sudhanshu-Ambastha/Pokemon-3D-api",
        "commit": "eaccd7e5e9522623d6d41249131a0aeb676388da",
        "documents": ["LEGACY_README.md", "docs/LICENSE", "docs/COPYRIGHT.md", "docs/CREDITS.md"],
    },
}
for source in SOURCES.values():
    source["raw"] = f"https://raw.githubusercontent.com/{source['repository']}/{source['commit']}/"
    source["tree_url"] = f"https://api.github.com/repos/{source['repository']}/git/trees/{source['commit']}?recursive=1"
RAW = SOURCES["06wj"]["raw"]
OUT = ROOT / "resources/visual/johto/models/pokemon"
MANIFEST = ROOT / "MODELS-PROVENANCE.json"
CACHE = ROOT / "build/model-source-metadata"
MAX_DOWNLOAD = 32 * 1024 * 1024
UNSUPPORTED = {"KHR_draco_mesh_compression", "EXT_meshopt_compression", "EXT_texture_webp", "KHR_texture_basisu"}
# Extensions implemented by the bundled gdx-gltf 2.1.0 data/loader classes.
SUPPORTED_EXTENSIONS = {"KHR_lights_punctual", "KHR_materials_pbrSpecularGlossiness", "KHR_materials_unlit", "KHR_texture_transform"}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def git_blob_hash(data: bytes) -> str:
    return hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()


def fetch(url: str, expected_size: int | None = None) -> bytes:
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "PokeWilds-PC-model-preparer/1.0"})
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read(MAX_DOWNLOAD + 1)
            if len(data) > MAX_DOWNLOAD:
                raise ValueError(f"Response exceeds download bound: {url}")
            if expected_size is not None and len(data) != expected_size:
                raise ValueError(f"Unexpected byte length for {url}: {len(data)} != {expected_size}")
            return data
        except Exception:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)
    raise AssertionError("unreachable")


def source_tree(source_id: str) -> dict:
    source = SOURCES[source_id]
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / f"{source['commit']}-tree.json"
    data = path.read_bytes() if path.is_file() else fetch(source["tree_url"])
    tree = json.loads(data)
    if tree.get("sha") != source["commit"] or tree.get("truncated"):
        raise ValueError("Source tree is truncated or does not match the pinned commit")
    if not path.is_file():
        path.write_bytes(data)
    return {entry["path"]: entry for entry in tree["tree"] if entry["type"] == "blob"}


def verified_source(path: str, tree: dict, source_id: str = "06wj") -> bytes:
    entry = tree[path]
    data = fetch(SOURCES[source_id]["raw"] + path, entry["size"])
    if git_blob_hash(data) != entry["sha"]:
        raise ValueError(f"Pinned source hash mismatch: {path}")
    return data


def game_species(runtime: Path) -> tuple[list[dict], list[str], str]:
    with zipfile.ZipFile(runtime) as archive:
        index_data = archive.read("pokemon/pokemon_to_index.txt")
        dex_names = index_data.decode("utf-8", errors="replace").splitlines()
        present = {
            match.group(1)
            for path in archive.namelist()
            if (match := re.match(r"^pokemon/(?:credited/)?pokemon/([^/]+)/", path))
        }
    if len(dex_names) < 151 or dex_names[65].lower() != "machop":
        raise ValueError("Unexpected base-game National Dex mapping")
    targets = []
    for dex, display_name in enumerate(dex_names[:151], 1):
        # The original index has a damaged apostrophe here; this is the actual
        # species directory and the alias used by Pokemon.nameToIndex().
        species = "farfetch_d" if dex == 83 else display_name.strip().lower()
        if species not in present:
            continue
        if not re.fullmatch(r"[a-z0-9_.-]+", species):
            raise ValueError(f"Unsafe species filename: {species}")
        targets.append({"dex": dex, "species": species, "source_id": "06wj", "source_path": f"public/models/{dex:03d}/model.glb"})
    unmapped = sorted(present - {entry["species"] for entry in targets})
    return targets, unmapped, sha256(index_data)


def legacy_targets(runtime: Path, missing: list[str], tree: dict) -> tuple[list[dict], list[str]]:
    with zipfile.ZipFile(runtime) as archive:
        names = archive.read("pokemon/pokemon_to_index.txt").decode("utf-8", errors="replace").splitlines()
    dex_by_name = {name.strip().lower(): dex for dex, name in enumerate(names, 1)}
    # Explicit game aliases only. Never substitute a base model for a distinct
    # regional/alternate form, or reuse one Unown letter for the whole alphabet.
    exact_aliases = {
        "ho_oh": (250, "regular"),
        "aexeggutor": (103, "alolan"),
        "amarowak": (105, "alolan"),
        "araichu": (26, "alolan"),
        "gdarmanitan": (555, "galar"),
    }
    targets = []
    for species in missing:
        dex, category = exact_aliases.get(species, (dex_by_name.get(species), "regular"))
        path = f"models/glb/{category}/{dex}.glb"
        if dex and path in tree:
            targets.append({"dex": dex, "species": species, "source_id": "legacy", "source_path": path})
    return targets, sorted(set(missing) - {target["species"] for target in targets})


def inspect_glb(data: bytes) -> dict:
    if len(data) < 20:
        raise ValueError("Truncated GLB")
    magic, version, length, json_length, kind = struct.unpack_from("<4sIIII", data)
    if magic != b"glTF" or version != 2 or length != len(data) or kind != 0x4E4F534A:
        raise ValueError("Invalid GLB 2.0 header")
    document = json.loads(data[20:20 + json_length])
    used = sorted(document.get("extensionsUsed", []))
    required = sorted(document.get("extensionsRequired", []))
    unsupported = UNSUPPORTED.intersection(used + required)
    if unsupported:
        raise ValueError(f"Unsupported compressed model extensions: {sorted(unsupported)}")
    # Optional material extensions have the glTF core fallback. Preserve the
    # original bytes and record these limitations; reject unknown required ones.
    unknown = set(required) - SUPPORTED_EXTENSIONS
    if unknown:
        raise ValueError(f"Extensions not supported by bundled gdx-gltf: {sorted(unknown)}")
    image_types = sorted({image.get("mimeType", "") for image in document.get("images", [])})
    if any(kind not in {"image/png", "image/jpeg"} for kind in image_types):
        raise ValueError(f"Unsupported embedded image formats: {image_types}")
    if any(image.get("uri") for image in document.get("images", [])) or any(buffer.get("uri") for buffer in document.get("buffers", [])):
        raise ValueError("Model must be self-contained; external buffer/image URI found")
    primitives = [primitive for mesh in document.get("meshes", []) for primitive in mesh.get("primitives", [])]
    accessors = document.get("accessors", [])
    counts = [accessors[primitive["attributes"]["POSITION"]]["count"] for primitive in primitives]
    for primitive in primitives:
        for semantic, index in primitive["attributes"].items():
            accessor = accessors[index]
            component = accessor["componentType"]
            if semantic.startswith(("TEXCOORD", "WEIGHTS")) and component != 5126:
                raise ValueError(f"gdx-gltf 2.1 requires float {semantic}; component type is {component}")
            if semantic.startswith("COLOR") and accessor["type"] == "VEC3" and component != 5126:
                raise ValueError(f"gdx-gltf 2.1 does not support integer RGB {semantic}")
    max_joints = max((len(skin["joints"]) for skin in document.get("skins", [])), default=0)
    if max_joints > 128:
        raise ValueError(f"Skin exceeds renderer bone limit: {max_joints} > 128")
    return {
        "format": "glTF 2.0 / GLB",
        "extensions_used": used,
        "extensions_required": required,
        "optional_extensions_ignored_by_loader": sorted(set(used) - SUPPORTED_EXTENSIONS),
        "image_mime_types": image_types,
        "max_skin_joints": max_joints,
        "mesh_count": len(document.get("meshes", [])),
        "primitive_count": len(primitives),
        "max_primitive_vertices": max(counts, default=0),
        "animation_names": [animation.get("name", "") for animation in document.get("animations", [])],
        "animation_kind": "animated" if document.get("animations") else "static",
        "animation_interpolations": sorted({sampler.get("interpolation", "LINEAR") for animation in document.get("animations", []) for sampler in animation.get("samplers", [])}),
        "embedded_asset_credits": document.get("asset", {}).get("extras", {}),
        "generator": document.get("asset", {}).get("generator", ""),
    }


def prepare(target: dict, tree: dict) -> dict:
    source = SOURCES[target["source_id"]]
    source_path = target["source_path"]
    entry = tree[source_path]
    destination = OUT / (target["species"] + ".glb")
    data = destination.read_bytes() if destination.is_file() else b""
    if len(data) != entry["size"] or git_blob_hash(data) != entry["sha"]:
        data = verified_source(source_path, tree, target["source_id"])
        metadata = inspect_glb(data)
        temporary = destination.with_suffix(".glb.part")
        temporary.write_bytes(data)
        temporary.replace(destination)
    else:
        metadata = inspect_glb(data)
    return {
        **target,
        "path": destination.relative_to(ROOT).as_posix(),
        "source_repository": f"https://github.com/{source['repository']}",
        "source_commit": source["commit"],
        "source_url": source["raw"] + source_path,
        "source_git_blob_sha1": entry["sha"],
        "bytes": len(data),
        "sha256": sha256(data),
        **metadata,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, default=ROOT / "lib/upstream-runtime.jar")
    parser.add_argument("--only", nargs="+", help="Exact game species names; default: every mapped species from the pinned sources")
    parser.add_argument("--workers", type=int, default=4, choices=range(1, 5), metavar="1..4")
    args = parser.parse_args()
    trees = {source_id: source_tree(source_id) for source_id in SOURCES}
    gen1_targets, gen1_missing, mapping_hash = game_species(args.runtime)
    extra_targets, unmapped = legacy_targets(args.runtime, gen1_missing, trees["legacy"])
    all_targets = gen1_targets + extra_targets
    targets = all_targets
    if args.only:
        requested = set(args.only)
        targets = [target for target in all_targets if target["species"] in requested]
        unknown = requested - {target["species"] for target in targets}
        if unknown:
            parser.error(f"No exact pinned model mapping for: {', '.join(sorted(unknown))}")
    OUT.mkdir(parents=True, exist_ok=True)
    upper_bound = sum(trees[t['source_id']][t['source_path']]['size'] for t in targets)
    print(f"Target: {len(targets)}/{len(all_targets)} mapped species; download upper bound {upper_bound:,} bytes; workers={args.workers}", flush=True)
    completed = {}
    skipped = {}
    # A targeted verification must not erase an earlier rejection reason for
    # a different, absent model. Only reuse entries for the same pinned mapping.
    if args.only and MANIFEST.is_file():
        previous = json.loads(MANIFEST.read_text(encoding="utf-8"))
        current_targets = {target["species"]: target for target in all_targets}
        selected = {target["species"] for target in targets}
        for record in previous.get("skipped_models", []):
            target = current_targets.get(record.get("species"))
            if target and target["species"] not in selected and all(record.get(key) == value for key, value in target.items()):
                skipped[target["species"]] = record
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        pending = {executor.submit(prepare, target, trees[target['source_id']]): target for target in targets}
        for future in as_completed(pending):
            target = pending[future]
            try:
                model = future.result()
            except Exception as error:
                skipped[target["species"]] = {**target, "reason": str(error)}
                print(f"Skipped {target['species']}: {error}", flush=True)
                continue
            completed[model["species"]] = model
            print(f"[{len(completed)}/{len(targets)}] {model['species']}: {model['bytes']:,} bytes; joints={model['max_skin_joints']}; animations={len(model['animation_names'])}", flush=True)
    # Include previously verified entries when running --only, and recheck their
    # files instead of trusting stale metadata. No concurrent writes to manifest.
    for target in all_targets:
        if target["species"] not in completed and (OUT / (target["species"] + ".glb")).is_file():
            try:
                completed[target["species"]] = prepare(target, trees[target['source_id']])
                skipped.pop(target["species"], None)
            except Exception as error:
                skipped[target["species"]] = {**target, "reason": str(error)}
    models = sorted(completed.values(), key=lambda model: (model["dex"], model["species"]))
    licenses = ROOT / "licenses"
    licenses.mkdir(parents=True, exist_ok=True)
    source_documents = {}
    source_records = []
    for source_id, source in SOURCES.items():
        documents = {}
        for path in source["documents"]:
            entry = trees[source_id][path]
            cached = CACHE / f"{source['commit']}-{path.replace('/', '-')}"
            data = cached.read_bytes() if cached.is_file() else b""
            if len(data) != entry["size"] or git_blob_hash(data) != entry["sha"]:
                data = verified_source(path, trees[source_id], source_id)
                cached.write_bytes(data)
            source_documents[(source_id, path)] = data
            documents[path] = {"url": source["raw"] + path, "sha256": sha256(data)}
        source_records.append({
            "id": source_id, "repository": f"https://github.com/{source['repository']}",
            "commit": source["commit"], "tree_url": source["tree_url"], "documents": documents,
            "model_count": sum(model["source_id"] == source_id for model in models),
        })
    license_bytes = source_documents[("06wj", "LICENSE")]
    notice = (
        "Pokemon actor model provenance\n\n"
        f"Repository: https://github.com/{REPOSITORY}\nPinned commit: {COMMIT}\n"
        f"Model source pattern: {RAW}public/models/NNN/model.glb\n"
        "Exact URLs, file hashes and model metadata: MODELS-PROVENANCE.json\n\n"
        "The repository contains an MIT software license, Copyright (c) 2018 06wj.\n"
        "That software license is not asserted here as a license for Pokemon models.\n"
        "The repository README's Asset credits section states that Pokemon\n"
        "characters and associated assets belong to their respective owners, and\n"
        "that this repository does not grant rights to those assets. It describes\n"
        "the project as an unofficial technical demonstration.\n"
        "The upstream model manifest identifies local Blender source files and\n"
        "original animation action names; no separate model permission is supplied.\n"
        "Downloaded GLB files remain byte-for-byte identical to the pinned source.\n\n"
        f"README: https://github.com/{REPOSITORY}/blob/{COMMIT}/README.md#asset-credits\n"
        f"Software license: https://github.com/{REPOSITORY}/blob/{COMMIT}/LICENSE\n\n"
        "Upstream software license text (distinct from model rights):\n\n"
    )
    (licenses / "06wj-models.txt").write_text(notice + license_bytes.decode("utf-8"), encoding="utf-8")
    legacy = SOURCES["legacy"]
    legacy_notice = (
        "Additional Pokemon actor model provenance\n\n"
        f"Repository: https://github.com/{legacy['repository']}\nPinned commit: {legacy['commit']}\n"
        f"Model source pattern: {legacy['raw']}models/glb/CATEGORY/DEX.glb\n"
        "Exact URLs, byte-for-byte source hashes, animation/static status and any\n"
        "embedded artist/source/license credits are recorded per model in MODELS-PROVENANCE.json.\n"
        "docs/CREDITS.md points to Sketchfab collections rather than a complete per-model\n"
        "attribution list. Embedded asset.extras credits are retained when supplied.\n"
        "docs/COPYRIGHT.md explicitly limits the repository's MIT license to code,\n"
        "excluding rights to Pokemon intellectual property. Any embedded model license\n"
        "is recorded as an upstream claim, not a grant of underlying Pokemon rights.\n"
        "Models are unmodified; regular forms are not substituted for regional variants.\n\n"
        f"Credits: {legacy['raw']}docs/CREDITS.md\n"
        f"Copyright notice: {legacy['raw']}docs/COPYRIGHT.md\n"
        f"Software license: {legacy['raw']}docs/LICENSE\n\n"
        "Upstream software license text (distinct from model rights):\n\n"
    )
    (licenses / "pokemon-3d-api-models.txt").write_text(legacy_notice + source_documents[("legacy", "docs/LICENSE")].decode("utf-8"), encoding="utf-8")
    manifest = {
        "schema_version": 2,
        "sources": source_records,
        "model_license": "Repository MIT licenses cover code, not Pokemon rights. See licenses/*-models.txt and per-model embedded_asset_credits.",
        "game_mapping_resource": "pokemon/pokemon_to_index.txt",
        "game_mapping_sha256": mapping_hash,
        "game_species_directory_count": len(all_targets) + len(unmapped),
        "regular_gen1_target_count": len(gen1_targets),
        "additional_source_target_count": len(extra_targets),
        "installed_model_count": len(models),
        "total_glb_bytes": sum(model["bytes"] for model in models),
        "max_skin_joints": max((model["max_skin_joints"] for model in models), default=0),
        "extensions_used": sorted({name for model in models for name in model["extensions_used"]}),
        "extensions_required": sorted({name for model in models for name in model["extensions_required"]}),
        "animation_clip_count": sum(len(model["animation_names"]) for model in models),
        "animated_model_count": sum(model["animation_kind"] == "animated" for model in models),
        "static_model_count": sum(model["animation_kind"] == "static" for model in models),
        "species_without_exact_source_mapping": unmapped,
        "skipped_models": sorted(skipped.values(), key=lambda item: item["species"]),
        "species_without_installed_models": sorted(set(unmapped) | ({target["species"] for target in all_targets} - set(completed))),
        "models": models,
    }
    temporary = MANIFEST.with_suffix(".json.part")
    temporary.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    temporary.replace(MANIFEST)
    print(f"Verified {len(models)} GLBs, {manifest['total_glb_bytes']:,} bytes, max joints {manifest['max_skin_joints']}, required extensions {manifest['extensions_required']}", flush=True)
    print(f"Animated={manifest['animated_model_count']}; static={manifest['static_model_count']}; skipped={len(skipped)}; exact source unavailable={len(unmapped)}", flush=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"Model preparation failed: {error}", file=sys.stderr, flush=True)
        sys.exit(1)
