# RotP RHCP (`rotp_rhcp`): source and license

## Public release

- Module: `rotp-rhcp`, mod id `rotp_rhcp`, version `1.0.29`.
- Original 1.16.5 add-on: `RotP-RHCP-Beta1.jar` (RotP Red Hot Chili Pepper Stand, mod id `rotp_rhcp`), by DanielGamer321, StandoByte; declared license: GNU GPL v3.0; SHA-256 `D4B871C68349C071AB20A5CFEB4604A6325EC727D4EC5A2C3E22665D0A3FA7BD`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-08-22 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module ports the complete reachable content of the supplied RHCP donor.

- Source artifact: `RotP-RHCP-Beta1.jar`
- Artifact bytes: `388058`
- SHA-256: `D4B871C68349C071AB20A5CFEB4604A6325EC727D4EC5A2C3E22665D0A3FA7BD`
- Original mod ID: `rotp_rhcp`
- Original version: `Beta1`
- Original Minecraft range: `[1.16.5,1.17)`
- Original Forge loader range: `[36,)`
- Original metadata license: `GNU GPL v3.0`
- Original authors: `DanielGamer321, StandoByte`
- Prior archived donor, pending refresh: `RotP-RHCP-Beta1.jar`
- Prior archived decompilation, pending refresh: `094_RotP-RHCP-Beta1`

The donor contains 137 ZIP entries, 97 file entries, 49 class entries, and
48 non-class files. All donor classes are reachable through registration,
entity behavior, client initialization, event handlers, model rendering, or
the action graph.

The port preserves the donor registry namespace and action IDs, two power-state
textures, glow layer, hard-coded model geometry, particles, HUD, sounds,
locales, battery/potency rules, conductor entry/exit, recharge, boost,
consumption reduction, dash, dodge, counter, chase, and electrocution behavior.
The updated action graph also includes the level-three drag-into-line action,
carried-target conductor travel, and the donor's world-positive-X exit offset.
Unsafe or obsolete implementation details are translated to server-owned
attachments, current action lifecycles, current payloads, and bounded cleanup.

Red Hot Chili Pepper is explicitly classified as
`LONG_DISTANCE_OPERATION`: `effectiveRange=150`, `rangeMax=300`,
`manualControl=true`, and `distanceStrengthDecay=false`. Conductor travel
is an ability-owned electrical-network state and does not borrow entity melee
distance decay.
