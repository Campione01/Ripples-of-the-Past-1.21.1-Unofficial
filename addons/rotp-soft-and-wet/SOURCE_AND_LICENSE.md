# RotP Soft And Wet (`rotp_sw`): source and license

## Public release

- Module: `rotp-soft-and-wet`, mod id `rotp_sw`, version `1.1.0-port.11`.
- Original 1.16.5 add-on: `RotP-软又湿-1.1.jar` (RotP Soft And Wet, mod id `rotp_sw`), by Top1545, StandoByte; declared license: GNU GPL v3.0; SHA-256 `E77115599B127F5ABE8CE83BF1CBE8265A67B9F9D1246C17DCDDA521808A5610`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Soft And Wet source and license

- Canonical source: `RotP-软又湿-1.1.jar`
- Canonical inventory row: 75 of the 91-row Stage 0 inventory
- Source SHA-256: `E77115599B127F5ABE8CE83BF1CBE8265A67B9F9D1246C17DCDDA521808A5610`
- Original mod id: `rotp_sw`
- Original authors: Top1545 and StandoByte
- Original license: GNU GPL v3.0

The full reachable public-API closure ports all ten canonical action owners:
the standard melee chain and guard, damaging/effect/explosive bubbles, the
bubble wall, and the custom stunning finisher. The canonical one-entity-type
bubble topology is preserved with a synchronized projectile-kind field.

The existing 1.21.1 stand-skin geometry/animation conversion, namespaced
ability assets, core shared charged-heavy control, corrected world visual
context, and upgraded 256-by-256 bubble model/material are retained as labeled
port improvements. Canonical shield and Go Beyond image files have no
registered action owner in the source artifact. Their three exact PNG payloads
are packaged only under `assets/rotp_sw/legacy_archive/source/`, with no action,
model, resource, or production-code reference. Both built artifacts include
the locked retention evidence.
