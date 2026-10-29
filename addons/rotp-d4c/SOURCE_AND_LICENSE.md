# RotP D4C - Dirty Deeds Done Dirt Cheap (`rotp_d4c`): source and license

## Public release

- Module: `rotp-d4c`, mod id `rotp_d4c`, version `0.8.65-port.30`.
- Original 1.16.5 add-on: `RotP-d4c-0.8.65-Alpha.jar` (RotP D4C - 恶行易施, mod id `rotp_d4c`), by Core:LL,浪浪,ResSupport:Denis; declared license: LGPL3.0-only; SHA-256 `76F58BF70F15262697992DB25E9A60D5AC4FED690827A0683FCC7E84A770063C`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: LGPL-3.0-only (see `LICENSE` and `LICENSE-GPL-3.0.txt`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## D4C source and license record

Canonical input:

`../rotp-addons-1.16.5/rotp/RotP-d4c-0.8.65-Alpha.jar`

The path label above is an ASCII description of the sibling legacy workspace.
The build resolves the real sibling path by replacing the current workspace
version suffix, so no copied or alternate donor is accepted.

Canonical identity:

- Bytes: `6101786`
- SHA-256:
  `76F58BF70F15262697992DB25E9A60D5AC4FED690827A0683FCC7E84A770063C`
- ZIP entries: `454`
- Class entries: `218`
- Non-class entries audited: `236`

The legacy metadata declares `LGPL3.0-only` and contains contributor credits.
It also contains a test-build redistribution notice that is not reconciled
with the declared SPDX-style license. This port preserves the declared LGPL
license and records the unresolved notice instead of broadening the asset
grant.

### Resource disposition

Every donor non-class entry has one exhaustive row in
`META-INF/migration/d4c-resource-closure.json`.

- `171` entries remain active at their original path with exact donor bytes.
- `33` entries remain active at legal modern paths with exact donor bytes.
- `1` invalid uppercase particle path is active at a lowercase legal path with
  exact donor bytes.
- `8` entries are active adaptations required by current data formats.
- `9` model, animation, or loot entries have an active repaired derivative and an
  exact donor snapshot in the owner archive.
- `14` incompatible, superseded, or unregistered entries are retained only as
  exact donor snapshots in the owner archive.

The single invalid donor path,
`assets/rotp_d4c/textures/particle/OLDwide_golden_light_pillar.png`, is not
deleted or moved into that archive. Its exact 678 bytes are packaged at the
legal active path
`assets/rotp_d4c/textures/particle/oldwide_golden_light_pillar.png`. Donor
class and JSON scans prove the backup name was unregistered and unreferenced;
the separately registered `wide_golden_light_pillar` particle continues to
use its distinct 2105-byte texture.

The exact owner archive contains `43` entries and `3918070` donor bytes under
`assets/rotp_d4c/legacy_archive/source/`. Some archived source rows also have
active exact or adapted targets; production Java never loads the archive
paths.

### Behavior disposition

The six donor mixins and the historical disabled/deleted/placeholder material
are audited separately in
`META-INF/migration/legacy-mixin-disposition.json` and
`META-INF/migration/historical-restoration-audit.json`. Both manifests are
regenerated from the hash-pinned donor JAR and packaged byte-for-byte in the
runtime and sources artifacts.

The module-owned core v3 veto restores Love Train protection for `CLEAR`,
`EXTRACT`, `FULL_RESET`, `INSERT`, and `REPLACE` transitions. Its contextual
transition and mutation preflights close the donor direct-give and replacement
branches, while contextual extraction also replaces the donor Whitesnake
memory-disc mixin after target normalization. Love Train Light Wall restores
the donor five-tick, 40-block yaw-axis water/lava clearing behavior.

The donor D4C Stand builder explicitly declares random weight `1.0`. The
current builder now preserves it with `.randomWeight(1.0D)` instead of
inheriting the newer core default of `2.0`. Its additive Stand Arrow tag remains
active with `replace: false` and exactly `rotp_d4c:d4c`; the generated
historical audit records the prior implicit-default downgrade as repaired.

Inactive retention is limited to evidenced private, unreachable, unregistered,
duplicate, development-only, or inert material. Automatic Devil's
Palm placement and the Saint Corpse X-ray, whose donor entries were private
Ctrl+R and U keys, are active as D4C wheel abilities; `textures/hud/d4c_sandstorm.png` has zero donor
text references while the active overlay uses four particle textures; and the
Curios `charm` tag has zero values and no membership effect.

### Active repairs

The current runtime restores the donor Portuguese and Russian translations,
which are retained key-for-key and extended with the port-era keys,
the original gold Curios slot texture, three omitted D4C skin variants, the
default donor animation set, all nine portal resource sets, the low-health
harm layer, the sandstorm overlay, player-profile rendering, the donor corpse
loot pool, Love Train destructive-transition protection, and Light Wall fluid
clearing.

Each skin model keeps its original cube geometry and texture bytes. The model
repair removes embedded authoring materials and animation payloads, then adds
the canonical renderer wrapper bones. The default external animation repair
retains all `20` named animations, maps legacy bone names to current canonical
bones, and removes only seven stale channels that target no bone in the
source-derived active model. Seven clips are added under the current core
ability IDs — six exact aliases of donor clips and the distinct
`heavy_charged` hold — for a `27`-clip active set. The exact original
animation JSON and each original model file remain in the owner archive.

The low-health `d4c_harm` material remains a health-driven second model layer,
not a selectable skin. Its 88-cube model and `25`-clip animation set, the 18
donor clips plus the same seven core-ability-ID clips, are repaired to
the current canonical bone hierarchy, while its texture remains exact donor
bytes. The nine portal blockstates, models, and shared translucent texture are
also active at their exact donor paths. The two donor vanilla loot overrides
are adapted to NeoForge add-table modifiers so vanilla tables are extended
rather than replaced.

Live client/server validation is still `not_run`. Pending checks cover restored
skins and animations, all portal destinations, low-health harm rendering,
profile skin rendering, corpse loot sampling, the Curios slot icon, and client
logs for missing resources.
