# RotP Pearl Jam (`rotp_pj`): source and license

## Public release

- Module: `rotp-pearl-jam`, mod id `rotp_pj`, version `1.0.3-port.19`.
- Original 1.16.5 add-on: `RotP-珍珠果酱-1.0.3.jar` (RotP-PearlJam, mod id `rotp_pj`), by Weever; declared license: GNU GPL v3.0; SHA-256 `9C8B98E92CD2D148CE0D2D7E74E556273AA7EB1838F59C749820891FC75C315E`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Pearl Jam source and license

This module is a clean NeoForge 1.21.1 migration of
`RotP-珍珠果酱-1.0.3.jar`.

- Original mod id: `rotp_pj`
- Original author: Weever
- Original declared license: GNU GPL v3.0
- Canonical artifact size: `250877` bytes
- Canonical artifact SHA-256:
  `9C8B98E92CD2D148CE0D2D7E74E556273AA7EB1838F59C749820891FC75C315E`
- Selected canonical decompile:
  `059_RotP-珍珠果酱-1.0.3`
- Byte-identical decompile mirrors:
  `057_RotP-珍珠果酱-1.0.3` and
  `058_RotP-珍珠果酱-1.0.3`

The legacy JAR has no bundled license text. Its authoritative
`mods.toml` declares `license="GNU GPL v3.0"`, so this module declares
`GPL-3.0-only`. Gameplay and resources were reconstructed from the
canonical artifact and selected decompile. Copied binary assets remain
attributable to Weever.

The canonical entity texture is retained only as hash-locked evidence under
`assets/rotp_pj/legacy_archive/source/` because it is an almost-transparent
shared placeholder. It is not a fallback, skin resource, model texture, or
production-code target. The physical 256x256 multi-member material atlas is
newly generated from the legacy Pearl Jam power icon as a visual reference;
its chroma and alpha masters, final hash, and prompt-derived disposition are
recorded under `migration/art` and `migration/resource-closure.json`. Runtime
and sources JARs package the locked B1 retention evidence.

Canonical topology and cooking behavior were also checked against:
<https://jojowiki.com/Pearl_Jam>.
