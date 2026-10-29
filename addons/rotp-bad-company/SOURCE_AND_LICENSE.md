# RotP Bad Company (`rotp_zbc`): source and license

## Public release

- Module: `rotp-bad-company`, mod id `rotp_zbc`, version `1.0.8-port.18`.
- Original 1.16.5 add-on: `RotP-极恶中队-1.0.8.jar` (RotP 极恶中队, mod id `rotp_zbc`), by Makuta Zeml, StandoByte; declared license: GNU GPL v3.0; SHA-256 `27FCCC637B082095581F20CAD8652F54718B0DE7EC859F42CC7DFC9E219F81D6`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Bad Company source and license

### Canonical source

- Artifact: `RotP-极恶中队-1.0.8.jar`
- SHA-256: `27FCCC637B082095581F20CAD8652F54718B0DE7EC859F42CC7DFC9E219F81D6`
- Size: `3,761,208` bytes
- Canonical decompile: `055_RotP-极恶中队-1.0.8`
- Decompiler: CFR 0.152
- Audited decompile surface: 85 Java sources
- Legacy mod id: `rotp_zbc`
- Legacy metadata version: `1.0.8`
- Legacy manifest implementation version: `1.0.7` (recorded mismatch)
- Legacy license declaration: GNU GPL v3.0
- Legacy authors: Makuta Zeml and StandoByte

The canonical JAR is the authority for registrations, resources, and runtime
behavior. Decompiled source is supporting evidence and is not treated as an
independent upstream source release.

### Port scope

This H3 port implements the full reachable closure:

- the canonically invisible `bad_company` coordinator;
- ten soldiers, two resolve-gated tanks, and two resolve-gated helicopters;
- unit selection, summon toggles, random/circle/line formations, wait/resume,
  close, destination, target, clear-target, and held volley orders;
- bullets, capped mines, missiles, helicopter missile accounting and reload;
- the `bad_gun` item, `sat` effect, canonical audio surface, and OST;
- native 1.21 model layers for every visible member, projectile, mine, missile,
  and parachute.
- all six canonical alternate skin payloads and their current per-skin
  descriptors, in addition to the default skin.

All commands use the current C/X resource-driven special wheel. The canonical
unit and formation commands open their direct icon selectors and send bounded,
server-validated exact choices. No fabricated LMB/RMB humanoid basics or grab
chain were added. Resolve level 4 unlocks the core per-user manual-control
ability through a dedicated colony-coordinator opt-in, without the donor's
shared mutable flag. The configurable O binding controls the invisible
coordinator, not an individual soldier or the army's follow position. Its
selected-skin-aware canonical remote-camera overlay is bound to that control
session. The earlier control-review status is preserved in the
port record.
Versioned runtime reports identify the artifact and control cases exercised.

### Coordinator control policy

- Type: `COLONY`, with an explicit dedicated-coordinator capability.
- Stand stats: `effectiveRange` 10 blocks; `rangeMax` 10 blocks, the donor
  `InitStands` value, so the stats panel shows range rank C.
- Coordinator boundary: 60 blocks. Only the invisible coordinator's
  `getMaxRange` override returns 60, as donor `BadCompanyStandEntity`
  multiplies its range by 6; the O-key movement clamp reads that override.
  These are implementation references, not verified manga meters.
- `manualControl`: true; `distanceStrengthDecay`: false. With decay off, the
  donor's matching x6 on the effective range has no runtime effect.
- Round 71 had raised the Stand stats to 60/60, which changed the panel's
  range rank from C to A and no in-game distance; port.11 restores 10/10.
- Movement requires the active owner/coordinator identity, manual-control
  state, and that owner's current controller target. Initial toggle establishes
  the flag and controller before movement authorization is consulted.
- Server ticks reuse the manual ability's production availability check. Leaving
  creative or losing temporary Resolve eligibility releases this coordinator's
  camera even when the O-key ability has become unavailable.
- Removal releases only this coordinator's control through the existing core
  off path. Unit orders, formations, ammo, and independent projectile damage
  and ranges are unchanged.

### Closure ownership

Go-to orders target the space above the selected block: one block for soldiers
and tanks, three for helicopters. Each unit completes its own order within one
block of that point. Wait remains enabled during travel and takes effect on
arrival; reissuing the same destination starts a new order. Ordinary formation
height and unsummon cleanup remain separate from this destination logic.

The coordinator owns all member, mine, projectile, formation, selection,
target, and command state. Closure entities carry synced owner/coordinator
identity. Command targets use UUIDs rather than reusable runtime entity IDs,
and retain the canonical 50-block visible or 25-block occluded capture range.
Reconciliation uses known UUIDs and bounded local queries, never
force-loads chunks, and performs explicit cleanup on unsummon, removal, owner
death, logout, dimension change, stale closure, lifetime, and detonation.

### Resource and rendering provenance

Canonical PNG, OGG, and model JSON resources are byte-for-byte extractions
from the canonical JAR. Ability and Stand icons are duplicated into current
resource-driven skin paths without resampling or H1 upscaling. Localization
keys are migrated to the current namespaced ability format; the previously
corrupted Chinese JSON was restored as valid UTF-8.

Every visible H3 surface resolves the coordinator's `StandVisualContext`, uses
`AlphaMultiBufferSource`, and requests the core Stand material. The module
also resolves its entity texture through the coordinator's selected
`StandSkin`. It adds no global living/player renderer replacement, mixin,
framebuffer hook, shader hook, or Iris hook.

### License

This derivative port and the copied canonical resources are distributed under
GNU GPL v3.0 only (`GPL-3.0-only`). No warranty is provided.
