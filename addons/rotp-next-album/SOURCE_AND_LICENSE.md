# RotP:NextAlbum (`rotp_nextalbum`): source and license

## Public release

- Module: `rotp-next-album`, mod id `rotp_nextalbum`, version `0.1.11-port.7`.
- Original 1.16.5 add-on: `RotP-Next-Album-0.1.11.jar` (RotP:NextAlbum, mod id `rotp_nextalbum`), by Babelmoth; declared license: GNU GPL v3.0; SHA-256 `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-21); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and License

This module ports the bounded residual content surface from
`RotP-Next-Album-0.1.11.jar`.

- Source artifact SHA-256:
  `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`
- Legacy metadata author: Babelmoth
- Legacy metadata credits: Babelmoth, StandoByte
- Legacy metadata license: GNU GPL v3.0
- Port metadata SPDX license identifier: `GPL-3.0-only`

The active residual registrations are `collapse`, `luck_pluck`,
`mista_suit_cloth`, `mista_suit_pants`, `mista_suit_boots`, and
`aja_stone_necklace`. Canonical source evidence remains under
`migration/baseline-0.1.11`; the strict active owner and resource decisions
are recorded in `migration/manifests/residual-owner-disposition.json` and
`migration/manifests/residual-resource-disposition.json`.

The two legacy root images, five legacy root metadata files, and dead unwired
full Silver Chariot Requiem geometry are preserved byte-for-byte under
`assets/rotp_nextalbum/legacy_archive`. The exact donor `logo.png` is also
active at the module JAR root because NeoForge metadata selects it through
`logoFile="logo.png"`; its contract uses the explicit
`module_resource_root` target scope. The other seven entries remain inert
source evidence and do not replace the active NeoForge metadata, mixin
configuration, resource-pack metadata, runtime resources, or standalone
Stand owner. Their source and target hashes are recorded in the current
retention and provenance manifests. Historical SCR phase evidence retains its
original
`dead_unwired_not_packaged` wording and is superseded only for current
retention by this exact archive contract.

Sex Pistols, its firearm content, Mista's hat, and the Joyful effect remain
owned by `rotp_sex_pistols`. Silver Chariot Requiem remains owned by
`rotp_silver_chariot_requiem`. The canonical
`rotp_nextalbum:plant_aging_dust` registration and resources are owned only
by `rotp_grateful_dead`; this module does not package a duplicate.

The donor `rotp_nextalbum:sex_pistols_ost` `OstSoundList` was attached to the
legacy `rotp_nextalbum:sex_pistols` Stand and generated seven suffixed
SoundEvents. Those IDs remain historical here under a formal replacement:
the Stand, firearm, sounds, resources, and lifecycle moved together to
`rotp_sex_pistols`, whose current Stand ID is
`rotp_sex_pistols:sex_pistols`. This residual module has no Stand or sound
registry, so recreating the old OST namespace locally would be a detached
duplicate. The canonical JAR also contains no OST OGG, and no media is
fabricated.

The port uses Curios 9.5.1+1.21.1 under LGPL-3.0-or-later and GeckoLib 4.9.2
under the MIT License. Their exact dependency hashes and source coordinates
are recorded in `migration/manifests/dependency-lock.json`. The GeckoLib
runtime JAR is used as an external build/runtime dependency and is not
embedded in this module's output JAR.

The module compiles against GeckoLib 4.9.2 but declares `[4.9.2,4.10)` at
runtime, so a 4.9.x patch release loads it. GeckoLib 4.9.3 (SHA-256
`20A1995E4074F387FF549E2C57EA79DD7D41EE83C6AFB9DD6A6E840E592D1C47`) was
compared class by class with 4.9.2: it removes no class, method, or field,
and none of the types this module calls (`GeoItem`, `GeoRenderProvider`,
`GeoArmorRenderer`, `GeoItemRenderer`, `GeoModel`, `GeoBone`,
`AnimationController`, `AnimatableManager`, `AnimatableInstanceCache`)
changed. In `GeckoLibUtil` and `RawAnimation`, which it also calls, only
private members and method bodies changed (thread safety, `equals`).
