#!/usr/bin/env python3
"""Check a frozen presentation-change allowlist against the classic source baseline.

This command never refreshes the allowlist: changed digests need explicit source
review and manifest edits. Passing proves source scope, not gameplay equivalence.
"""
from __future__ import annotations
import difflib
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GAME = "src/com/pkmngen/game/Game.java"
MANIFEST = "baseline/presentation-scope.json"
PREFIX = "src/com/pkmngen/game/"
ACTION_NAMES = (
    "BattleFadeOut.java", "BattleIntro.java", "BattleIntroAnim1.java", "DrawAttacksMenu.java",
    "DrawBattle.java", "DrawBattleMenuNormal.java", "DrawBattleMenuSafariZone.java",
    "DrawEnemyHealthGen2.java", "DrawFriendlyHealthGen2.java", "EggHatchAnim.java",
    "EvolutionAnim.java", "FadeAnim.java", "RegigigasBattle.java", "SpecialBattleMegaGengar.java",
    "SpecialBattleMewtwo.java",
)
ADDED_NAMES = (
    "ActorModelRenderer.java", "JohtoBattleRenderer.java", "JohtoRenderer.java", "ModernBatch.java",
    "ModernInventoryUi.java", "ModernPartyUi.java", "ModernUi.java", "TrainerModel3D.java",
)
CRITICAL_NAMES = (
    "Attack.java", "Battle.java", "CheckMovesLearned.java", "GenIsland1.java", "InputProcessor.java",
    "Network.java", "Player.java", "PkmnMap.java", "Pokemon.java", "DrawSetupMenu.java",
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def restore_game(visual: str) -> str:
    """Reverse only the exact integration points; extra edits fail comparison."""
    edits = [
        ("   private JohtoRenderer johtoRenderer;\n   public ModernUi modernUi;\n"
         "   public JohtoBattleRenderer johtoBattleRenderer;\n", ""),
        ("      this.uiBatch = new ModernBatch();\n", "      this.uiBatch = new ProxyBatch();\n"),
        ('      if ("johto".equalsIgnoreCase(System.getProperty("pokewilds.visual", "classic"))) {\n'
         "         this.johtoRenderer = new JohtoRenderer();\n"
         "         this.modernUi = new ModernUi();\n"
         "         this.johtoBattleRenderer = new JohtoBattleRenderer();\n      }\n", ""),
        ("      if (this.johtoRenderer != null) {\n         this.johtoRenderer.dispose();\n      }\n"
         "      if (this.modernUi != null) this.modernUi.dispose();\n"
         "      if (this.johtoBattleRenderer != null) this.johtoBattleRenderer.dispose();\n", ""),
        ("      if (this.johtoRenderer != null) {\n"
         "         boolean worldRendered = this.johtoRenderer.render(this);\n"
         "         this.johtoBattleRenderer.prepareFrame(this, worldRendered);\n      }\n", ""),
        ("      if (this.modernUi != null) this.modernUi.mapFrame(this);\n", ""),
        ("                  if (this.modernUi != null) this.modernUi.step(this, action);\n"
         "                  else action.step(this);\n", "                  action.step(this);\n"),
    ]
    for edited, classic in edits:
        if visual.count(edited) != 1:
            raise RuntimeError("Game.java integration point missing, changed, or duplicated")
        visual = visual.replace(edited, classic, 1)
    return visual


def audit(root: Path = ROOT) -> tuple[dict, str]:
    baseline = json.loads((root / "baseline/classic-build-report.json").read_text(encoding="utf-8"))
    manifest = json.loads((root / MANIFEST).read_text(encoding="utf-8"))
    original = baseline["source_files_sha256"]
    current = {p.relative_to(root).as_posix(): sha256(p) for p in (root / "src").rglob("*.java")}
    expected_actions = {PREFIX + name for name in ACTION_NAMES}
    expected_added = {PREFIX + name for name in ADDED_NAMES}
    allowed = manifest["presentation_actions"]
    if set(allowed) != expected_actions or set(manifest["added_sources"]) != expected_added:
        raise RuntimeError("Presentation manifest differs from the explicit code allowlist")
    if manifest["classic_report_sha256"] != sha256(root / "baseline/classic-build-report.json"):
        raise RuntimeError("Classic baseline report differs from its frozen digest")
    changed = sorted(p for p, digest in original.items() if current.get(p) != digest)
    added = sorted(set(current) - set(original))
    if set(changed) != expected_actions | {GAME} or set(added) != expected_added:
        raise RuntimeError(f"Unexpected source scope: changed={changed}, added={added}")
    patches = []
    classic_game = root / "baseline/Game.java"
    if sha256(classic_game) != original[GAME]:
        raise RuntimeError("Classic Game.java does not match its recorded digest")
    classic = classic_game.read_text(encoding="utf-8")
    visual = (root / GAME).read_text(encoding="utf-8")
    if restore_game(visual) != classic:
        raise RuntimeError("Game.java has changes beyond the seven exact integration points")
    patches.extend(difflib.unified_diff(classic.splitlines(True), visual.splitlines(True),
                                      fromfile="baseline/Game.java", tofile=GAME))
    action_checks = {}
    for path in sorted(allowed):
        entry = allowed[path]
        baseline_path = root / "baseline/presentation-actions" / Path(path).name
        if entry["baseline_sha256"] != original[path] or sha256(baseline_path) != original[path]:
            raise RuntimeError(f"Presentation action baseline mismatch: {path}")
        if current[path] != entry["presentation_sha256"]:
            raise RuntimeError(f"Presentation action changed since explicit review snapshot: {path}")
        patches.extend(difflib.unified_diff(
            baseline_path.read_text(encoding="utf-8").splitlines(True),
            (root / path).read_text(encoding="utf-8").splitlines(True),
            fromfile=baseline_path.relative_to(root).as_posix(), tofile=path))
        action_checks[path] = {"baseline_sha256": original[path], "presentation_sha256": current[path],
                               "scope": entry["scope"]}
    critical = {}
    for name in CRITICAL_NAMES:
        path = PREFIX + name
        if path not in original:
            raise RuntimeError(f"Critical baseline source missing: {path}")
        if current.get(path) != original[path]:
            raise RuntimeError(f"Protected rule/input/save source changed: {path}")
        critical[path] = current[path]
    report = {
        "status": "success", "baseline_source_count": len(original),
        "unchanged_original_sources": len(original) - len(changed),
        "modified_original_sources": changed, "added_sources": added,
        "game_java_changes": "Seven exact reversible integration points: fields, UI batch allocation, opt-in initialization, disposal, world/battle frame, minimap frame, UI action presentation dispatch",
        "presentation_actions": action_checks,
        "protected_rule_input_save_sources_sha256": critical,
        "current_source_files_sha256": current,
        "scope": "All original sources outside the explicit presentation allowlist remain byte-identical to the classic baseline. Listed action revisions are frozen by digest and included in the complete patch.",
        "limits": "Source invariance and a reviewed change snapshot are not proof of gameplay equivalence, input timing, complete menu coverage, or renderer correctness. Native integration tests remain separate evidence.",
    }
    return report, "".join(patches)


def main() -> None:
    report, patch = audit()
    destination = ROOT / "build/visual-scope.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (ROOT / "build/visual-hooks.patch").write_text(patch, encoding="utf-8")
    print(f"PASS: {report['unchanged_original_sources']} original sources unchanged; "
          f"{len(report['modified_original_sources'])} explicitly scoped presentation files; "
          f"{len(report['added_sources'])} added sources.")
    print(f"Report: {destination}")


if __name__ == "__main__":
    main()
