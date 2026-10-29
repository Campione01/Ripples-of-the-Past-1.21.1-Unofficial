# RotP Cream (`rotp_cream`): source and license

## Public release

- Module: `rotp-cream`, mod id `rotp_cream`, version `1.1.1-port.18`.
- Original 1.16.5 add-on: `RotP-亚空瘴气-1.1.1.jar` (RotP Cream, mod id `rotp_cream`), by Hello There; declared license: GNU GPL v3.0; SHA-256 `FD9E9AC1D6C2A4EE02187E8AF1E3DE0CED0E19CDC1BCC68F4A2AAB71884430A0`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Cream source and license

This NeoForge 1.21.1 module is an evidence-backed full reachable port of the
legacy `RotP-亚空瘴气-1.1.1.jar`.

- Locked source JAR:
  `RotP-亚空瘴气-1.1.1.jar`
- Size: `1767436` bytes
- SHA-256:
  `FD9E9AC1D6C2A4EE02187E8AF1E3DE0CED0E19CDC1BCC68F4A2AAB71884430A0`
- Legacy mod author: Hello There
- Legacy credits: StandoByte
- Legacy metadata license: GNU General Public License version 3
- Authoritative decompile:
  `041_RotP-亚空瘴气-1.1.1`

The authority contains 52 top-level Java owners and 152 `rotp_cream` asset
files. All 152 canonical asset paths are retained. Of those, 149 are copied
byte-for-byte; `sounds.json`, `lang/en_us.json`, and `lang/zh_cn.json` are
ported to valid current-format JSON while preserving every canonical OGG
stream. All six canonical 512x512 main textures are already above the minimum
art threshold, so no art enlargement is applied.

The port registers the full legacy action, Stand, entity, block, effect, and
sound ID surface. Melee, Chomp, item eating, Void Ball, Void Dash, Semi-Void,
damage/effect behavior, sound isolation through public `PlaySoundEvent`, overlay
rendering, F1-safe HUD visibility, cooldowns, flight leases, and cleanup are
implemented through NeoForge and public core APIs.
Cream keeps the shared standard-humanoid input profile and charged heavy; its
canonical destructive power rank B means the grab chain is intentionally
absent.

No legacy mixin, shader, post chain, framebuffer, `PlayerRenderer` hook, Iris
hook, super-resolution hook, custom packet, or Y-coordinate disposal path is
copied. The legacy `F1Mixin` behavior is actively replaced through public
`RenderFrameEvent.Pre` and `RenderFrameEvent.Post` callbacks: while true
blindness or inside-Cream is active, `hideGui` is forced off only for the
current render frame and its pre-frame value is conditionally restored
afterward. Logout and stale-frame cleanup release the same scoped lease.
Runtime multiplayer, dimension, death, spectator, disconnect, resource-reload,
shader-pack, and super-resolution scenarios remain listed as an unrun live
matrix rather than being claimed as executed.

The new NeoForge integration is distributed under GPL-3.0-only to remain
compatible with the legacy module's declared license. See
`META-INF/migration/full-source-manifest.json`,
`META-INF/migration/full-closure-ledger.json`, and
`META-INF/migration/strict-contract.json` for the evidence and enforcement
surface.
