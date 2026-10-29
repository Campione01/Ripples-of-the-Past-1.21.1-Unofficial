# RotP Memory Discs (`rotp_mds`): source and license

## Public release

- Module: `rotp-memory-discs`, mod id `rotp_mds`, version `1.0.0-port.5`.
- Original 1.16.5 add-on: `RotP-能力光碟-1.0.jar` (RotP Memory Discs, mod id `rotp_mds`), by Hello There; declared license: GNU GPL v3.0; SHA-256 `536B19FAEE6A450284531280811659E64DBE915E47C6434769B2D01E72DBD554`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-09-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Memory Discs migration

This module is a clean NeoForge 1.21.1 migration of the reachable content in
`RotP-能力光碟-1.0.jar`.

The legacy artifact declares GNU GPL v3.0 in `META-INF/mods.toml`, credits
StandoByte, and names Hello There as author. The five active item models and
textures in this module are copied byte-for-byte from that artifact. The Java
implementation is rewritten against the current public ROTP and NeoForge APIs.

The three duplicate decompile mirrors prefixed 063, 064, and 065 are evidence
copies of the same logical unit. They are not separate modules.

The legacy AI-disc and full-clear model/texture pairs had zero item
registrations and zero references across all four donor Java files and all
eight donor class entries. Their six files and the exact 799-byte donor
language source containing their keys are retained byte-exactly under
`assets/rotp_mds/legacy_archive`; they are packaged only as inert evidence and
remain absent from active model, texture, registry, Java, and class references.

The current core retains inactive `PlayerPowerData` instances after
`PlayerPower.setPowerType(null)`. To preserve the legacy disc's reset
semantics, this addon reinitializes those core-owned instances through the
public `PlayerPowerType.newDataInstance()` and `PowerData` NBT lifecycle before
granting a power again. It does not access the core's private data map, add an
attachment, or register a replacement capability.
