# RotP - Gold Experience Requiem (`geraddon`): source and license

## Public release

- Module: `rotp-gold-experience-requiem`, mod id `geraddon`, version `1.0.0-port.5-candidate.29`.
- Original 1.16.5 add-on: see the record below (original authors: Inza).
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Gold Experience Requiem source and license

### Canonical merged source

- Logical unit: production rows 90 and 91, merged as one addon
- Legacy mod id: `geraddon`
- Legacy version: `1.0.0-AMDFix`
- Canonical merged decompile: `RotP-GoldExperienceRequiem-1.0.0-AMDFix-Canonical-Merged`
- Canonical visual donor JAR SHA-256: `441DBB27E1A113983EF17D656EC56538B5AD91DB257C51C103A239A8EA6833CC`
- Canonical gameplay donor JAR SHA-256: `1318E2C318DA50084A5E0152EA213E313AB3687C36EB4F729170706D7569460C`
- Canonical visual evidence tree SHA-256: `BEDD3E25193D29DBE9DC35B3E9DF963F3DAF7F04FC3909F99377DEB700C430C8`
- Canonical gameplay evidence tree SHA-256: `E90A68E07CFF70663F1BF5BC5B2F195F40D07919C732688D77DA6822A3DCE6BD`
- Decompiler: CFR 0.152
- Legacy author: Inza
- Legacy license: GNU GPL v3

The gameplay donor is authoritative for registrations, mechanics, sounds,
language, and non-model resources. The visual donor is authoritative only for
the GER geometry, skeleton motion, and active Stand texture. Duplicate
evidence directories are byte-identical copies and do not create additional
modules.

### Port scope

This module preserves the ten legacy action registry IDs, one Stand, sixteen
direct sound assets, the complete current Gold Experience utility suite, the
legacy evolution trigger, Life Shot variants, Disabler, the Undie death loop,
resolve-gated flight, Return to Zero, and every reachable resource surface.
Gold Experience evolution remains a server-owned Beetle Arrow interaction
through `StandPowerTransitions`.

The current core humanoid contract owns punch, barrage, heavy punch, charged
heavy, guard, grab, grabbed attacks, throw, and automatic release. Life Shot
uses the public selected-lifeform API. Disabler is bounded to loaded entities
and public action state. No unrelated action or leap class is injected.

### Return to Zero

Return to Zero is player-reachable from the special wheel before Disabler. Its
Shift variation sets the mark. The donor values remain 160 cooldown ticks, 200
stamina, and an eight-chunk mark radius; there is no additional donor resolve
gate.

The server stores one transient, owner-scoped mark with day time, player
inventories and selected slots, player and Stand transforms, full non-player
and non-Stand entity NBT, relationships, periodic pose frames, and first block
preimages. The history is bounded to 200,000 block operations, 1,200 entity
frames sampled every ten ticks, 256 transported ghost entities, and 120 ghost
points per entity.

Execution first stages and validates the entire plan, including dimension,
loaded-target, block-state, block-entity, entity-type, and recreation checks.
Only then does one guarded commit apply blocks, entities, players, Stands,
relationships, and day time. An inverse snapshot is captured before mutation
and applied if any commit step fails. Preflight failure consumes no mark,
stamina, or cooldown. Commit failure restores the exact previous stamina and
does not set cooldown or consume the mark. Successful completion alone
consumes the mark, sets cooldown, applies the scoped time-stop interaction
below, and starts the synchronized presentation.

#### GER and time stop gameplay extrapolation

The manga does not establish how Return to Zero resolves simultaneous core
time-stop fields. The following is a canon-guided gameplay extrapolation, not a
manga-confirmed rule. A GER mark snapshots module-owned monotonic generation
keys for active public `TimeStopState` instances without creating an absent
attachment. Core instance IDs may reuse the time-stop user's entity ID, so a
same-user restart receives a new generation and cannot alias a pre-mark
instance. After a successful commit, RTZ terminates only a generation absent
from the mark whose chunk area intersects the marked rewind area. An instance
already active at the mark and a later unrelated non-intersecting instance are
preserved. A non-positive core chunk range is global and therefore intersects
the mark.

When a new intersecting generation freezes the marked GER owner, the public
`TimeStopLifecycleEvent.Added` hook queues one autonomous response for the next
server tick. Execution occurs outside the core Added commit stack. Before the
queue is consumed, the server revalidates the exact same mark, its one-tick
maturity, the owner's dimension, life and actual frozen state, a still-active
post-mark intersecting generation, GER identity, configured and unlocked RTZ,
cooldown, stamina, and summoned-Stand health and stun state. It then initializes
the existing RTZ action, performs it once, and clears that action. A failed gate
is not retried for the same Added event and leaves the time stop and mark
unchanged.

This autonomous entry bypasses only the fact that a frozen player cannot send
the manual input. It does not register a movement authorizer, alter stopped-time
awareness, grant a status-effect immunity, permit manual frozen-time actions,
or stop a pre-mark or non-intersecting instance.

Each selected instance is terminated through public
`TimeStopState.removeInstance(int)`. Core time-stop cooldown settlement,
stamina refund rules, entity and gamerule restoration, client synchronization,
awareness updates, audio/effect cleanup, and lifecycle hooks therefore remain
on the normal core path. King Crimson Time Erase capture, termination, and RTZ
presentation-mode selection are unchanged.

The initiator's inventory rewinds, but the initiator's transform and entity
state do not. The initiator's owned Stand also remains current. Existing
living entities load full marked NBT while retaining their current health and
absorption; missing living entities are not recreated, while missing
non-living entities are recreated. Entities absent from the mark are removed
inside the marked area.

Block recording is deliberately restricted to public NeoForge events: break,
entity place and multi-place, fluid placement, tool modification, farmland
trample, explosion detonation, piston pre, crop grow pre, neighbor notify, and
player right-click block. A mod that performs an arbitrary direct
`level.setBlock` call without any of these events is outside the recorder.
This limitation is explicit because the module does not add a global block
mixin.

### Visual provenance

On 2026-09-14 the default skin moved back to `ger_stand`, the model migrated
from the 1.16.5 source; this paragraph still described the skin it replaced
until 2026-09-16. The active GER Stand texture is
`stand_skins/ger_stand/.../textures/entity/stand/ger_stand.png`, 256x256,
SHA-256
`6C54D94D267F03A1E4BAF4D9FB917AE89CD56CCA312417E13DD0CDF6258503C0`,
over the canonical merged donor atlas and its material shading: 61377 of its
65536 pixels are still the donor's own bytes. Its geometry is the donor's
35-bone hierarchy with the donor's own 65 cubes, SHA-256
`9CC4A07928AE0361473A343632B10C243DA92CB746932C9FDC82C71D908B66D6`.

The former default is retained byte-identically as the `ger_classic` skin, and
the description that follows is of that model: the same 35-bone hierarchy,
names, parents and animation axes, with a 113-cube inventory and 49 named
relief pieces, from the approved deployed pre-proportion-refinement model. No additional skeleton normalization, crown compression, torso-volume
reduction, limb-volume reduction, or foot-depth reduction is applied. The full
non-concave face, layered eye treatment, forehead and temple ornaments, center
chest crest, four chest emblems, back slots, forearm bands, front shin plates,
orange slots, and separate rear-calf diamonds remain in their established
model-sheet regions. Arms remain 6+6, legs remain 7+7, feet remain grounded at
y=0, and the entity-space `body_rot` anchor stays at y=24. Donor animation
clips remain mapped to current ability names, and Stand rendering remains on
the existing double-sided translucent path.

`geraddon:ger_refined` is a separate resource-only Stand skin derived from the
approved default geometry. It preserves the exact 35-bone hierarchy, pivots,
animation axes and every default cube except twenty-six legacy face pieces (the
nose bridge, jaw, both cheek blocks, both brow lines, both cheek lines, both 6x6
eye plates, the lower gem point, the textured brow block and the four side
temple ovals, the olive face panel, and on the torso the four chest gems, the
centre crest, both abdomen ribs, both hip wings and the loincloth block, which
are rebuilt as seated buttons, a seated heart, segmented ribs, tapered wings and
a belt band over a chamfered shield-shaped loincloth panel). Candidate.9 is an unapproved cube-only remake in the
construction style of the core ROTP Stand models: a hundred and twenty-seven small added cubes
at two texture pixels per model unit. The face is gold as in Araki's colour
art: a raised faceted mask (centre plane plus two side facets falling back at
about eleven degrees) carries full lips (upper lip, thin deep-gold line, lower
lip) and a receding chin with no nose, every relief cube showing the head's own
front pixels on its front with mid gold sides and a deep gold underside; the GE-derived purple bands
behind the old panel are painted over with the same gold. The eyes are a relief read of the
anime eye: a plump black almond pointed at the nose with a long thin tail
sweeping up to the temple, built as two stacked layers of irregular hexagons
(straight-run edges as turned slabs, turned squares at the nose and corners,
coaxial fillers), all reaching back to the head, a cream ring
around a purple square-and-diamond disc at the belly with a dark pupil and a
small lighter gloss diamond, and an upright pale-yellow strap behind the
iris, thirty-seven cubes per eye; the brow heart follows The
World's rotated-cube heart with painted gem gloss; the four side temple gems sit on the head's sides with
their oval pixel art facing outward; each crown spike gains a
fourth tier; the rear hood gains a stepped peak. The result is 213 cubes with
no meshes. No nose, lip or jaw cubes are modeled.

The refined texture is written by `migration/tools/build_ger_refined_atlas.ps1`
from the canonical 256x256 atlas: on the head front tile the GE-derived purple
eye bands and their black outline pixels are painted over with each row's own
centre gold, three 4x4 gold cells (UV 108,22, 111,22 and 123,22, sampled at
22,22, 16,22 and 22,18) serve the relief cubes' sides, and three body cells (UV
109,25 oval orange from 212,52; 112,25 oval highlight from 210,50; 123,25 heart
light purple from 232,80) serve the torso pieces; the olive panel tile keeps its lip, nose and dash edits for the
`--face olive` variant, and small tiles are painted in
unused transparent areas (a 14x14 ink-rimmed eye ring at UV 115,22, a 4x22
outlined strap pill at UV 118,11, a 21x4 outlined cross band pill at UV 115,30, a 4x4
heart gloss tile at UV 108,11, two 2x2 lobe tiles at UV 111,11 and 113,11, and
the plain 12x12 ring and manga-look variant tiles), every colour sampled from
the canonical texture; the canonical atlas file is unchanged and
the UV space stays 128x128. The geometry is generated from the default
geometry by `modeling/ger_rotp_remake.py`; `modeling/render_ger_geo.py` only
renders review images in Blender. The earlier Blender mesh drafts, together
with the Gold Experience reference copies they used (GNU GPL v3, from the
current local
[ROTP core source](https://github.com/Campione01/Ripples-of-the-Past-1.21.1-Unofficial)),
are parked under `modeling/superseded/` for provenance only and are not
packaged. Runtime required-bone, canonical transform and visible-region checks
remain in place; baked cube counts may differ for valid selectable skins. The
113-cube count remains an offline default-skin regression baseline. A future
friend-authored skin will use a different skin ID and is not represented by an
empty placeholder.

The shared Shift+RMB key intentionally resolves GER moveset-local
`heavy_charged` on hold and `grab` on click. Both have the exact power type
`geraddon:ger_stand`; the core ability implementations do not change their
resource owner. The prior UI regression was binary icon reuse: GER's
`heavy_charged.png` matched C-Moon bytes, while the grab family matched D4C
punch/barrage/heavy bytes. Those reachable and contextual icons now reuse
GER's own punch, barrage, and heavy-punch art. The exact resolved tuples,
resource paths, and rejected foreign hashes are recorded in
`migration/manifests/resolved-control-surface.json`.

Life Shot beams and Disabler/evolution fields are synchronized exact-owner
entities. Their renderers use `StandVisualContext.world`,
`AlphaMultiBufferSource`, and the core Stand translucent material.

Disabler and RTZ each use a separate core `AddonPostEffect` lifecycle. RTZ
normal mode supplies the donor gold aura, golden fog, five bounded particle
storms, loop/fade/end audio, and invisible ghost history. TIME_ERASE mode
activates the donor cosmic and bad-signal look through the legal
`geraddon:shaders/post/rtz_bad_signal.json` four-pass PostChain and renders the
bounded ghost history after particles. Client settings can select NORMAL,
TIME_ERASE, or OFF for each server presentation type.

The server captures the public King Crimson Time Erase state before the
successful rewind closes temporal effects and carries that mode in the RTZ
start payload. A silent client diagnostic snapshot exposes a monotonic
presentation sequence and mode-specific render submission receipts for
runtime validation without changing the presentation.

The RTZ program resources were moved out of the archive and transformed only
as required by the 1.21.1 PostChain contract: obsolete camera varyings and
unlinked uniforms were removed, donor stages were linked through two generated
program JSON files, and the result blits back to the Minecraft main target.
The Disabler chain remains active and independent. Only the forbidden
core-namespace Gold Experience animation override remains inert under
`assets/geraddon/legacy_archive`.

There is no reflection, mixin, nearest-owner lookup, PlayerRenderer layer,
Iris/Sodium/Super Resolution implementation hook, framebuffer replacement,
direct GameRenderer shader takeover, or world-render cancellation. Exact donor
and transformed hashes are recorded in
`migration/manifests/legacy-resource-retention.json`.

No in-game visual, multiplayer, Iris, Super Resolution, or release-complete
claim is made until the runtime evidence matrix is executed.

### License

This derivative port and copied canonical resources are distributed under GNU
GPL v3.0 only (`GPL-3.0-only`). No warranty is provided.
