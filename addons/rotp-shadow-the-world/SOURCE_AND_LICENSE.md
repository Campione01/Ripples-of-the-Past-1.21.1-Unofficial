# RotP - Shadow The World (`rotp_shadowtheworld`): source and license

## Public release

- Module: `rotp-shadow-the-world`, mod id `rotp_shadowtheworld`, version `1.2.1-mc1.21.1-phase22`.
- Original 1.16.5 add-on: `RotP-影世界-1.2.0.jar` (RotP 影世界, mod id `rotp_shadowtheworld`), by LL,浪浪; declared license: GNU GPL v3.0; SHA-256 `F0ED011CB4D69010DE3FC33BA92A7ACD2C0CD9818B7175343448ED4385088886`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Shadow The World source and license

This 1.21.1 migration is derived from the unique Stage 0 artifact:

- File: `RotP-影世界-1.2.0.jar`
- Size: `1697753` bytes
- SHA-256: `F0ED011CB4D69010DE3FC33BA92A7ACD2C0CD9818B7175343448ED4385088886`
- Legacy mod id: `rotp_shadowtheworld`
- Legacy version: `1.2.0`
- Legacy license declaration: `GNU GPL v3.0`

The Java behavior was reconstructed from canonical CFR mirror
`046_RotP-影世界-1.2.0`. Mirrors `048` and `049` were independently
normalized and compared before choosing the canonical tree. All three
contain 50 Java files and 229,059 normalized bytes, with normalized tree
SHA-256
`2802E75CAE1E42F4A492CF6D8C797B19F780243D47895E43C8FF088B1E81B4A6`
and zero pairwise differences.

The core dependency is selected by the workspace build configuration.
The earlier pin is preserved in the port record.
The implementation uses the core's
public addon ABI, Stand transition API, time-stop lifecycle, renderer,
visual-context, alpha, glow-layer, input, and attachment contracts. It
does not depend on a sibling The World addon.

All 59 source OGG files are copied byte-for-byte. Active source PNGs are
copied byte-for-byte under current resource paths. The eight primary
Stand textures and eight shade textures remain 256x256 and are not
rescaled. Geometry changes are structural only: the source cubes and UV
contract are retained while the bone hierarchy is adapted to the
current semantic Stand model. The 21 source animation clips are retained
and supplemented with current-name aliases.

The source sound map references files that do not exist in the canonical
JAR. Missing variants are filtered, the
`sddio_shadow_world_free_3` typo is remapped to the present
`sddio_shadow_world_3` file, and current aggregate time-stop voice events
replace the legacy multi-event sound type. `shadow_world_muda`,
`revolver_shot`, and `revolver_no_ammo` have no call path outside their
legacy declarations and remain excluded.

The donor's two-part OST relationship is restored exactly: the unsuffixed
`rotp_shadowtheworld:shadow_world_ost` SoundEvent is registered once and is
played by the evolution sequence at 200 ticks remaining, while that same base
ID constructs the `OstSoundList` attached to the Stand. The list registers
seven generated duration/cassette events. None has an active sound definition
or OGG because the canonical JAR contains no matching media; no duplicate base
registration or replacement audio is introduced.

The migrated source code is distributed under GNU GPL v3.0. Reused
assets remain subject to the source package's GPL v3.0 declaration and
applicable third-party rights.
