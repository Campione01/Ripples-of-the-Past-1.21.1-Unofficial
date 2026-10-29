# Ripples of the Past Reworked (`rejojo`): source and license

## Public release

- Module: `rotp-reworked`, mod id `rejojo`, version `1.0.5-port.11`.
- Original 1.16.5 add-on: `RotP-Reworked-1.0.5.jar` (Ripples of the Past Reworked, mod id `rejojo`), by dikiytechies; declared license: GNU GPL v3.0; SHA-256 `0872F29ECAE7CE7B9438185FC067A32E70931AA60B1445725A4D732AF88C7F8A`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is an independent NeoForge 1.21.1 migration of
`RotP-Reworked-1.0.5.jar` (`rejojo`).

Canonical artifact SHA-256:
`0872F29ECAE7CE7B9438185FC067A32E70931AA60B1445725A4D732AF88C7F8A`.

The canonical metadata declares GPLv3 and credits `dikiytechies`. This
migration remains GPL-3.0-only. It retains the independently expressible Slow
Burn and resolve-reward behavior, restores the canonical action registry and
repeat-input variations through reviewed public APIs, restores the donor MR
flame and stand-specific time-stop cooldown values through public NeoForge and
core lifecycle events, and preserves the current input template, state
settlement, rendering, and UI behavior.

The 15 historical UI payloads plus the old mixin and pack metadata are
retained byte-for-byte under `assets/rejojo/legacy_archive/`. Foreign
`assets/jojo/**` entries are rehomed below
`assets/rejojo/legacy_archive/foreign/jojo/` and their three historical source
paths remain exact active copies. All fourteen donor action icons are copied
to current ability-atlas paths; two are `jojo` fallback sprites selected when
`enableNewTSIcons` is disabled. The donor time-stop effect icon is also copied
to the current core effect namespace. Only the old mixin and pack metadata
remain inert.

See `META-INF/migration/` in the built JAR for source and resource
dispositions, resolved API blockers, and static verification evidence.
