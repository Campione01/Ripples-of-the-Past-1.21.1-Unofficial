# RotP Aqua Necklace (`rotp_aqua_necklace`): source and license

## Public release

- Module: `rotp-aqua-necklace`, mod id `rotp_aqua_necklace`, version `1.1.0c-port.28`.
- Original 1.16.5 add-on: `RotP-Extra-Stands-1.1.0c.jar` (RotP Extra Stands, mod id `rotp_extra_dg`), by DanielGamer321, StandoByte; declared license: GNU GPL v3.0; SHA-256 `0D0202912D14B2AB50CD5353275ACD6571FFFF0916807F8DDB1EF2928C1E3672`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module migrates only logical unit 25c, Aqua Necklace, from mixed
inventory row 25, **RotP Extra Stands 1.1.0c**.

- Canonical artifact:
  `RotP-Extra-Stands-1.1.0c.jar`
- Canonical size: `1,532,026` bytes
- Canonical SHA-256:
  `0D0202912D14B2AB50CD5353275ACD6571FFFF0916807F8DDB1EF2928C1E3672`
- Canonical decompile: `018_RotP-Extra-Stands-1.1.0c`
- Byte-identical mirror: `017_RotP-Extra-Stands-1.1.0c`
- Authors: DanielGamer321 and StandoByte
- Declared license: GNU General Public License v3.0

The physical donor also contains Stone Free, The Hand, Kraft Work, and shared
framework code. This module registers none of those logical units.
Compatibility uses public registry, action, entity, and damage ids only.

The official repository history through commit
`5c6b422db67e3106993a4ac082440797b81b0b8f` was reviewed. All 49 commits,
including deleted-path history, contain no Aqua-owned deletion. The latest
server-safe targeting, skin-aware marker, liquid splash, and fire-extinguish
fixes are present. `migration/manifests/historical-recovery.json` records the
history audit and restoration evidence.

Aqua Necklace keeps the H4 remote material-state and possession template with
nine wheel actions and no direct H1 binding. The `host_pressure` slot is a
core light punch with the donor icon/name outside possession and Internal
Pressure inside possession. General host heavy, brain finisher, Fluid Guard,
and the current grab/charged-heavy exclusions are unchanged.

The donor combo rows are restored as an active module-owned JSON resource.
Their step and block identifiers are translated to the current
`host_pressure` and `fluid_guard` `Ability.name()` values. The donor Spanish
locale contains no Aqua Necklace-owned keys, so this split deliberately adds
no `es_ve.json`.

The Stand remains under the core Stand renderer and skin system. Canonical
state, glow, action, outside-punch, and marker PNG bytes are preserved where
available. The 128x128 logical UV geometry has 27 bones and 24 cubes. The
skin-scoped animation restores donor summon, solid/liquid idle, and
conditional vapor idle poses. The marker resolves through the active skin and
the core visual-context alpha gate. No global renderer, shader, Iris,
SuperResolution, framebuffer, or post-chain hook was added.

Liquid collision filtering is entity-local and limited to doors, trapdoors,
fences, fence gates, iron bars, end rods, and chains. Other block shapes,
entity collisions, and the world border remain collidable.

The legacy OST registry and Stand hook are active as seven current
`SoundEvent` ids. One source-binary blocker remains: neither the donor JAR nor
any official commit supplies Aqua `sounds.json` entries or OGG media: the
audited sources contain zero Aqua OGG binaries. No media was fabricated or
substituted.

`migration/manifests/full-closure.json` is the generated hashed Java,
resource, registration, art, owner, and blocker ledger.
`migration/manifests/historical-resource-restoration.json` records the combo
split and the zero-key Spanish locale ownership decision.
`migration/manual-runtime-matrix.json` retains 93 `not_run` live cases.
Static verification does not claim live runtime proof.
