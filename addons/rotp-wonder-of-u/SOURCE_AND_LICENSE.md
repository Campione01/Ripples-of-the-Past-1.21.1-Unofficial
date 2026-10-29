# RotP Wonder of U (`rotp_wou`): source and license

## Public release

- Module: `rotp-wonder-of-u`, mod id `rotp_wou`, version `1.0.2c-full-closure.7-mc1.21.1`.
- Original 1.16.5 add-on: `RotP-奇迹于你-1.0.2c.jar` (RotP 奇迹于你, mod id `rotp_wou`), by FilipeGamer12BR, Co-creator: Hoo, Model: Blonde; declared license: GNU GPL v3.0; SHA-256 `A05CED8AB554CA6A8DCB4CC9AF211755AC3EEE0F7AF0932F2D84A9AB1486E7C0`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is the full reachable NeoForge 1.21.1 closure of **RotP Wonder
of U 1.0.2c**.

- Canonical JAR size: `5420493` bytes
- Canonical SHA-256:
  `A05CED8AB554CA6A8DCB4CC9AF211755AC3EEE0F7AF0932F2D84A9AB1486E7C0`
- Reconciled decompile authorities: `043`, `045`, and `046`
- Reconciled source-tree SHA-256:
  `EA78A381B04B876002A164E095413F0BB67B74FBB2736648D7C72A8C541CDC64`
- Original author: FilipeGamer12BR
- Original co-creator: Hoo
- Original model credit: Blonde
- License: GNU General Public License v3.0

The canonical JAR contains 18 classes and 77 non-class resources. The three
decompile mirrors contain the same 18 source paths and byte-identical files.
The complete entry, class, resource, registry, behavior, state, effect-owner,
blocker, and runtime dispositions are packaged under
`META-INF/rotp_wou/migration`.

The port keeps the locked `limited-humanoid-no-barrage` profile: LMB click
punch, RMB click heavy, shared-context held charged heavy, and RMB held guard
in both control builders. The special wheel contains only the five canonical
calamity actions. No barrage or fabricated grab chain is exposed.

Passive and active calamity retain their source costs, toggles, pursuit
reaction, projectile interception, damage reduction, reflection, effects, and
per-tick drains. Car calamity retains its independent toggle, attack
interception, retargeting, 120-tick spawn gate, falling car, radius-6
collision explosion, particles, and hurt sound. Dedicated damage types,
relationship checks, a bounded car lifetime, game-time clocks, and fail-closed
hazard authority validation replace unsafe or wall-clock legacy behavior.

As in the 1.16.5 source, a car that is already in the world is independent of
its caster: it still lands and explodes after the caster dies, logs out,
changes dimension or power, unsummons the Stand, switches car mode off, or the
target is lost. Only the 100-tick lifetime remains as port hardening against a
car that never lands. While the caster entity can still be found the blast
keeps the port's relationship filter; afterwards it is the car's own blast.

Stand mode flags are synced and intentionally nonpersistent because the core
Stand manifestation is `noSave`. Car identity is synced and saved. TNT and
creeper hazards carry saved owner, Stand, dimension, and expiry authority
tags; join/pre-tick validation and Stand cleanup cover relog, death, power
swap, dimension change, and chunk reload without a global map or custom
packet.

The configured `manga_2` skin and both canonical OST tracks are reachable and
ported. The complete residual set is also retained byte-for-byte under the
legal owner-scoped `assets/rotp_wou/legacy_archive` path without runtime
registration or production Java references. It contains:

- `B30`: thirteen `*_old` or otherwise dormant assets, thirteen files from
  the unregistered `manga_1` skin, two barrage icons with no registered
  action, and two development BBModels;
- `M3`: the original `META-INF/MANIFEST.MF`, `META-INF/mods.toml`, and
  `pack.mcmeta`, path-normalized below `legacy_archive/root` without changing
  their bytes; and
- `P3`: the three previously retained development Java exports.

The archive therefore contains 36 files and 1,388,873 canonical bytes while
the active surface remains 55 files. The recovery command is
`migration/restore-legacy-resources.ps1`; exact source/target hashes and
dispositions are recorded in `legacy-resource-retention.json`,
`resource-provenance.json`, and `legacy-resource-closure.json`. `manga_1`,
`*_old`, both barrage icons, `calamity_old`, and the development models remain
inactive. The empty legacy `summon_voiceline` registry ID is retained without
fabricated media.

The H1 Stand uses the canonical 103-element model, 512x512 authored textures,
128-grid UVs at 4x raster density, twelve current animation routes, and ten
action icons per reachable skin. As the 1.16.5 plain mob renderer did, the car
is drawn opaque for every viewer with its model's cutout material; the
caster's Stand and the viewer's Stand transparency settings do not reach it.
It sits from its feet up inside its hitbox. Like the donor mob it flashes red
when struck, and a destroyed car tips over about its feet for vanilla's 20
death ticks before it is removed, without exploding. As the donor animal, a
car killed by a player drops 1 to 3 experience.
There is no Player renderer, Iris, framebuffer, global shader, or post-chain
hook.

Static verification does not claim interactive client, dedicated-server, or
shader-stack runtime acceptance; those rows remain `not_run` in the packaged
runtime matrix.
