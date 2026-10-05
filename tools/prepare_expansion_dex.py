#!/usr/bin/env python3
"""Build an auditable expansion dex from pinned PokeAPI CSV and exact PMD assets.

Uses no invented stats, evolution levels or substitute species. Existing playable
species remain untouched. Run after import_pmd_assets.py has written catalog.json.
"""
from __future__ import annotations
import argparse
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
import csv
import hashlib
import json
from pathlib import Path
import re
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "build/dex-source"
DEST = ROOT / "resources/visual/dex"
DATA_COMMIT = "bc92d3b6029ef1abe9e7ad424c400b338f3c11fe"
CSV_NAMES = ("pokemon", "pokemon_species", "pokemon_stats", "pokemon_types", "types", "egg_groups",
             "pokemon_egg_groups", "moves", "pokemon_moves", "pokemon_evolution", "growth_rates", "items")
ALIASES = {"ancient power": "ancientpower", "bubble beam": "bubblebeam", "double slap": "doubleslap",
           "dragon breath": "dragonbreath", "dynamic punch": "dynamicpunch", "feather dance": "featherdance",
           "feint attack": "faint attack", "high jump kick": "hi jump kick", "sand attack": "sand-attack",
           "self destruct": "selfdestruct", "smelling salts": "smellingsalt", "smoke screen": "smokescreen",
           "soft boiled": "softboiled", "solar beam": "solarbeam", "sonic boom": "sonicboom",
           "thunder punch": "thunderpunch", "thunder shock": "thundershock", "vice grip": "vicegrip"}

def rows(name: str) -> list[dict]:
    with (SOURCE / (name + ".csv")).open(encoding="utf-8", newline="") as file:
        return list(csv.DictReader(file))

def download_sources() -> None:
    SOURCE.mkdir(parents=True, exist_ok=True)
    pin = SOURCE / "commit.txt"
    commit = pin.read_text().strip() if pin.exists() else DATA_COMMIT
    for name in CSV_NAMES:
        target = SOURCE / (name + ".csv")
        if target.exists(): continue
        url = f"https://raw.githubusercontent.com/PokeAPI/pokeapi/{commit}/data/v2/csv/{name}.csv"
        request = urllib.request.Request(url, headers={"User-Agent": "PokeWilds-dex-import"})
        target.write_bytes(urllib.request.urlopen(request, timeout=90).read())
    pin.write_text(commit, encoding="utf-8")

def original_species() -> tuple[set[str], set[str]]:
    java = (ROOT / "src/com/pkmngen/game/Specie.java").read_text(encoding="utf-8")
    credited = set(re.findall(r'"([a-z0-9_]+)"', java[java.index("   static {"):]))
    with zipfile.ZipFile(ROOT / "lib/upstream-runtime.jar") as archive:
        names = set(archive.namelist())
        fronts = {x.split("/")[-2] for x in names if x.endswith("/front.png")}
        playable = {name for name in fronts if
                    f'pokemon/{"credited/" if name in credited else ""}pokemon/{name}/front.png' in names}
        attacks = set(archive.read("pokemon/attacks_implemented.txt").decode().splitlines())
    return playable, attacks

def prepare() -> dict:
    catalog = json.loads((ROOT / "resources/visual/pmd/catalog.json").read_text(encoding="utf-8"))["species"]
    existing, attacks = original_species()
    existing_aliases = {re.sub(r"[^a-z0-9]", "", name): name for name in existing}
    species = {int(row["id"]): row for row in rows("pokemon_species")}
    pokemon = {int(row["species_id"]): row for row in rows("pokemon") if row["is_default"] == "1"}
    stats = defaultdict(dict)
    for row in rows("pokemon_stats"):
        stats[int(row["pokemon_id"])][int(row["stat_id"])] = int(row["base_stat"])
    types = {row["id"]: row["identifier"].upper() for row in rows("types")}
    mon_types = defaultdict(list)
    for row in rows("pokemon_types"):
        mon_types[int(row["pokemon_id"])].append((int(row["slot"]), types[row["type_id"]]))
    egg_names = {row["id"]: "EGG_" + row["identifier"].upper().replace("-", "_") for row in rows("egg_groups")}
    egg_names.update({"5": "EGG_FIELD", "15": "EGG_NONE"})
    eggs = defaultdict(list)
    for row in rows("pokemon_egg_groups"):
        eggs[int(row["species_id"])].append(egg_names[row["egg_group_id"]])
    moves = {row["id"]: ALIASES.get(row["identifier"].replace("-", " "), row["identifier"].replace("-", " "))
             for row in rows("moves")}
    all_moves = defaultdict(lambda: defaultdict(list))
    egg_moves = defaultdict(set)
    for row in rows("pokemon_moves"):
        name = moves[row["move_id"]]
        if name not in attacks:
            continue
        mon, version, method = int(row["pokemon_id"]), int(row["version_group_id"]), int(row["pokemon_move_method_id"])
        if method == 1:
            all_moves[mon][version].append((int(row["level"]), int(row["order"] or 0), name))
        elif method == 2:
            egg_moves[mon].add(name)
    entries, deferred = {}, []
    for dex, row in sorted(species.items()):
        name = row["identifier"]
        normalized = re.sub(r"[^a-z0-9]", "", name)
        if normalized in existing_aliases:
            continue
        record = pokemon.get(dex)
        asset = catalog.get(name) or catalog.get(normalized)
        if not record or not asset or not asset.get("portrait") or not all(key in asset.get("animations", {}) for key in ("Idle", "Walk")):
            deferred.append({"dex": dex, "name": name, "reason": "Exact PMD portrait/Idle/Walk unavailable"})
            continue
        mon_id = int(record["id"])
        versions = all_moves[mon_id]
        # A real level-one move is required; do not silently teach a made-up fallback.
        valid_versions = [version for version, learn in versions.items() if any(level <= 1 for level, order, move in learn)]
        if not valid_versions:
            deferred.append({"dex": dex, "name": name, "reason": "No implemented level-one move in canonical learnsets"})
            continue
        version = max(valid_versions)
        learnset = defaultdict(list)
        for level, order, move in sorted(set(versions[version])):
            if move not in learnset[str(level)]: learnset[str(level)].append(move)
        if len(stats[mon_id]) < 6:
            raise RuntimeError(f"Missing base stats: {name}")
        entries[name] = {"dex": dex, "pokemonId": mon_id, "generation": int(row["generation_id"]),
            "types": [value for slot, value in sorted(mon_types[mon_id])],
            "stats": [stats[mon_id][i] for i in range(1, 7)],
            "catchRate": int(row["capture_rate"]), "baseExp": int(record["base_experience"]),
            "baseHappiness": int(row["base_happiness"]), "eggCycles": int(row["hatch_counter"]),
            "genderRate": int(row["gender_rate"]), "eggGroups": eggs[dex],
            "growthRate": {"1":"GROWTH_SLOW", "2":"GROWTH_MEDIUM_FAST", "3":"GROWTH_FAST", "4":"GROWTH_MEDIUM_SLOW",
                           "5":"GROWTH_ERRATIC", "6":"GROWTH_FLUCTUATING"}[row["growth_rate_id"]],
            "weightKg": int(record["weight"]) / 10, "legendary": row["is_legendary"] == "1",
            "mythical": row["is_mythical"] == "1", "habitatId": int(row["habitat_id"] or 0),
            "learnsetVersionGroup": version, "learnset": dict(learnset), "eggMoves": sorted(egg_moves[mon_id]),
            "evolutions": {}, "evolvesFrom": int(row["evolves_from_species_id"] or 0)}
    available = existing | entries.keys()
    for name, entry in entries.items():
        base = species[entry["dex"]]
        while base["evolves_from_species_id"]:
            base = species[int(base["evolves_from_species_id"])]
        entry["baseSpecies"] = existing_aliases.get(re.sub(r"[^a-z0-9]", "", base["identifier"]), base["identifier"])
        if entry["baseSpecies"] not in available:
            deferred.append({"dex": entry["dex"], "name": name, "reason": "Breeding ancestor unavailable; wild spawning disabled"})
        special_encounter = 793 <= entry["dex"] <= 799 or 803 <= entry["dex"] <= 806 or 984 <= entry["dex"] <= 995 \
            or entry["dex"] in (1005, 1006, 1009, 1010, 1020, 1021, 1022, 1023)
        entry["spawnable"] = not entry["legendary"] and not entry["mythical"] and not special_encounter and entry["baseSpecies"] in available
    unsupported = []
    evo_rows = rows("pokemon_evolution")
    for row in evo_rows:
        if row["is_default"] != "1": continue
        target = species[int(row["evolved_species_id"])]
        if not target["evolves_from_species_id"]: continue
        parent = species[int(target["evolves_from_species_id"])]["identifier"]
        if parent not in entries: continue
        destination = existing_aliases.get(re.sub(r"[^a-z0-9]", "", target["identifier"]), target["identifier"])
        allowed = {"id", "evolved_species_id", "evolution_trigger_id", "version_group_id", "is_default", "minimum_level"}
        conditions = {key: value for key, value in row.items() if key not in allowed and value not in ("", "0")}
        if row["evolution_trigger_id"] == "1" and row["minimum_level"] and not conditions and destination in available:
            key = row["minimum_level"]
            prior = entries[parent]["evolutions"].get(key)
            if prior and prior != destination:
                unsupported.append({"from": parent, "to": destination, "reason": "Branch collision", "source": row})
            else: entries[parent]["evolutions"][key] = destination
        else:
            unsupported.append({"from": parent, "to": destination, "reason": "Condition unsupported or destination unavailable", "source": row})
    return {"schema": 1, "sourceCommit": (SOURCE / "commit.txt").read_text().strip(),
            "source": "https://github.com/PokeAPI/pokeapi", "species": entries,
            "deferred": deferred, "unsupportedEvolutions": unsupported,
            "notes": ["Latest canonical version group with at least one implemented level-one move; unsupported moves omitted.",
                      "Only unconditional level evolutions are added. No invented levels for trade, friendship or special conditions.",
                      "Existing playable species, moves and mechanics are preserved. Default forms only."]}

def download_cries(data: dict) -> None:
    pin_file = SOURCE / "cries-commit.txt"
    if pin_file.exists(): commit = pin_file.read_text().strip()
    else:
        request = urllib.request.Request("https://api.github.com/repos/PokeAPI/cries/commits/HEAD", headers={"User-Agent":"PokeWilds-dex-import"})
        commit = json.loads(urllib.request.urlopen(request, timeout=60).read())["sha"]
        pin_file.write_text(commit)
    folder = DEST / "cries"
    folder.mkdir(parents=True, exist_ok=True)
    def fetch(item: tuple[str, dict]) -> None:
        name, entry = item
        target = folder / f'{entry["pokemonId"]}.ogg'
        mp3_target = target.with_suffix(".mp3")
        if mp3_target.exists(): target = mp3_target
        url = f'https://raw.githubusercontent.com/PokeAPI/cries/{commit}/cries/pokemon/latest/{entry["pokemonId"]}.ogg'
        if not target.exists():
            last_error = None
            for attempt in range(3):
                try:
                    request = urllib.request.Request(url, headers={"User-Agent":"PokeWilds-dex-import"})
                    body = urllib.request.urlopen(request, timeout=60).read()
                    if not body.startswith(b"OggS"):
                        if body[:2] not in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"):
                            raise RuntimeError("Unsupported source audio header")
                        # PokeAPI's Meltan and Melmetal .ogg files contain MP3 audio.
                        # Preserve their bytes and use the correct extension for LibGDX.
                        target = mp3_target
                    target.write_bytes(body)
                    break
                except Exception as error: last_error = error
            if not target.exists(): raise RuntimeError(f"Cry unavailable for {name}: {last_error}")
        body = target.read_bytes()
        if body.startswith(b"OggS"):
            # Last Ogg granule position; Vorbis identification stores the sample rate.
            sample_rate = int.from_bytes(body[body.index(b'\x01vorbis') + 12:body.index(b'\x01vorbis') + 16], "little")
            last_page = body.rfind(b"OggS")
            samples = int.from_bytes(body[last_page + 6:last_page + 14], "little")
            seconds = samples / sample_rate
        else:
            header = int.from_bytes(body[:4], "big")
            version = (header >> 19) & 3
            sample_rate = [44100, 48000, 32000][(header >> 10) & 3] // ({3: 1, 2: 2, 0: 4}[version])
            xing = body.index(b"Xing")
            flags = int.from_bytes(body[xing + 4:xing + 8], "big")
            if not flags & 1: raise RuntimeError("MP3 frame count unavailable")
            frames = int.from_bytes(body[xing + 8:xing + 12], "big")
            seconds = frames * (1152 if version == 3 else 576) / sample_rate
        if not 0 < seconds < 20: raise RuntimeError(f"Unexpected cry duration: {name} {seconds}")
        entry.update({"cry": f'visual/dex/cries/{target.name}', "cryFrames": int(seconds * 60) + 1,
                      "crySha256": hashlib.sha256(body).hexdigest()})
    with ThreadPoolExecutor(max_workers=10) as pool: list(pool.map(fetch, data["species"].items()))
    data["criesSourceCommit"] = commit
    data["criesSource"] = "https://github.com/PokeAPI/cries"

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--no-cries", action="store_true", help="Development only; not a release dataset")
    args = parser.parse_args()
    download_sources()
    data = prepare()
    DEST.mkdir(parents=True, exist_ok=True)
    if not args.no_cries: download_cries(data)
    (DEST / "expansion-dex.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f'Expansion dex: {len(data["species"])} species, {len(data["deferred"])} deferred, {len(data["unsupportedEvolutions"])} unsupported evolution rows')

if __name__ == "__main__": main()
