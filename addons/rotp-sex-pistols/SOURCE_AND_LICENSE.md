# RotP: Sex Pistols (`rotp_sex_pistols`): source and license

## Public release

- Module: `rotp-sex-pistols`, mod id `rotp_sex_pistols`, version `0.1.11-port.18`.
- Original 1.16.5 add-on: `RotP-Next-Album-0.1.11.jar` (RotP:NextAlbum, mod id `rotp_nextalbum`), by Babelmoth; declared license: GNU GPL v3.0; SHA-256 `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a NeoForge 1.21.1 migration of the Sex Pistols, revolver, bullet,
and Mista hat logical unit from `RotP-Next-Album-0.1.11.jar`.

The canonical legacy artifact identifies Babelmoth as author and StandoByte in
its credits. Its metadata declares GNU General Public License version 3. This
module therefore uses `GPL-3.0-only`.

The migration preserves canonical art except for the maintained revolver
replacement described below, and all 28 shipped sound files where
their ownership is within this logical unit. The legacy OST directories are
empty and the registered `sex_pistols_hungry_2` file is absent from the
canonical JAR, so neither can be redistributed. The Java implementation was
adapted to the current Ripples of the Past API and is not a verbatim
decompile.

### Maintained revolver model

The current revolver geometry and 64x64 pixel atlas were rebuilt in Blockbench
on 2026-09-14. They replace the migrated gun art, not its firing or ammunition
logic. The editable project is in `modeling/revolver/revolver.bbmodel`.
The model retains the `bone4` and `magazine` animation interface and is
distributed under this module's GPL-3.0-only license.

Visual references were the official Golden Wind Mista character artwork and
an anime frame. Those reference images are not included in the mod. Existing
original-author credits and licenses above remain applicable to the inherited
code, sounds and other assets.
