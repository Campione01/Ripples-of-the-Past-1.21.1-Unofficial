# RotP Tusk (`rotp_t`): source and license

## Public release

- Module: `rotp-tusk`, mod id `rotp_t`, version `1.0.2-port.26`.
- Original 1.16.5 add-on: `RotP-牙-1.0.2.jar` (Tusk RotP Stand Addon, mod id `rotp_t`), by HK47bot, TheDoGGyS; declared license: GNU GPL v3.0; SHA-256 `7C1231E2C76903D82AE9922C00AA50DF7E616E1DF739264CCBA6A28D4BB169B1`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is the NeoForge 1.21.1 reachable legacy closure of
**RotP Tusk 1.0.2**.

- Canonical source artifact: `RotP-牙-1.0.2.jar`
- Canonical SHA-256: `7C1231E2C76903D82AE9922C00AA50DF7E616E1DF739264CCBA6A28D4BB169B1`
- Canonical decompile: `058_RotP-牙-1.0.2`
- Original authors: HK47bot, TheDoGGyS
- Original credits: StandoByte, Tilto, TheDoGGyS, White Mind
- License: GNU General Public License v3.0

The port preserves Tusk Acts 1 through 4, including the exact model closures,
textures, glow masks, icons, reachable animations, summon and melee audio,
nail state, normal and charged nail shots, nail scratch, Tea, the nail
projectile, the damaging rotational wormhole, the swipe effect, linked
teleporting wormholes, the remote-arm variants, the guarded double-crouch
acceleration request, and Act 4's direct melee, grab, guard, leap, and
uppercut topology.

The Act 2 summon event restores both donor variants. Nail rendering now selects
the donor's exact `nail_trace1` through `nail_trace4` textures from the
shooter's current fixed Act. The registered client HUD uses the donor's exact
`act_gui.png` slot and selection sprites with each current Act's skin-owned
Stand icon.

The donor shapeless Tea recipe is restored under the singular 1.21 recipe
directory. Its three plant ingredients are unchanged; the legacy Forge NBT
water-potion ingredient is migrated to NeoForge's component ingredient and
the result uses the current `id` field.

Nail shots follow what the donor does in play, not what its unused code
suggests. The donor nail never overrides `constVelocity()`, so its 1.5 gravity
and the tick-5 `setNoGravity(false)` never apply: a nail keeps its launch
velocity until it hits something or passes 360 ticks. A nail is removed on its
first block contact; a soft block it breaks only lets the rest of that tick's
path be checked, and a charged nail opens its wormhole there. A held shot
fires on held ticks 5, 10 and 15, and Creative skips its free-hand rule. With
the remote arm out, the arm turns to the exact point its owner's 100-block ray
hits, and a shot leaves 0.5 above the arm along the arm's own rotation at 2.0
plus the arm's motion, with the shot sound at the user's eyes.

Act 3 operations use addon-local persisted UUID ownership and pair records;
runtime entity IDs, singleton handshakes, and indefinite chunk forcing are not
preserved.

The linked portals and the remote arm move like the donor's
`AbstractWormholeEntity`: every server tick each one moves by its motion plus
`(0, -0.7, 0)`, and when its last move was stopped vertically (the donor's
`field_70124_G`, `collidedVertically`) it first makes a step-up move of its
motion plus `(0, 0.7, 0)`. A wormhole nobody moves therefore sinks until it
rests on the ground. A held move sets the motion and makes that tick's move
itself. As in the donor, the exit portal keeps the motion of its last held
move after release, and the arm keeps only the vertical part. The arm counts
the nails shot through it and closes when the nails left minus that count
reach zero (five shots from ten nails, ten in Creative); unlike the donor's
field the count is saved with the entity. As in the donor, a linked portal
never teleports another wormhole that comes to rest in it (a portal, a remote
arm or a damaging wormhole); mobs, players and other entities are still sent.

The damaging wormhole of a charged nail makes the same per-tick move, with
block collision, so one opened in the air or on a wall sinks to the ground.
With a target it then moves, as the donor's `WormholeEntity.tick` does, by its
motion plus the way to the target scaled by `(0.15, 1, 0.15)`, and hurts the
target for 1 in every tick that ends closer than 0.2 (squared) to it; it keeps
moving while in range.

Infinite Rotation preserves the legacy Horse plus Resolve IV charge, its
contextual replacement of the Act 4 uppercut, 425 stamina cost, forward-half
Stand dash, one first-target effect toggle per action, mount propagation,
persistent application anchor, uncurable effect, charge consumption, and nail
refill. Mount propagation keeps the donor's `new EffectInstance(this)`: its
zero duration is dropped on the mount's next tick before any rotation tick
runs, so a victim's living mount never keeps Infinite Rotation itself.
The donor's client render hook is restored: any entity with the tracked effect
is spun `partialTick * -225` degrees around Y and shaken on X/Z before its
model renders.
The donor configures `disableBlocking(100.0F)` and then
`damage(0.0F)` on its punch. Core execution returns on nonpositive damage
before hurt and shield handling, so the observable donor result has no damage,
damage-source event, blocking interruption, or shield cooldown. The original
action icon, effect icon, and `tusk_infinite_rotation` animation are copied
from the canonical artifact.
Its legacy root and right-arm channel names are mapped to the H1 model's
`stand_pos` and `right_arm_xrot` groups without changing keyframes.

Open Space preserves the Act 4-only 225 stamina action, six-block target
range, twelve-block maximum depth, and 200-tick opening. The unsafe
`block_swapper` entity is intentionally replaced by addon-local per-dimension
saved leases. Leases snapshot block state and block-entity NBT, suppress item
drops, never force chunks, defer restoration while an entity occupies the
space, survive reloads, and relinquish ownership instead of overwriting a
foreign replacement.

Act 4 stopped-time awareness uses the public
`Ability.canUserSeeInStoppedTime` extension. The donor's movement mixin is
narrower than the former symmetric time-stop movement label: it bypasses the
freeze only for a `TuskEntity` whose non-null user is on Act 4.
It does not authorize the player body. The module now carries a stricter additive
authorizer predicate that also requires a live, currently manifested Act 4
Tusk entity and explicitly rejects other Stands, unmanifested or stale Tusk
entities, and Tusk Acts 1 through 3. It is registered through the public
owner-keyed movement-authorizer API behind the exact
`FEATURE_TIME_STOP_ENTITY_MOVEMENT_AUTHORIZERS_V1` fail-fast guard. Final clean
artifact validation passes against the replacement core bootstrap pin; live
validation remains pending. The legacy artifact contains no Tusk post-effect
shader or post chain, so there is no legacy post-effect asset to register
through the available lifecycle.

The donor `rotp_t:tusk_ost` `OstSoundList` is restored in the shared Stand
factory. The mutable legacy Stand became four fixed current Stand types, so
all four inherit the same list and its seven generated SoundEvents. The
canonical artifact provides no OST definitions or OGG files; the registered
family remains intentionally silent and no replacement media is invented.
The unregistered `johnny_arigato` voice, two named example placeholders, and
unreferenced `rotation.png` are the four proven-inert C rows retained
byte-exactly only under `assets/rotp_t/legacy_archive`. The donor manifest
runtime-registers `mixins.rotp_t.json`, so that descriptor is an A semantic
conversion to fixed Stand types, serialized Act rules, public time-stop
awareness, current ability/input owners, and current render owners. Its donor
bytes remain in the module archive for provenance, while its incompatible
former root path is absent.
