# RotP Green Day (`rotp_zgd`): source and license

## Public release

- Module: `rotp-green-day`, mod id `rotp_zgd`, version `1.0.2-port.20`.
- Original 1.16.5 add-on: `RotP-青春岁月1.0.2.jar` (RotP Green Day, mod id `rotp_zgd`), by Makuta Zeml, StandoByte; declared license: GNU GPL v3.0; SHA-256 `0D408356CD109762909D01AD50F027D3CD8DFCE54BCC635C793A5DBA874EC0E6`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a full reachable NeoForge 1.21.1 migration of **RotP Green Day 1.0.2**.

- Canonical source artifact: `RotP-&#38738;&#26149;&#23681;&#26376;1.0.2.jar`
- Canonical source size: `3255662` bytes
- Canonical SHA-256: `0D408356CD109762909D01AD50F027D3CD8DFCE54BCC635C793A5DBA874EC0E6`
- Canonical decompile: `072_RotP-&#38738;&#26149;&#23681;&#26376;1.0.2`
- Original authors: Makuta Zeml, StandoByte
- License: GNU General Public License v3.0

The migration closes all nine legacy actions, four effects, eleven sound
registrations, two entities, mold state and synchronization, held healing,
detached-arm persistence and limb restrictions, the world and first-person
mold overlays, and the detached-arm marker. Green Day qualifies for and
includes the complete shared grab chain and the core configurable
charged-heavy input.

The first-person mold hand uses the core owner-scoped post-arm layer API
without replacing or canceling the vanilla arm. Player-limb mesh suppression
is implemented in
GreenDayLimbVisibilityRenderer.hideMissingPlayerLimbs
through `ScopedPlayerModelVisibility.hidePartsForCurrentRender`, including the
corresponding sleeve. The same subscriber's `hideMissingFirstPersonHand`
cancels the first-person hand render when that arm is missing.
