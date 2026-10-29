# RotP Killer Queen (`rotp_zkq`): source and license

## Public release

- Module: `rotp-killer-queen`, mod id `rotp_zkq`, version `1.6.10-port.25`.
- Original 1.16.5 add-on: `RotP-Killer_Queen-1.6.10b添加败者食尘修复.jar` (RotP Killer Queen, mod id `rotp_zkq`), by Makuta Zeml, StandoByte, Model by: Lanostry, StandSkins by: MlgMC; declared license: GNU GPL v3.0; SHA-256 `D4C302283C9A5CF2134EB7A4AFB154464065C30366BB2DC6AEA3620B01EB539F`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Killer Queen Source And License Record

Canonical merged input:

`RotP-Killer_Queen-1.6.10b添加败者食尘修复.jar`

Size and SHA-256:

`2,897,011 bytes`

`D4C302283C9A5CF2134EB7A4AFB154464065C30366BB2DC6AEA3620B01EB539F`

Authoritative CFR source:

`RotP-Killer_Queen-1.6.10b添加败者食尘修复`

The canonical merge keeps the legacy 1.6.10 resources and metadata and
replaces only twelve Bites the Dust related source/class files with the
preserved 1.6.9b fix donor. The numbered `020_` and `021_` CFR directories
contain the same 72 Java files byte-for-byte and are duplicate retry records.

Legacy metadata declares GNU GPL v3 and credits Makuta Zeml and StandoByte,
with the model credited to Lanostry and Stand skins credited to
MlgMC'Funkadelic'Player. This port uses the SPDX identifier
`GPL-3.0-only`.

The playable closure implements all eighteen custom legacy actions: living,
item, and block First Bomb loops; snow and bubble bombs; manual projectile
detonation; Sheer Heart Attack with recall; and the five-action Bites the Dust
chain. The five legacy base-combat action IDs map to current shared humanoid
combat controls. The full shared grab chain and rebindable charged-heavy input
are retained.

Snow and bubble bombs keep the donor's player-owned projectile lifecycle.
Launching needs the summoned Stand, but a bomb already in flight survives an
ordinary Stand recall; it ends at its 100-tick lifespan, on impact or manual
detonation, or when its owner dies. The port additionally ends it when the
owner leaves the bomb's dimension or no longer has Killer Queen. As in the
donor, a bomb is drawn for its whole flight to viewers who can see Stands.
The port draws the snow bomb in the Stand skin stored at launch; a bomb's
alpha follows the viewer's Stand opacity setting alone, never the launching
Stand.

The reproducible resource converter preserves the semantic 31-bone,
37-cube legacy model conversion, native source texture dimensions, all
eighteen active custom action icons/animations, all 25 canonical sound
files, and the requested seven non-default skins (`skinb` through `skinh`).
No texture is upscaled. Primary Stand and marker rendering uses the core
world Stand visual context, alpha buffer, and Stand translucent material.
Snow and bubble bombs keep the alpha buffer and Stand translucent material;
Sheer Heart Attack renders as a mob to viewers who can see Stands.

Bites the Dust preserves the merged donor's five registered action IDs,
contextual mark/reset/remove/enter/quit chain, 60-tick reset charge,
2,000-tick cooldown, 24,000-tick marker lifetime, armed victim protection,
40-tick player-attacker delay, smoke/flame trigger cues, player-position
rewind, day-time rewind, owner healing, and condemned-attacker death. Rewind
execution is server-authoritative
and scopes the transaction to those donor-visible mutations. It bounds the
serialized player preimages to 256 entries and pending triggers to 128, fully
preflights destinations, applies deterministically, and retains inverse
preimages so the core `AtomicRollbackJournal` compensates every attempted
entry in reverse order after a partial apply. The repaired donor's dedicated
`textures/power/bites_za_dust.png` world marker and its four supplied skin
overrides identify the armed victim. Tracking, reconnect, death, logout,
dimension-change, level-unload, server-stop, power-loss, and manual cleanup
paths are synchronized without introducing a global mutable player map.

The canonical JAR registers generated `kq_ost` IDs but contains no OST OGG
media, so OST playback has no source asset to migrate. No legacy packet,
global player/entity map, command-driven rewind, mixin, shader, post chain,
framebuffer access, PlayerRenderer injection, Iris mutation, or global
renderer mutation is copied.
