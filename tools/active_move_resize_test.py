#!/usr/bin/env python3
"""Resize actual running Surf/Tackle actions against the exact built JAR.

Each invocation has isolated classes and unique flat log/PNG names. The latest
receipt is marked running before setup and failed on every caught error; earlier
successes cannot be mistaken for the current run. No production source overlays.
"""
from __future__ import annotations

from datetime import datetime, timezone
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import uuid

from smoke_test import ROOT, JAR, CREATE_FLAGS, find_jdk, sha256

OUTPUT = ROOT / "build/active-move-resize"
SOURCE = ROOT / "tools/ActiveMoveResizeTest.java"
SCRIPT = Path(__file__).resolve()


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if OUTPUT.is_symlink() or OUTPUT.resolve() != ROOT.resolve() / "build/active-move-resize":
        raise RuntimeError("Native output must remain inside build/active-move-resize")
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ-") + uuid.uuid4().hex[:12]
    prefix = "active-move-resize-" + run_id
    log_path = OUTPUT / (prefix + ".log")
    receipt_path = OUTPUT / "active-move-resize-verification.json"
    archive_path = OUTPUT / (prefix + ".json")
    receipt = {"schema": 1, "run_id": run_id, "status": "running", "platform": platform.platform(),
               "started_utc": datetime.now(timezone.utc).isoformat(), "production_overrides": False,
               "log": log_path.relative_to(ROOT).as_posix(), "pngs": {}, "commands": [],
               "limits": "Real Surf/Tackle actions and four in-flight landscape resizes; not every move or portrait viewport."}

    def save() -> None:
        text = json.dumps(receipt, indent=2) + "\n"
        # Replace only after the complete new JSON is on disk.
        for path in (archive_path, receipt_path):
            temporary = path.with_name(path.name + ".tmp-" + run_id)
            temporary.write_text(text, encoding="utf-8", newline="\n")
            temporary.replace(path)

    save()
    try:
        receipt.update(commit=git("rev-parse", "HEAD"), working_tree=git("status", "--porcelain"),
                       jar_sha256=sha256(JAR), harness_sha256=sha256(SOURCE), script_sha256=sha256(SCRIPT))
        java, javac = find_jdk()
        receipt["jdk"] = java
        save()
        with tempfile.TemporaryDirectory(prefix="fixture-" + run_id + "-", dir=OUTPUT) as temporary:
            run = Path(temporary)
            classes = run / "classes"
            classes.mkdir()
            commands = [
                [javac, "--release", "17", "-encoding", "UTF-8", "-proc:none", "-implicit:none",
                 "-cp", str(JAR), "-d", str(classes), str(SOURCE)],
                [java, "-Xmx2g", "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto", "-Dpokewilds.models=off",
                 "-cp", os.pathsep.join((str(classes), str(JAR))), "com.pkmngen.game.ActiveMoveResizeTest"],
            ]
            try:
                with log_path.open("w", encoding="utf-8", newline="\n") as log:
                    log.write(f"Run: {run_id}\nCommit: {receipt['commit']}\nExact JAR SHA256: {receipt['jar_sha256']}\n"
                              f"Harness SHA256: {receipt['harness_sha256']}\nRunner SHA256: {receipt['script_sha256']}\n"
                              "No production source overlays\n")
                    for index, command in enumerate(commands):
                        if index == 1:
                            actual = {p.relative_to(classes).as_posix() for p in classes.rglob("*.class")}
                            expected = {"com/pkmngen/game/ActiveMoveResizeTest" + suffix + ".class"
                                        for suffix in ("", "$Sample", "$Next")}
                            if actual != expected:
                                raise RuntimeError("Harness compilation produced unexpected classes: " + repr(actual))
                        log.flush()
                        result = subprocess.run(command, cwd=run, stdout=log, stderr=subprocess.STDOUT,
                                                timeout=90 if index == 0 else 180, creationflags=CREATE_FLAGS)
                        receipt["commands"].append({"stage": "compile" if index == 0 else "native", "exit_code": result.returncode})
                        save()
                        if result.returncode:
                            raise RuntimeError("Active move resize failed; see " + str(log_path))
            finally:
                for image in sorted(run.glob("*.png")):
                    target = OUTPUT / (prefix + "-" + image.name)
                    shutil.copyfile(image, target)
                    receipt["pngs"][target.relative_to(ROOT).as_posix()] = sha256(target)
            content = log_path.read_text(encoding="utf-8", errors="replace")
            if content.count("ACTIVE MOVE RESIZE PASS:") != 1 or "Exception" in content or "\tat " in content:
                raise RuntimeError("Incomplete or exceptional active move resize run: " + str(log_path))
            if content.count("RESIZE APPLIED ") != 4 or content.count("RESIZED MOVE ") != 2:
                raise RuntimeError("The original actions did not complete all four in-flight resizes")
            for marker in ("surf-resize-1-", "surf-resize-2-", "tackle-resize-1-", "tackle-resize-2-"):
                if not any(marker in name for name in receipt["pngs"]):
                    raise RuntimeError("Missing real post-resize screenshot: " + marker)
            if sha256(JAR) != receipt["jar_sha256"] or sha256(SOURCE) != receipt["harness_sha256"] or sha256(SCRIPT) != receipt["script_sha256"]:
                raise RuntimeError("JAR, harness or runner changed during verification")
            if git("rev-parse", "HEAD") != receipt["commit"]:
                raise RuntimeError("HEAD changed during verification; rerun at the final checkpoint")
            receipt["status"] = "passed"
    except BaseException as error:
        receipt.update(status="failed", error=f"{type(error).__name__}: {error}")
        if log_path.exists():
            print(log_path.read_text(encoding="utf-8", errors="replace"))
        raise
    finally:
        if log_path.exists():
            receipt["log_sha256"] = sha256(log_path)
        receipt["finished_utc"] = datetime.now(timezone.utc).isoformat()
        save()
    print("PASS:", receipt_path)


if __name__ == "__main__":
    main()
