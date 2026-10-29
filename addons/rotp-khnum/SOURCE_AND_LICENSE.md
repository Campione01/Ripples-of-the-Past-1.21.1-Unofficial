# RotP Khnum (`rotp_khnum`): source and license

## Public release

- Module: `rotp-khnum`, mod id `rotp_khnum`, version `0.9.4b-port.8`.
- Original 1.16.5 add-on: `rotp_yellowtemperance-0.9.4b.jar` (Yellow Temperance Addon, mod id `rotp_yellowtemperance`), by TheChaYe; declared license: GNU GPL v3.0; SHA-256 `F1D39C5A28C77BFCB9E8BC6FF2D273135E2EA551CCA2AA469F9CC2A16876125D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Khnum source and license

This module is an isolated NeoForge 1.21.1 port of every Khnum-owned or shared
runtime surface reachable from the mixed source archive:

`rotp_yellowtemperance-0.9.4b.jar`

- Original mod id: `rotp_yellowtemperance`
- Original version: `0.9.4b`
- Original author: `TheChaYe`
- Original license: GNU General Public License v3.0
- Source archive bytes: `577422`
- Source archive SHA-256:
  `F1D39C5A28C77BFCB9E8BC6FF2D273135E2EA551CCA2AA469F9CC2A16876125D`

The source archive contains 104 decompiled top-level Java files and 64 asset
files. The closure audit classifies the Java census as 61 Khnum-owned or
shared reachable files, 33 Yellow Temperance-owned files, 8 unreachable dead
files, 1 other-owner file, and 1 genuine ABI exception. The resource census is
closed as 5 source-exact runtime glyphs, 2 migrated language files, 1
audit-only transparent body, 3 native-UI replacements, and 53 Yellow
Temperance-owned resources.

The shared donor-resource restoration ledger keeps
`rotp-yellow-temperance` as the canonical owner of the legacy namespace.
Khnum uses its independent owner-scoped `DisguiseSelectionScreen` instead of
referencing Yellow Temperance resources, and owns the transparent duplicate
body row because it is byte-identical to `Khnum.png`. Its active replacement
is the selected target's native player or entity visual.

Yellow Temperance combat, erosion, effects, Stand disguise, doll, food
integration, audio, and visuals are outside this module. AppleSkin therefore
is not a Khnum blocker. Signed-chat author substitution is the only reachable
ABI exception because Minecraft 1.21 cannot preserve signed-chat trust while
changing the message author.

The four 22x22 action icons and 8x8 power icon are byte-for-byte GPL source
copies used as native UI glyphs. The source 64x64 `Khnum.png` contains zero
visible texels. It remains in `migration/source-assets` and is also packaged
byte-for-byte at the legal owner-scoped archive path
`assets/rotp_khnum/legacy_archive/khnum_transparent.png`. It is not selected
as a runtime body texture. Runtime identity visuals use the selected target's
native player skin/model or entity renderer/layers; no body art is resampled.

Pehkui 3.8.3 is an external required dependency. Its exact JAR is mirrored
locally for offline resolution and is not shaded into this module's output.
