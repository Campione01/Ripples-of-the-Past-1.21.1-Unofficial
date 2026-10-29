# RotP Planet Waves (`rotp_pw`): source and license

## Public release

- Module: `rotp-planet-waves`, mod id `rotp_pw`, version `1.0.0-port.17`.
- Original 1.16.5 add-on: `RotP-行星波动 [BETA].jar` (RotP Planet Waves, mod id `rotp_pw`), by Hello There; declared license: GNU GPL v3.0; SHA-256 `8F37F0867A1DC05A33F9347271798A46990CB05A1851AE138F9527063EA03645`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is the full bounded NeoForge 1.21.1 migration of **RotP Planet
Waves [BETA]**.

- Canonical source artifact:
  `RotP-行星波动 [BETA].jar`
- Canonical source size: `508911` bytes
- Canonical SHA-256: `8F37F0867A1DC05A33F9347271798A46990CB05A1851AE138F9527063EA03645`
- Canonical decompile:
  `067_RotP-行星波动 [BETA]`
- Original author namespace: `com.hello_there.rotp_pw`
- License: GNU General Public License v3.0

The migration preserves the Stand identity, canonical art and animation,
meteor trajectories and shower timing, effect registration and exact local
overlay, icons, and registered sound identifiers. It uses the current full
shared grab chain and the remappable shared Shift+RMB input: short press is
grab, long press is charged heavy, and the binding appends to the existing RMB
heavy/guard surface.

The source artifact contains no Planet Waves audio definitions or media.
Registered Planet Waves events therefore remain source-silent; no synthetic
mapping or Enigma/Terunosuke OGG file is falsely substituted. The canonical
source also has no True Blindness producer, so none is invented.

The canonical Enigma/Terunosuke `sounds.json` and 11 OGG files have no Planet
Waves registry or class references. The table defines four unrelated event
keys, every media target uses the absent `rotp_enigma` namespace, and zero
targets resolve to the OGG files bundled under `rotp_pw`. Their exact bytes are
therefore retained only under the legal lowercase owner paths in
`assets/rotp_pw/legacy_archive/owners/`; the invalid active donor paths are not
packaged. `migration/manifests/donor-resource-disposition.json` records the 12
historical audit rows and their reachability proof. The legacy JAR manifest,
`mods.toml`, and `pack.mcmeta` are preserved only under
`legacy_archive/mechanical_metadata/`; current NeoForge metadata must replace
those three mechanical files. `migration/manifests/legacy-archive.json`
records all archived hashes.

The UTF-8 disposition, registry, input, state, visual, runtime, closure, and
post-build result records are under `migration/`.
