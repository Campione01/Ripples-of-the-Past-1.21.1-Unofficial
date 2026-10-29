# RotP Kraft Work (`rotp_kraft_work`): source and license

## Public release

- Module: `rotp-kraft-work`, mod id `rotp_kraft_work`, version `1.1.0c-port.28`.
- Original 1.16.5 add-on: `RotP-Extra-Stands-1.1.0c.jar` (RotP Extra Stands, mod id `rotp_extra_dg`), by DanielGamer321, StandoByte; declared license: GNU GPL v3.0; SHA-256 `0D0202912D14B2AB50CD5353275ACD6571FFFF0916807F8DDB1EF2928C1E3672`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is the NeoForge 1.21.1 Kraft Work static-closure split and port
from:

- `RotP-Extra-Stands-1.1.0c.jar`
- Size: `1532026` bytes
- SHA-256:
  `0D0202912D14B2AB50CD5353275ACD6571FFFF0916807F8DDB1EF2928C1E3672`
- Original authors: DanielGamer321 and StandoByte
- License: GNU General Public License v3.0 only (`GPL-3.0-only`)

The source archive contains several Stands. This module contains only Kraft
Work code and resources plus published ROTP core dependencies. Stone Free, The
Hand, and Aqua Necklace are explicitly excluded.

All 46 Kraft Work authority binaries have an explicit disposition in
`migration/manifests/source-assets.json`. Runtime copies are byte-identical to
their listed source entries. The default and manga primary textures are
distinct, authored 256 by 256 images. The 1.21.1 Bedrock geometry is a
mechanical reconstruction of all 57 explicit `KraftWorkModel` cubes plus the
14 inherited humanoid cubes; it preserves the source's 128 by 128 logical UV
layout and does not upscale or replace the source art.

The closure preserves Kraft Work's full lock/equipment/transport state,
specialized item projectile behavior, kinetic charge and release families,
the advanced release UI (the donor's ring of six icon buttons from the
authority GUI atlas, labels shown as hover tooltips), effects, lifecycle
cleanup, and the shared close-range humanoid grab chain. Module-local NeoForge
attachments own the server state. The visible frozen-item owner follows the
controlling Stand's world visual context and alpha buffer. The block marker
remains invisible.

The donor combo rows and all 41 Kraft Work-owned Venezuelan Spanish entries
are active module resources. The four donor condition messages are connected
to the current self-lock, target-equipment, useless-item, and item-cooldown
checks. `migration/manifests/historical-resource-restoration.json` seals the
entry hashes, ownership counts, and current core pin. The 38 Kraft Work entries
of the donor's Spain Spanish file are active the same way in `es_es`, under
the keys `es_ve` uses. Both skins' `es_es` overlays also carry the combo punch
name, because a skin answers ability names from its own `es_es` and then its
own `en_us` before the root file.
