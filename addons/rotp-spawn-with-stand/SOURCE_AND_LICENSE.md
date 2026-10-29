# RotP Spawn With Stand (`rotp_sws`): source and license

## Public release

- Module: `rotp-spawn-with-stand`, mod id `rotp_sws`, version `1.1-port.5`.
- Original 1.16.5 add-on: `RotP-SpawnWithStand-1.1.jar` (RotP Spawn With Stand, mod id `rotp_sws`), by Hello There; declared license: GNU GPL v3.0; SHA-256 `8BE9FAE4F2CAA0584EACD14702B6BEF10E97C81002B8F716FFE5FF79A9A32AF7`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Spawn With Stand source and license

This module is the full reachable NeoForge 1.21.1 migration of the
Spawn With Stand utility from:

`RotP-SpawnWithStand-1.1.jar`

Canonical JAR evidence:

- Size: `11989` bytes
- SHA-256:
  `8BE9FAE4F2CAA0584EACD14702B6BEF10E97C81002B8F716FFE5FF79A9A32AF7`
- Legacy mod ID: `rotp_sws`
- Legacy implementation version: `1.0`

The selected CFR 0.152 mirror is:

`028_RotP-SpawnWithStand-1.1`

Mirrors `030_RotP-SpawnWithStand-1.1` and
`031_RotP-SpawnWithStand-1.1` contain the same five file hashes and identify
the same canonical JAR. They are duplicate mirrors, not separate modules.
The canonical class-entry hashes and mirror-selection evidence are packaged
under `META-INF/migration`.

The legacy `META-INF/mods.toml` declares `license="GNU GPL v3.0"` and names
Hello There as author with StandoByte in credits. This module preserves that
license declaration without inferring an "only" or "or later" qualifier.

The implementation is newly written against public Minecraft, NeoForge, and
ROTP APIs. It preserves the three common-config keys, first-login chance,
ordered assignment counter, UUID roll ledger, NBT data name/tags, configured
and random selection, and the successful-grant translation. It explicitly
defines respawn, dimension re-entry, existing-Stand, config reload, invalid or
disabled Stand, multiplayer, and persistence timing behavior.

No legacy class file or decompiler output is packaged. The canonical archive
contains no Stand registration, ability, renderer, model, texture, animation,
sound, shader, or post effect. This utility grants existing enabled Stand
types through the current core and does not own Stand combat or art.
