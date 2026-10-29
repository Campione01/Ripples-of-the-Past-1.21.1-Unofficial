# RotP Yellow Temperance (`rotp_yellowtemperance`): source and license

## Public release

- Module: `rotp-yellow-temperance`, mod id `rotp_yellowtemperance`, version `0.9.4b-port.18`.
- Original 1.16.5 add-on: `rotp_yellowtemperance-0.9.4b.jar` (Yellow Temperance Addon, mod id `rotp_yellowtemperance`), by TheChaYe; declared license: GNU GPL v3.0; SHA-256 `F1D39C5A28C77BFCB9E8BC6FF2D273135E2EA551CCA2AA469F9CC2A16876125D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Yellow Temperance source and license

This NeoForge 1.21.1 module is an evidence-backed port of Yellow
Temperance from the mixed legacy Yellow Temperance/Khnum archive.

Locked source artifact:

- File: `rotp_yellowtemperance-0.9.4b.jar`
- Audit path:
  `rotp_yellowtemperance-0.9.4b.jar`
- Size: `577422` bytes
- SHA-256:
  `F1D39C5A28C77BFCB9E8BC6FF2D273135E2EA551CCA2AA469F9CC2A16876125D`
- Legacy mod id: `rotp_yellowtemperance`
- Legacy version: `0.9.4b`
- Legacy author: TheChaYe
- Legacy license: GNU GPL v3.0
- Decompiler: CFR 0.152

Three readable decompile mirrors (`075`, `076`, and `077`) each contain 105
files and are byte-equivalent by relative file path. The JAR contains Khnum
as well. No Khnum class, registry entry, action, texture, translation, or
gameplay path is part of this module.

The source Yellow coating is a 64x64 player layer. The port replaces it with
two native 256x256 default/slim materials and dedicated player-space geometry.
The generator works per output texel and uses vector drawing for the doll; it
does not resize the legacy texture and contains no nearest-neighbor path. The
old coating texture and geo file remain under `migration/source-assets` as
audit inputs and are also retained byte-for-byte in the packaged
`assets/rotp_yellowtemperance/legacy_archive` tree. The source energy atlas is
copied byte-for-byte to runtime for the module-owned food HUD replacement.

The same archive tree retains 39 exact source entries: 32 reconstruction
inputs, three old UI images, two AppleSkin images, one malformed duplicate
texture, and the empty legacy mixin descriptor. The descriptor has no common,
client, or server mixin entries and is not registered in either current
module. `migration/manifests/donor-resource-restoration.json` maps the 38
formerly unresolved donor-audit rows to their fixed 1.21.1 runtime owners.
The three shared disguise UI images have one canonical legacy namespace owner
here, while Yellow Temperance and Khnum use independent owner-scoped,
resolution-aware screen implementations. Khnum-owned resources remain
isolated in the separate `rotp-khnum` module.

All copied runtime PNG and OGG files recorded in
`migration/manifests/source-assets.json` are byte-identical JAR entries. The
legacy `yt_punch` SoundEvent and seven `example_stand_ost_*` compatibility
IDs remain registered without runtime mappings because the archive contains
no matching streams. No replacement audio was fabricated.

Pehkui is an external runtime dependency, not shaded into this addon:

- Coordinate: `virtuoel:Pehkui:3.8.3+1.21-neoforge`
- JAR size: `507568` bytes
- JAR SHA-256:
  `50AAB835B0F98DEDF33383483B021789C2991786F02B00E6D1B7152DC43570EC`
- Upstream license: MIT
- Runtime version range: `[3.8.3,3.9)`

Yellow Temperance's canonical topology and Stand classification were checked
against `https://jojowiki.com/Yellow_Temperance`, which identifies it as a
close-range, materialized, natural non-humanoid Stand that coats its user.

The port is distributed under `GPL-3.0-only`, compatible with the source
archive's declared license. The copied Pehkui mirror remains MIT-licensed and
external.
