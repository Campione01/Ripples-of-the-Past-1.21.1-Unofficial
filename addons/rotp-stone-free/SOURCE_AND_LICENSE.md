# RotP Stone Free (`rotp_stone_free`): source and license

## Public release

- Module: `rotp-stone-free`, mod id `rotp_stone_free`, version `1.1.0c-port.40`.
- Original 1.16.5 add-on: `RotP-Extra-Stands-1.1.0c.jar` (RotP Extra Stands, mod id `rotp_extra_dg`), by DanielGamer321, StandoByte; declared license: GNU GPL v3.0; SHA-256 `0D0202912D14B2AB50CD5353275ACD6571FFFF0916807F8DDB1EF2928C1E3672`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Stone Free source and license

This module is the Stone Free-owned split and full reachable migration to
NeoForge 1.21.1 from:

`RotP-Extra-Stands-1.1.0c.jar`

Source JAR SHA-256:

`0D0202912D14B2AB50CD5353275ACD6571FFFF0916807F8DDB1EF2928C1E3672`

The canonical Java audit used the CFR 0.152 decompile at:

`017_RotP-Extra-Stands-1.1.0c`

The original mod declares GNU GPL v3.0 and credits DanielGamer321 and
StandoByte as authors. The selected Stone Free model, textures, sounds,
translations, and gameplay behavior remain under that upstream license.

The donor combo rows are restored as an active Stone Free-owned resource using
the current `Ability.name()` identifiers. All 38 Stone Free-owned Venezuelan
Spanish entries are restored under `assets/rotp_stone_free/lang/es_ve.json`.
`migration/manifests/historical-resource-restoration.json` records their
donor hashes, current hashes, counts, and core pin.

Only Stone Free-owned classes and assets are included. The Hand, Aqua
Necklace, Kraft Work, and their registry and resource owners remain in their
separate modules. Legacy shared capability, packet, and PlayerRenderer layer
owners are replaced here with module-local NeoForge attachments, automatic
attachment synchronization, and `RenderLivingEvent.Post` overlays.
