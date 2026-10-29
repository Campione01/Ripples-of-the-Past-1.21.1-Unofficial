# RotP Grateful Dead (`rotp_grateful_dead`): source and license

## Public release

- Module: `rotp-grateful-dead`, mod id `rotp_grateful_dead`, version `0.1.11-port.13`.
- Original 1.16.5 add-on: `RotP-Next-Album-0.1.11.jar` (RotP:NextAlbum, mod id `rotp_nextalbum`), by Babelmoth; declared license: GNU GPL v3.0; SHA-256 `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-21); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Grateful Dead Source and License

This module is a bounded NeoForge 1.21.1 migration of the Grateful Dead
logical unit from `RotP-Next-Album-0.1.11.jar`.

Canonical artifact:

- Path: `RotP-Next-Album-0.1.11.jar`
- Size: `2258019` bytes
- SHA-256: `749AF3B9F710490534BDA502BCE4A2A734921FF2A1C84CB1007EF892EF2FB19D`
- Legacy mod id: `rotp_nextalbum`
- Legacy implementation version: `0.1.11`
- Legacy author: `Babelmoth`
- Legacy license: GNU GPL v3.0

The CFR 0.152 decompile at
`025_RotP-Next-Album-0.1.11`
was used to recover behavior and registration topology. Canonical binary
resources were read from the locked JAR and its extracted baseline.

The port is distributed under `GPL-3.0-only`. Original code and assets remain
attributed to Babelmoth and the original Next Album contributors; Ripples of
the Past and its current addon API are attributed to StandoByte and their
contributors.

Touch, aura and remove-aging actions operate on the user's action performer;
they do not require or automatically summon a Stand body. A target's own aging
is cleared immediately on death, independently of fading aging cast by that
target. These follow the canonical StandAction inputs and AgingEntityEvents
death cleanup. Ordinary aging rates, aura ownership and block fading remain
separate from those entry and lifecycle rules.

Touch lasts at most `max(40, floor(60 + durability * 6))` ticks, using the
Stand's current developed durability. Movement during its perform phase is
60 percent of ordinary input speed; releasing the button starts recovery and
restores the normal multiplier.

Incoming aging damage preserves the canonical multiplier. A finite input that
would overflow the float range saturates at that range instead of introducing
infinite damage into Minecraft's damage pipeline.

The default Stand atlas and aging overlay are new 256x256 derivatives. An
AI image-generation edit supplied material and color reference, after
which a deterministic local post-process preserved the canonical alpha and UV
silhouette and rebuilt material regions. The migrated model retains the donor
cube inventory and doubled donor UVs while using current core bone names; the
head and body remain independent at their donor action pivots. The nine 32x32 ability icons are
module-local pixel-art derivatives using that same palette. Geometry and
animation JSON were structurally converted to the current Stand skin skeleton
and current action animation names.

The rebuilt base atlas differs from a nearest-neighbor 2x expansion of the
canonical 128x128 atlas at 11,948 output pixels and contains 242 non-uniform
2x source blocks. It is therefore a material and UV rebuild rather than a
Scale2x-only enlargement.

The three canonical aging-blur post chains are retained byte-for-byte under
the inert owner `legacy_archive`. Their active owner-namespace copies replace
the removed 1.16.5 `blur` program with Minecraft 1.21.1 `box_blur` plus linear
filtering while preserving the two-pass directions and 8/18/30 radii.

No custom shader program, mixin, global living renderer replacement,
PlayerRenderer cancellation, framebuffer replacement, chunk force-loading,
or optional scale-library dependency is packaged. Exact visual and gameplay
parity limitations are recorded in
`META-INF/migration/blocker-ledger.json`.
