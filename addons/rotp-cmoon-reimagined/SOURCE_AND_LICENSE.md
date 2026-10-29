# C-Moon Reimagined (`cmoon_rm`): source and license

## Public release

- Module: `rotp-cmoon-reimagined`, mod id `cmoon_rm`, version `1.0.0-port.7`.
- Original 1.16.5 add-on: `RotP-newcmoon.jar` (C-Moon Reimagined, mod id `cmoon_rm`), by silentevermore; declared license: GNU GPL v3.0; SHA-256 `176E7C48FDF4F2B311D8EFEC8786B29C7EFD8E90DB2E61601D8224B9A32DDA98`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## C-Moon Reimagined source and license

This module migrates the legacy `RotP-newcmoon.jar` addon by
silentevermore. The legacy metadata declares GPLv3; this migration uses
`GPL-3.0-only`.

Locked source artifact:

- Path: `rotp/RotP-newcmoon.jar`
- Size: `381602` bytes
- SHA-256:
  `176E7C48FDF4F2B311D8EFEC8786B29C7EFD8E90DB2E61601D8224B9A32DDA98`

The byte-equivalent locked decompiles are
`rotp附属反编译/022_RotP-newcmoon`,
`023_RotP-newcmoon`, and `024_RotP-newcmoon`. Their 17 Java units,
the canonical JAR's 19 physical class entries, and all 19 resources are
disposed in `migration/full-scope.json` and
`migration/physical-entry-inventory.json`. The canonical manifest title
`55555555-专用测试` is UTF-8 provenance residue, not this module's identity.

Original audio and action/effect icons are preserved. The 128x128 Stand
texture and Java model were jointly remade as a true 256x256 material atlas
and 256-UV Bedrock geometry by the deterministic tools in `migration/`.

The migration does not include copied RotP core code. It consumes the shared
core ABI for combat, input, Stand rendering, first-person arms, lifecycle
alpha, charged-heavy, directional gravity, time stop, and post effects.
`rotp_cm:cmoon` is a mandatory runtime prerequisite, but this alternate
variant retains the legacy artifact's independent `cmoon_rm:cmoon` registry
identity and does not mutate the base Stand.

The prior audited core lock is
`RipplesOfThePast-Unofficial-NeoForge:1.21.1-0.2.2.2-260128-a` with mod
metadata range `[0.2.2.2]`, but it is not a final directional-gravity v2
receipt. The current core source now publishes `directional_gravity_v2` and
owns the complete registered donor movement, collision, landing, passenger,
camera, client-prediction, and server-validation chain. Static validation uses
a source-built core overlay; final artifact closure still waits for that exact
implementation to become the sealed core pin. The addon owns only the
per-Stand synchronized provider state and lifecycle binding.

The six-frame transform repair, global-state repair, world/local boundary
repair, Arms Inversion cadence repair, base-presence isolation, and joint 256
art are retained private 1.21.1 improvements, not claims about canonical
bytecode. The resolved implementation blocker, remaining final-pin gate, and
all still-unexecuted live checks are recorded in
`migration/blocker-ledger.json` and
`migration/manual-runtime-matrix.json`.
