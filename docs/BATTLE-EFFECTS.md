# Normal move presentation

`Battle.LoadAndPlayAnimation` still reads the original `attacks/<move>_<side>_gsc/metadata.out`
and numbered PNG frames. It owns the frame counter, audio, action completion, flags,
translations and photosensitive timing. Its Johto rendering entry points delegate to
`MoveEffectPresentation`. The classic path remains available with its original raster
renderer. No changes outside this nested Battle class are authorized by this adaptation.

## Scope is semantic

Every original normal-move PNG has a 160×144 canvas, including small targeted effects.
Transparent bounds and canvas size are therefore insufficient to decide whether an
effect covers the screen. The helper explicitly classifies Surf, Whirlpool, Rain Dance,
Sunny Day, Sandstorm, Hail, Blizzard, Mist and Haze as screen effects. Other move images
retain their authored local coordinates. This also fixes normal move `Sprite` drawing,
which never called the previous `SpriteProxy` screen-overlay hook.

The original PNG and source Sprite are not edited. Screen artwork maps to the battle
viewport; local artwork is drawn normally. Global color metadata is separate from PNG
scope: Thunder has a local lightning image but a screen-wide inversion command.

## Snapshots and metadata

Screenshots use a framebuffer at the actual backbuffer dimensions. The renderer paints
the arena and current PMD actors into it without calling action steps, animation clock
updates, input or random generators. Health panels and command text never enter that
snapshot. It is not a scaled capture of the old central 160×144 rectangle.

`row_copy` draws source rows across the whole viewport for screen effects. Targeted
copies retain the original side masks within authored x=0…160, without repainting
the untouched field. Shifted target rows are also clipped to that original canvas. `row_displace` and
`row_split` retain the original sine, alternating-row formulas and progress thresholds;
their target masks remain local, while an explicitly untargeted distortion covers the
expanded field. All compositing is clipped above the command area at y=48. Health
panels are redrawn after effects using the existing renderer and original visibility
metadata, so neither distortion nor recoloring corrupts their text.

Inversion, darkening and lightening affect the clean modern arena through a temporary
shader, using the same metadata frames, including the original photosensitive-mode
extension. These modern colors are continuous RGB transforms, not the classic
four-color texture palette. Dig's two shrink stages draw the existing PMD species at
the original foot anchor with 5/7 and 3/7 scale, replacing the old sliced classic sprite.
Neither target selection nor move mechanics change.

Framebuffer, viewport, scissor box/enable, depth test/write mask, blending factors,
texture bindings, batch shader, color and matrices are restored around snapshot
rendering. Snapshot targets are recreated after a viewport size change and disposed
when the original animation finishes. `Game.dispose` also disposes the presentation
of an animation still on the action stack when the window closes. The unused per-animation grayscale shader was
removed; it was never read and otherwise leaked one shader per move.

## Verification

Run `python tools/move_effect_presentation_test.py` against the rebuilt JAR. The runner
compiles only its test harness; it does not overlay production classes or loose assets.
It plays actual Surf, Tackle, Rain Dance, Thunder, Dig, Water Gun, Teleport and Double Team sequences at 1280×720 and
1024×768 and 1600×600 ultrawide. Assertions check original frame advancement and completion, side-margin
pixels for global/local scope, viewport-sized Surf snapshots, row metadata execution,
Thunder's real inversion, Dig's PMD shrink, unchanged actor dimensions, command-area
pixels and restored GL/batch state. A final real Surf animation is closed while its
snapshot is still active to verify shutdown cleanup. PNGs and a JAR-hash-bound log are written to
`build/move-effects/`.

Native results must be read from the latest log and recorded in `REWORK-TESTS.md`; this
design document alone is not a claim that a particular JAR passed. The fixture does not
prove every move, special boss action or portrait viewport. Arbitrary mod animation
names default to local scope until their semantics are explicitly classified.

### Orientation regression evidence

Visual review rejected diagnostic JAR `54c4e03de7045e1b1551953a91e1b3a08e17cee7fae82885da6d28e458253d9a`
because its FBO composite was upside down, despite passing the initial extent/state
checks. An added identity-snapshot assertion fails that exact JAR at arena pixel
`3,360` (1280×720). The explicit-UV SpriteBatch overload takes its lower-edge v
coordinate first; FBOs and uploaded PNGs therefore require opposite v ordering.
The regression now compares asymmetric arena pixels and the actual Surf image
against normal scaled Sprite geometry. This test must pass on the corrected JAR.
The original negative log is retained at `build/move-effects/regression-54c4-orientation.log`.

The extended target-scope test also rejected diagnostic JAR
`d785a7ed929689a3f81754c74de935923e202a4ed1a7273d6dd0035f39616278`:
Water Gun unnecessarily repainted its complete snapshot before copying local rows,
resampling untouched margin pixels. The assertion remains exact (zero modified
margin samples). Local rows now update only their original bands; full-field
composites are reserved for screen effects, global recoloring and explicitly
untargeted distortions. The negative log is retained at
`build/move-effects/regression-d785-local-margins.log`.

### Verified candidate

JAR `f5a817d55651b6060585b2373c8e1b0771d628a6b0ef8c2de55b38d56e32978a`
passes the complete native fixture at 1280×720, 1024×768 and 1600×600:
all eight original sequences complete (348/57/161/117/82/182/148/162 frames),
Surf executes 306 row-effect frames, Water Gun/Teleport/Double Team execute
58/33/111 local row-effect frames with zero sampled margin changes, and
Thunder executes 77 color-effect frames. Orientation identity, actual PNG
geometry, GL restoration, command-area protection and mid-Surf window-close
cleanup pass at all three resolutions. Real Surf/Thunder/Dig/Water Gun PNGs
were inspected after the fixes; the magenta lower region in this test is an
intentional command-area sentinel, not product UI. Full playable UI screenshots
come from the separate end-to-end fixture. Live window resize is outside this
fixture's claims; it verifies three actual viewport sizes in separate processes.

### Mesa nearest-neighbor boundary evidence

The Linux CI run for `4d3a1d02446e72411ff61c325848a489036407fd` built the same
JAR SHA256 as Windows (`ad9a49459a306a379634f9fb37ce2766ce064ede612a50fd0a884bec4e8828ea`),
but rejected a screen-to-repaint identity sample on Mesa llvmpipe. Two isolated
R15 diagnostic runs retained exact assertions and saved the source screen,
raw captured FBO and final composite. The second run is
https://github.com/ZeriFox/pokewilds-2.5d/actions/runs/37678169777.

At 1280×720, all 19,938 differing pixels in the 230,400 arena-margin pixels
were already different in the raw FBO. Every difference lay on an exact
nearest-neighbor texel boundary in the 2.5× scaled arena (PNG rows 2 modulo 5),
and each was the exact RGBA value of one immediate vertical neighbor. There
were zero differences between the raw FBO and its final composite. This is
nearest-neighbor tie selection during repainting, not a color-rounding error,
UV inversion or a displaced final copy. The original failed logs and PNGs
are retained locally under `build/mesa-diagnostic/`.

The fixture now compares raw FBO versus final composite **exactly at every
arena-margin pixel**. Its separate screen-versus-repaint orientation check
allows only either immediate vertical neighbor at a mathematically exact
source-texel boundary: `(2*y+1)*sourceHeight % (2*viewportHeight) == 0`.
The source height is read from the actual arena texture. All RGBA channels
must still match exactly; non-boundary pixels remain position-exact. Negative
controls feed the identical comparator a vertically inverted image and a
general one-pixel translation and require rejection. The actual Surf PNG UV
comparison, local-effect margin equality, GL state and original frame/schedule
assertions remain unchanged. No production rendering or assets changed for
this test correction.
