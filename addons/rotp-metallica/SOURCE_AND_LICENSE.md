# RotP Metallica (`rotp_metallica`): source and license

## Public release

- Module: `rotp-metallica`, mod id `rotp_metallica`, version `1.0.6-port.17`.
- Original 1.16.5 add-on: `RotP-金属制品-1.0.6.jar` (RotP Metallica, mod id `rotp_metallica`), by UncleStalin, StandoByte; declared license: GNU GPL v3.0; SHA-256 `D58ACFA05CDB0B068331641A39EB6F22BBECB8CEEFE2E4909F071965B789BC17`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Metallica source and license

This module is a full static-closure NeoForge 1.21.1 migration derived from the canonical
`RotP-金属制品-1.0.6.jar` by UncleStalin and StandoByte.

Canonical artifact:

- File: `RotP-金属制品-1.0.6.jar`
- Size: `482336` bytes
- SHA-256:
  `D58ACFA05CDB0B068331641A39EB6F22BBECB8CEEFE2E4909F071965B789BC17`
- Legacy mod id: `rotp_metallica`
- Legacy version: `1.0.6`
- Legacy license: `GNU GPL v3.0`
- Decompiled for semantic audit with CFR `0.152`

The legacy dependency tables were incorrectly emitted under
`dependencies.rotp_tutorial`. The `@Mod` value, `[[mods]]` id, Java package,
asset namespace, data namespace, and every registry owner agree that the
module owner is `rotp_metallica`; this port repairs only that metadata table
owner.

Imported PNG and OGG files are copied byte-for-byte from the canonical JAR.
The absent `metallica_ost.ogg` is referenced only by a source-dead,
unregistered sound definition and is not fabricated.

The canonical Stand entity has zero dimensions and both of its textures are
fully transparent. It is therefore retained as an invisible, non-colliding
coordinator rendered by `NoopEntityRenderer`; this port does not fabricate a
humanoid or a visible colony model.

The legacy compass tracker is implemented without its unbounded strong
reference list. A pristine held compass receives a self-describing,
reversible data-component marker and an untracked lodestone target for the
nearest active Metallica user. The marker is restored on hand or inventory
movement, container transfer, item toss, item-frame placement, death, clone,
dimension transfer, logout, and next observation after reload. If another
system replaces the lodestone component, that external value wins and only
Metallica's marker is removed.

The canonical `OstSoundList` creates seven registry placeholders, but none
has a `sounds.json` definition or audio payload. The separate unregistered
`metallica_ost` definition references a missing `metallica_ost.ogg`. The
seven generated events and donor Stand attachment are restored as silent
registrations without fabricating definitions or audio; all ten backed sound
events and all seventeen OGG files remain active.

The executable per-entry closure ledger is packaged at
`META-INF/metallica-source-manifest.json`. It classifies all 26 legacy class
files, 60 namespace assets, one data file, and three packaging resources.
The canonical artifact contains zero mixins, capability classes, and packet
classes.

This port is distributed under `GPL-3.0-only`.
