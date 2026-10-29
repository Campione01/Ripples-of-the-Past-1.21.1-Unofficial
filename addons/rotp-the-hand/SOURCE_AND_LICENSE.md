# RotP The Hand (`rotp_the_hand`): source and license

## Public release

- Module: `rotp-the-hand`, mod id `rotp_the_hand`, version `1.1.0c-port.22`.
- Original 1.16.5 add-on: `RotP-Extra-Stands-1.1.0c.jar` (RotP Extra Stands, mod id `rotp_extra_dg`), by DanielGamer321, StandoByte; declared license: GNU GPL v3.0; SHA-256 `0D0202912D14B2AB50CD5353275ACD6571FFFF0916807F8DDB1EF2928C1E3672`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a bounded NeoForge 1.21.1 split and port of The Hand from:

- `RotP-Extra-Stands-1.1.0c.jar`
- SHA-256:
  `0D0202912D14B2AB50CD5353275ACD6571FFFF0916807F8DDB1EF2928C1E3672`
- Original authors: DanielGamer321 and StandoByte
- License: GNU General Public License v3.0 only (`GPL-3.0-only`)

The source archive contains several Stands. This module contains only The Hand
code and resources plus published ROTP core dependencies. Stone Free, Aqua
Necklace, and Kraft Work implementations are explicitly excluded. The optional
Aqua Necklace vapor interaction uses only its public entity registry ID and
published `StandEntity` state, so the other addon is not a dependency.

The default and manga textures, glow maps, icons, and sounds are copied exactly
from the locked source JAR. The 1.21.1 Bedrock geometry is a mechanical
reconstruction of the source `TheHandModel` Java cubes and hierarchy. Shared
humanoid combat animations are copied from the pinned ROTP core animation
resource and merged with the source-specific The Hand poses.

The donor combo rows are restored as
`data/rotp_the_hand/stand_combos/base_combos.json` using current
`Ability.name()` values. Fourteen The Hand-owned Venezuelan Spanish entries
are restored at `assets/rotp_the_hand/lang/es_ve.json`; sibling Stand locale
entries remain excluded. The exact donor and current hashes are recorded in
`migration/manifests/historical-resource-restoration.json`.

The port isolates the source's temporary erased-health and healing-cap behavior
behind the addon-owned `rotp_the_hand:erased_health` attachment,
`rotp_the_hand:erased` effect, and `rotp_the_hand:erased_health_sync` payload.
It does not reuse the mixed archive's capability, packet manager, event owner,
or cross-Stand state.

The legacy registry references `rotp_extra_dg:the_hand_ost`, but the locked
source JAR contains no matching OGG or `sounds.json` entry. This module does not
register an empty or fabricated OST event.
