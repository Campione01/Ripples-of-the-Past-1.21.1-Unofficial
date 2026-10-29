# Tournament RotP Addon (`tournament`): source and license

## Public release

- Module: `rotp-tournament`, mod id `tournament`, version `1.0.0-mc1.21.1-port.5`.
- Original 1.16.5 add-on: `rotp-tournament-1.0_ARG_EDITION.jar` (Tournament RotP Addon, mod id `tournament`), by dikiytechies; declared license: GNU GPL v3.0; SHA-256 `498C145D7427868E56DA14820C31B4693B827F90AEAB198A4E17D16B9A6DA538`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Tournament source and license

This NeoForge 1.21.1 module is a bounded migration of the canonical artifact:

- File: `rotp-tournament-1.0_ARG_EDITION.jar`
- Size: `1,479,590` bytes
- SHA-256: `498C145D7427868E56DA14820C31B4693B827F90AEAB198A4E17D16B9A6DA538`
- Original mod id: `tournament`
- Original author: `dikiytechies`
- Original license declaration: `GNU GPL v3.0`

The selected decompile is
`036_rotp-tournament-1.0_ARG_EDITION`. Its nine Java owners are retained
under `migration/canonical/decompiled`. Decompile candidates 036, 038, and
039 have identical Java trees: 9 files, 31,879 bytes, normalized SHA-256
`B2C1CA7D5D6252043EF7649DE4C3EFC31410D34899298691C4CEABE250B0A794`.

All 22 canonical application resources are retained byte-for-byte under
`migration/canonical/resources`. The 21 canonical `assets/tournament`
files remain byte-identical in live resources. `pack.mcmeta` is the only
canonical resource converted for the current pack schema. The current
`data/tournament/jukebox_song/assgore_record.json` entry is required by
Minecraft 1.21.1's native jukebox data registry; it points to the canonical
sound and existing canonical description key.

The canonical JAR contains no recipe, loot-table, or tag files. This
migration does not invent any. It also refers to
`tournament:block/spikes_bottom` and
`tournament:block/spikes_top`, but the canonical artifact contains neither
referenced block texture.

The original author's public GPL-3.0 repository at
`https://github.com/dikiytechies/tournament` supplies the missing source
bytes. Twenty of the 21 canonical asset files match repository HEAD
`2d7ad0114bfe8f4818b679bf121917d107abcb7b` byte-for-byte; the sole
difference is the committed music-disc description versus the packaged
description. Commit `b2d35c9c854c572d7dede39421cb38091fc2a2cd` is the
last commit containing the two registered spike textures. Its immediate
child, `3f2e7ee8d112c99e96902a335ee571e6c32ee1ac`, deletes only those two
PNGs while leaving the models and blockstates registered.

The exact 16x16 RGBA source files are retained under
`migration/recovered/upstream-history/b2d35c9c854c572d7dede39421cb38091fc2a2cd`
and copied byte-for-byte to their lowercase live asset paths. The lower
texture SHA-256 is
`394578507BF62A416F4BD39FA281B80D9AFABB18042F8453ABF26A441572C453`;
the upper texture SHA-256 is
`DD09A3EE3D78C83CB68D9264C90186936C97306A8E714EF0995C5B9C1E95A70D`.
This upstream source-history recovery resolves the missing-texture blocker
without inventing art. Manual runtime matrix rows remain unexecuted.

The original `recordPlayers` declaration contains the one comma-separated
string `August_dr, Pr1tcha, iIIiBlackiIIi`, not three list entries.
Canonical behavior remains the default.
The separate `enableCorrectedRecordPlayerList` common-config option is a
private, default-off correction and is never represented as canonical
parity.

The original empty access transformer and original metadata are retained
only as canonical evidence under `migration/canonical/metadata`; they are
not active or packaged as runtime transformation instructions. No mixin,
global renderer, shader, or intrusive ROTP-core hook is used.
