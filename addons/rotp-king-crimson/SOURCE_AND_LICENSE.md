# RotP King Crimson (`rotp_kingcrimson`): source and license

## Public release

- Module: `rotp-king-crimson`, mod id `rotp_kingcrimson`, version `1.0.3-port.30`.
- Original 1.16.5 add-on: `RotP-绯红之王-1.0.3.jar` (RotP 绯红之王, mod id `rotp_kingcrimson`), by HelloThere, DeezQuartz, StandoByte; declared license: GNU GPL v3.0; SHA-256 `72528112AC68750B0499414626AFEBDF5695F4C554F185FDFE19B5845B60E2F0`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## King Crimson Phase 5 Source And License Record

Canonical input:

`RotP-绯红之王-1.0.3.jar`

SHA-256:

`72528112AC68750B0499414626AFEBDF5695F4C554F185FDFE19B5845B60E2F0`

Canonical CFR source:

`064_RotP-绯红之王-1.0.3`

The legacy metadata declares `GNU GPL v3.0` and credits HelloThere,
DeezQuartz, and StandoByte. This bounded port uses the SPDX identifier
`GPL-3.0-only`.

Phase 1 preserves the canonical Java model geometry, 1024x1024 default and
glow Stand textures, base action icons, Stand icon, and only the sound files
reachable from summon, unsummon, punch, barrage, and heavy punch. The
reproducible converter maps the legacy Java model onto the current native
semantic Stand skeleton, converts the two legacy idle poses and all five
legacy summon poses, and filters the pinned core's current shared combat/grab
animations to that skeleton.

The permanent small face modeled on King Crimson's forehead remains part of
the base Stand geometry. At the Phase 1 boundary, no Epitaph gameplay, player
layer, shader, or tracked state was included.

Phase 2 adds the independently closable ordinary physical slice: Chop,
Impale, single and triple projectile throws, Bleeding, Gaping Wound, and
Mangled Body. Their icons, impact sounds, voice lines, pose rotations, and
legacy timing fractions are regenerated from the same hash-pinned source.
Server code owns damage, stamina, cooldowns, item consumption, guard/shield
breaks, and wound behavior. The current core owns Stand visual context and
global transparency; this addon adds no custom renderer or global visual
state.

Phase 3 adds only Epitaph's independently closable prediction and one-hit
dodge slice. The server owns the serialized player attachment, resolve-based
prediction tier, 60-tick window, one safe relocation, cooldown and stamina,
expiry, stand-swap/death/logout/dimension cleanup, and tracked-player
snapshots. Login fails closed by clearing a persisted active window. This
phase originally deferred the legacy future Time Skip branch; the final donor
gameplay audit now closes that branch without changing the Phase 3 input.

The client adds a normal player render layer and first-person layer for the
byte-exact legacy Epitaph overlay. Their alpha and classic-outline decision
come from the core-owned Stand visual context and AlphaMultiBufferSource; no
player renderer or model is replaced or globally mutated. Specifically, the
layer uses `StandVisualContext.world` and `AlphaMultiBufferSource`. The
byte-exact legacy Epitaph PostChain is structurally namespaced to the current
core `jojo_ripples:color_convolve` program and registered through the
core-managed addon post-effect lifecycle. Its only reachable chain is
`rotp_kingcrimson:shaders/post/epitaph.json`. The addon records that exact
chain for Super Resolution's injected-chain blacklist but does not read or
write Iris or Super Resolution state.

Epitaph's vision (2026-09-15, beyond the donor, which kept only the reaction to
a foreseen hit): while Epitaph is armed the server predicts where nearby
creatures will be (navigation path at current speed, otherwise current motion)
and how nearby projectiles will fly (own gravity and drag, stopped at blocks),
1, 3, 6 and 10 seconds ahead at resolve levels 1 to 4, and sends the result to
the Epitaph user alone. The client shows each foreseen creature as a
half-transparent red afterimage and each flight as red dust, brighter where it
reaches the user; images are client-only afterimage entities that vanish when
Epitaph ends or the creature leaves the foreseen future, and fade out near the
camera.

The images are truly half-transparent by the owner's decision of 2026-09-23.
Before it most creature images drew opaque: an image scaled its vertex alpha to
0.55, but most creatures draw their bodies in cutout or solid entity render
types, which ignore vertex alpha, so only players, Stands and the few creatures
drawn translucent anyway (allays, vexes, breezes) showed through. The renderer
now draws the cutout and solid entity types of the foreseen creature's own
texture in the translucent entity type of that texture, matched against
vanilla's cached types, still dark red at 0.55 opacity. Layers with a texture of
their own (armour, wool, clothing, saddles, collars) and specially rendered
items such as shields, tridents and mob heads stay opaque, and glowing eyes stay
additive; held items with ordinary item models already drew translucent through
the core's alpha buffer. Time Erase afterimages are unchanged.

As in the manga, what Epitaph foresees for a creature is certain (2026-09-15,
round 24): a navigating mob keeps to the exact path it was foreseen on until it
arrives, whatever its AI decides; a mob moving without a path keeps its momentum
for one second. A mob thrown more than six blocks off its course is released
and foreseen anew after half a second. Other players and Stands keep their own
will. Letting go of the Epitaph key keeps the vision for the rest of its window
(the foresight plus two seconds), so Time Erase can follow it; starting Time
Erase no longer ends Epitaph. Since round 69 the fates persist through Time
Erase: neither its start nor its end releases them, so what Epitaph foresaw
still comes true after the erased time. Fates live only on the Epitaph user's
server-side state and are never saved.

Phase 4 adds the canonical heavy-punch replacement chain: ordinary Heavy Punch,
Ground Punch, its Ground Slam finisher, and the low-health Desperate Eyejab
replacement. The server owns hit resolution, damage source identity,
armor penetration, block and shield behavior, knockback, finisher
consumption, stamina, and cooldowns. The original animation timing, icons,
voice lines, and impact sounds are regenerated from the hash-pinned source.
The canonical Eyejab event references a sound file absent from the source
JAR, so the packaged King Crimson Impale impact is used as the recorded
fallback instead of inventing an untraceable asset.

Phase 4 also adds the left and right Half Blindness effects. Their third-person
and first-person visuals are ordinary player render layers plus a namespaced
GUI overlay. They do not replace `PlayerRenderer`, take over a framebuffer,
mutate Iris or Super Resolution state, or enter the Stand transparency chain.

Phase 5 resolves `SAFE_REGION_FAST_FORWARD_V1` with a bounded, collision-checked
server relocation of at most 32 blocks. It advances only the user and summoned
Stand age counters by the canonical implied 200 ticks; it never invokes an
entity tick method, recursively ticks nearby entities, or loads destination
chunks. The server also owns stamina, cooldown, the ten-tick damage window,
target loss, sound selection, and tracked state.

Phase 5 resolves `OBSERVER_SCOPED_TIME_ERASE_PRESENTATION_V1` with serialized
server state, one active Time Erase user, activation and per-tick stamina,
bounded duration, interaction exit, damage handling, prior invisibility
restoration, and dynamic cooldown. The client uses the public
`ClientSkyRenderers`, `LivingEntityMaterialTintPolicies`, and
`ObserverWorldRenderPolicies` APIs for Time Erase, plus `AddonPostEffect` for
Time Skip. Donor reachability proves that `time_erase_kc.json` and
`time_erase_kc_old.json` had no runtime call sites, so neither is packaged as
an active PostChain. The active Time Erase path is the six-face projected sky
with seed 31100, a base pass plus thirteen portal layers, donor red-dominant
colors, owner-scoped cloud/world suppression, and a whole-living-material red
tint that excludes the owner, owner Stand, decoy chain, and afterimages. Only
`rotp_kingcrimson:shaders/post/time_skip.json` remains active here and ends at
`minecraft:main`.

Time Erase also restores the donor's 375 ms, sixteen-frame activation and
termination transition. Activation is owner-only; termination is sent to players
within 192 blocks and, as in the donor (which sent it only to King Crimson users),
is drawn only for players whose own Stand is King Crimson; it stops the owner's
looping start sound and sends the end sound. Every Time Skip shows the same
375 ms flash to the same audience. The owner receives regular invisibility plus the core full-invisibility
effect at amplifier 999999, the summoned Stand receives regular invisibility,
and pre-existing owner and Stand effects are restored on exit. As in the
donor, sprinting is not cancelled during Time Erase.

The canonical Time Erase sky textures, all sixteen Time Skip frames, default
ability icons, effect icon, and all 72 packaged sound payloads are restored at
their original active paths. The canonical
`kingcrimson_timeerase` event references a missing
`diavolo_timeerasure2.ogg`, so it uses the packaged
`diavolo_kingcrimson` event as a recorded fallback. All donor action icons,
including the thirty per-skin Time Erase and Time Skip variants, now retain
their original `textures/action` paths and current `textures/ability` aliases.
The Epitaph future-branch `epitaph_timeskip.ogg` is active and hash-pinned.
At resolve level 3 or above, a predicted hit with an attacker now consumes the
donor's additional 150 stamina and enters the bounded current Time Skip state
toward the attacker. The port advances only the canonical 40 implied age ticks,
never recursively ticks nearby entities, and falls back to the existing safe
dodge when the future branch cannot start. As in the donor, a fall or a crash
into a wall is only the Time Skip flash and the Epitaph sound (no stamina, no
move), and a hit with no attacker costs the 150 stamina without moving the
user. Below level 3 every hit except a fall, a wall crash and an explosion is
dodged, projectiles and attacker-less damage included.

The donor's custom barrage pair is also active. The normal barrage retains
the donor's half-health gate at resolve level 3 or above and contextually
switches to the separately registered Doppio barrage while the Stand is
absent or arms-only. Doppio keeps its own cry, 60-tick cooldown, half-health
gate, and arms-only auto-summon behavior. The donor registers Bloodthrow only
as a sound/icon asset and never registers or calls a Bloodthrow action.
No Bloodthrow gameplay has been invented.

The donor also constructs the seven-event `kc_ost` graph and binds it to the
King Crimson Stand. All seven registrations and their canonical event
references are restored through the current core `OstSoundList`; the four
streamed tracks under `sounds/ost/king_crimson_1.ogg` through
`king_crimson_4.ogg` are byte-exact copies from the hash-pinned donor.

No legacy reflection or donor mixin is copied. The addon's own three mixins,
added in round 69, only end Time Erase when a Stand punch or barrage reaches
its perform phase and keep the caster's own Time Erase decoy and its Stand out
of the caster's targeting; they touch no render state. The addon performs no
framebuffer takeover or global render cancellation, and it copies no static
player map, Iris mutation, or Super Resolution mutation. No global renderer
mutation is copied. The donor's owner-only dark-red moving afterimage, tracked
player-profile decoy and Stand, mob redirection, and lifecycle cleanup are
restored with module-owned entities and payloads. The donor's delayed entity
rewind is not restored: by the owner's decision of 2026-09-23 (manga logic,
where the world simply resumes at the end of the erased time), Time Erase end
moves no real entity. The donor's full-color stationary afterimage is not
spawned either. It did two things, for the Time Erase user alone: it marked
where each entity would be rewound to, and, glowing on the red `KC_RedGlow`
team, it showed an outline of every tracked creature within 192 blocks through
walls (where the creature stood when the erase began, later trailing it by 60
ticks). The rewind marker is gone by the same owner decision. The through-wall
outline is kept: asked separately, the owner chose on 2026-09-23 to put it on
the remaining moving afterimage. Each dark-red afterimage now glows in the
`KC_RedGlow` red (`TextFormatting.RED`) for its whole life, so the Time Erase
user alone sees a red outline of every tracked creature through walls, trailing
it by 10 ticks instead of 60. The 192 blocks are only the server scan: the
outline is drawn only where the afterimage itself renders, about 64 blocks from
the camera at the default Entity Distance, the same limit as 1.16 (both
versions size it 0.6x1.8), and only for creatures the owner's client tracks.
No other player receives these afterimages, so no
other player sees the outline. Epitaph vision images are never outlined.
Observer terrain, weather, particle, and block-entity suppression uses the core
observer policy API and only the local Time Erase observer. Renderers that
bypass the guarded vanilla methods remain unsupported and fail open; no Iris or
Sodium equivalence is claimed.

The eight-row historical disposition receipt classifies every row as A.
Seven donor-reachable media rows are exact active resources. The runtime-
registered legacy mixin descriptor is a legal semantic conversion to the
Phase 5 public post-effect, compatibility, layer, overlay, and server-state
owners. The descriptor's donor bytes are also retained exactly under
`assets/rotp_kingcrimson/legacy_archive/root`; the former root path is absent.

The registration-reachability audit also closes the older deferred and
archive-only paths. All fifteen donor action registrations, both Time Erase
projection entities, and the donor mixin semantics are repaired active. The
legacy skin manifest has 273 active rows and zero archived rows. The 63 PNG
receipts retained under `legacy_archive/skin_resources` are provenance-only
fully equivalent duplicates: every SHA-256 matches an active ability resource
and none is stranded. `kingcrimson_bloodthrow` and `doppio_damnit` are the only
proven-inert gameplay registrations; donor evidence records zero action
registrations and zero runtime call sites while their media bytes remain
packaged. Exact restoration of the two filenames absent from the canonical
donor and world-pass equivalence in renderers that bypass vanilla guards are
external-blocked; their active local fallbacks and fail-open boundaries are
recorded rather than hidden by deletion.
