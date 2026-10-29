# RotP Vampire Climb (`rotp_vamp_climb`): source and license

## Public release

- Module: `rotp-vampire-climb`, mod id `rotp_vamp_climb`, version `1.0.1-port.6`.
- Original 1.16.5 add-on: `RotP-吸血鬼爬墙-1.16.5-1.0.1.jar` (RotP 吸血鬼爬墙, mod id `rotp_vamp_climb`), by Weever; declared license: GNU GPL v3.0; SHA-256 `671A62D7B51E75DDDD3D05AF757A9D5C0F136ABDE7C1F058E1ABB8F3CDA709A6`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-02); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and License

This module migrates the original Weever Vampire Climb addon for Ripples of
the Past 1.16.5 to NeoForge 1.21.1. The legacy addon and this migration are
distributed under GNU GPL v3.0.

The canonical legacy archive has SHA-256
`671A62D7B51E75DDDD3D05AF757A9D5C0F136ABDE7C1F058E1ABB8F3CDA709A6`.
Its full path, inventory, decompile hashes, and core receipts are recorded in
`migration/manifests/canonical-source.json`.

The original logo and action icon are preserved byte-for-byte. The four
translations retain their original wording while the action icon and
translation key use the addon-owned `rotp_vamp_climb` namespace.

The gameplay migration registers a native Vampirism `AbilityType` through
`PlayerPowerMovesetExtensions`. Core owns HOLD input, PlayerPower action
synchronization, HUD integration, and lifecycle cleanup. Eligible movement and
the five-blood-per-tick Survival cost are server-authoritative. Creative players
retain the donor's zero-cost climbing and may start with zero blood.

The additional first-person climbing arm uses the core's owner-scoped
`FirstPersonPostArmLayers` API.
VampireClimbClientEvents.clientSetup
registers the layer, and
VampireClimbArmLayer.render
draws it for the visible, non-spectator local player while wall climbing with
both hands empty. This replaces the donor's reflected private-renderer path
without reflection, a mixin, or a renderer takeover.
