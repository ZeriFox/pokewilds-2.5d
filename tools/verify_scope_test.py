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
    with tempfile.TemporaryDirectory(prefix="schema6-", dir=destination) as temporary:
        root = Path(temporary).resolve()
        if not root.is_relative_to(destination.resolve()):
            raise RuntimeError("Test copy escaped the intended temporary directory")
        shutil.copytree(verifier.ROOT / "src", root / "src")
        shutil.copytree(verifier.ROOT / verifier.CLASSIC_SOURCES, root / verifier.CLASSIC_SOURCES)
        for source in [verifier.MANIFEST, verifier.CLASSIC_REPORT, *verifier.V2_ARCHIVE, *verifier.V3_ARCHIVE, *verifier.V4_ARCHIVE, *verifier.V5_ARCHIVE]:
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
        reject("input changed after freeze", verifier.PREFIX + "InputProcessor.java", append)
        reject("new helper changed after freeze", verifier.PREFIX + "DesktopControls.java", append)
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
        verifier.audit(root)
    report = destination / "results.txt"
    report.write_text("\n".join(results) + f"\nSCOPE NEGATIVE PASS: {len(results)} mutations rejected\n", encoding="utf-8", newline="\n")
    print(f"PASS: {len(results)} mutations rejected; {report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
