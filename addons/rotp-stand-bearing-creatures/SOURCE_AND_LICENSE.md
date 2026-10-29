# RotP Stand-Bearing Creatures (`rotp_mwp`): source and license

## Public release

- Module: `rotp-stand-bearing-creatures`, mod id `rotp_mwp`, version `1.1.2-port.13`.
- Original 1.16.5 add-on: `RotP-拥有替身的生物-1.1.2.jar` (RotP 拥有替身的生物, mod id `rotp_mwp`), by Weever; declared license: GNU GPL v3.0; SHA-256 `9C93C2CA8BA823BF153E751568E0B3332EC04EE4B0F8D2B549DC3908A08BFD74`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source And License Record

This module is a bounded Minecraft 1.21.1 migration of reachable behavior
from:

- Jar: `RotP-拥有替身的生物-1.1.2.jar`
- Size: `259423` bytes
- SHA-256:
  `9C93C2CA8BA823BF153E751568E0B3332EC04EE4B0F8D2B549DC3908A08BFD74`
- Canonical CFR 0.152 decompile:
  `053_RotP-拥有替身的生物-1.1.2`
- Legacy mod ID: `rotp_mwp`
- Legacy implementation version: `1.1.2`
- Legacy metadata author/vendor: `Weever`
- Legacy metadata credits: `StandoByte`

The legacy jar's `META-INF/mods.toml` declares `license="GNU GPL v3.0"`.
The NeoForge metadata preserves that declaration without inferring an "only"
or "or later" qualifier.

The implementation is newly written against public Minecraft, NeoForge, and
ROTP APIs. It preserves the twelve common-config entries and their defaults,
eligible-mob attachments, spawn rolls and advancement gate, persistent
processed state, combo datapacks, Stand and player-power mob AI, death discs,
Stand-virus behavior, baby Stand behavior, the Debug Stand and its eight
operator abilities, and operator diagnostics.
Random Stand assignment preserves the donor's weighted selection after the
addon toggle and blocked-ID filters, using the current core Stand random
weight and excluding non-positive or non-finite values.

The legacy `logo.png` is copied byte-for-byte because it is the declared
metadata logo. The two combo JSON resources are migrated data: power IDs and
ability names were translated to the current ROTP registry/moveset model.
The Debug Stand icon is newly assembled from current core Stand icons and uses
standard resource-pack animation metadata; no legacy Debug icon binary is
packaged.
No legacy class file, Mixin configuration, or decompiler output is packaged.

The Debug Stand registers all eight donor abilities with both donor hotbars,
both Shift variations, rainbow labels and selection, and dynamic Stand/action
icons. Existing permission-level-two commands remain additive operator tools.
The generic-mob Stand-virus behavior uses the public owner-keyed core extension
and does not use addon Mixins, reflection, or private core access. Client
config-sync packets are not reproduced because all mob decisions are
server-authoritative.
