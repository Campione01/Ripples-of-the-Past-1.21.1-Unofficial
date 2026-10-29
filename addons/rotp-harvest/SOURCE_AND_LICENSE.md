# RotP Harvest (`rotp_harvest`): source and license

## Public release

- Module: `rotp-harvest`, mod id `rotp_harvest`, version `1.0.1-1-port.13`.
- Original 1.16.5 add-on: `RotP-收成者-1.0.1-1-250321.jar` (RotP-Harvest, mod id `rotp_harvest`), by Weever; declared license: GNU GPL v3.0; SHA-256 `B9A1E4A47348D8055E7F2CCFA7EBF3E0AB0DE137209500F1EC379AD21639E5B8`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Harvest source, scope, and license

### Canonical source

- Artifact: `RotP-收成者-1.0.1-1-250321.jar`
- SHA-256: `B9A1E4A47348D8055E7F2CCFA7EBF3E0AB0DE137209500F1EC379AD21639E5B8`
- Size: `334,274` bytes
- Canonical decompile: `054_RotP-收成者-1.0.1-1-250321`
- Decompiler: CFR 0.152
- Canonical inventory: 68 class files, 135 non-class resources, and 49 decompiled Java files
- Legacy mod id: `rotp_harvest`
- Legacy metadata version: `1.0.1-1`
- Legacy manifest implementation version: `1.0.1-1-250321`
- Legacy license declaration: GNU GPL v3
- Legacy author: Weever
- Legacy credits: StandoByte, Makuta Zeml, Hello There, Kira Yoshikage
  (not an anime person), and the legacy beta testers

The canonical JAR is authoritative for registration, resource, topology, and
runtime behavior. Decompiled source is supporting evidence. Every canonical
class and non-class file has an explicit disposition in
`META-INF/migration/source-disposition.json` and
`META-INF/migration/resource-disposition.json`.

### Reachable H4 closure

This port implements the reachable complex remote swarm as H4:

- one invisible, owner-bound `rotp_harvest:harvest_stand` coordinator;
- exactly 27 stable-slot, visible, nonhumanoid `rotp_harvest:harvest` members;
- the registered starting abilities on the default C/X special wheel, including
  `carry_up`; the order and input methods are defined by
  HarvestAddon.createStand;
  no
  fabricated humanoid LMB/RMB, charged heavy, grab, manual control, or leap;
- explicit targets, bounded owner retaliation and hostile acquisition,
  follow, go-to, stay-with target mirroring, close formation, climbing,
  combat selection, resolve gain, and recall on target death;
- bounded item search, carried-item rendering, and tagged item return drops;
- four-use potion transfer, bounded payloads, potion-stab effects, removal,
  effect immunity, and synchronized effect-color glow;
- 300-stamina member replacement after 20 ticks, stable-slot missing-member
  recovery, lifecycle cleanup, and owner-local reversible member camera;
- all eight skin choices, all three member silhouettes, variant-correct
  held-item transforms, animation, and a genuine 256x256 model/UV/material
  remaster with new model-consumed UV regions.

All entity resolution is direct and bounded. The port does not force-load
chunks, scan every loaded entity, or retain player-global legacy capability
state. The donor registered `carry_up` without a default hotbar entry. This port
includes it as a starting ability on the default hotbar through
HarvestAddon.createStand.
Its two-tick HOLD lifecycle
selects a random owned member within five blocks, toggles player mounting and
forced swimming pose, exposes owner steering at speed 2.0 without a sitting
pose, and makes eligible non-selected members approach or teleport to the
owner. Coordinator UUID/entity-id and member carry state replace the legacy
player-global capability packet.

The canonical JAR contains potion payload mechanics but no standalone alcohol
mechanic or alcohol registration. This port does not invent one.

Each go-to order ends its travel phase once a member is within the canonical
arrival threshold of the shared point. Members remain stationary until the
owner leash applies; completion does not switch them to close formation.
Reissuing a point starts travel again. Replacement members do not inherit an
already-issued travel order. The member's pending-travel flag is saved with its
existing order state; older go-to records without that flag resume as pending.

### Canonical resource recovery

Eight canonical PostChain resources form one active A8 cluster: the default
`harvest.json` plus the blue, purple, rabbit, red, Ryomen Sukuna, Sukuna
Itadori, and Sukuna Megumi skin routes. They are restored byte-for-byte at
their legal original owner paths.
`META-INF/migration/active-resource-cluster.json` records each donor path,
target, byte count, SHA-256, no-transform decision, runtime route, skin, and
activation predicate. All eight donors are validated before any active path
is written.

The cluster is registered only through core `AddonPostEffect` and becomes
active only when the local player's live Harvest coordinator identifies the
server-selected member, that member is owned by the local player, remains
bound to the coordinator and in range, and is the active camera. The member's
synchronized skin selects the route. SHIFT, invalid selection, camera
cleanup, death, and world cleanup disable the effect.

The eight canonical `carry_up` icons are restored byte-for-byte under the
current `textures/ability` convention for the default and seven skin routes.
The remaining 15 unreachable canonical resources are packaged byte-for-byte
under module-owned `legacy_archive` resource paths.
`META-INF/migration/legacy-archive.json` records the canonical source path,
archive path, byte count, and SHA-256 for every inert copy.

These files are preservation payloads only. Production Java, active resource
descriptors, registries, and mixin metadata do not reference or activate the
archive. In particular, the legacy root `mixins.rotp_harvest.json` is stored
only as
`assets/rotp_harvest/legacy_archive/root/mixins.rotp_harvest.json`; no
descriptor is installed at the JAR root. Archiving does not change the orphan
classification.

### Visual boundary

Visual behavior uses only current core surfaces:
`StandVisualContext`, the Stand material/alpha buffer path, Stand skin
resources, `EntityGlowChannel`, camera entity selection, and the declarative
`AddonPostEffect` route set, plus the bounded NeoForge `RenderHandEvent` and
`CalculatePlayerTurnEvent` client surfaces. No direct `GameRenderer`,
`PlayerRenderer`, Iris, Super Resolution, framebuffer, shader-pack, persistent
global option mutation, or addon mixin is installed.

Super Resolution integration is operator-owned rather than code-owned. Every
declared route must be listed under the operator
`inject_post_chain_blacklist`; the exact eight values are recorded in
`META-INF/migration/active-resource-cluster.json`.

The donor-reachable first-person hand and mouse-turn behavior is restored under
the same validated control-camera predicate. While the local player's live
coordinator owns the server-selected member and that member remains the active
camera, `RenderHandEvent` is canceled. When the player is not stunned,
`CalculatePlayerTurnEvent` receives the raw `-1/3` sensitivity value, producing
zero mouse turn without writing or later restoring `Options.sensitivity`.
Non-Harvest cameras, stun, SHIFT exit, invalid selection, member death, player
death, and level cleanup do not receive the turn override.

Static restoration still does not claim live visual parity.
`releaseComplete` remains `false` until the manual runtime matrix is executed.

### Verification state

The static contract validates the canonical lock, full disposition counts,
registrations, H4 input/state bounds, active A8 donor/source/runtime/sources
equality, inert-archive donor equality and legal path mapping, 256x256 art/UV
mapping, compiled bounded hand/input handlers, prohibited persistent/direct
hook absence, evidence consistency, and build artifacts. The 15-case live
client/server matrix remains entirely `not_run`; static verification does not
convert any live case to passed.

### License

This derivative port and the copied or remastered canonical resources are
distributed under GNU GPL v3.0 only (`GPL-3.0-only`). No warranty is provided.
