# RotP 新月 (`rotp_cm`): source and license

## Public release

- Module: `rotp-c-moon`, mod id `rotp_cm`, version `1.4.3-port.17`.
- Original 1.16.5 add-on: `RotP-新月-1.4.3.jar` (RotP 新月, mod id `rotp_cm`), by Weever; declared license: GNU GPL v3.0; SHA-256 `2FE56D6E10234C855DE5D9A4ADA939EC656EF6A8C3B26F79010DFFE24ADD9E99`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP C-Moon source and license

This module migrates Weever's legacy `RotP-新月-1.4.3.jar` addon.
Its metadata declares `GNU GPL v3.0`; this migration packages the code
under `GPL-3.0-only`.

Locked source artifact:

- Physical inventory row: `63`
- Size: `590909` bytes
- SHA-256:
  `2FE56D6E10234C855DE5D9A4ADA939EC656EF6A8C3B26F79010DFFE24ADD9E99`
- Mod ID: `rotp_cm`
- Display name: `RotP 新月`
- Version: `1.4.3`
- Author: `Weever`

The selected decompile is `053_RotP-新月-1.4.3`. Mirrors `055` and
`056` contain the same 24 Java files byte for byte. The selected tree
contains 85025 Java bytes. All 32 physical class entries, 24 top-level
Java units, and 47 resources are disposed in
`migration/full-scope.json`; the exact 79-entry artifact inventory is
in `migration/physical-entry-inventory.json`.

The canonical 256x256 Stand texture is used byte for byte. Bedrock
geometry is deterministically regenerated from the locked 57-cuboid
`CMModel.java`. Its per-face UVs preserve the donor's mixed renderer
state: 37 cuboids attached to humanoid renderers created at 128x128 are
mapped at 2x onto the physical atlas, while 20 later arrow cuboids created
at 256x256 remain at 1x. The existing current-core animation library is
retained only as a clip-name compatibility layer.

The port repairs three legacy implementation defects without changing
the intended feature set: C-Moon's global static barrier state is now
per-Stand synchronized state; the cumulative global inversion-duration
leak is now a per-hit 45-tick barrier bonus; and amplifier zero cannot
divide the inversion tick cadence by zero. The obsolete custom config
packets were unreachable and are replaced by server-owned NeoForge
common config.

The donor-registered `rotp_cm:buy_my_patreon` compatibility ID remains
registered with the current core `TimeStopAbility`. It is intentionally
absent from the moveset and both control schemes, matching the donor
stand's zero-tick time-stop configuration and its lack of a hotbar
entry. No icon, language entry, sound, or other payload was present in
the locked donor artifact, so none is fabricated here.

The donor's internal `rotp_cm:cm_destroy_barrage` ID is also restored as
a hidden compatibility registration. It instantiates the existing
`DestroyAbility`, which only selects the target and delegates all movement,
barrage cadence, damage, sound, reward, and cleanup to the single
`CMoonStandEntity` pursuit loop. It is not added to either control scheme, so
the restored registry identity cannot execute a second attack loop.

For current-core interoperability, the canonical gravity-shift toggle
also owns a loaded-player gravity binding under `rotp_cm:gravity_shift`.
The same C-Moon entity instance performs bind and unbind operations, and
stale-owner, retract, removal, and Stand-replacement paths clear it. This
does not import another addon's registry IDs or action implementation.

No legacy ROTP core code is copied. The module consumes the pinned core
coordinate
`RipplesOfThePast-Unofficial-NeoForge:1.21.1-0.2.2.2-260128-a`,
runtime SHA-256
`43D49425BE55A3558B3C291020A94705B38F6764C9935477B061B4FC242436AA`,
and metadata range `[0.2.2.2]`.

The canonical missing `cm_heavy_punch.ogg` and
`cm_inversion_hit1/2.ogg` are not invented. Heavy impact uses the
current core fallback; the donor never registered an inversion-hit
`SoundEvent`, so its dangling `sounds.json` row is omitted. The canonical
purple custom-particle definitions and textures are active through
addon-owned registrations and the current public core Hamon factories.
