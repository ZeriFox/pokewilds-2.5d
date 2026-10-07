#!/usr/bin/env python3
"""Extract and render the exact portable Windows ZIP using its bundled Java.

Exercises the actual GIOCA.cmd launcher with the production opt-in startup probe,
then compiles only the existing test harness outside the extracted game. All
production classes/resources come exclusively from the packaged JAR.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import subprocess
import tempfile
import zipfile

from package_windows import PREFIX, ROOT, digest, windows_x64


def run_owned_launcher(game: Path, environment: dict[str, str], log_path: Path) -> dict:
    """Start cmd suspended, contain its descendants, then permit any execution.

    Closing our Windows Job Object terminates only our process tree, including
    when the launcher hangs. No enumeration or killing of unrelated JVMs.
    """
    import ctypes as c
    from ctypes import wintypes as w
    import msvcrt
    import time

    class StartupInfo(c.Structure):
        _fields_ = [("cb", w.DWORD), ("lpReserved", w.LPWSTR), ("lpDesktop", w.LPWSTR),
                    ("lpTitle", w.LPWSTR), ("dwX", w.DWORD), ("dwY", w.DWORD),
                    ("dwXSize", w.DWORD), ("dwYSize", w.DWORD), ("dwXCountChars", w.DWORD),
                    ("dwYCountChars", w.DWORD), ("dwFillAttribute", w.DWORD), ("dwFlags", w.DWORD),
                    ("wShowWindow", w.WORD), ("cbReserved2", w.WORD), ("lpReserved2", c.POINTER(c.c_byte)),
                    ("hStdInput", w.HANDLE), ("hStdOutput", w.HANDLE), ("hStdError", w.HANDLE)]

    class ProcessInfo(c.Structure):
        _fields_ = [("hProcess", w.HANDLE), ("hThread", w.HANDLE), ("dwProcessId", w.DWORD), ("dwThreadId", w.DWORD)]

    class BasicLimit(c.Structure):
        _fields_ = [("PerProcessUserTimeLimit", c.c_longlong), ("PerJobUserTimeLimit", c.c_longlong),
                    ("LimitFlags", w.DWORD), ("MinimumWorkingSetSize", c.c_size_t),
                    ("MaximumWorkingSetSize", c.c_size_t), ("ActiveProcessLimit", w.DWORD),
                    ("Affinity", c.c_size_t), ("PriorityClass", w.DWORD), ("SchedulingClass", w.DWORD)]

    class IoCounters(c.Structure):
        _fields_ = [(name, c.c_ulonglong) for name in ("ReadOperationCount", "WriteOperationCount", "OtherOperationCount",
                                                      "ReadTransferCount", "WriteTransferCount", "OtherTransferCount")]

    class ExtendedLimit(c.Structure):
        _fields_ = [("BasicLimitInformation", BasicLimit), ("IoInfo", IoCounters),
                    ("ProcessMemoryLimit", c.c_size_t), ("JobMemoryLimit", c.c_size_t),
                    ("PeakProcessMemoryUsed", c.c_size_t), ("PeakJobMemoryUsed", c.c_size_t)]

    class Accounting(c.Structure):
        _fields_ = [("TotalUserTime", c.c_longlong), ("TotalKernelTime", c.c_longlong),
                    ("ThisPeriodTotalUserTime", c.c_longlong), ("ThisPeriodTotalKernelTime", c.c_longlong),
                    ("TotalPageFaultCount", w.DWORD), ("TotalProcesses", w.DWORD),
                    ("ActiveProcesses", w.DWORD), ("TotalTerminatedProcesses", w.DWORD)]

    kernel = c.WinDLL("kernel32", use_last_error=True)
    signatures = {
        "CreateJobObjectW": ([c.c_void_p, w.LPCWSTR], w.HANDLE),
        "SetInformationJobObject": ([w.HANDLE, c.c_int, c.c_void_p, w.DWORD], w.BOOL),
        "QueryInformationJobObject": ([w.HANDLE, c.c_int, c.c_void_p, w.DWORD, c.c_void_p], w.BOOL),
        "AssignProcessToJobObject": ([w.HANDLE, w.HANDLE], w.BOOL),
        "CreateProcessW": ([w.LPCWSTR, w.LPWSTR, c.c_void_p, c.c_void_p, w.BOOL, w.DWORD,
                             c.c_void_p, w.LPCWSTR, c.POINTER(StartupInfo), c.POINTER(ProcessInfo)], w.BOOL),
        "ResumeThread": ([w.HANDLE], w.DWORD),
        "WaitForSingleObject": ([w.HANDLE, w.DWORD], w.DWORD),
        "GetExitCodeProcess": ([w.HANDLE, c.POINTER(w.DWORD)], w.BOOL),
        "TerminateProcess": ([w.HANDLE, w.UINT], w.BOOL),
        "CloseHandle": ([w.HANDLE], w.BOOL),
    }
    for name, (arguments, result) in signatures.items():
        getattr(kernel, name).argtypes = arguments
        getattr(kernel, name).restype = result

    def check(ok):
        if not ok:
            raise c.WinError(c.get_last_error())

    job = kernel.CreateJobObjectW(None, None)
    check(job)
    process = ProcessInfo()
    assigned = False
    started = time.monotonic()
    try:
        limits = ExtendedLimit()
        limits.BasicLimitInformation.LimitFlags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        check(kernel.SetInformationJobObject(job, 9, c.byref(limits), c.sizeof(limits)))
        with log_path.open("wb") as log, open(os.devnull, "rb") as input_file:
            output_handle = msvcrt.get_osfhandle(log.fileno())
            input_handle = msvcrt.get_osfhandle(input_file.fileno())
            os.set_handle_inheritable(output_handle, True)
            os.set_handle_inheritable(input_handle, True)
            startup = StartupInfo()
            startup.cb = c.sizeof(startup)
            startup.dwFlags = 0x100  # STARTF_USESTDHANDLES
            startup.hStdInput, startup.hStdOutput, startup.hStdError = input_handle, output_handle, output_handle
            cmd = Path(os.environ["SystemRoot"]) / "System32/cmd.exe"
            line = c.create_unicode_buffer(subprocess.list2cmdline([str(cmd), "/d", "/c", "call GIOCA.cmd"]))
            block = c.create_unicode_buffer("\0".join(f"{key}={value}" for key, value in sorted(environment.items(), key=lambda x: x[0].upper())) + "\0\0")
            try:
                # SUSPENDED | UNICODE_ENVIRONMENT | NO_WINDOW: assignment happens before cmd can spawn Java.
                check(kernel.CreateProcessW(str(cmd), line, None, None, True, 0x08000404,
                                             block, str(game), c.byref(startup), c.byref(process)))
            finally:
                os.set_handle_inheritable(output_handle, False)
                os.set_handle_inheritable(input_handle, False)
            check(kernel.AssignProcessToJobObject(job, process.hProcess))
            assigned = True
            if kernel.ResumeThread(process.hThread) == 0xFFFFFFFF:
                raise c.WinError(c.get_last_error())
            deadline = time.monotonic() + 90
            while kernel.WaitForSingleObject(process.hProcess, 1000) == 0x102:
                if time.monotonic() >= deadline:
                    raise TimeoutError("GIOCA.cmd exceeded 90 seconds; terminating only its owned process tree")
            exit_code = w.DWORD()
            check(kernel.GetExitCodeProcess(process.hProcess, c.byref(exit_code)))
            accounting = Accounting()
            # Job accounting can lag the signalled process handle briefly.
            drain_deadline = time.monotonic() + 5
            while True:
                check(kernel.QueryInformationJobObject(job, 1, c.byref(accounting), c.sizeof(accounting), None))
                if not accounting.ActiveProcesses or time.monotonic() >= drain_deadline:
                    break
                time.sleep(.05)
            if accounting.ActiveProcesses:
                raise RuntimeError("Launcher exited while owned child processes remained active")
            if exit_code.value:
                raise RuntimeError(f"GIOCA.cmd exited {exit_code.value}; see {log_path}")
            if accounting.TotalProcesses < 2:
                raise RuntimeError("Launcher never started its child JVM")
            return {"entrypoint": "GIOCA.cmd", "exit_code": 0, "pid": process.dwProcessId,
                    "owned_processes": accounting.TotalProcesses, "remaining_owned_processes": accounting.ActiveProcesses,
                    "elapsed_seconds": round(time.monotonic() - started, 3), "containment": "Windows Job Object; kill only owned tree on failure"}
    finally:
        # Assignment failure leaves only our suspended cmd, which never executed user code.
        if process.hProcess and not assigned:
            kernel.TerminateProcess(process.hProcess, 1)
        kernel.CloseHandle(job)
        if process.hThread:
            kernel.CloseHandle(process.hThread)
        if process.hProcess:
            kernel.WaitForSingleObject(process.hProcess, 5000)
            kernel.CloseHandle(process.hProcess)


def verify(archive_path: Path, output: Path) -> None:
    if os.name != "nt":
        raise RuntimeError("A Windows host is required; Linux is not a packaged Windows runtime test")
    archive_path = archive_path.resolve(strict=True)
    output.mkdir(parents=True, exist_ok=True)
    receipt_path = output / "windows-package-verification.json"
    receipt = {"schema": 1, "status": "running", "platform": platform.platform(),
               "zip_sha256": digest(archive_path), "zip_filename": archive_path.name,
               "scope": "Actual GIOCA.cmd launch plus native Game.create/render/dispose harness; bundled runtime and packaged JAR only",
               "limitations": "Automated hidden OpenGL windows; not a manual playthrough, OS DPI test, or physical mouse double-click."}

    def save() -> None:
        receipt_path.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")

    save()
    try:
        with tempfile.TemporaryDirectory(prefix="packaged-windows-", dir=output) as folder:
            extraction = Path(folder)
            with zipfile.ZipFile(archive_path) as archive:
                names = archive.namelist()
                if len(set(names)) != len(names) or archive.testzip():
                    raise RuntimeError("Duplicate or corrupt entries in portable ZIP")
                info = json.loads(archive.read(PREFIX + "BUILD-INFO.json"))
                expected = {PREFIX + name for name in info["files"]} | {PREFIX + "BUILD-INFO.json"}
                if set(names) != expected:
                    raise RuntimeError("ZIP entries differ from BUILD-INFO payload inventory")
                for member in archive.infolist():
                    path = PurePosixPath(member.filename)
                    if path.is_absolute() or ".." in path.parts or "\\" in member.filename or ":" in member.filename:
                        raise RuntimeError("Unsafe archive entry: " + member.filename)
                    if not member.filename.startswith(PREFIX):
                        raise RuntimeError("Unexpected archive root")
                    target = extraction.joinpath(*path.parts)
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.open(member) as source, target.open("wb") as destination:
                        shutil.copyfileobj(source, destination)
                    relative = member.filename[len(PREFIX):]
                    if relative != "BUILD-INFO.json":
                        expected_file = info["files"][relative]
                        if target.stat().st_size != expected_file["bytes"] or digest(target) != expected_file["sha256"]:
                            raise RuntimeError("Package payload hash mismatch: " + relative)
            game = extraction / PREFIX.rstrip("/")
            jar = game / "dist/pokewilds-rebuilt.jar"
            runtime = game / "toolchain/jdk-17"
            java, javac = runtime / "bin/java.exe", runtime / "bin/javac.exe"
            windows_x64(java)
            if digest(jar) != info["jar_sha256"]:
                raise RuntimeError("Packaged JAR differs from BUILD-INFO")
            receipt.update(commit=info["commit"], jar_sha256=info["jar_sha256"],
                           payload_files_checked=len(info["files"]))
            # No inherited user JVM agents/options or external Java installation.
            environment = {key: value for key, value in os.environ.items()
                           if key.upper() not in {"JAVA_HOME", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"}}
            environment["PATH"] = str(runtime / "bin") + os.pathsep + str(Path(os.environ["SystemRoot"]) / "System32")
            launcher_environment = dict(environment, JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dpokewilds.verifyStartup=true",
                                        POKEWILDS_NO_PAUSE="1")
            launcher_log = output / "windows-launcher.log"
            launcher_receipt = run_owned_launcher(game, launcher_environment, launcher_log)
            launcher_text = launcher_log.read_text(encoding="utf-8", errors="replace")
            marker = re.search(r"^POKEWILDS_LAUNCHER_VERIFIED frames=120 java.home=(.+)$", launcher_text, re.MULTILINE)
            launcher_image = game / "run-johto/launcher-menu.png"
            if not marker or not launcher_image.is_file() or "Exception" in launcher_text or "\tat " in launcher_text:
                raise RuntimeError("Actual GIOCA.cmd did not complete the production menu verification; see " + str(launcher_log))
            if Path(marker.group(1).strip()).resolve() != runtime.resolve():
                raise RuntimeError("Actual launcher used a Java runtime outside the extracted package")
            saved_launcher_image = output / "windows-launcher-menu.png"
            shutil.copy2(launcher_image, saved_launcher_image)
            launcher_receipt.update(java_home_verified="extracted toolchain/jdk-17", frames=120,
                                    log_sha256=digest(launcher_log), screenshot=saved_launcher_image.name,
                                    screenshot_sha256=digest(saved_launcher_image))
            receipt["launcher"] = launcher_receipt
            save()
            classes = extraction / "verification-harness"
            classes.mkdir()
            work = game / "run-johto"
            work.mkdir(exist_ok=True)
            source = ROOT / "tools/PcSmokeTest.java"
            receipt["harness_sha256"] = digest(source)
            commands = [
                [str(java), "-version"],
                [str(javac), "--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", str(jar), "-d", str(classes), str(source)],
                [str(java), "-Dfile.encoding=UTF-8", "-Dpokewilds.visual=johto", "-Dpokewilds.models=off",
                 "-Dsmoke.frames=120", "-Dsmoke.timeoutSeconds=45", "-cp", str(classes) + os.pathsep + str(jar), "PcSmokeTest"],
            ]
            log_path = output / "windows-package.log"
            with log_path.open("w", encoding="utf-8") as log:
                for index, command in enumerate(commands):
                    log.write(f"Phase {index + 1}: {'runtime version' if index == 0 else 'harness compilation' if index == 1 else 'packaged native startup'}\n")
                    log.flush()
                    result = subprocess.run(command, cwd=work, env=environment, stdout=log,
                                            stderr=subprocess.STDOUT, timeout=90,
                                            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                    if result.returncode:
                        raise RuntimeError(f"Portable Windows startup failed in phase {index + 1}; see {log_path}")
            screenshot = work / "smoke-menu.png"
            if "SMOKE PASS:" not in log_path.read_text(encoding="utf-8", errors="replace") or not screenshot.is_file():
                raise RuntimeError("Native packaged game did not produce success marker and screenshot")
            saved_image = output / "windows-package-menu.png"
            shutil.copy2(screenshot, saved_image)
            if digest(jar) != info["jar_sha256"] or digest(archive_path) != receipt["zip_sha256"]:
                raise RuntimeError("JAR or ZIP changed during native package verification")
            receipt.update(status="passed", log_sha256=digest(log_path), screenshot=saved_image.name,
                           screenshot_sha256=digest(saved_image))
    except Exception as error:
        receipt.update(status="failed", error=str(error))
        raise
    finally:
        save()
    print("PASS:", receipt_path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("zip", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/windows-package-verification")
    args = parser.parse_args()
    verify(args.zip, args.output)


if __name__ == "__main__":
    main()
