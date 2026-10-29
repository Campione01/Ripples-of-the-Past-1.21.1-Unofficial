# RotP Diver Down (`rotp_dd`): source and license

## Public release

- Module: `rotp-diver-down`, mod id `rotp_dd`, version `1.1.2.1-port.29`.
- Original 1.16.5 add-on: `RotP-怒海潜将-1.1.2.1.0.jar` (RotP Diver Down, mod id `rotp_dd`), by HK47bot, Agamatius (sounds), FriendlyCoop (suggestion author); declared license: GNU GPL v3.0; SHA-256 `A9C2171E9A953AC19AB929411B537C4FF241ED1C425F2A07B9B5E3D3F4F4F1C9`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Diver Down source and license

This module is a NeoForge 1.21.1 migration of the canonical RotP Diver Down
1.1.2.1 legacy artifact.

- Canonical file size: `497649` bytes
- Canonical SHA-256:
  `A9C2171E9A953AC19AB929411B537C4FF241ED1C425F2A07B9B5E3D3F4F4F1C9`
- Legacy mod id: `rotp_dd`
- Legacy implementation title: `DiverDownROTP`
- Legacy implementation version: `1.1.2.1`
- Legacy license: GNU General Public License v3.0
- Legacy authors: HK47bot, Agamatius (sounds), FriendlyCoop

The canonical archive contains 93 non-directory entries: 39 classes,
51 namespace assets, and three metadata files. The three recovered decompile
mirrors each contain the same 36 Java source files; the archive hash and
archive inventory remain authoritative.

The migrated Java implementation is a clean current-API port based on the
observed legacy behavior. It restores all reachable action, entity, effect,
renderer, marker, lifecycle, and resource owners. It also fixes unsafe legacy
inventory/trade fallthrough while preserving the intended player-facing
behavior.

The active visual chain is `DiverDownNewRenderer` to `DiverDownNewModel` to
`diver_down.png`. The 128x128 active texture was upgraded to a physical
256x256 atlas with authored per-texel material, edge, and joint-island detail.
It is not an interpolated or four-pixel replicated scale. The logical UV
atlas remains 128x128, all 33 canonical cubes remain present, and the
11-animation source is mapped to the current semantic humanoid hierarchy.

Canonical binary resources retained in this module remain under
`GPL-3.0-only`. The migrated source is distributed under
`GPL-3.0-only`.
