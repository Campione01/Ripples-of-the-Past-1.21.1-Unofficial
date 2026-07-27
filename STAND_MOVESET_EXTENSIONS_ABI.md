# Stand Moveset Extensions ABI

Feature: `stand_moveset_extensions_v1`

ABI compatibility remains `RotpAddonApi.ABI_VERSION == 1`.

## Registration

Addons register an immutable `StandMovesetExtensions.Extension` during common
setup. Every definition declares:

- target Stand `ResourceLocation`;
- globally unique extension `ResourceLocation`;
- explicit integer order;
- declarative ability, Stand skill, and existing-hotbar operations.

Definitions apply in stable `(order, extensionId)` order. Registering the same ID
with an equal declarative definition is a no-op. Reusing an ID with a different
target, order, or operation list throws `IllegalStateException`.

The ability type `ResourceLocation`, rather than supplier identity, is part of the
definition. The supplier is resolved only when a matching moveset is built and
must return an ability type with that exact registry key.

## Restricted Builder

The public builder can:

- add a named ability from an explicitly identified ability type;
- add a declarative `StandSkill`;
- append a CLICK or HOLD entry to an existing named control scheme and existing
  hotbar ID.

It does not expose a `MovesetBuilder`, `PowerType.getDefaultMoveset`,
`MovesetBuilder.controlSchemes`, or private fields. It cannot create a control
scheme or hotbar. Missing abilities, schemes, hotbars, null suppliers, duplicate
skill names, and ability-type ID mismatches fail fast with
`IllegalStateException`.

## Lifecycle

Applied extension IDs are copied with internal moveset-builder copies. Equal
registration and repeated application are therefore idempotent. Extensions remain
present exactly once, in the same order, across:

- base moveset refresh;
- repeated user moveset creation;
- config application;
- default restoration;
- client default control-scheme construction.

Registering an extension for an absent target is safe and inert. It applies later
if a Stand with the matching ID builds a moveset.

## Non-Goals

Version 1 does not replace existing abilities or skills, create schemes or
hotbars, edit sibling source, add input bindings outside CLICK/HOLD hotbar
entries, or provide rollback/BTD behavior.
