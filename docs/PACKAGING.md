# Reproducible Windows package

Delivered local version: **v0.8.11-stardew.1**, tagged source commit
`9066b0674e3343e12d00110b8863937be234c44c`. Its 461,587,802-byte ZIP has SHA-256
`cb66c990f4acf060300c6883a8719246c8eadfb27f9caff08a9fee131aacac0d`.
Real Windows extracted `GIOCA.cmd` and the separate packaged native smoke pass;
all 529 payload files match. Receipt: `build/windows-package-verification/`.
The release's full 21-suite Windows/Linux acceptance is preserved; additive
test/documentation commit `53696d3e` passes the new active-move resize check on
Windows and the complete 22-suite Linux CI. Both use the identical released JAR.
Use the release tag and matching archived receipt to reproduce that exact
package; a future checkout requires its own complete acceptance and new version.

These commands compile and test tracked sources. They never run the historical
`apply_presentation_rework.py` migration and never commit or publish changes.
Run from the repository root with Python 3.11+ and a complete JDK 17.

## Inputs

Restore the two `lib/` files from `pokewilds-2.5d-build-inputs.zip` in release
`v0.8.11-johto.2` **after** checking SHA-256:

```
5692696180a62dc1c8822e2a2d2a324f60647535a821865d7fb556e109b3883f
```

The Windows runtime can be restored, without copying any old game or save, from
`pokewilds-2.5d-windows-x64.zip` in the same release, SHA-256:

```
7f21bb752c6eb5766a2a5f842cf6291e35123cb667ed588e553fda6d25133ae5
```

Extract only `pokewilds-2.5d/toolchain/jdk-17/` into `toolchain/jdk-17/`.
Keep all runtime files, including its legal notices, modules, DLLs and `release`.
Do not copy a user's `run/`, `run-johto/`, settings or saves.

## Source, build and native verification

```
python -m pip install Pillow==11.3.0
python tools/verify_rework.py --stage prepare
python tools/verify_rework.py --stage build
python tools/verify_rework.py --stage native
```

`prepare` regenerates assets and requires their bytes to equal the checked-in
version; changed generated files must be reviewed and committed first. It does
not rewrite Java. `build` runs the pure regressions and creates the complete JAR.
`native` runs each required fixture against that JAR, without production class
overrides. Linux needs a working OpenGL display (for example Xvfb/Mesa). Windows
needs a working OpenGL driver. A missing test or unsuccessful subprocess fails
the run. Receipts and logs are in `build/rework-verification/` and identify the
actual JAR, source hashes, commit, platform and scripts.

Individual native scripts remain useful during development, but a passing
diagnostic run with `--sources` or `--classes-override` is not release evidence.
The PMD asset test requires `--bundled` for delivered-JAR checks; the runner adds it.

## Local Windows ZIP

Commit the reviewed changes before the final verification, then use a new version:

```
python tools/package_windows.py --version v0.8.11-johto.4
python tools/verify_windows_package.py build/release/pokewilds-2.5d-windows-x64-v0.8.11-johto.4.zip
```

The example version is not reserved; inspect existing versions before choosing
one. The package command rejects a dirty checkout, stale build, mismatched test
receipt, incomplete assets/native DLLs and a non-Windows/non-x64/non-Java-17
runtime. Reusing a filename with different bytes is refused. Entries are sorted,
timestamps fixed, and metadata excludes wall-clock timestamps. Reproducibility
requires identical inputs, receipts, Python and zlib versions.

The ZIP contains the exact tested JAR, portable Java, launchers, documentation,
original notices and internal `BUILD-INFO.json`. That file includes the source
commit, JAR/source/asset hashes and a hash/size inventory of the payload. The ZIP's
own SHA-256 is external in `SHA256SUMS.txt` to avoid a circular hash.

The second command must run on Windows. It checks every extracted payload file
and starts the actual `GIOCA.cmd` with only its extracted runtime on PATH. An
opt-in production startup probe renders 120 setup-menu frames, writes the real
framebuffer, reports `java.home` and exits normally through LibGDX disposal. The
runner verifies that this runtime is inside the extracted package and no owned
child processes remain. A Windows Job Object contains the launcher and its
children before execution; a timeout terminates only that process tree.

The command also compiles the existing `PcSmokeTest` as a separate harness and
checks native menu rendering, disposal, GL errors and exceptions against the
packaged JAR. It saves `windows-launcher-menu.png`, `windows-package-menu.png`,
logs and `windows-package-verification.json` bound to the tested ZIP hash. This
is automated launcher execution, not a manual campaign or physical double-click.
Temporary settings are isolated and removed; no personal saves are accessed.
A Windows runner without working OpenGL fails explicitly.

## Distribution rights

See `ASSET-RIGHTS.md`. Reference art retains its original ownership. The local ZIP
does not establish redistribution permission. Workflows do not create releases,
tags or public uploads. The packaging workflow retains test evidence only; the
large local package is not uploaded automatically. Public distribution requires
explicit clearance of the reference materials first.
