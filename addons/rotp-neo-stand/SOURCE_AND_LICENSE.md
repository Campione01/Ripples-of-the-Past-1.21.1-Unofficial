# NEOJOJOSTAND (`neojojostand`): source and license

## Public release

- Module: `rotp-neo-stand`, mod id `neojojostand`, version `0.2-A-port.17`.
- Original 1.16.5 add-on: `RotP-neostand-0.2-A.jar` (NEOJOJOSTAND, mod id `neojojostand`), by 浪浪; declared license: GNU GPL v3.0; SHA-256 `6068721A09009AF8AE490DCD8F68F440A80BA94430F02ECB320F553ECDE7C965`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Neo Stand source and license

This module is a clean migration of `RotP-neostand-0.2-A.jar`, whose
canonical metadata identifies the mod as `neojojostand`, the author as
`浪浪`, and the license as GNU GPL v3.0.

The canonical artifact is retained as migration evidence. Runtime code in this
module targets Minecraft 1.21.1, NeoForge 21.1.197, and the pinned Ripples of
the Past addon ABI.

Six donor resources that were reachable from registered gameplay are restored
byte-exactly to legal 1.21.1 owners: four action icons under the current
`textures/ability` convention, the Hierophant emerald trail texture, and the
registered Bullet Time effect icon. The emerald texture is consumed by a
client-tick sampled 30-block trail with entity/world cleanup; the registered
emerald presentation restores the donor's second full-bright pass through a
scoped core post-render extension using the current core renderer, model,
texture, and skin. Bullet Time couples the registered marker effect to a
server-owned regional field, typed observer payload, late-observer sync,
paired cancel, donor-style countdown GUI layer, and an owner-keyed core
regional policy for timer, particle-motion, and sound-pitch dilation. The core
hooks replace the donor reflection with narrow typed access and retain no
global timer or renderer ownership.

Three archived `jojo` animation JSON files are byte-identical to the pinned
1.16.5 core payloads, and the two `.bbmodel` files are Blockbench authoring
sources with no runtime loader. Their exact donor bytes remain under
`assets/neojojostand/legacy_archive`. The legacy mixin descriptor and old
`pack.mcmeta` are retained under the same owner archive as inert root
metadata. The descriptor's sole `TimeStopInstanceMixin` duplicated the
donor's separate held-speed write. The donor manifest has no `MixinConfigs`,
its mods metadata has no connector, and donor code contains no configuration
reference, so the descriptor was never registered; it is not exposed at its
former root path. The registered timer-scale packet receiver likewise has no
constructor or send call in the canonical decompile and remains orphan
evidence rather than an invented runtime owner.
