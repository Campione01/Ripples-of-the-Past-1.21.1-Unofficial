# RotP Frost Traveler (`rotp_zcs`): source and license

## Public release

- Module: `rotp-frost-traveler`, mod id `rotp_zcs`, version `1.2.1-port.26`.
- Original 1.16.5 add-on: `RotP-护霜旅行者-1.2.1.jar` (RotP 护霜旅行者, mod id `rotp_zcs`), by Makuta Zeml, StandoByte; declared license: GNU GPL v3.0; SHA-256 `B39D35D35AF8F34AB13F442E60DE3048EFF07E6D8A4DC3F38ED004DCBD88513C`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP Frost Traveler source and license

### Canonical source

- Artifact: `RotP-护霜旅行者-1.2.1.jar`
- SHA-256: `B39D35D35AF8F34AB13F442E60DE3048EFF07E6D8A4DC3F38ED004DCBD88513C`
- Size: `1,654,314` bytes
- Canonical decompile: `052_RotP-护霜旅行者-1.2.1`
- Decompiler: CFR 0.152
- Legacy mod id: `rotp_zcs`
- Legacy metadata version: `1.2.1`
- Legacy manifest implementation version: `1` (recorded mismatch)
- Legacy license declaration: GNU GPL v3.0
- Legacy authors: Makuta Zeml and StandoByte

The canonical JAR is authoritative for registrations, resources, and runtime
behavior. Decompiled source is supporting evidence.

### Port scope

This migration closes the complete legacy registration surface: one Stand,
nine abilities, two items, two projectiles, one particle, five direct sound
events, and all seven data tags. The legacy mixins and C2S packet are replaced
with module-local events, persistent attachments, synchronized item
components, and server-owned transient hold state. The Stand uses only the
special wheel; it has no fabricated humanoid basics and no grab chain.

The owner sprayer reconciler scans online inventories and already-loaded item
entities only. It never force-loads chunks. All gameplay effects, ammo,
cooldowns, disguise capture, conversion, and projectile hits are validated
on the logical server.

### Visual provenance

Active PNG and OGG resources are byte-for-byte extractions from the canonical
JAR. The main Stand texture is the canonical 256x256 image. The current
full-anchor humanoid geometry preserves the legacy logical 128x128 UV layout
and the custom right-forearm shell. The main Stand uses the core Stand
renderer. Projectile and disguise surfaces derive lifecycle opacity from the
real Stand controller through `StandVisualContext.world`,
`AlphaMultiBufferSource`, and the core Stand translucent material. No
PlayerRenderer, Iris, Sodium/Rubidium, shader, framebuffer, or post-processing
intrusion is introduced.

The disguise mask remains a canonical ordinary item that reports the HEAD
equipment slot. It is not an ArmorItem, does not register an armor material,
and never enters HumanoidArmorLayer. Its canonical item model already uses a
zero-scale head transform; the captured disguise is rendered only by the
module-local DisguiseLayer and stops as soon as the item leaves the head slot.
The module exposes read-only per-frame mask render receipts for client
acceptance. Those receipts observe production callbacks and registry state;
they do not create disguise state, alter equipment, or submit test rendering.

No completed visual-parity claim is made until the runtime evidence matrix in
the migration manifests is executed in game.

### License

This derivative port and copied canonical resources are distributed under GNU
GPL v3.0 only (`GPL-3.0-only`). No warranty is provided.
