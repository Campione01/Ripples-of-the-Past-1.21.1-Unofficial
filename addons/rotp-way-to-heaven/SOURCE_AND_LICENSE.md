# RotP Way to Heaven (`rotp_waytoheaven`): source and license

## Public release

- Module: `rotp-way-to-heaven`, mod id `rotp_waytoheaven`, version `1.0.0-closure.7-mc1.21.1`.
- Original 1.16.5 add-on: `RotP-新天堂之路-1.3.3.jar` (RotP 新天堂之路, mod id `rotp_waytoheaven`), by Makuta Zeml, Weever; declared license: GNU GPL v3.0; SHA-256 `DAE4DDB311ED9E8EB908EE7F09E24F7C044A985A0D9DF84542EDDE2B0E6A88D4`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-20); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a NeoForge 1.21.1 port of the GPL-3.0-only artifact
`RotP-新天堂之路-1.3.3.jar`.

- Canonical artifact SHA-256:
  `DAE4DDB311ED9E8EB908EE7F09E24F7C044A985A0D9DF84542EDDE2B0E6A88D4`
- Canonical artifact size: `1577246` bytes
- Canonical decompile mirrors:
  `052_RotP-新天堂之路-1.3.3`,
  `054_RotP-新天堂之路-1.3.3`, and
  `055_RotP-新天堂之路-1.3.3`
- Original authors: `Makuta Zeml` and `Weever`
- Port namespace:
  `rotp.addons.waytoheaven`

All three decompile mirrors are byte-for-byte identical for every relative
file. The port gives every one of the 15 top-level source files and 17 class
entries an explicit disposition. Source-owned semantics, required platform
adaptations, and port-private hardening are separated in
`migration/provenance-ledger.json`.

The two evolution ability bodies, atomic transitions, starting skills, and
special-wheel attachments are implemented. `WTH-B01` is resolved in source by
`stand_moveset_extensions_v1`: two order-100 definitions append HOLD to the
existing Whitesnake and C-Moon `hotbar` scheme at hotbar `0`. The port does not
call `@ApiStatus.Internal` moveset surfaces or edit sibling source, default
movesets, Stand stats, or private fields.

The donor combination-specific arrow rule is implemented without private
random-weight mutation. `stand_arrow_pool_overrides_v1` registers exact
exclusions for `rotp_cm:cmoon` and `rotp_mih:made_in_heaven` under owner
`rotp_waytoheaven:stand_arrow_pool` during common setup. Each sibling retains
its own donor weight and additive arrow tag when Way to Heaven is absent.

The donor client event is also active rather than retired. A LOW-priority
NeoForge `RenderHandEvent` handler restores the selected-and-enabled
`whitesnake_frases` free-offhand player-arm branch through the core
`FirstPersonRender` path. It preserves local hands-busy, hand-free item, carried
Coco Jumbo, canceled-event, MAIN_HAND, and visibility conditions; uses the
renderer-cached offhand stack to exclude vanilla's two-handed-map arm path even
during equip transitions; and does not recursively post a hand event.
The addon accesses no private renderer field and installs no mixin or renderer
replacement. The minimal core helper owns the current offhand equip
interpolation and existing arm/layer render path.

The required core features include `FEATURE_STAND_ARROW_POOL_OVERRIDES_V1`
and `FirstPersonRender.renderExtraPlayerArm`. The active core pin is owned by
the workspace build configuration.

The earlier `closure.2` artifact, intermediate core pins, offline test results
and deployment status are preserved in the
port record.
The original machine receipt remains at `migration/build-result.json`.

Legacy sound, texture, and logo assets remain covered by the source addon's
GPL-3.0-only license. The module does not copy code or assets from the
Whitesnake, C-Moon, or Made in Heaven sibling addons. Corrected translations,
the bone tooltip/bar color, exact starting-bone ownership, projectile
attribution, transient immunity state, and expanded cleanup are port-private
improvements documented separately from the canonical behavior.
