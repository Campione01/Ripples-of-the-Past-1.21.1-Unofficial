# RotP Emperor (`rotp_zemperor`): source and license

## Public release

- Module: `rotp-emperor`, mod id `rotp_zemperor`, version `1.2.0-port.15`.
- Original 1.16.5 add-on: `RotP-皇帝-1.2.0.jar` (RotP The Emperor, mod id `rotp_zemperor`), by Makuta Zeml, StandoByte; declared license: GNU GPL v3.0; SHA-256 `1304F3619295043672FD17C4A8AE6C6A737FB3000E1D1DBA7826EBEF950CDEC1`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a full static-closure NeoForge 1.21.1 migration of
**RotP Emperor 1.2.0**.

- Canonical source artifact: `RotP-皇帝-1.2.0.jar`
- Canonical size: `1,347,716` bytes
- Canonical SHA-256: `1304F3619295043672FD17C4A8AE6C6A737FB3000E1D1DBA7826EBEF950CDEC1`
- Canonical decompile: `061_RotP-皇帝-1.2.0`
- Original authors: Makuta Zeml
- Original framework and credits: StandoByte
- License: GNU General Public License v3.0

The active closure contains the invisible Stand state controller; the
summon-owned Emperor weapon; normal and crouching burst fire; four
target-filter modes; manual set/clear target actions; the server-authoritative
homing bullet and Stand-visible trace renderer; the handed summon pose and
item spin on the current core animation pipeline; the donor-registered hidden
`give_emp` compatibility action and repaired ability-atlas icon; current HUD art, locales,
sounds, and the legacy mod-list logo.

All 31 legacy class files, 41 namespace assets, and four packaging resources
are classified in `META-INF/emperor-source-manifest.json`. The legacy JAR has
no mixins. Its registered trail packets were never sent, its bullet model was
never instantiated, its duplicate Give Emperor class was unregistered, and
its Hermit Purple files were unrelated bundled content. Those dead surfaces
remain inactive. Exact bytes for the two dead Emperor-owned payloads and
eight otherwise unretained bundled-foreign payloads are preserved under
`assets/rotp_zemperor/legacy_archive/source/`; they are unregistered and
unreferenced. Six other bundled-foreign entries are exact active resources
under their Hermit Purple or Catch the Rainbow owners, and `hp_summon_5.ogg`
is retained in Hermit Purple's owner archive. Emperor remains the ranged
non-humanoid exception: it has no generic melee, barrage, charged-heavy,
guard, grab, manual-control, global renderer, shader, framebuffer, recipe, or
creative-tab surface.

The full static blocker ledger is empty. Dedicated-server, two-client
late-tracker, visibility/alpha, Iris, and Shoulder Surfing validation remain
manual runtime checks and are not represented as static migration blockers.
