# RotP The Sun (`rotp_the_sun`): source and license

## Public release

- Module: `rotp-the-sun`, mod id `rotp_the_sun`, version `0.1.11-port.5`.
- Original 1.16.5 add-on: `RotP-Next-Album-0.1.11.jar` (RotP:NextAlbum, mod id `rotp_nextalbum`), by Babelmoth; declared license: GNU GPL v3.0; SHA-256 `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-09-25); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## The Sun source and license

This module is an isolated NeoForge 1.21.1 port of The Sun logical unit from
`RotP-Next-Album-0.1.11.jar`.

- Canonical binary SHA-256:
  `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`
- Canonical author: Babelmoth
- Canonical license: GNU GPL v3.0
- Port namespace owner: `rotp_the_sun`
- Preserved registry namespace: `rotp_nextalbum`

The original texture and Blockbench/Gecko geometry are retained from the
canonical GPL-3.0 source artifact. Port code is provided under GPL-3.0-only.

### Build dependency

The active dependency is defined by the workspace build configuration.
The former core pin and its artifact hash are preserved in the
port record.

### Low-level replacement boundary

The legacy global sky and light-texture mixins are not copied. Their observable
daylight semantics are owned through the final core's owner-keyed
`ClientSkyPresentationProviders` API plus the public NeoForge fog-color event.
The provider transforms time of day, sky darkening, star brightness, and sky
color without mutating client level time or `doDaylightCycle`; returning no
presentation falls through to the original client values without retained
world state.

The legacy private front-face-cull render type is likewise not copied. The
canonical core and inverse-outline geometry is rendered through the core Stand
materials, `StandVisualContext.world`, and `AlphaMultiBufferSource`. These are
semantic replacements, not missing gameplay registrations or effects. No
global renderer hook is installed.

The module-owned provider applies the exact legacy 0.85 sky-dome blend toward
`[0.56, 0.75, 1.0]`, while `ViewportEvent.ComputeFogColor` applies the matching
fog blend. The earlier rendering-validation status is retained in the port
record linked above; runtime results belong to the artifact and profile tested.
