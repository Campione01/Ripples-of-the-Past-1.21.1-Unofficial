# RotP - Diego's The World (`rotp_theworld`): source and license

## Public release

- Module: `rotp-diegos-the-world`, mod id `rotp_theworld`, version `1.2.1-mc1.21.1-phase11`.
- Original 1.16.5 add-on: `RotP-The-World-Alternative-Universe-1.2.1.jar` (RotP THE WORLD, mod id `rotp_theworld`), by Solkree, August_dr feat.Pr1tcha, StandoByte; declared license: GNU GPL v3.0; SHA-256 `17D2C9DDE44928BEA726FBCD883731C74C336D2BD899DC6DF52D114160719B52`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Diego's The World source and license

This NeoForge 1.21.1 migration is derived from the replacement donor:

- File: `RotP-The-World-Alternative-Universe-1.2.1.jar`
- Size: `1244424` bytes
- SHA-256: `17D2C9DDE44928BEA726FBCD883731C74C336D2BD899DC6DF52D114160719B52`
- Manifest implementation version: `1.2.1`
- Mod id: `rotp_theworld`
- Authors: `Solkree, August_dr feat.Pr1tcha, StandoByte`
- License declaration: `GNU GPL v3.0`

CFR 0.152 reconstructed 30 top-level Java source files from the donor's
33 class entries. The closure inventory maps every donor source owner to a
current implementation, a current ROTP core facility, or a current resource
surface. No donor Java source owner is deferred or dead.

All 43 donor OGG files and all 39 donor PNG files are packaged byte-for-byte.
Only resource paths were adapted where the current skin loader requires
`skin_a`, `skin_b`, and `skin_c`. The donor has no packaged logo despite its
legacy metadata reference, so the current metadata intentionally has no logo.

The H1 geometry and animation resources are retained from the existing
1.21.1 conversion of the same donor model source. Their geometry and animation
hashes are `B2B96017455F8EF5D05047E16D10D7FA951D058676E91B7DC51F860AA3D16667`
and `4EA30BA8CA1F425816DB6DEB352827834C865BD05D0F5137ADAAE976E4E392F1`.
The idle clip carries the donor renderer's idle sway on `stand_pos` with the
core's `idleSway = legacy` mark, in place of the conversion's body bob.
Time-stop shaders and sound substitutions remain skin-scoped and use the
current ROTP pipeline; the module does not hook Iris or Super Resolution.

The donor's knife barrage, time-stop punch, barbed-wire trap, bleeding effect,
time-stop lifecycle voices, overlap immobilization, vehicle continuity, and
client barrage-voice filtering are represented through current NeoForge and
ROTP APIs. The donor's obsolete custom knife entity surface is replaced by the
current core `KnifeEntity`.

The donor references audio that it does not contain for `the_world_muda` and
the OST family. Those registrations remain explicit empty sound definitions;
no substitute copyrighted audio was invented.

The migrated source code is distributed under GNU GPL v3.0. Reused assets
remain subject to the donor package's GPL v3.0 declaration and applicable
third-party rights.
