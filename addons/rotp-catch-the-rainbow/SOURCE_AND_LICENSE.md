# RotP Catch the Rainbow (`rotp_ctr`): source and license

## Public release

- Module: `rotp-catch-the-rainbow`, mod id `rotp_ctr`, version `1.1-beta-port.17`.
- Original 1.16.5 add-on: `RotP-彩虹捕手-1.1-beta.jar` (RotP 彩虹捕手, mod id `rotp_ctr`), by ArchLunatic, StandoByte; declared license: GNU GPL v3.0; SHA-256 `497F1F81FACEA3B359F67CE616141E8972D3918C04C7AD62AF4A311E44B421A5`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Catch the Rainbow source and license

This NeoForge 1.21.1 migration is based on the released Forge 1.16.5
artifact:

- artifact: `RotP-彩虹捕手-1.1-beta.jar`
- exact size: `546132` bytes
- SHA-256:
  `497F1F81FACEA3B359F67CE616141E8972D3918C04C7AD62AF4A311E44B421A5`
- original authors recorded by the artifact: ArchLunatic and StandoByte
- original license: GNU General Public License v3.0

The released artifact is the byte and resource authority. Decompiled directory
`045_RotP-彩虹捕手-1.1-beta` is the readable implementation authority.
Directories `047_RotP-彩虹捕手-1.1-beta` and
`048_RotP-彩虹捕手-1.1-beta` contain the same 39 relative files and are
byte-identical to directory 045.

The source manifest and migration ledger under `META-INF` record the complete
38-source-owner disposition, registration surface, authority boundaries,
resource accounting, visual integration, and remaining manual runtime checks.
The source artifact does not contain audio for its declared
`catch_the_rainbow_ost` sound; the legacy sound registration surface is kept,
but missing audio is not fabricated.

The released artifact and sole upstream source commit contain zero-byte
`umbrella_falling_rain_drops2.ogg`, `umbrella_falling_rain_drops3.ogg`, and
`umbrella_falling_rain_drops6.ogg` files. Variants 2 and 3 were declared by
the legacy umbrella-rain event, while variant 6 was never selected. The
current event selects only the distinct, decodable donor variants 4 and 5.
All three zero-byte donor files remain byte-identical under
`migration/baseline-legacy`, outside the runtime resource source set. They are
not packaged, and no replacement audio was fabricated or copied.

Original textures and sounds retained by this module remain under the original
GPL-3.0 license. Port code and compatibility changes in this module are also
distributed under GPL-3.0-only.

The active Stand base color is a documented migration reconstruction rather
than a byte-identical extraction. Every nontransparent donor pixel remains
unchanged; only the donor-empty torso and limb UV islands are filled to satisfy
the current H2 surface-closure contract. The canonical JAR remains the identity
and palette authority.

### Owner decisions

- Rain Redemption re-application (2026-09-23). In the source artifact the
  effect's side effects live in its attribute hooks, so applying Rain
  Redemption again while it is active (a creative re-cast, or `/effect give`
  with a longer duration) first runs the effect's end and kills the user. The
  owner chose to follow the manga ("按漫画设定走"): on re-application this port
  keeps the running effect, at most extending its duration, and does not kill.
  In survival the 12000-tick cooldown equals the duration, so a normal cast
  cannot re-apply it.
