# RotP Ticket to Ride (`rotp_ttr`): source and license

## Public release

- Module: `rotp-ticket-to-ride`, mod id `rotp_ttr`, version `1.0-full-closure.9-mc1.21.1`.
- Original 1.16.5 add-on: `RotP-Ticket to ride-1.0.jar` (RotP ticket to ride Addon, mod id `rotp_ttr`), by voidzyy , Love Train from LL; declared license: GNU GPL v3.0; SHA-256 `53B39A9D86A459B3294460532FCC75533FF9EF990C2482182999A6724EF1CA14`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Ticket to Ride source and license

This module is a clean NeoForge 1.21.1 migration of
`RotP-Ticket to ride-1.0.jar`.

- Original mod id: `rotp_ttr`
- Original authors: voidzyy and Love Train from LL
- Original declared license: GNU General Public License v3.0
- Canonical artifact SHA-256:
  `53B39A9D86A459B3294460532FCC75533FF9EF990C2482182999A6724EF1CA14`
- Canonical decompile:
  `034_RotP-Ticket to ride-1.0`

The gameplay implementation was reconstructed from the canonical artifact and
its decompiled Java. Copied binary assets remain attributable to their
original authors. The module is distributed under GPL-3.0-only.

The donor-registered `rotp_ttr:givetears` action remains active as a
default-unbound compatibility ability. Its empty behavior, 20-tick hold,
20-tick recovery, 75-stamina cost, and arm requirement are preserved without
changing the current control schemes or fabricating an absent icon.

The donor summon-voice registration and attached `stand_ost` list are also
restored through the current sound registry and `OstSoundList`. This preserves
`example_stand_summon_voiceline` plus the seven generated OST IDs and both
Stand attachments. All eight events intentionally remain silent because the
locked donor contains no matching `sounds.json` definitions or OGG files; no
media payload is fabricated.
