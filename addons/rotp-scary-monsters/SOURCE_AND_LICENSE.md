# RotP Scary Monsters (`scary_monsters`): source and license

## Public release

- Module: `rotp-scary-monsters`, mod id `scary_monsters`, version `1.0.3-port.30`.
- Original 1.16.5 add-on: `RotP-scary-monsters-1.0.3.jar` (Scary Monsters, mod id `scary_monsters`), by jiutoh; declared license: GNU GPL v3.0; SHA-256 `BB98A0201F7346EC392512BA263F78B4A59E2285ACCBCFCD5BF5579B76F50CDB`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-08-22 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

- Donor: `RotP-scary-monsters-1.0.3.jar`
- Archive: `RotP-scary-monsters-1.0.3.jar`
- Decompiled source: `096_RotP-scary-monsters-1.0.3`
- Bytes: `11575752`
- SHA-256: `BB98A0201F7346EC392512BA263F78B4A59E2285ACCBCFCD5BF5579B76F50CDB`
- Original mod ID/version: `scary_monsters`, `1.0.3`

The port retains all 17 action IDs, dinosaur and morph ownership, three effects,
three items, coffee recipe, sounds, models, animations, textures, and cleanup.
The donor embeds and uses undeclared GeckoLib 3 classes; the port instead uses
the current core resource-model loader and introduces no new prerequisite.

Scary Monsters is `HYBRID_FORM`. Generic Stand O-key control is disabled.
Player morph and controlled-dinosaur movement use dedicated ownership, and
target dinosaurification is an independently reversible state. Donor range
4/4 is retained only as supplementary evidence; canonical distance is unclear.
