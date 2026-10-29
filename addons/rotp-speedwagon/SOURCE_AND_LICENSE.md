# RotP Speedwagon (`rotp_sp`): source and license

## Public release

- Module: `rotp-speedwagon`, mod id `rotp_sp`, version `1.0.0-port.9`.
- Original 1.16.5 add-on: `RotP-S-1.0.jar` (RotP S, mod id `rotp_sp`), by DanielGamer321, StandoByte; declared license: GNU GPL v3.0; SHA-256 `D1F9C3C4E50813F9707C727D00305D0B5916D75CE921858C8853265465BE21B0`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Speedwagon source and license

### Identity

- Current mod id: `rotp_sp`
- Current module: `addons/rotp-speedwagon`
- Content category: `NON_STAND_CONTENT_OR_SYSTEM`
- Legacy artifact: `RotP-S-1.0.jar`
- Legacy artifact SHA-256: `D1F9C3C4E50813F9707C727D00305D0B5916D75CE921858C8853265465BE21B0`
- Canonical decompile: `030_RotP-S-1.0`
- Legacy decompile inventory tree SHA-256: `5B20CE7A53FA3A1AC405CAA0B8851D80BBD8F7DEE6138918C58514E583C13F6F`

`RotP-S-1.0.jar` is the Speedwagon non-Stand power/NPC/structure
module. The short artifact name does not identify a generic humanoid Stand.
This port intentionally registers no Stand type and no Stand entity.

### Upstream authors and license

The legacy `mods.toml` names DanielGamer321 and StandoByte and declares
GNU General Public License v3.0. This port remains licensed under
`GPL-3.0-only`; see the repository license.

The legacy NPC skin credits are preserved:

- Henchman: https://www.minecraftskins.com/skin/21877836/tattoo--jojo---s-bizarre-adventure-/
- Henchman 2: https://www.minecraftskins.com/skin/21874210/kempo-fighter--jojo---s-bizarre-adventure-/
- Instructor: https://www.minecraftskins.com/skin/21868618/robert-e--o--speedwagon--jojo---s-bizarre-adventure-/

### Binary provenance

The five action icons, Speedwagon power icon, three NPC skins, and instructor
house structure template are copied byte-for-byte from the locked legacy JAR.
`migration/generate-locked-resources.ps1` refuses to extract from any JAR
whose SHA-256 differs from the value above and verifies every copied resource.
Action and power icons move from the legacy core-owned `assets/jojo` namespace
to the module-owned `assets/rotp_sp` namespace required by the current
namespaced ability-resource contract. The structure template moves from the
plural 1.16 path to the singular 1.21.1 data-pack path.

The legacy module contains no OGG files and its sound register is empty.
This port registers no fabricated audio; the hat action uses the current ROTP
blade-hat throw sound and the remaining actions use current vanilla/ROTP
effects only where the legacy behavior did.

The donor's target Resolve multiplier remains `2`. The pinned core predates
the direct multiplier hook, so a module event supplies the second contribution
there. The power type also declares the newer same-signature hook, and the
event disables itself when that hook exists to avoid double application.

### Build dependency

The active core dependency is defined by the workspace build configuration.
The earlier core lock is preserved in the port record.

### Migration boundary

All production code, resources, validation, and migration evidence for this
port live in this module. Parent integration is declarative through
`migration/manifests/parent-integration.json`; the standalone module does not
edit root settings, global ledgers, smoke manifests, the core repository, or
other addons.
