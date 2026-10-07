# Rework verification ledger

Platform: Windows x64, 2026-10-07. Results apply only to the source and JAR hashes recorded with each run.

Initial native checkpoint JAR: `8d5c4b844abf0bcd9f1b0f17b1c293c4427cb3e32f37d8802ac308b932127b15`. Complete Java 17 compilation succeeded; Windows NVIDIA RTX 4060 / OpenGL 4.6.0 driver 596.36.

| Check | Result | Evidence |
|---|---|---|
| Sampling / geometry | PASS, 12,309 / 312 assertions | console, pure geometry |
| Scope / mutation protection | PASS, 16 rejected mutations | build/visual-scope.json, build/scope-negative/results.txt |
| Current source asset crops | PASS, 8 pixel/integrity/determinism tests | tools/test_stardew_assets.py |
| Landscape | PASS | build/landscape-smoke/*.png and logs |
| Desktop controls | PASS, versioning/focus/custom/input/typing/controller cases | build/desktop-controls/desktop-controls.log |
| PMD battle | PASS, 183 draw assertions, 18 captures | build/pmd-battle-visual/ |
| Full viewport effect / local particle bounds | PASS at 1280x720, 1920x1080, 1024x768, 1600x600 | build/presentation-rework/ |

These native results precede the final isolated-tree crop correction and are not release acceptance. The final packaged hash must repeat the suite. An initial 1080p decorated-window test failed because Windows clamped its framebuffer to 1866x1011; the undecorated rerun confirmed an actual 1920x1080 buffer. An old PMD test expected the transparent pose origin at ground level; the updated assertion checks a stable pose origin plus opaque idle-foot contact against the arena plane. No actor, effect or alpha validation was disabled.

Evidence will include native framebuffer captures from the packaged game classes; harness classes must not replace production classes on the classpath. Failed or unavailable tests stay explicit.
