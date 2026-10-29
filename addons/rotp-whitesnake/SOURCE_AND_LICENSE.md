# RotP Whitesnake (`rotp_whitesnake`): source and license

## Public release

- Module: `rotp-whitesnake`, mod id `rotp_whitesnake`, version `1.1.1-alpha.23-mc1.21.1`.
- Original 1.16.5 add-on: `RotP-Whitesnake-BETA-1.1.1.jar` (RotP Whitesnake, mod id `rotp_whitesnake`), by StandoByte, silentevermore, TheDoGGyS; declared license: GNU GPL v3.0; SHA-256 `B37D7EC539B06315859EC78D762A01E8198B6DA97EA20225EFB7968645CEE41D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Whitesnake source and license

This module is a bounded NeoForge 1.21.1 port from the legacy archive
`[RotP: Whitesnake Addon] RotP-Whitesnake-BETA-1.1.1.jar`.

Source JAR SHA-256:

`B37D7EC539B06315859EC78D762A01E8198B6DA97EA20225EFB7968645CEE41D`

The canonical Java audit used the corresponding
`004_[RotP: Whitesnake Addon] RotP-Whitesnake-BETA-1.1.1` decompile under
the local 1.16.5 addon evidence workspace.

The original mod metadata declares GNU GPL v3.0. The selected model,
textures, sound files, translations, and gameplay behavior remain under that
upstream license. Original addon credits include ArchLunatic and the authors
listed in the legacy metadata; sound-effect credit includes LAMBIRNO.

The completed static migration contains Whitesnake's base Stand, core humanoid actions, two legacy
skins, reachable base sound closure, arrow-pool integration, the accepted
power-disc follow-on, the memory-disc vertical slice, standalone Blindness,
the server-authoritative Melt Your Heart gameplay loop, and Disguise. The disc systems
register `whitesnake_throw_disc`, `whitesnake_remove_stand_disc`,
`removing_the_memory_disk`, `memory_disc_item`, and the shared `disc`
projectile. Stand removal, insertion, and projectile replacement use only the
pinned public `StandPowerTransitions` API. Extracted Stand discs additionally
carry a persistent and network-synchronized `stand_disc_authority` component
with the original Stand owner's UUID, a random extraction UUID, the Stand ID,
and a bounded display name. The normalized owner holds the matching
`stand_disc_loss_state` serializable attachment.

Stand-disc extraction snapshots only the core `StandTypePersistentData`
payload and the core `StandAwakening` ownership payload through their public
`serializeNBT` contracts. A valid direct or thrown restoration first matches
owner UUID, extraction UUID, Stand ID, item Stand identity, and attachment
state, then inserts through `StandPowerTransitions`. It reapplies those
payloads through the public `deserializeNBT`, `StandPower.afterNbtRead`,
`syncOnUpdate`, and awakening `sync` persistence/synchronization contracts.
This preserves unlocked skills, ability locks and training, XP, resolve level,
awakening/control stage, prior-Stand ownership, and fated/canon Stand state
across save, relog, unload, dimension transfer, and Player clone/death without
serializing the whole player or whole `StandPower`. Transient stamina,
cooldowns, resolve counters, and active effects are not part of the extracted
disc state.

The first valid restore consumes the attachment token, so copied or stale
discs cannot restore a second time. Authority-bound Stand discs are always
consumed on success, are returned after a failed throw even in creative mode,
and cannot invoke the core Stand-disc right-click replacement path. Unbound
core Stand discs retain the legacy direct insertion and thrown replacement
behavior.

The memory disc uses a persistent and network-synchronized typed item component
containing only the normalized target UUID, a random extraction UUID, and a
bounded display name. A module-local serializable living attachment owns the
authoritative extraction UUID. Extraction and restoration are server-only, and
both direct insertion and the shared projectile require the component,
projectile payload snapshot, normalized target, and attachment token to agree.
This prevents a copied or stale disc from restoring or overwriting newer state
without serializing arbitrary player internals or accepting client authority.

Both attachments persist through save, relog, and dimension transfer. Player
clone events copy their token state, including death clones; non-player death
creates no successor state. Discs are never deleted because a target is
unloaded, logged out, or in another dimension. Server damage handling
normalizes direct users, Stands, and owned projectiles to the Stand user before
suppressing outgoing damage while memory is extracted. The disc slices use no
mixin, raw persistent entity data, global state map, target-scanning inventory
tick, custom framebuffer, post chain, or custom network payload.

`blindness` preserves the legacy five-tick hold-to-fire, 50 stamina cost,
Resolve level 3 gate, required Stand arms, and vanilla Blindness toggle. Its
100000-tick amplifier-1 effect is marked by the serializable
`blindness_state` attachment so addon-owned sight loss is cleared on death,
logout, dimension change, ability or Stand loss, unsummon, and entity unload.
The toggle itself still removes any current Blindness effect, matching the
legacy Restore Sight behavior.

`melt_your_heart` preserves the 100-stamina activation condition, one-time
10-stamina cost, 100-tick maximum hold, full user walk speed, 20 technical plus
100 additional cooldown with a 0.0 Resolve multiplier, one projectile per
held tick, and the legacy start sound. Each no-save projectile has the legacy
100-tick lifetime, 0.1 inertia and gravity, rising/falling trajectory,
ceiling response, zero entity damage, and floor hazard placement. The owned
layered hazard applies the legacy stuck motion, Slowness III, Blindness I,
Weakness I, swimming pose, and 0.5 magic damage to non-Whitesnake living
entities. Its decay counter is block state rather than the legacy global
static timer. Projectile and block-entity ownership are user UUID based and
self-clean on owner, ability, Stand, summon, dimension, or load invalidation;
no global state map is used.

`disguise` preserves the legacy Resolve level 3 and Stand-arms gate as a
separate click item on the C/X wheel. Activating it while transformed clears
the form on the server. Otherwise the Stand opens a bounded 600-tick
selection window. The one C2S `select_disguise` payload carries only the
candidate entity type. The server derives the sender's currently summoned
Whitesnake, checks ownership, ability unlock, selection/action state, a
five-tick request interval, and a summonable living creature/monster
allowlist. Players, Stand entities, recursive or oversized forms, constructor
failures, multipart forms, forms above 200 health, and the Ender Dragon,
Wither, Warden, and Giant are rejected.

The selected registry ID is an optional `ResourceLocation` in Whitesnake's
`SynchedEntityData`; normal entity tracking owns current, late, and retracking
clients. Death, logout, dimension change, respawn/clone, ability or Stand
loss, unsummon, conflicting actions, expiry, removal, and entity unload clear
the entity-owned form or authorization without a global map. The client
picker searches large registries, scrolls by rows, scales its grid to the GUI,
uses spawn-egg icons where available, and draws a text fallback for missing
icons.

The module-owned `WhitesnakeRenderer` snapshots that synced type through
`WhitesnakeRenderState`, consumes the core `StandVisualContext` and alpha
buffer, and falls back to the normal Stand renderer for arms-only/classic
obstruction contexts, recursive entry, invalid forms, or renderer failure. It
bounds its level-local preview cache to 32 forms and does not hook
PlayerRenderer, mixins, PostChain, Iris/SR framebuffer state, or the shared
core renderer.

The legacy Blindness, Melt Your Heart, and Disguise ability icons, projectile
model and texture, layered block models and texture, and Melt sound are
included. The reachable legacy `GlintLayer` and exact `textures/glint.png`
are also active. Its current implementation is owned by
`WhitesnakeRenderer`, derives world opacity from `StandVisualContext.world`,
applies it once through `AlphaMultiBufferSource`, and draws with the core
Stand translucent material. It has no PlayerRenderer, mixin, global shader,
PostChain, Iris/SR framebuffer, or global render-state hook.

All three primary Stand textures were rebuilt as 512x512 atlases. The
model coordinates and skin scale remain unchanged for first-person and
third-person parity; every cuboid instead uses explicit per-face UVs at four
times the source texel density. Each skin adds four inflated joint-shell
cuboids, and the atlas preserves donor texels with nearest-neighbor scaling
and no post-scale color filtering or procedural noise. The deterministic
tool, canonical 128x128 inputs, generated material reference, UV occupancy,
and fixed front/left/back/isometric evidence live under `migration/`.
Static art evidence is complete, while the in-game first-person and
third-person action matrix remains a release-evidence task.

The full official repository history shows that commit
`182c87f98a15bcabedfc3a9b5a001d95cdafc013` registered the harmful
`melt_heart_effect` and its render hooks before commit
`346e073cf77637022eb96a4ea43bffa76a20f961` removed them as "unused".
The effect is therefore restored with its uncurable status, 50-tick damage
cadence, cloud particles, healing cancellation, amplifier-selected global
living-entity overlay, and first-person arm overlay. All 24 exact Melt
entity-overlay PNGs are active. The common `showGlintOnWhitesnake` config is
also registered with its original true default even though the legacy
renderer never consumed it; only its unregistered synchronization packet
helpers remain unreachable. The registered empty mixin declaration is
retained as a Java 21 no-op config through the JAR `MixinConfigs` manifest.

The registered `whitesnake_barrage` event and exact deleted
`whitesnake_barrage_sound.ogg` are restored from its deletion-parent commit.
The referenced `pucci_punch2.ogg` is likewise restored byte-for-byte from its
deletion-parent commit. The OST registry object, sounds.json reference, and
Stand `.ost` hook are active. Two references remain source blocked:
`whitesnake_ost.ogg` and the combo event's first
`stand_user/whitesnake_punch_combo.ogg` variant were never published in the
official repository history or release JARs. No replacement audio was
fabricated.

Placeholder custom abilities, PostChain/framebuffer work, and the orphan
`pucci_punch1.ogg` remain excluded from active runtime paths. The active Melt
overlay uses the public first-person post-arm layer API and the normal living
render event; it does not require a PlayerRenderer mixin.

The `manual_control` icon reuses the legacy Whitesnake Stand icon because the
legacy archive contains no dedicated manual-control icon.

Exact bytes for the orphan `pucci_punch1.ogg`, unregistered `disguise_as`
icon, duplicate `_whitesnake.png`, and donor `fmcui_bg.png` are retained under
`assets/rotp_whitesnake/legacy_archive/source/`. The donor atlas is also
restored byte-for-byte at
`assets/rotp_whitesnake/textures/gui/fmcui_bg.png`; the current searchable,
scrolling disguise screen uses its original base and hover button states.
Earlier combo, barrage-cry,
and predecessor disc-removal clips removed by explicit sound redesigns are
retained under commit-keyed history archives. No sound event, action,
renderer, model, skin descriptor, resource enumeration, or production code
references the inert archive paths. Both built artifacts package the locked
latest-donor and historical-recovery evidence.
