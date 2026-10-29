# RotP OVA The World (`rotp_ova_addon`): source and license

## Public release

- Module: `rotp-ova-the-world`, mod id `rotp_ova_addon`, version `1.0.0-port.14`.
- Original 1.16.5 add-on: `RotP-OVA附属-1.0.jar` (RotP OVA附属, mod id `rotp_ova_addon`), by Pr1tcha feat. August.dr, StandoByte; declared license: GNU GPL v3.0; SHA-256 `7EDAE43D54E6609A318FF9A928C68F72446B47C37DDF4EBE394C44FB9760E75C`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a bounded NeoForge 1.21.1 port of OVA The World from:

- `RotP-OVA附属-1.0.jar`
- Size: `1688260` bytes
- SHA-256:
  `7EDAE43D54E6609A318FF9A928C68F72446B47C37DDF4EBE394C44FB9760E75C`
- Original authors: Pr1tcha, August.dr, and StandoByte
- Declared code license: GNU General Public License v3.0 only
  (`GPL-3.0-only`)

The runtime mod ID remains `rotp_ova_addon`. The historical metadata string
`rotp_the_world_ova` was a malformed dependency owner, not a second mod ID.

The OVA texture, icons, and audio are copied exactly from the locked source
JAR. The primary texture is already 256 by 256 pixels. The 1.21.1 geometry is
a mechanical reconstruction of all 68 explicit cuboids, the original
`SlopeModelBox` as a native poly mesh, and the 14 inherited humanoid cuboids.
It preserves the source model's 128 by 128 logical UV layout, pivots,
inflation, mirroring, and hierarchy.

The missing legacy resume SFX is closed with the pinned current ROTP core
`the_world_time_resume.ogg`; its provenance and hash are recorded in
`migration/manifests/source-assets.json`. Missing legacy event definitions
for the ultra-punch windup and barrage hit use explicit event aliases to the
existing OVA heavy-punch SFX and the core light-punch SFX.

The source archive does not establish third-party character, voice, music, or
other media redistribution rights. This local port must not be published
until those rights are independently cleared. The legacy OST registry family
is retained for ID compatibility but is not attached to the Stand because the
locked source contains no OST audio or sound definitions.
