Dedicated mounted trainer poses

The body components in sources/mounted-bodies.png were generated using the
OpenAI built-in image_gen tool, with the existing Gold redraw as a style
reference. PROMPT.txt preserves the complete generation request. The generated
source remains unchanged. These are newly generated components, not extracted
official Ride/Surf/Fly sprites.

tools/prepare_mounted_trainers.py only crops alpha silhouettes, thresholds the
transparent generation fringe, scales with nearest-neighbour sampling and
packs the reviewed components. It does not paint or redraw pixels. The source
has uneven row spacing; explicit reviewed row bounds exclude adjacent bodies.
PROVENANCE.json records the source SHA256, rectangles, alpha bounds and targets.

Runtime MountedTrainerSprites composes the resolved idle trainer's head, cap,
hair and face with a seated/folded body. The component's red/blue/green/yellow
materials map to the current outfit/trousers/shoes/skin palette. Existing upper
outfit and rear-pack patterns are sampled where they fit the generated torso.
All 19 current identities remain selectable. Installed trainer art, recolors
and local wardrobe profiles are resolved before composition. Explicit custom
atlas trainer_<identity>_ride|surf|fly_<direction>_1 keys can override the pose.

The rider uses one mounted pose per cardinal direction and never the walking
frame clock. Slight movement is applied to the entire seated body at its hip
anchor. Ride/Fly and Surf use different back-height contacts. The mount's
locomotion envelope excludes attack outliers; ordinary field companions retain
their existing size. None of this changes field actions, collision, saves,
Pokemon positions, flight heights, movement timing or gameplay random numbers.

Verification: python tools/prepare_mounted_trainers.py --check
Native proof: python tools/mounted_pose_test.py
Development diagnostics: python tools/mounted_pose_test.py --sources
The native proof uses controlled unsaved fixture maps and its own profiles/mod,
and covers 228 mounted frames, wardrobe changes, mod recolors, full world UVs,
projected head/body visibility, cache/disposal and unchanged state/RNG.
