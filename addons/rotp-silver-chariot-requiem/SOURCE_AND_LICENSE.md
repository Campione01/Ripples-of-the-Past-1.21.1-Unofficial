# RotP Silver Chariot Requiem (`rotp_silver_chariot_requiem`): source and license

## Public release

- Module: `rotp-silver-chariot-requiem`, mod id `rotp_silver_chariot_requiem`, version `0.1.11-1.2.1-port.11`.
- Original 1.16.5 add-on: `RotP-Next-Album-0.1.11.jar` (RotP:NextAlbum, mod id `rotp_nextalbum`), by Babelmoth; declared license: GNU GPL v3.0; SHA-256 `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`.
- Original 1.16.5 add-on: `RotP-银色战车镇魂曲-1.2.1.jar` (Chariot Requiem RoTP Addon, mod id `rotp_cr`), by TheDoGGy; declared license: GNU GPL v3.0; SHA-256 `6E0386AE943331FCD2E3DDC9BFD579EC53CA67914DEE465412047512DFCF4395`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and License

This replacement module merges one Silver Chariot Requiem logical unit from
two physical 1.16.5 authorities:

- `RotP-Next-Album-0.1.11.jar`
  - SHA-256:
    `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`
  - size: `2258019` bytes
  - legacy author: Babelmoth
- `RotP-银色战车镇魂曲-1.2.1.jar`
  - SHA-256:
    `6E0386AE943331FCD2E3DDC9BFD579EC53CA67914DEE465412047512DFCF4395`
  - size: `2735635` bytes
  - legacy author: TheDoGGy

Both sources credit StandoByte and declare GNU GPL v3. The migrated module
uses SPDX license `GPL-3.0-only`.

Row 32 remains authoritative for the save-compatible
`rotp_nextalbum:silver_chariot_requiem` Stand/entity IDs and its H2 main,
outline, and arrow-beetle renderer. Row 80 is authoritative for canonical
destructive power E, autonomous control, its raw numeric stats, punch,
barrage, heavy/finisher, block, charged heavy, Aura, both Virus actions,
generic Stand Arrow evolution, effect, particle, sounds, and action icons.
Row 80 also owns its registered H1 renderer/model path, custom idle and
summon/Virus poses, 128x128 donor baseline, and conventional power icon.
The row-80 `rotp_cr:chariot_requiem` Stand/entity IDs are not separately
registered because both sources describe one logical Stand.

The fixed grasp rule requires canonical A+, direct control, close range, and
grasp-capable humanoid arms. Row 80 is canonical E, autonomous, and has
100/100 range, so no grab, throw, release, or grab-variation surface is
registered or packaged. The raw legacy numeric value 4 is retained as source
data and is not treated as proof of a canonical rank.

The row-80 H1 model is restored as the selectable `row80_legacy` skin. Its
Blockbench cubes are translated to a native 1.21.1 layer; its custom idle,
two summon poses, and both Virus poses are restored in the skin animation
resource. The exact 128x128 donor texture remains archived and baseline-
locked; the selectable skin packages a deterministic 256x256 runtime texture
while retaining the native model's logical 128x128 UV domain. The 32x32 icon
remains byte-exact. Row 32's H2 renderer and 256x256 main art remain the
default skin, while the
registered renderer selects H1 for the legacy skin and skips the H2-only
outline and arrow-beetle layers. The art receipt records the baseline and
output hashes; no donor-exact exception remains for the primary runtime art.

The H2 outline uses the donor's front-face-cull shell state, so it exposes
only the expanded back faces instead of covering the textured main model.
The default animation resource resolves all nine registered skills; the
legacy skin retains its donor idle, summon, and Virus poses and inherits the
remaining action animations through the normal skin fallback.

All sixteen suspicious row-80 resources remain byte-exact under
`assets/rotp_cr/legacy_archive/`. The texture and icon also have declared
active rehomes. The two donor-unreachable sounds, eleven unreferenced
purple-charge frames, and wrong-plural duplicate particle texture are the
fourteen inert archive-only entries.

Every row-80 Java owner and every non-class archive file has an individual
hash and disposition in the packaged
`META-INF/migration/row80-owner-disposition.json` and
`META-INF/migration/row80-resource-disposition.json` ledgers. The merged
source, input, render, private-extension, blocker, and runtime ledgers are
packaged beside them.

The 16-entry, 110,697-byte archive and its two declared active rehomes are
governed by the packaged strict, retention, provenance, disposition, and
closure manifests. Production source, compiled classes, loader metadata, and
runtime classes cannot reference the archive path.

The preserved row-32 Beetle Arrow and bounded soul/body-state mechanics remain
identified as compatibility extensions. A packaged-donor audit found no
registered or called account identity, inventory, skin, advancement, input
ownership, or AI-brain exchange in either source jar, so those surfaces are
not restoration requirements.
