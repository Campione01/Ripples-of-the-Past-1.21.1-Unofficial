# RotP Mandom (`rotp_mandom`): source and license

## Public release

- Module: `rotp-mandom`, mod id `rotp_mandom`, version `1.1.2-port.13`.
- Original 1.16.5 add-on: `RotP-Mandom-1.1.2.jar` (RotP-Mandom, mod id `rotp_mandom`), by Weever; declared license: GNU GPL v3.0; SHA-256 `79B77F092C703BC4907F671DC6F18B2764A9B512E52D9687E07CFB850DC92BD4`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Mandom source and license

This module is a clean NeoForge 1.21.1 port derived from the canonical
`RotP-Mandom-1.1.2.jar` by Weever.

Canonical artifact:

- File: `[RotP:Mandom addon] RotP-Mandom-1.1.2.jar` (the actual local
  filename contains the original Chinese punctuation)
- Size: `814259` bytes
- SHA-256:
  `79B77F092C703BC4907F671DC6F18B2764A9B512E52D9687E07CFB850DC92BD4`
- Legacy metadata license: `GNU GPL v3.0`
- Legacy author: `Weever`
- Legacy credits: `StandoByte, Makuta Zeml, TheDoGGys`
- Decompiled for semantic audit with CFR `0.152`

Imported PNG and OGG files remain byte-identical to the canonical JAR.
The migration manifest records each source entry, destination, size, and
digest.

The 17 canonical rewind resources are restored byte-exactly at their active
runtime paths. Byte-identical copies remain under
`assets/rotp_mandom/legacy_archive` solely as provenance evidence. The three
canonical rewind sound events are registered, and all four post chains and
ten skin-aware action icons are available to the runtime implementation.

The generated `MandomAttachmentModel` is a mechanical API translation of
the canonical `MandomLayerModel`: node hierarchy, pivots, rotations, UVs,
cube dimensions, mirror flags, and the subtle idle motion are preserved.
Mandom's own zero-offset controller retains its entity renderer identity,
while the donor user attachment is rendered from the parent living model
root after copying the right-arm pose. The canonical paired arm clock
geometry is consolidated into one custom item renderer with the original
texture and animated hand.

The base Stand, skins, cosmetic stats, clock item, recipe, and pillager
outpost loot are migrated. The time-rewind action is registered and included
in the default hotbar by
MandomAddon.createStand.
The module-local gate is exposed by `REWIND_EXPOSED` and
`moduleSupportsAtomicRewind()` in
MandomRollbackGate.
The donor's `mandom_ost` constructs seven generated sound events and attaches
them to the Mandom Stand. Those registry semantics are restored without adding
sound definitions or audio payloads absent from the canonical JAR.
TimeRewindAbility.TimeRewindAction.actionPerformStart
calls the module's
MandomRewindService.rewind.
History capture is driven by
MandomEvents.onServerTick,
and RewindEffectPayload.register
registers the client effect message. The core `RollbackTransactions` support
matrix remains unavailable for world-history rollback; that separate result,
reported by `coreSupportsAtomicRewind()`, does not disable Mandom's module-local
path.

This port is distributed under `GPL-3.0-only`.
