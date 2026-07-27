# Rollback transaction ABI foundation

## Status

This change adds negotiation and lifecycle infrastructure only. It does not
implement world rewind.

- `RotpAddonApi` does not advertise `rollback_transaction_v1`.
- `RollbackSupportMatrix` reports `UNSUPPORTED` for every required surface.
- `RollbackReadiness` is never `READY`.
- `RollbackTransactionLedger.commit` has no world-apply branch and returns
  `FEATURE_UNAVAILABLE` for an otherwise valid request.
- No rollback capture or mutation mixin was added.
- Mandom and BTD remain locked against this ABI.

The existing `time_stop_lifecycle_v1` feature is unrelated. Time stop pauses
selected execution; it does not capture preimages or restore ordered world
mutation.

## Ownership and authority

`RollbackTransactionManager` is a non-serializable `ServerLevel` attachment.
It is the only production owner of the transaction ledger for that dimension.
There is no static player, entity, handle, or transaction map.

Each accepted `RollbackHandle` contains only immutable identity metadata:

- opaque transaction UUID;
- owner UUID;
- one dimension key;
- a process-local server epoch.

`ServerSavedData.runtimeEpoch` is created anew when saved data is constructed
or loaded and is intentionally omitted from NBT. Handles therefore fail with
`SERVER_EPOCH_MISMATCH` after restart.

The public facade accepts `ServerPlayer` for begin and commit. It rejects
off-thread calls, owner mismatch, dimension mismatch, server-epoch mismatch,
unknown handles, invalid scopes, unloaded declared chunks, expired handles,
and exceeded hard limits. No client packet is an authorization path.

Lifecycle subscribers invalidate transactions on owner logout, owner
dimension change, level unload, and server stop. The manager also invalidates
if any declared chunk becomes unloaded. It calls `ServerLevel.hasChunk` only;
this foundation neither loads chunks nor acquires tickets. A future
operational implementation must ticket only the already declared bounded
scope before capture begins.

## Bounds

One six-second history has 120 tick intervals and at most 121 frame
boundaries. A discontinuity resets boundary continuity rather than pretending
that missed frames exist.

| Resource | Hard limit |
| --- | ---: |
| Active transactions per level | 8 |
| Retained transaction records per level | 32 |
| Frame boundaries per transaction | 121 |
| Declared chunks | 25 |
| Entities | 256 |
| Block mutations | 4,096 |
| Container slots | 2,048 |
| Serialized bytes | 8 MiB |
| Capture time per tick | 2 ms |
| Expiry | 120 to 200 ticks |
| Adapter descriptors | 64 |
| Total adapter-declared bytes | 8 MiB |

Cumulative usage counters and journal sequence numbers are monotonic; the
capture-time counter resets at each tick boundary. Crossing a limit
invalidates the transaction; the ledger does not silently truncate usage.
The ring rotates only at its defined 121-boundary window.

## Evidence matrix

The following matrix records the current source evidence and the missing
atomic behavior. Every row remains `UNSUPPORTED`.

| Surface | Current source evidence | Missing requirement |
| --- | --- | --- |
| Players | Player power and inventory attachments plus ordinary sync hooks in `core/EventHandler.java` | No immutable per-frame transform, health/effect, power, inventory, or selected-slot preimage; no inverse apply |
| Other living entities | Behavior-specific `LivingDeathEvent` and entity tick handlers in `core/EventHandler.java` | No bounded codec snapshot, ordered creation/removal/death journal, or inverse recreation |
| Projectiles | Projectile behavior and item-location mixins under `mixin/itemtracking/track/projectile` | No complete owner, transform, motion, age, pickup-state snapshot or mutation journal |
| Dropped item entities | `ItemTrackingEventHandler` observes tracked item entities joining a level | Location observation is not transaction-local lineage and does not cover all drops, pickup, split, or merge edges |
| Other entities | `EntityJoinLevelEvent` is used for feature-specific initialization in `core/EventHandler.java` | No opt-in codec registry wired to capture/preflight/inverse apply; unsupported mutations are not detected |
| Blocks | `mixin/stand/crazyd/LevelDestroyBlockMixin.java` stores a temporary preimage only for successful no-drop `destroyBlock` | No general pre-`setBlock`/destroy interception, total mutation order, suppression, or inverse journal |
| Block entities | The Crazy Diamond hook retains an in-memory block entity reference in its specialized path | No immutable type plus NBT/codec preimage before every mutation; no capability adapter coverage |
| Containers | Item-tracking mixins observe selected `setItem`, slot, equipment, and inventory operations | No complete slot ownership graph, opaque-handler invalidation, preimage capture, or atomic restore |
| Item movement and drops | `subsystems/itemtracking/ItemTracking.java` stores a persisted UUID-to-location map and rejects stacks whose count is not one | No transaction-local lineage IDs or ordered split, merge, move, consume, craft, drop, pickup, and death-drop edges |
| Death and removal | Feature handlers consume `LivingDeathEvent`; no general leave/removal journal exists | No ordered cause, drops, XP, replacement relationship, removal, or inverse recreation record |
| Scheduled block/fluid ticks | `mixin/timestop/ServerLevelTimeStopTickMixin.java` cancels execution during time stop and schedules a later tick | Rescheduling is not queue capture; there is no rollback snapshot or touched-scheduler invalidation |
| Allowlisted world state | No rollback world-state adapter is wired into level mutation | No bounded capture, codec, validator, ordered apply, or inverse state |
| Addon state | `RollbackAdapterRegistry` reserves bounded descriptor IDs and deterministic order | Descriptor registration has no operational codec/capture/preflight/apply contract and cannot elevate support |

Existing item tracking is especially insufficient for anti-duplication:
`ItemTracking.startTracking` explicitly supports only `ItemStack` count one,
while a rollback requires one transaction-local ownership graph spanning
inventories, containers, dropped entities, crafting, consumption, death
drops, split, and merge.

## Public foundation

The public package `api.rollback` supplies:

- capability and support types plus the immutable support matrix;
- bounded scope and capture policy;
- opaque handle, readiness, result, failure, and invalidation types;
- server-authoritative `begin`, `readiness`, `commit`, and `invalidate`
  facade methods;
- a deterministic capture/apply-ordered, freeze-on-first-begin adapter
  descriptor registry.

Adapter registration accepts only addon state and allowlisted world state,
requires a declared inverse guarantee, rejects duplicate IDs and invalid
bounds, and freezes before transaction creation. Because no adapter codec or
inverse executor exists yet, descriptors do not produce `ADAPTER_ONLY` or
`CORE_ATOMIC` support.

The proposal's capture and commit callbacks are intentionally not published
in this foundation. Publishing callbacks that never correspond to real frame
capture, preflight, apply, inverse recovery, and synchronization would create
a false lifecycle contract.

## Atomicity gate

An operational feature may be advertised only after all matrix rows are
`CORE_ATOMIC` in one transaction and commit implements:

1. freeze of the declared scope;
2. complete preflight of chunks, preimages, lineage, limits, and adapters;
3. apply under drop/event suppression while building an inverse journal;
4. inverse replay on every apply failure;
5. synchronization only after successful apply;
6. deterministic release of tickets, guards, handles, and journals.

This foundation performs no world mutation, so it cannot report partial
success. It also cannot claim rollback support: there is no inverse journal.

## Validation

`RollbackTransactionFoundationSmokeTest` is called by the existing
`addonApiSmokeTest` task. It covers feature absence, the unsupported matrix,
policy bounds, the 121-boundary ring, frame-gap reset, owner/dimension/epoch
and thread rejection, duration checks, limit and lifecycle invalidation,
bounded manager records, descriptor validation/order/freeze, and the absence
of static manager maps or entity references in handles.
