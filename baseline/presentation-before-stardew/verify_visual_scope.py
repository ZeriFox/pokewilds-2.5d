#!/usr/bin/env python3
"""Verify frozen modernization scope against the original 198 source files.

This phase intentionally changes presentation, procedural generation, roster and desktop bindings.
It does not claim full gameplay equivalence. Neither normal verification nor
--inventory refreshes approved hashes. Approval requires explicit source review
and edits to the manifest and this verifier's frozen manifest digest.
"""
from __future__ import annotations
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = "baseline/modernization-scope.json"
CLASSIC_REPORT = "baseline/classic-build-report.json"
CLASSIC_REPORT_SHA256 = "ff9ad5b0313ecc39d252d450ccd8f04cd47c467f7ce695b28a0c99e0cfae239e"
CLASSIC_SOURCES = "baseline/johto-v1/classic-sources"
# Set only after coordinated final source review. No command auto-updates this.
FROZEN_MANIFEST_SHA256 = "73cb6a1c9cd99e9a223df8c2c707ced128283c82e7ac2ac97858f739d90e2f0b"
PREFIX = "src/com/pkmngen/game/"
V2_ARCHIVE = {
    "baseline/modernization-v2/modernization-scope.json": "f978edd535ac4440d6ce01924bc98135105a718e3a57faf9838827cd3ebf2343",
    "baseline/modernization-v2/verify_visual_scope.py": "20aa2d3c1257379ecc5168b66693bc0f076c3c0bfdc09cab762a7e3c38ce727b",
    "baseline/modernization-v2/VERIFICA.txt": "ddcc0a7f24914d6d0d91fa28abadb9367f61de9d601dd2f1e038e6187e5b1a82",
}
V3_ARCHIVE = {
    "baseline/modernization-v3/modernization-scope.json": "4b9d503e27d00be1b822f8e39e8bc4f425610d8f631bad4cea2ef347d398c700",
    "baseline/modernization-v3/verify_visual_scope.py": "a0266056798619d31219a225e8e12e0b0daef4d0ec93a4739e7ffd0c69b0e58e",
    "baseline/modernization-v3/VERIFICA.txt": "e531e3ad3f94911cd37f5cf9a5330f5e7b132108751c3a7ac08e13ceddba3baf",
}
V4_ARCHIVE = {
    "baseline/modernization-v4/modernization-scope.json": "8171c83e110e6bdae3380832dc1c6eafaab24a10ead7a11cbcc8862f674b7de5",
    "baseline/modernization-v4/verify_visual_scope.py": "2b181884511149c8355ad62ddbf175d17b20d766ce9fc6551698d247ce030cab",
    "baseline/modernization-v4/VERIFICA.txt": "454c76557b823134030b26f055738ee07828b15e67e6520f6714725b2b709ab4",
    "baseline/modernization-v4/archive.json": "ec7a08ddded007938b9cfe9464a1b036d8456ebe8242ebc4d42751c7a102830d",
}
V5_ARCHIVE = {
    "baseline/modernization-v5/modernization-scope.json": "ed68a9912360aa7972864acf09358b02b8d126463622f044d44f1bfbff6e4003",
    "baseline/modernization-v5/verify_visual_scope.py": "8cc684671bacdd3f94b4f4373f6a6f1004de69d4e56e7bf67ef9f8dcd9433e49",
    "baseline/modernization-v5/VERIFICA.txt": "ec11e79cf8eb0177424156f966bb452e0e7e463565840ae93f48525020568f31",
    "baseline/modernization-v5/archive.json": "bc32b1a3a40124c457960c34dfc7a7b4126ab96594b67806e7a900098403f4ac",
}

# Names and review scope are explicit; an edited manifest cannot expand them.
MODIFIED_SCOPES = {
    "BattleFadeOut.java": "Presentation: replace fade drawing while retaining action timing and dispatch.",
    "BattleIntro.java": "Presentation: preserve the modern world image during the original battle intro.",
    "BattleIntroAnim1.java": "Presentation: replace intro frame and border drawing, retain frame progression.",
    "DrawAttacksMenu.java": "Presentation: modern move panel; retain cursor state progression and original input.",
    "DrawBattle.java": "Presentation: arena, actor drawing hooks, masks and night tint.",
    "DrawBattleMenuNormal.java": "Presentation: modern battle commands; retain selection and input logic.",
    "DrawBattleMenuSafariZone.java": "Presentation: modern safari commands; retain selection and input logic.",
    "DrawControls.java": "Desktop controls presentation: show configured movement, confirm, back/run and menu bindings.",
    "DrawPokemonMenu.java": "Desktop UI layout: remove stale fixed-key guidance from the party menu while preserving menu selection and actions.",
    "DrawUseTossMenu.java": "Desktop UI layout: show configured action bindings without altering item-use/toss state transitions.",
    "DrawEnemyHealthGen2.java": "Presentation: modern enemy HP panel; retain health updates.",
    "DrawFriendlyHealthGen2.java": "Presentation: modern party HP/EXP panel; retain health updates.",
    "EggHatchAnim.java": "Presentation: modern event background and exact PMD actor/shiny appearance.",
    "EnemyFaint.java": "Presentation: PMD actor during original enemy faint action; retain removal and victory flow.",
    "EnterBuilding.java": "Presentation: replace travel fade drawing; retain interior selection, timers and actions.",
    "EscapeRope.java": "Presentation: replace travel fade drawing; retain travel destination and action timing.",
    "EvolutionAnim.java": "Presentation: modern event background and PMD pre/post-evolution actor, preserving shiny identity.",
    "FadeAnim.java": "Presentation: replace fade drawing; retain original timer and transition condition.",
    "FriendlyFaint.java": "Presentation: PMD actor during original friendly faint action; retain status/action flow.",
    "Game.java": "Integration: exclusive modern world/UI drawing without skipping action steps; resource lifecycle; desktop defaults and legacy preset migration; unified modern viewport sizing.",
    "GenIsland1.java": "Generation: modern terrain pass before coastification, habitat-aware wild selection, safe oasis anchoring and explicit bounded retries for mansion drafts lacking a valid endpoint.",
    "InputProcessor.java": "Authorized desktop input: WASD and mouse bindings, debounced press/held/release, text-entry isolation, preserving gamepad and mobile controls.",
    "PlayMusic.java": "Expanded roster initialization: resolve exact imported Pokemon cry paths.",
    "PlaySound.java": "Expanded roster initialization: resolve exact imported Pokemon cry paths.",
    "PlayerStanding.java": "Wild encounter coherence: profile-based habitat/rarity selection and valid natural evolutions; retain encounter rates, levels, scripted branches and original time availability.",
    "Pokemon.java": "Expanded roster initialization: National Dex lookup, imported growth data and 87.5 percent female ratio; keep uncaught aquatic species in water while preserving owned-companion movement.",
    "PokemonFrame.java": "Presentation: modern reveal background and exact PMD actor/shiny appearance.",
    "RegigigasBattle.java": "Presentation: modern nested intro and arena drawing; retain battle action progression.",
    "Route.java": "Expanded roster generation: append verified additional species to biome spawn candidates.",
    "SpecialBattleMegaGengar.java": "Presentation: modern special intro, dedicated boss arena, PMD actor and exact Pokemon cry paths; preserve original battle action progression and timing.",
    "SpecialBattleMewtwo.java": "Presentation: modern special intro, boss arena, PMD actor, rocks and ripple; preserve original battle action progression, RNG and timing.",
    "SpecialMegaGengar1.java": "Boss resource compatibility: initialize the shipped Mega Gengar boss with a dedicated mgengar identity backed by existing Gengar data; preserve its explicit stats, attacks, display name and battle rules.",
    "Specie.java": "Species initialization: verified additional species and the shipped Mega Gengar boss alias before legacy initialization; support fresh-process boss reload without changing serialization.",
    "ThrowOutPokemon.java": "Presentation: replace original RED sliced send-out actor with exact PMD actor and bound its legacy 7x7 crop; retain ball/poof sequence, durations, sounds and next actions.",
    "ThrowOutPokemonCrystal.java": "Presentation: replace sliced actor art in send-out; retain poof and action schedule.",
    "TrainerTipsTile.java": "Desktop tutorial presentation: substitute current binding labels in original sign text; preserve sign identity and interaction mechanics.",
    "util/SpriteProxy.java": "Presentation: intercept known battle actors for PMD rendering; legacy fallback retained.",
}
ADDED_SCOPES = {
    "ActorModelRenderer.java": "Existing optional 3D helper, disabled by the modern sprite launcher.",
    "BiomeProfiles.java": "Shared data-driven biome identity, materials, geometry rules, atmosphere, transitions and spawn habitats, derived from existing Tile/Route state.",
    "BwAssets.java": "Lazy, disposable local landscape atlas access, biome-specific semantic materials, validated optional texture overrides and sampling independent of the simulation grid, contextual missing-asset diagnostics and separate Black/White trainer assets.",
    "DesktopControls.java": "Authorized desktop binding parser, exact legacy-preset migration, text-entry detection and truthful dynamic hints.",
    "ExpansionDex.java": "Authorized additional species data, graphics, cries, supported moves, experience and biome integration.",
    "JohtoBattleRenderer.java": "Modern biome battle arenas/HUD, full-viewport composition, preserved-world transitions and presentation helpers for special boss actions.",
    "JohtoRenderer.java": "Exclusive perspective world drawing with shared derived terrain heights, continuous cliff/ramp geometry and contact shading, coherent biome materials/atmosphere, aspect-preserving decor, configurable sprite dimensions/anchors, proportional trainer crops and building-wall classification, modern intentional ghost presentation, anchored PMD actors and original field-action feedback; no persisted terrain or collision changes.",
    "ModernBatch.java": "Suppress legacy draw submissions while original menu actions advance.",
    "ModernInventoryUi.java": "Modern inventory, item actions, crafting, quantities and guide presentation.",
    "ModernPartyUi.java": "Modern setup/party/storage/nickname UI, full animated summary actors on all three pages and configured control hints.",
    "ModernUi.java": "Modern typography, shared logical viewport and anchored panel layout, dialogs, event backgrounds and menu rendering dispatch.",
    "ModernWorldGenerator.java": "Authorized deterministic procedural landforms, biome distribution and volcanic terrain.",
    "PmdBattleSprites.java": "Exact PMD battle/event actors with diagonal battle-facing poses, complete animation-envelope fit and ground anchoring, trimmed trainers, hidden-identity ghost presentation and visual effects.",
    "PmdPokemonSprites.java": "Pinned PMD portrait/animation loading, timing/directions, bounded texture cache and complete animation bounds for uncropped actors.",
    "TrainerModel3D.java": "Existing optional experimental trainer model helper.",
    "VisualGeometry.java": "Pure presentation helpers for diagonal battle-facing poses, uniform sprite fitting, cliff footprint math and building-wall classification; no map or collision writes.",
    "VisualSampling.java": "Validated pixel-region sampling independent of the fixed simulation grid, including deterministic signed world coordinates and exact source-cell boundaries.",
    "WorldBatch.java": "Drop legacy world draw submissions while preserving original action step execution and state changes.",
    "WildSpawnRules.java": "New-wild habitat filtering/rarity, safe nonempty fallback pools, oasis encounter anchoring and uncaught aquatic movement gating; no saved or owned Pokemon migration.",
    "WorldElevation.java": "Read-only geometry sidecar derived from saved ledges and ramps; deterministic plateau/boundary constraint resolution, continuous ramp heights, local ambiguity diagnostics and cache invalidation without serialized height fields or gameplay collision edits.",
}
PROTECTED_NAMES = (
    "Attack.java", "Battle.java", "Player.java", "Network.java", "util/Save.java",
    "CheckMovesLearned.java", "CheckEndOfBattle.java", "PkmnMap.java", "DrawSetupMenu.java",
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _scope(root: Path) -> tuple[dict, dict, dict, list[str], list[str], dict]:
    for archived, expected in V2_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable schema 2 archive changed: " + archived)
    for archived, expected in V3_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable schema 3 archive changed: " + archived)
    for archived, expected in V4_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable schema 4 archive changed: " + archived)
    for archived, expected in V5_ARCHIVE.items():
        if sha256(root / archived) != expected:
            raise RuntimeError("Immutable schema 5 archive changed: " + archived)
    if sha256(root / CLASSIC_REPORT) != CLASSIC_REPORT_SHA256:
        raise RuntimeError("Classic baseline report differs from its immutable digest")
    baseline = json.loads((root / CLASSIC_REPORT).read_text(encoding="utf-8"))
    original = baseline["source_files_sha256"]
    if baseline["source_count"] != 198 or len(original) != 198:
        raise RuntimeError("Expected the original 198-source baseline")
    current = {p.relative_to(root).as_posix(): sha256(p) for p in (root / "src").rglob("*.java")}
    if set(original) - set(current):
        raise RuntimeError("Original sources removed: " + str(sorted(set(original) - set(current))))
    protected = {}
    for name in PROTECTED_NAMES:
        path = PREFIX + name
        if current.get(path) != original.get(path) or path not in original:
            raise RuntimeError("Protected battle/rule/save source changed: " + path)
        protected[path] = current[path]
    for path, expected in original.items():
        snapshot = root / CLASSIC_SOURCES / path
        if not snapshot.is_file() or sha256(snapshot) != expected:
            raise RuntimeError("Archived original source differs from classic digest: " + path)
    changed = sorted(path for path, expected in original.items() if current[path] != expected)
    added = sorted(set(current) - set(original))
    expected_changed = {PREFIX + name for name in MODIFIED_SCOPES}
    expected_added = {PREFIX + name for name in ADDED_SCOPES}
    if set(changed) != expected_changed or set(added) != expected_added:
        raise RuntimeError("Unexpected modernization source scope: " + json.dumps({
            "unexpected_changes": sorted(set(changed) - expected_changed),
            "expected_but_unchanged": sorted(expected_changed - set(changed)),
            "unexpected_additions": sorted(set(added) - expected_added),
            "missing_additions": sorted(expected_added - set(added)),
        }))
    manifest = json.loads((root / MANIFEST).read_text(encoding="utf-8"))
    if manifest.get("schema") != 6 or manifest.get("classic_report_sha256") != CLASSIC_REPORT_SHA256:
        raise RuntimeError("Expected schema 6 manifest bound to classic source baseline")
    if set(manifest.get("modified_original_sources", {})) != expected_changed:
        raise RuntimeError("Manifest changes differ from explicit source allowlist")
    if set(manifest.get("added_sources", {})) != expected_added:
        raise RuntimeError("Manifest additions differ from explicit source allowlist")
    if manifest.get("protected_original_sources") != sorted(protected):
        raise RuntimeError("Manifest protected-source set differs from verifier")
    for path, entry in manifest["modified_original_sources"].items():
        if entry.get("baseline_sha256") != original[path] or entry.get("scope") != MODIFIED_SCOPES[path[len(PREFIX):]]:
            raise RuntimeError("Manifest original baseline/scope mismatch: " + path)
    for path, entry in manifest["added_sources"].items():
        if entry.get("scope") != ADDED_SCOPES[path[len(PREFIX):]]:
            raise RuntimeError("Manifest added-source scope mismatch: " + path)
    return original, current, manifest, changed, added, protected


def audit(root: Path = ROOT, inventory: bool = False) -> tuple[dict, str]:
    original, current, manifest, changed, added, protected = _scope(root)
    if not inventory:
        if FROZEN_MANIFEST_SHA256 is None or manifest.get("status") != "frozen":
            raise RuntimeError("Modernization review is not frozen. --inventory is read-only; no approved hashes are updated.")
        if sha256(root / MANIFEST) != FROZEN_MANIFEST_SHA256:
            raise RuntimeError("Frozen manifest was edited; source hashes cannot be silently re-approved")
        for group in ("modified_original_sources", "added_sources"):
            for path, entry in manifest[group].items():
                if current[path] != entry.get("reviewed_sha256"):
                    raise RuntimeError("Source changed after explicit review freeze: " + path)
    patch = []
    for path in changed + added:
        old = (root / CLASSIC_SOURCES / path).read_text(encoding="utf-8") if path in original else ""
        new = (root / path).read_text(encoding="utf-8")
        patch.extend(difflib.unified_diff(old.splitlines(True), new.splitlines(True),
            fromfile=(CLASSIC_SOURCES + "/" + path) if path in original else "/dev/null", tofile=path))
    combined_patch = "".join(patch)
    diff_digest = hashlib.sha256(combined_patch.encode("utf-8")).hexdigest()
    if not inventory and diff_digest != manifest.get("reviewed_patch_sha256"):
        raise RuntimeError("Complete source diff differs from reviewed patch digest")
    result = {
        "status": "inventory_not_approved" if inventory else "success",
        "manifest_schema": 6, "baseline_source_count": len(original), "current_source_count": len(current),
        "unchanged_original_sources": len(original) - len(changed),
        "modified_original_sources": changed, "added_sources": added,
        "modified_source_scopes": {PREFIX + key: value for key, value in MODIFIED_SCOPES.items()},
        "added_source_scopes": {PREFIX + key: value for key, value in ADDED_SCOPES.items()},
        "protected_rule_save_sources_sha256": protected,
        "current_source_files_sha256": current, "complete_patch_sha256": diff_digest,
        "frozen_manifest_sha256": FROZEN_MANIFEST_SHA256,
        "scope": "Authorized update includes exclusive modern presentation, coherent biome profiles, derived terrain geometry, habitat-aware wild encounters/movement, desktop bindings, procedural generation and additional Pokemon data. All other original sources remain byte-identical. Success binds every changed and added source to a reviewed manifest and complete diff.",
        "limits": "Source-scope verification is NOT proof of total gameplay equivalence. Generator, wild habitats, uncaught aquatic movement, roster and desktop input intentionally change. Combat formulas, Player, map serialization and save sources remain immutable here; behavior, visuals, performance, data assets and save round-trips require separate native tests.",
    }
    return result, combined_patch


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", action="store_true", help="Read scope/diff without approving or freezing source hashes")
    args = parser.parse_args()
    report, patch = audit(inventory=args.inventory)
    stem = "modernization-scope-inventory" if args.inventory else "visual-scope"
    destination = ROOT / "build" / (stem + ".json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    patch_path = ROOT / "build" / ("modernization-review.patch" if args.inventory else "visual-hooks.patch")
    patch_path.write_text(patch, encoding="utf-8", newline="\n")
    label = "INVENTORY ONLY (not approved)" if args.inventory else "PASS"
    print(f"{label}: {report['unchanged_original_sources']} originals unchanged; "
          f"{len(report['modified_original_sources'])} explicitly scoped originals; {len(report['added_sources'])} added sources.")
    print("Scope includes intentional generator/roster/input changes; this is not total gameplay equivalence.")
    print(f"Report: {destination}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        print("SOURCE SCOPE FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
