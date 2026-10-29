# RotP Sticky Fingers (`rotp_stfn`): source and license

## Public release

- Module: `rotp-sticky-fingers`, mod id `rotp_stfn`, version `1.0.1-port.29`.
- Original 1.16.5 add-on: `RotP-钢链手指-1.0.1.jar` (RotP Sticky Fingers, mod id `rotp_stfn`), by HK47bot, StandoByte, DikiyTechies, TheDoGGyS, Weever; declared license: GNU GPL v3.0; SHA-256 `FBF5BB6C46C1F885BDD5D742E0772DA5522EF3875ECE9D90E6D5D0E4D26F97CF`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Sticky Fingers source and license

This module is a bounded split and NeoForge 1.21.1 port of Sticky Fingers
from:

`RotP-钢链手指-1.0.1.jar`

Source JAR SHA-256:

`FBF5BB6C46C1F885BDD5D742E0772DA5522EF3875ECE9D90E6D5D0E4D26F97CF`

The canonical Java audit used the successful CFR 0.152 decompile at:

`072_RotP-钢链手指-1.0.1`

The original mod declares GNU GPL v3.0 and credits HK47bot, StandoByte,
DikiyTechies, TheDoGGyS, and Weever. The selected Sticky Fingers geometry,
animations, textures, sounds, translations, and gameplay behavior remain
under that upstream license.

This build contains the independently playable combat slice: Stand
registration, standard humanoid melee, the complete current grab chain, the
source-backed extended punch, connected reversible zipper rendering, zipper
climbing, hide-in-mob possession, zip-wound healing,
the server-owned unzip-finisher/body-part lifecycle,
persistent entity, inner, and block zipper storage, and the repaired
Stand-bearing-creatures combo.

The zipper is server-authoritative and available only on the special wheel.
It leases the clicked safe block through addon-owned per-dimension SavedData
and a matching addon block entity. Each lease records the original
BlockState, optional full BlockEntity NBT, owner, dimension, creation and
expiry times, open state, and close reason. The temporary block is included
in `jojo_ripples:crazy_d_cannot_restore`.

A plain press on a zipper removes the zipper and restores the snapshot; a
Shift press toggles it between closed and open, for any Sticky Fingers user.
Restoration is also requested on timeout, ability or summon loss, logout,
death, dimension change, excessive owner distance, chunk unload, and
server restart. Unloaded chunks keep a persistent pending close and restore
on the first safe server tick after reload. The open and close transitions
retain the canonical `bruno_unzip` and `bruno_zip` voice events.

Only full-collision, non-fluid, non-protected blocks are accepted by default.
Signs, hanging signs, banners, and skulls are the complete decorative
BlockEntity allowlist; container and modded BlockEntities are rejected. The
mutation suppresses drops and neighbor cascades, never accepts a
client-selected position, and does not use a core mixin or global mutable
map.

Since R764 (owner decision of 2026-10-07, registry D-rotp-sticky-fingers-10)
an open zipper is the donor's hollow, not an empty cell. The zipper block
keeps a 0.01 plate on every side whose neighbour is free space, a door, a
trapdoor, a liquid, or the plated side of another zipper, has no plate on
the side that carries the zipper, and leaves solid neighbours to block by
themselves, so neighbouring open zippers join. This holds for every entity,
and nobody suffocates inside. The far side follows the donor's placing rule:
on a wall one block thick with free space behind it the same block is also
open on the side opposite to its facing (blockstate `through`, drawn on both
faces); on a wall two blocks thick a second zipper block replaces the back
block and the two leases name each other; a thicker wall, no free cell
behind, a back block without a sturdy far face, a back block that cannot
take a zipper, or a full lease cap gives a single zipper. Linked zippers and
touching zippers of the same facing open and close together, a new zipper
closes the open ones of its facing that it touches, and removing or losing a
zipper removes its linked zipper; each restores its own snapshot. Zippers
touch sideways and diagonally. A zipper whose faced cell is itself a zipper
block, which only the port's replaced blocks allow, joins no group on that
side: it neither closes nor toggles the zippers in front of it or beside
those, so zipping onward from inside an open doorway or floor pit keeps the
opening open, and such a zipper is toggled by its own Shift press. As in the
donor, a user who is in the ground and zips a block through to a free cell
without making a linked pair gets a second single zipper on the block below
(when the cell under the exit is free, else on the block above when that
cell is free), which gives a two-high exit. A zipper block is never
walkable for mob pathfinding, open or closed, as the donor's wall block
behind a zipper is not. The replace-and-restore lease and the rule for
which blocks can take a zipper are unchanged.

Zip Wounds preserves the canonical 1200-tick duration, 250 stamina, 100-tick
cooldown, two-health healing pulse every 50 ticks, bleeding removal, and
break-on-damage-above-two rule for both the user and an aimed target. A
per-dimension combat lease ties the effect to its Sticky Fingers owner so
logout, death, dimension change, Stand loss, manual effect removal, and
expiry cannot leave orphan state.

The heavy-punch finisher (unzip punch) preserves half heavy-punch base damage,
the 100-tick harmful zipper effect and its 1.5x incoming-damage
vulnerability, and records the body region its user aimed at when it started.
As in the donor it takes no limb by itself: its three Resolve-1 follow-ups
(unzip head, arm and leg) are offered on the heavy key for the recorded region
while it runs at a supported humanoid; a press costs 50 stamina, and the part
comes off at the end of the punch's recovery, which heals the target by half
the actual damage dealt. As in the donor the punch adds no heavy knockback and
turns the Stand to the centre of the recorded part; the arm is chosen by the
donor's two rolls (with both arms on, sometimes none), the leg by the target's
main arm; a head appears on top of the target, an arm or leg at its feet. It
also keeps
the skinned detached head/arm/leg entities, held-item arm impairment,
canonical leg slowdown, damage forwarding, owner recovery, assisted homing
return, persistent NBT, and a bounded 1200-tick expiry. Supported targets are
explicitly and extensibly tagged in `rotp_stfn:zipper_humanoids`; the default
tag includes armor stands and all vanilla `AbstractIllager` renderers as well
as players, zombies, and skeletons.

Entity and inner storage share the canonical nine-slot inventory keyed by
the target UUID. Block storage preserves the canonical eighteen-slot layout
and is keyed by dimension plus block position. Both menu types are opened
only from server-recomputed targets, sync through vanilla container slots,
and continuously validate the server-created viewer/target session, active
Sticky Fingers ownership, target or block existence, and five-block range.
Entity storage remains shared between Sticky Fingers users as upstream did;
block storage is hardened to the first server-side opener and, for the
temporary zipper block, must also match the zipper lease owner.

Storage records are held in overworld SavedData rather than a static global
map. Player storage survives death only when keepInventory is enabled.
Otherwise entity death or destruction and block invalidation close all
viewers, remove the record before draining it, and drop each stored stack
once. Logout, respawn, dimension change, chunk unload, Stand loss, and
unsummon close affected sessions without deleting valid persistent storage.

Zipper climbing is a held public EntityAction with the canonical activation
stamina and cooldown. Hide in Mob uses the core possession attachment for
camera, synchronization, spectator-mode restoration, and target tracking,
with owner-side host-damage ejection.

Detached-body presentation remains server-owned. `ZipperCombatData` sends
only the target UUID and a five-bit detached-part mask when the lease changes
and when a player starts tracking the target. Carried state uses entity
`SynchedEntityData` and vanilla passenger tracking. Pickup requires the main
hand, an empty usable offhand, and a server-side interaction; the passenger
uses the donor 0.55-width side, 0.35-height, and 0.75-width forward offset.

Player head, hat, arm, sleeve, leg, and pants visibility uses
`ScopedPlayerModelVisibility`; matching humanoid armor pieces use
`ScopedHumanoidArmorVisibility`, applied after vanilla slot visibility and
restored after each armor render. The offhand carrying pose uses
`ScopedPlayerModelPose` plus one NeoForge extensible arm pose, then
`HumanoidModelPostSetup` reapplies the donor absolute `-PI/10` rotation after
vanilla attack, crouch, bob, and swim adjustments. Supported non-player
`HumanoidModel` and `IllagerModel` instances retain their bounded base-model
snapshots. First-person hands are suppressed while the detached head owns the
camera, and a detached local hand is suppressed in its matching view.

The detached head uses the core `ClientEntityController`. It acquires only
when no foreign controller or camera owns the view, forwards body movement and
rotation, supplies the local body as the owner-scoped vanilla pick source, and
opts the local body into vanilla controlled-camera movement and creative-flight
handling. It releases only its own controller on restoration, entity loss,
player loss, logout, or foreign takeover. The addon does not replace
`PlayerRenderer`, install a model or game-renderer mixin, use reflection, or
mutate a global render type, framebuffer, shader, or post chain.

The donor `HeadRespawnPacket` path is reachable, not a placeholder. Its client
event adds a `deathScreen.respawn` or `deathScreen.spectate` button to
`ChatScreen` while the detached head is the camera, and its C2S handler clears hurt
invulnerability and fire resistance before applying lethal damage. The port
restores that exact screen trigger and button placement. Its compact C2S
control payload contains only an action ID; the server validates that the
sender is alive and currently has a detached head before applying the kill.

Carried-part release is also server-owned. NeoForge's cancellable
`LivingSwapItemsEvent.Hands`, which fires from
`ServerGamePacketListenerImpl.handlePlayerAction`, releases the passenger and
cancels the ordinary offhand swap. Vanilla `DROP_ITEM` and `DROP_ALL_ITEMS`
packets pass through the core `CarriedPassengerActionHooks` registry at
`handlePlayerAction` HEAD. The registry supplies the actual connection player;
the addon resolves its passenger from server state, stops riding, applies the
donor 1.2-forward throw velocity, and cancels vanilla item drop only after a
successful release. The custom control payload is now reserved for the
server-validated detached-head respawn action. The core packet hook resends the
authoritative selected slot because 1.21 `LocalPlayer.drop` removes that stack
before sending the packet.

The five precise detached-body core integration points are complete:

* `ScopedHumanoidArmorVisibility` scopes each armor piece between vanilla
  `setPartVisibility` and its render return.
* `ClientEntityController.getPickSource(Entity)` substitutes the owner body
  only while the controller owns the exact camera entity.
* `ClientEntityController.controlsLocalPlayerCamera()` participates in
  `LocalPlayer.isControlledCamera` without changing the vanilla true path.
* `HumanoidModelPostSetup` runs after the existing core Coco Jumbo carry
  adjustment and applies the final absolute carried-arm rotation.
* `CarriedPassengerActionHooks` handles only vanilla drop actions for the
  actual server connection player, then core restores the authoritative
  selected slot before cancelling a client-predicted drop.
* `CrazyDiamondRestoreExtensions` supplies side-effect-free target matching,
  server-only external restoration, and a post-living restore observer.
* `GoldExperienceExternalHealingTargets` carries one raw, classification, and
  healing target through the complete heal-other workflow.
* `LivingHandUseBlockers` is a deny-dominant physical-hand registry combined
  with the original item-hand-free result.

The donor JAR activates `mixins.rotp_stfn.json` through the manifest
`MixinConfigs` attribute. The descriptor is required, uses
`defaultRequire: 1`, and registers `CDHealMixin`,
`GoldExperienceHealMixin`, `HandMixin`, and
`ServerPlayNetHandlerMixin` in its common list, along with the other audited
common hooks. It also registers `BipedModelMixin`, `ClientPlayerMixin`,
`GameRendererMixin`, `ArmorStandArmorModelMixin`, `IllagerModelMixin`,
`LivingRendererInvoker`, and `ModelRendererMixin` in its client list.
`ClientInit` separately registers `ClientEvents`, and common setup calls both
`CapabilityHandler.commonSetupRegister()` and `AddonPackets.init()`.
`EntityRemoveHeadPacket` is registered but inert: the canonical source has no
constructor/send site beyond its decoder, while the reachable camera trigger
is `UnzippedHeadEntity.readSpawnData`. `HeadUserSyncPacket` is empty and
unregistered. The donor-registered `ArmorStandArmorModelMixin` is restored by
the tagged armor-stand base-model scope plus the required armor-layer core
scope; `IllagerModelMixin` is restored directly through a scoped
`RenderLivingEvent` snapshot for evokers, illusioners, pillagers, and
vindicators.

The remaining four common mixins are registered and call-path reachable. They
are now implemented through owner-keyed core APIs rather than addon mixins.
`CDHealMixin` runs after every Crazy Diamond living-target restore attempt: it
starts a directly targeted foreign body part returning, or starts all five
loaded parts recorded on a healed owner returning, regardless of the original
heal result. The port registers both direct-part and owner-target behavior with
`CrazyDiamondRestoreExtensions`. `GoldExperienceHealMixin` only makes a body
part inherit its owner's living/nonliving classification; later donor checks
and effects still use the part. The current nonliving port retains the part as
the raw visual target, classifies through its owner, and applies vanilla
healing to that owner after the existing target, material, and stamina gates.
`HandMixin` overwrites `MCUtil.areHandsFree` once per requested hand, which
means its multi-hand result is the last hand only and can incorrectly override
an occupied-hand failure. The port intentionally uses veto-only,
all-requested-hands semantics instead of preserving that bug.
`ServerPlayNetHandlerMixin` handles only swap, drop, and drop-all. Despite the
packet class's old `CPlayerDiggingPacket` name, it does not intercept any
block-destroy action and supplies no donor mining behavior. Swap remains
event-owned; handled drop and drop-all actions restore the authoritative
selected slot before cancellation. Exact owners, methods, side/lifecycle
rules, and donor call chains are locked in
`migration/manifests/core-integrations.json`.

Detached entities use only their module renderer. Every active Stand-visible
secondary renderer resolves `StandVisualContext.world`, wraps its buffer with
`AlphaMultiBufferSource`, and uses a core Stand translucent render type without
mutating global render state.

### Legacy resource retention

The canonical extractor preserves the donor `zipper/none` model exactly in
the owner archive and writes an active semantic replacement whose particle
uses `rotp_stfn:block/dynamic_stand_icon`. The referenced PNG is extracted
byte-for-byte from the canonical 1.16.5 core donor and copied under
`textures/block`, where the 1.21.1 block atlas stitches it; the legacy
`assets/jojo/textures` copy remains as exact provenance. All 25
renderer-reachable zipper textures and eight registered or dynamically
selected action icons remain exact active resources. The foreign
`rotp_mwp` combo is repaired under the current `stand_combos` reload
directory with moveset-local action IDs.

Forty-two canonically dormant files remain exact owner archives: 30 zipper
authoring models never selected by the donor blockstate or renderer, nine
mini-corner/old-void textures never constructed by the donor renderer, and
three OGG streams absent from donor `sounds.json` and donor code references.
The donor combo, mixin descriptor, refmap, and access transformer are also
retained as four exact semantic-source archives while their repaired 1.21.1
implementations remain active elsewhere. Together with the exact donor
`zipper/none` model, the semantic-source archive count is five.

`migration/restore-legacy-resources.ps1` verifies the canonical JAR hash,
verifies and extracts the core donor particle texture, recreates the 47-file
archive without stale entries, writes exact and semantic active targets, and
regenerates both manifests. `legacy-resource-retention.json`
locks every archived source/target path, byte count, and SHA-256;
`resource-disposition.json` accounts for all 141 legacy resources, and
`donor-resource-restoration.json` records the 77-row audit closure.
