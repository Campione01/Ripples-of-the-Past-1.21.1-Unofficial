# RotP The Lock (`rotp_tl`): source and license

## Public release

- Module: `rotp-the-lock`, mod id `rotp_tl`, version `0.3.1-beta-port.14`.
- Original 1.16.5 add-on: `RotP-TheLock-0.3.1-beta.jar` (RotP The Lock Addon, mod id `rotp_tl`), by YarickTT; declared license: GNU GPL v3.0; SHA-256 `F3B4CEA0AED5C6C6D5B3EB324E458311A1531BA71FF575CEBE923D82BE2628DA`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is the full NeoForge 1.21.1 migration closure of
**RotP The Lock 0.3.1-beta**.

- Canonical source artifact: `RotP-TheLock-0.3.1-beta.jar`
- Canonical size: `63,550` bytes
- Canonical SHA-256: `F3B4CEA0AED5C6C6D5B3EB324E458311A1531BA71FF575CEBE923D82BE2628DA`
- Canonical decompile: `035_RotP-TheLock-0.3.1-beta`
- Original author: YarickTT
- Original framework and credits: StandoByte
- License: GNU General Public License v3.0

The active closure preserves the invisible Stand controller, target-attached
lock state, guilt escalation, owner-distance cleanup, attack and chat guilt
triggers, tier-five self-harm, summon-owned lock item, target marker, valuable
inventory check, exact seven-cube target layer, canonical textures, and current
Stand skin/ability resources. All 26 decompiled Java sources and all 11
canonical resources have an explicit migrated, replaced, repaired, or
evidence-backed retired disposition. Stand targets normalize to their living
user while the lock effect mirrors to that user's current summoned Stand, so
the legacy shared combat debuff remains without duplicate persistent state.
Chat guilt keeps the legacy order. The nearest lock-effect entity within 16
blocks whose plain name (`getName`, ignoring case; not a team-prefixed or
nicknamed display name) is a word of the message is chosen first, and only
then is its chat charge checked. If that entity was already charged, the
message does nothing and is never passed on to a farther victim with the same
name. The tier-five self-harm names the target as its own attacker, as the
legacy source did, and is tagged as a panic cause so panicking mobs still flee
after each hit.

The migration deliberately repairs UUID reference comparisons, zero-guilt
reset, immediate effect removal, persisted chat charging, self-recipient and
late-tracker synchronization, fragile player-name item ownership, and unsafe
legacy self-damage double counting. Persisted locks retain the owner UUID
and an optional creation-controller UUID. The player can set or inspect a
lock without summoning its controller, as the source actions did. Validity
depends on the owner being alive,
in the same dimension, and within 50 blocks. Unsummon, Stand loss, and
controller replacement do not clear existing locks. Owner death, logout,
and dimension changes clear loaded targets; unloaded targets recheck the
current owner conditions when they resume ticking, not the controller UUID.

The legacy silent placeholder registry IDs are restored for registry parity.
The canonical JAR has an empty `sounds.json` and zero OGG files, so these IDs
remain silent; the port uses the core default Stand summon/unsummon sounds and
does not invent replacement audio.

This is an H4 `PLAYER_LAYER` target-effect unit. Humanoid melee, barrage,
charged heavy, guard, grab, manual control, shaders, post-processing, mixins,
recipes, and creative-tab exposure are outside the source behavior and are not
added. The original Power 2 converts to current rank E, not A+, and the
zero-size invisible controller is neither a directly controlled close-melee
humanoid nor a grasp-capable body. The Lock is therefore not grab-eligible,
and no grab or charged-heavy mechanics are added. The H1/H2 joint texture
upgrade rule does not apply to this H4 unit; all seven canonical primary
textures remain their verified 32x32 originals.
