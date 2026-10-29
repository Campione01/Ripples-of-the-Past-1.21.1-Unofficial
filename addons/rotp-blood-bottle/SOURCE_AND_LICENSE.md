# Blood Bottle RotP Addon (`rotp_bb`): source and license

## Public release

- Module: `rotp-blood-bottle`, mod id `rotp_bb`, version `1.0.3.1-port.9`.
- Original 1.16.5 add-on: `RotP-血瓶-1.0.3.1.jar` (Blood Bottle RotP Addon, mod id `rotp_bb`), by dikiytechies; declared license: GNU GPL v3.0; SHA-256 `C548D72546C239CC248243F127BE32776EF67FF1713AC81B4D3F9D69ED5A1881`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Blood Bottle source and license record

### Canonical authority

- Legacy binary:
  `rotp附属1.16.5/rotp/RotP-血瓶-1.0.3.1.jar`
- Legacy binary size: `91502` bytes
- Legacy binary SHA-256:
  `C548D72546C239CC248243F127BE32776EF67FF1713AC81B4D3F9D69ED5A1881`
- Canonical decompile:
  `rotp附属反编译/064_RotP-血瓶-1.0.3.1`
- Corroborating mirrors:
  `065_RotP-血瓶-1.0.3.1` and
  `066_RotP-血瓶-1.0.3.1`
- The three CFR mirrors are byte-identical across six Java owners and
  `summary.txt`.

The canonical archive contains six reachable classes. All six owners are
accounted for by this module's registration, configuration, item, economy,
power-transition, and Vampirism ability implementations.

### Resource disposition

- `textures/item/bottle.png`, `models/item/blood_bottle.json`, and `logo.png`
  are retained byte-for-byte.
- The legacy action icon is retained byte-for-byte and moved from the core
  namespace to
  `assets/rotp_bb/textures/ability/vampirism_blood_refill.png`.
- The English, Spanish, and Russian language files are migrated to current
  item and ability translation keys.
- The empty `sounds.json` is omitted. Runtime audio uses vanilla drink and
  bottle-fill sounds, as in the legacy implementation.
- `pack.mcmeta` is omitted because NeoForge supplies current pack metadata.
- `assets/rotp_bb/for august_dr.txt` is not used as runtime content. Its exact
  bytes are retained at the legal archive path
  `assets/rotp_bb/legacy_archive/for_august_dr.txt`.
- The legacy archive contains no recipes or other data-pack entries, so this
  port does not invent any.

### Behavioral translation

The legacy config accepted unbounded integers while documenting recommended
ranges. This port enforces those documented ranges:

- `bottleDrainPenalty`: `0..100`
- `fillBottleMultiplier`: `1..1000`
- `maxBottleHungerValue`: `1..100`

The blood bottle item uses only public `PlayerPower` and public power-data
methods. It supports the four legacy core player powers: no power and Hamon
transition to Vampirism, while Vampirism, Zombie, and Pillarman receive their
native resource. Unknown addon player powers fail closed instead of being
mutated through private state. The legacy item only called `givePower`, so a
vampire made by drinking is not at full power, like one made by Blood Gift;
this port likewise leaves the full-power flag alone.

The refill ability is registered and fully implemented. Pinned core commit
`bc9502423e3ca5d131f614ab1fb012ddfe15ea54` exposes the public
`player_power_moveset_extensions_v1` contract, so the addon attaches the
ability as the legacy `SHIFT + HOLD` context variation of
`jojo_ripples:vampire_blood_gift`. It does not create a standalone hotbar slot
or mutate internal moveset state. Like the legacy action, which the 1.16 core
left locked for any vampire below full power, it keeps the core Vampirism
full-power gate. The former atomic blocker is recorded as
resolved in the packaged migration manifests.

### License and attribution

The legacy metadata declares `GNU GPL v3.0`, author `dikiytechies`, and credit
to `StandoByte`. This migration preserves that license and attribution.
