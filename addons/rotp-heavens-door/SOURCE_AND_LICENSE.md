# RotP - Heaven's Door (`rotp_hd`): source and license

## Public release

- Module: `rotp-heavens-door`, mod id `rotp_hd`, version `1.2-port.19`.
- Original 1.16.5 add-on: `RotP-天堂之门-1.2.jar` (RotP 天堂之门, mod id `rotp_hd`), by YarickTT, TheDoGGyS, HK47bot; declared license: GNU GPL v3.0; SHA-256 `B6EB5F2B9994206C722AAC1D9CB0E3C8066BA715CCAAF0A8E6121E707EC54469`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is an evidence-bound migration of the following local canonical
artifact:

- `RotP-天堂之门-1.2.jar`
- size: `519631` bytes
- SHA-256:
  `B6EB5F2B9994206C722AAC1D9CB0E3C8066BA715CCAAF0A8E6121E707EC54469`

The canonical metadata identifies the addon as version `1.2`, authored by
YarickTT, TheDoGGyS, and HK47bot, and licensed under GPL v3. Reused legacy
model, animation, texture, icon, and sound assets remain subject to that
license and their original authors' rights. The migration implementation is
provided under GPL v3 to preserve the canonical license boundary.

The original artifact's unsafe arbitrary-target client command packets,
reflection, global mixins, and inventory-wide page scans are not copied.
The port adds one read-only Entity persistent-data accessor to avoid allocating
empty tags during self-harm owner checks; it has no setter or injected behavior.
The owner-approved R768-W1 Cannot Move command additionally uses six narrow
input/acceleration hooks on Entity, LivingEntity, Player, Mob, AbstractHorse
and Boat. They are
conditional on this module's own effect and never reset external velocity or
replace core STUN, view, attacks, items, Stand actions or renderer ownership.
The world and first-person book overlays use core-owned additive render
surfaces; neither replaces `PlayerRenderer` nor cancels the vanilla arm.
`migration/manifests/full-closure.json` is the authoritative class, resource,
registry, packet, mixin, reachable-behavior, and blocker ledger.
`migration/controlled-entity-command-abi.md` specifies the remaining core ABI
requirements for behavior that cannot be expressed safely through public APIs.
