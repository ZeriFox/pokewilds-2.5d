#!/usr/bin/env python3
"""Reject unauthorized source/archive/manifest edits in an isolated copy of the frozen scope."""
from __future__ import annotations
import json
from pathlib import Path
import shutil
import tempfile
import verify_visual_scope as verifier


def main() -> int:
    verifier.audit()  # A pending review must never count as a successful test.
    destination = verifier.ROOT / "build" / "scope-negative"
    destination.mkdir(parents=True, exist_ok=True)
    results: list[str] = []
    with tempfile.TemporaryDirectory(prefix="schema8-", dir=destination) as temporary:
        root = Path(temporary).resolve()
        if not root.is_relative_to(destination.resolve()):
            raise RuntimeError("Test copy escaped the intended temporary directory")
        shutil.copytree(verifier.ROOT / "src", root / "src")
        shutil.copytree(verifier.ROOT / verifier.CLASSIC_SOURCES, root / verifier.CLASSIC_SOURCES)
        for source in [verifier.MANIFEST, verifier.CLASSIC_REPORT, *verifier.V2_ARCHIVE, *verifier.V3_ARCHIVE, *verifier.V4_ARCHIVE, *verifier.V5_ARCHIVE, *verifier.STEP2_ARCHIVE, *verifier.STEP3_ARCHIVE, *verifier.TERRAIN_ARCHIVE, *verifier.LIVING_WORLD_ARCHIVE, *verifier.CINEMATIC_NATURE_ARCHIVE, *verifier.TRAINER_REDRAW_ARCHIVE, *verifier.CINEMATIC_CUSTOMIZATION_ARCHIVE, *verifier.PLAYTEST_ARCHIVE, *verifier.RELIEF_ARCHIVE]:
            target = root / source
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(verifier.ROOT / source, target)
        verifier.audit(root)

        def reject(name: str, relative: str, edit) -> None:
            path = root / relative
            original = path.read_bytes() if path.exists() else None
            try:
                edit(path)
                try:
                    verifier.audit(root)
                except (OSError, ValueError, KeyError, RuntimeError) as error:
                    results.append(f"PASS {name}: {error}")
                else:
                    raise AssertionError("Verifier accepted: " + name)
            finally:
                if original is None:
                    path.unlink(missing_ok=True)
                else:
                    path.write_bytes(original)

        def append(path: Path) -> None:
            path.write_bytes(path.read_bytes() + b"\n// unauthorized mutation\n")

        for name in ("Attack.java", "Player.java", "PkmnMap.java", "util/Save.java"):
            reject("protected " + name, verifier.PREFIX + name, append)
        reject("Battle outside presentation action", verifier.PREFIX + "Battle.java", append)
        # Even read-only inventory cannot broaden the R15 presentation exception
        # into combat logic. Hash freezing is not the only protection here.
        battle_path = root / verifier.PREFIX / "Battle.java"
        battle_bytes = battle_path.read_bytes()
        try:
            battle_path.write_bytes(b"// mutation outside the move renderer\n" + battle_bytes)
            try:
                verifier.audit(root, inventory=True)
            except RuntimeError as error:
                if "Protected combat rules" not in str(error):
                    raise
                results.append("PASS Battle combat remains immutable during inventory: " + str(error))
            else:
                raise AssertionError("Inventory accepted an out-of-scope Battle mutation")
        finally:
            battle_path.write_bytes(battle_bytes)
        def reject_inventory(name: str,relative: str,old: str,new: str) -> None:
            path=root/relative;before=path.read_bytes()
            try:
                text=before.decode("utf-8")
                if old not in text:raise AssertionError("Missing negative fixture target: "+name)
                path.write_bytes(text.replace(old,new,1).encode("utf-8"))
                try:verifier.audit(root,inventory=True)
                except RuntimeError as error:
                    if "Protected" not in str(error):raise
                    results.append("PASS inventory rejects "+name+": "+str(error))
                else:raise AssertionError("Inventory accepted protected mutation: "+name)
            finally:path.write_bytes(before)

        reject_inventory("ball identity hook changed",verifier.PREFIX+"Battle.java",
            "CaptureBallVisual.select(game, itemName);",'CaptureBallVisual.select(game, "master ball");')
        reject_inventory("capture timing",verifier.PREFIX+"CatchPokemonWobbles3Times.java",
            "this.repeats.add(43);","this.repeats.add(44);")
        reject_inventory("capture party cap",verifier.PREFIX+"CatchPokemonWobblesThenCatch.java",
            "game.player.pokemon.size() < 6","game.player.pokemon.size() < 7")
        reject_inventory("Creative inventory bridge",verifier.PREFIX+"Player.java",
            "return CreativeMode.inventory(this, this.itemsDict);","return this.itemsDict;")
        reject_inventory("Creative infinite amount",verifier.PREFIX+"Player.java",
            "CreativeMode.SUPPLY : this.itemsDict.get(name)","1 : this.itemsDict.get(name)")
        reject_inventory("virtual supply serialization",verifier.PREFIX+"Network.java",
            "this.itemsDict = player.getStoredItemsDict();","this.itemsDict = player.getItemsDict();")
        reject_inventory("Creative sidecar removed",verifier.PREFIX+"PkmnMap.java",
            "         CreativeMode.save(game);\n","")
        reject_inventory("Creative reset on load",verifier.PREFIX+"DrawSetupMenu.java",
            "if (this.newLoadIndex == 0) CreativeMode.newWorld(game);","if (this.newLoadIndex == 1) CreativeMode.newWorld(game);")
        reject_inventory("Safari capture identity",verifier.PREFIX+"DrawBattleMenuSafariZone.java",
            'CaptureBallVisual.select(game, "safari ball");','CaptureBallVisual.select(game, "master ball");')
        reject("input changed after freeze", verifier.PREFIX + "InputProcessor.java", append)
        reject("new helper changed after freeze", verifier.PREFIX + "DesktopControls.java", append)
        reject("material resolver changed after freeze", verifier.PREFIX + "WorldMaterials.java", append)
        reject("ambient overlay changed after freeze", verifier.PREFIX + "WorldAmbientOverlay.java", append)
        reject("pre-step-2 review archive changed", "baseline/java-material-step2/modernization-scope.json", append)
        reject("unreviewed original source", verifier.PREFIX + "DrawMap.java", append)
        reject("original source removed", verifier.PREFIX + "Attack.java", lambda p: p.unlink())
        reject("unreviewed class addition", verifier.PREFIX + "UnapprovedClass.java", lambda p: p.write_text("class UnapprovedClass {}\n"))

        def edit_manifest(path: Path) -> None:
            data = json.loads(path.read_text(encoding="utf-8"))
            data["review_notes"].append("Silently approved without coordinated review")
            path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

        reject("manifest edited", verifier.MANIFEST, edit_manifest)
        reject("classic baseline changed", verifier.CLASSIC_REPORT, append)
        reject("historical v2 changed", "baseline/modernization-v2/modernization-scope.json", append)
        reject("historical v3 changed", "baseline/modernization-v3/modernization-scope.json", append)
        reject("historical v4 changed", "baseline/modernization-v4/modernization-scope.json", append)
        reject("historical v5 changed", "baseline/modernization-v5/modernization-scope.json", append)
        for name in ("DesktopMenuController.java", "MenuTextSession.java", "TrainerCatalog.java"):
            reject("interface source changed after review " + name, verifier.PREFIX + name, append)
        for name in verifier.STEP3_ARCHIVE:
            reject("pre-interface review archive changed " + name, name, append)
        for name in verifier.TERRAIN_ARCHIVE:
            reject("pre-terrain review archive changed " + name, name, append)
        reject("authored terrain changed after freeze", verifier.PREFIX + "AuthoredTerrain.java", append)
        reject("nature motion changed after freeze", verifier.PREFIX + "WorldNatureMotion.java", append)
        for name in verifier.LIVING_WORLD_ARCHIVE:
            reject("pre-living-world review archive changed " + name, name, append)
        for name in verifier.CINEMATIC_NATURE_ARCHIVE:
            reject("pre-cinematic nature review archive changed " + name, name, append)
        reject("battle scenery changed after freeze", verifier.PREFIX + "BattleScenery.java", append)
        for name in verifier.TRAINER_REDRAW_ARCHIVE:
            reject("pre-trainer redraw archive changed " + name, name, append)
        for name in verifier.CINEMATIC_CUSTOMIZATION_ARCHIVE:
            reject("pre-cinematic customization archive changed " + name, name, append)
        for name in ("CharacterAppearance.java", "StartupCinematic.java", "GhostVisualAssets.java", "MenuMotion.java"):
            reject("cinematic/customization helper changed " + name, verifier.PREFIX + name, append)
        reject("original setup launch logic changed", verifier.PREFIX + "DrawSetupMenu.java", append)
        for name in verifier.PLAYTEST_ARCHIVE:
            reject("pre-playtest checkpoint changed "+name,name,append)
        for name in ("CreativeMode.java","CreativeModeUi.java","CaptureBallVisual.java","UserSpriteAssets.java","ShinyVisuals.java","WorldReadability.java"):
            reject("playtest helper changed after freeze "+name,verifier.PREFIX+name,append)
        for name in verifier.RELIEF_ARCHIVE:
            reject("pre-relief checkpoint changed "+name,name,append)
        reject("relief texture loader changed after freeze",verifier.PREFIX+"TerrainReliefAssets.java",append)
        reject("relief renderer changed after freeze",verifier.PREFIX+"JohtoRenderer.java",append)
        verifier.audit(root)
    report = destination / "results.txt"
    report.write_text("\n".join(results) + f"\nSCOPE NEGATIVE PASS: {len(results)} mutations rejected\n", encoding="utf-8", newline="\n")
    print(f"PASS: {len(results)} mutations rejected; {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
