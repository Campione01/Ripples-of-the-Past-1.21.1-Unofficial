# RotP Jumpin' Jack Flash (`jumping_jack_flash`): source and license

## Public release

- Module: `rotp-jumping-jack-flash`, mod id `jumping_jack_flash`, version `1.0.3a-port.14`.
- Original 1.16.5 add-on: `rotp-jumping_jack_flash-1.0.3a.jar` (Rotp-Jumping Jack Flash, mod id `jumping_jack_flash`), by jiutoh; declared license: GNU GPL v3.0; SHA-256 `CB4CA8BCFB04FFD6039B5AD7F05524D6C51122A50D505EFCB78C3574F54DFCCE`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-08-22 to 2026-10-14); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module ports the complete reachable content of the supplied Jumpin' Jack
Flash donor.

- Source artifact: `rotp-jumping_jack_flash-1.0.3a.jar`
- Archived donor: `rotp-jumping_jack_flash-1.0.3a.jar`
- Archived decompilation: `097_rotp-jumping_jack_flash-1.0.3a`
- Artifact bytes: `118426`
- SHA-256: `CB4CA8BCFB04FFD6039B5AD7F05524D6C51122A50D505EFCB78C3574F54DFCCE`
- Original mod ID and version: `jumping_jack_flash`, `1.0.3a`
- Original metadata license: `GNU GPL v3.0`
- Original author: `jiutoh`

The donor contains 77 ZIP entries, 49 file entries, and 27 class entries. This
port retains its reachable Stand identity, standard punch/barrage/heavy/
finisher/guard surface, 16-round tracking volley, targeted or ordinary
gravity snowball, owner-scoped cancellation, persistent radius-20 domain,
domain close pull, visual resources, sounds, controls, and skills. The legacy
action IDs `cd_blood_cutter` and `cd_blood_cutter1` remain registry IDs for
save and resource compatibility.

Jumpin' Jack Flash is explicitly `HYBRID_FORM`. The body is
`CLOSE_RANGE_DIRECT` with manual control and body-only O-key distance strength
decay. Projectiles and the domain own their ranges and damage independently.
The donor's `20/20` Stand-stat range is retained as supplementary
implementation evidence. No stronger canonical meter value was supplied, so
that distance remains explicitly uncertain and must not be treated as a
canonical rank-to-meter conversion.

The donor's generic living capability called its owner UUID a "bomb". That
name and its unused time-marker-style state are not ported. NeoForge 1.21.1
uses an owner/source gravity lease with bounded cleanup instead.
