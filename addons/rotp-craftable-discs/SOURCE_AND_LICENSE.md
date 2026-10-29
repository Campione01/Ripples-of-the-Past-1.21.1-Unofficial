# RotP Craftable Discs (`rotp_cds`): source and license

## Public release

- Module: `rotp-craftable-discs`, mod id `rotp_cds`, version `1.0.0-mc1.21.1`.
- Original 1.16.5 add-on: `RotP-CraftableDiscs-1.0.jar` (RotP Craftable Discs, mod id `rotp_cds`), by Hello There; declared license: GNU GPL v3.0; SHA-256 `D38D87D425F0190ABEF4E37A99C9342B8B6AFB429480779A727C29333E5A454E`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-09-16); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source And License Record

This module is a bounded Minecraft 1.21.1 migration of recipe data from:

- Jar: `RotP-CraftableDiscs-1.0.jar`
- SHA-256: `D38D87D425F0190ABEF4E37A99C9342B8B6AFB429480779A727C29333E5A454E`
- Legacy mod ID: `rotp_cds`
- Legacy implementation version: `1.0`
- Legacy metadata author: `Hello There`
- Legacy metadata credits: `StandoByte`

The legacy jar's `META-INF/mods.toml` declares `license="GNU GPL v3.0"`.
The NeoForge metadata for this migration preserves that declaration verbatim;
it does not infer an "only" or "or later" qualifier.

The migrated content includes all eight donor recipe definitions and a minimal
NeoForge entrypoint. The three restored Stand-removal recipes differ from the
donor only where Minecraft 1.21.1 requires the singular `recipe` directory,
the result `id` field, and the current core namespace `jojo_ripples`.

No public source repository, homepage, issue tracker, or additional permission
grant is claimed here because none is identified by the inspected jar metadata.
