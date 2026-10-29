# RotP Purple Haze (`rotp_ph`): source and license

## Public release

- Module: `rotp-purple-haze`, mod id `rotp_ph`, version `1.0.4-port.17`.
- Original 1.16.5 add-on: `RotP-紫烟-1.0.4.jar` (RotP 紫烟, mod id `rotp_ph`), by Parmezannchik; declared license: GNU GPL v3.0; SHA-256 `2972A968A484F0F785EC78D8C9C6D7E15EE7FC23578E8F4043FD758DA2D3BE11`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-21); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a full NeoForge 1.21.1 legacy closure of **RotP Purple Haze
1.0.4**.

- Canonical source artifact: `RotP-紫烟-1.0.4.jar`
- Canonical source bytes: `550655`
- Canonical SHA-256: `2972A968A484F0F785EC78D8C9C6D7E15EE7FC23578E8F4043FD758DA2D3BE11`
- Canonical decompile: `060_RotP-紫烟-1.0.4`
- Duplicate verified decompiles: `061_RotP-紫烟-1.0.4`,
  `062_RotP-紫烟-1.0.4`
- Normalized decompile tree SHA-256:
  `A231F4E8E6D09E891CD809A5CE5F25A8913D471F1309EFF46D135AAEB030E173`
- Original author: Parmezannchik
- Original credits: StandoByte, hk47bit
- License: GNU General Public License v3.0

The archive inventory contains 28 classfiles (27 top-level classes), 38
registry ids, and 43 files under `assets/rotp_ph`. All 27 top-level class
responsibilities are dispositioned; all 43 namespaced resources are present,
with 39 retained byte-for-byte and four text resources ported. The source
contains no custom packet classes and no mixin classes or configurations.

The closure includes the Stand, all nine legacy action registrations, three
entity registrations, six authoritative capsule slots, virus effect and
particles, virus clouds and aura, viral finisher, madness targeting and
barrage, and the legacy summon and secondary idle poses. State, damage,
targeting, and cleanup are server authoritative. Stand-visible secondary
visuals inherit the effect owner's current core Stand visibility matrix.

Bounded compatibility repairs and the two missing source audio assets are
recorded in `migration/manifests/full-closure.json`; no replacement audio is
invented.

The donor `rotp_ph:purple_haze_ost` `OstSoundList` is registered and attached
to `rotp_ph:purple_haze`. Its seven generated SoundEvents are intentionally
silent: the canonical JAR supplies neither OST definitions nor OST OGG files,
so this migration restores the runtime relationship without inventing media.
