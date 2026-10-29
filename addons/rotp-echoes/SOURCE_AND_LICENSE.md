# RotP - Echoes (`rotp_echoes`): source and license

## Public release

- Module: `rotp-echoes`, mod id `rotp_echoes`, version `1.0.0-act1-3-port.16`.
- Original 1.16.5 add-on: `RotP-EchoesACT1-3Addon-1.0.jar` (Rotp-Echoes, mod id `myrotpaddon`), by Grasshopper,Day_GeE; declared license: GNU GPL v3.0; SHA-256 `C746A7304AF1515F2BD3DC3FFBBFEC6AC73D7EADEE64408EC29F255DBC1D170F`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module ports the Echoes 1.16.5 donor identified below. The
September 2026 donor update
records its inventory and freeze-policy differences.

- Source artifact: `RotP-EchoesACT1-3Addon-1.0.jar`
- Artifact bytes: `844677`
- SHA-256: `C746A7304AF1515F2BD3DC3FFBBFEC6AC73D7EADEE64408EC29F255DBC1D170F`
- Previous donor SHA-256: `8CEF2D458F55777FF1DDF23A5D55B253986738AB49550A0598FD6EF99A0BA030`
- Original mod ID: `myrotpaddon`
- Original display name: `Rotp-Echoes`
- Original metadata license: `GNU GPL v3.0`
- Original Minecraft range: `1.16.5,1.17)`
- Original Forge loader range: `[36,)`

The locked donor contains ACT2 and ACT3 classes, registrations and resources.
ACT1 is absent from that artifact despite its ACT1-3 filename.

The donor has 118 ZIP entries, 92 file entries, 46 class entries, 38 Java
source units, and 46 non-class files. `InitPotions` and `FreezeEffect` remain
dead: their deferred register is not attached to the mod event bus and no
reachable code uses the effect. The new `FreezeHelper` event listener has no
caller that activates its freeze state, so the port adds no activation path.

The current port registers `rotp_echoes:echoes_act1`, `rotp_echoes:echoes_act2`
and `rotp_echoes:echoes_act3` in
[EchoesEntities
and EchoesStands;
RotpEchoes attaches both
registers to the mod event bus. The donor inventory above covers ACT2 and ACT3.
ACT2 retains donor statistics and arrow-pool
eligibility. ACT3 has random weight zero and is reached by the evolution
action. The donor's unsafe background threads and command-based Stand swaps
are replaced by a server-tick action that calls the core Stand transition API.

ACT2 and ACT3 use addon Stand entity types and a narrow model/renderer extension
of the core Stand pipeline. The idle offset is the core default, which the
donors kept: 0.75 behind the user's right shoulder, feet 0.2 above the user's
feet, turned with the view yaw. The parent-first tail chain, render
dimensions, and icon overrides are preserved. ACT2 has explicit action,
left/right-hand, and arms-only animation names and its updated donor head tracking.
The ACT3 finisher no longer applies a freeze. Donor animations, textures, icons,
sounds, and locales are migrated into the current resource layout. Five locales
come from the current donor; fifteen earlier translations are retained. Dormant
donor resources are retained byte-exact under
`assets/rotp_echoes/legacy_archive` without runtime registration.

The addon registers no packet, mixin, or global render takeover. Detailed source inventory, state ownership,
input policy, rendering ownership, and runtime validation gaps are recorded
under `migration/manifests/`.
