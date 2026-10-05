#!/usr/bin/env python3
"""Verify frozen modernization scope against the original 198 source files.

This phase intentionally changes presentation, procedural generation and roster.
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
FROZEN_MANIFEST_SHA256 = 'f978edd535ac4440d6ce01924bc98135105a718e3a57faf9838827cd3ebf2343'
PREFIX = "src/com/pkmngen/game/"

# Names and review scope are explicit; an edited manifest cannot expand them.
MODIFIED_SCOPES = {
    "BattleFadeOut.java": "Presentation: replace fade drawing while retaining action timing and dispatch.",
    "BattleIntro.java": "Presentation: preserve the modern world image during the original battle intro.",
    "BattleIntroAnim1.java": "Presentation: replace intro frame and border drawing, retain frame progression.",
    "DrawAttacksMenu.java": "Presentation: modern move panel; retain cursor state progression and original input.",
    "DrawBattle.java": "Presentation: arena, actor drawing hooks, masks and night tint.",
    "DrawBattleMenuNormal.java": "Presentation: modern battle commands; retain selection and input logic.",
    "DrawBattleMenuSafariZone.java": "Presentation: modern safari commands; retain selection and input logic.",
    "DrawEnemyHealthGen2.java": "Presentation: modern enemy HP panel; retain health updates.",
    "DrawFriendlyHealthGen2.java": "Presentation: modern party HP/EXP panel; retain health updates.",
    "EggHatchAnim.java": "Presentation: modern event background and exact PMD actor/shiny appearance.",
    "EnemyFaint.java": "Presentation: PMD actor during original enemy faint action; retain removal and victory flow.",
    "EnterBuilding.java": "Presentation: replace travel fade drawing; retain interior selection, timers and actions.",
    "EscapeRope.java": "Presentation: replace travel fade drawing; retain travel destination and action timing.",
    "EvolutionAnim.java": "Presentation: modern event background and PMD pre/post-evolution actor, preserving shiny identity.",
    "FadeAnim.java": "Presentation: replace fade drawing; retain original timer and transition condition.",
    "FriendlyFaint.java": "Presentation: PMD actor during original friendly faint action; retain status/action flow.",
    "Game.java": "Integration: visual allocation/disposal, world/UI dispatch, PMD/BW/expanded-dex resource cleanup.",
    "GenIsland1.java": "Generation: authorized modern terrain/biome pass before original coastification.",
    "PlayMusic.java": "Expanded roster initialization: resolve exact imported Pokemon cry paths.",
    "PlaySound.java": "Expanded roster initialization: resolve exact imported Pokemon cry paths.",
    "Pokemon.java": "Expanded roster initialization: National Dex lookup, imported growth data and 87.5 percent female ratio.",
    "PokemonFrame.java": "Presentation: modern reveal background and exact PMD actor/shiny appearance.",
    "RegigigasBattle.java": "Presentation: modern nested intro and arena drawing; retain battle action progression.",
    "Route.java": "Expanded roster generation: append verified additional species to biome spawn candidates.",
    "SpecialBattleMegaGengar.java": "Presentation: modern special intro frame drawing.",
    "SpecialBattleMewtwo.java": "Presentation: modern special intro frame drawing.",
    "Specie.java": "Expanded roster initialization: verified additional species before legacy initialization.",
    "ThrowOutPokemonCrystal.java": "Presentation: replace sliced actor art in send-out; retain poof and action schedule.",
    "util/SpriteProxy.java": "Presentation: intercept known battle actors for PMD rendering; legacy fallback retained.",
}
ADDED_SCOPES = {
    "ActorModelRenderer.java": "Existing optional 3D helper, disabled by the modern sprite launcher.",
    "BwAssets.java": "Lazy, disposable Black/White environmental asset access.",
    "ExpansionDex.java": "Authorized additional species data, graphics, cries, supported moves, experience and biome integration.",
    "JohtoBattleRenderer.java": "Modern battle arena, HUD and preserved-world transition rendering.",
    "JohtoRenderer.java": "Perspective terrain, cliffs, vegetation, modern actor sprites and scene lighting.",
    "ModernBatch.java": "Suppress legacy draw submissions while original menu actions advance.",
    "ModernInventoryUi.java": "Modern inventory, item actions, crafting, quantities and guide presentation.",
    "ModernPartyUi.java": "Modern setup, party, storage, portraits, statistics and nickname menus.",
    "ModernUi.java": "Modern typography, panels, dialogs, event backgrounds and menu rendering dispatch.",
    "ModernWorldGenerator.java": "Authorized deterministic procedural landforms, biome distribution and volcanic terrain.",
    "PmdBattleSprites.java": "Exact PMD battle/event actors, portraits, transforms and visual effects.",
    "PmdPokemonSprites.java": "Pinned PMD portrait/animation loading, original timing/directions and bounded texture cache.",
    "TrainerModel3D.java": "Existing optional experimental trainer model helper.",
}
PROTECTED_NAMES = (
    "Attack.java", "Battle.java", "Player.java", "InputProcessor.java", "Network.java", "util/Save.java",
    "CheckMovesLearned.java", "CheckEndOfBattle.java", "PkmnMap.java", "DrawSetupMenu.java",
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _scope(root: Path) -> tuple[dict, dict, dict, list[str], list[str], dict]:
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
            raise RuntimeError("Protected battle/rule/input/save source changed: " + path)
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
    if manifest.get("schema") != 2 or manifest.get("classic_report_sha256") != CLASSIC_REPORT_SHA256:
        raise RuntimeError("Expected schema 2 manifest bound to classic source baseline")
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
        "manifest_schema": 2, "baseline_source_count": len(original), "current_source_count": len(current),
        "unchanged_original_sources": len(original) - len(changed),
        "modified_original_sources": changed, "added_sources": added,
        "modified_source_scopes": {PREFIX + key: value for key, value in MODIFIED_SCOPES.items()},
        "added_source_scopes": {PREFIX + key: value for key, value in ADDED_SCOPES.items()},
        "protected_rule_input_save_sources_sha256": protected,
        "current_source_files_sha256": current, "complete_patch_sha256": diff_digest,
        "frozen_manifest_sha256": FROZEN_MANIFEST_SHA256,
        "scope": "Authorized update includes presentation, procedural generation and additional Pokemon data. All other original sources remain byte-identical. Success binds every changed and added source to a reviewed manifest and complete diff.",
        "limits": "Source-scope verification is NOT proof of total gameplay equivalence. Generator and roster intentionally change. Combat formulas, core input and save sources are immutable here, but behavior, visual correctness, performance and save round-trips require separate native tests.",
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
    print("Scope includes intentional generator/roster changes; this is not total gameplay equivalence.")
    print(f"Report: {destination}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        print("SOURCE SCOPE FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
