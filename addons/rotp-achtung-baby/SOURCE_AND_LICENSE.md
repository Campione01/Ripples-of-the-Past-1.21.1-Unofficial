# RotP Achtung Baby (`rotp_ab`): source and license

## Public release

- Module: `rotp-achtung-baby`, mod id `rotp_ab`, version `1.0.0-port.17`.
- Original 1.16.5 add-on: `RotP-AchtungBaby-1.16.5-1.0.0.jar` (RotP-AchtungBaby, mod id `rotp_ab`), by Weever; declared license: GNU GPL v3.0; SHA-256 `0154BBD0576F2FCF5A5F4DDEA16ADF0DDF6DA502DBDEB5B78CC64AA1EB4801B0`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-08-22 to 2026-10-21); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a NeoForge 1.21.1 source migration of:

- `RotP-AchtungBaby-1.16.5-1.0.0.jar`
- SHA-256: `0154BBD0576F2FCF5A5F4DDEA16ADF0DDF6DA502DBDEB5B78CC64AA1EB4801B0`
- Size: `188802` bytes
- Original author: Weever
- Original metadata license: GNU GPL v3.0

Original textures and `void.ogg` are retained byte-for-byte. Production Java
was rewritten for the public ROTP NeoForge 1.21.1 addon API. The obsolete
1.16.5 renderer/projectile/debug mixins are not redistributed as executable
classes.

### Donor behaviour kept

The rewrite follows the donor's gameplay values:

- Cooldowns, which Resolve does not shorten: Item Invisibility 3 s, Blood
  Splash 5 s, Active Invisibility 15 s, Ghost Mode none, Perfect Invisibility
  0.5 s, Grant Invisibility 0.5 s, or 10 s after six uses each within 10 s of
  the last.
- Stamina: Active Invisibility 20% of maximum stamina, Grant Invisibility 10%,
  Item Invisibility 5% per item (only as many items as the stamina covers are
  made invisible; making an item visible again is free), Ghost Mode 12.5% to
  enter and again every second, for at most a minute, with a 20 s lockout if
  it goes unpaid. Each action refuses below its own share, with no
  Resolve-mode allowance.
- Active Invisibility's rune: a player gets Weakness III, Slowness III and
  Mining Fatigue II for 15 s, or Weakness IV, Slowness III and Mining Fatigue
  III for 20 s if they have a Stand past resolve level 1; a mob gets
  Weakness IV, Slowness IV and Nausea for 10 s.
- Grant Invisibility: only a player can use it; 12 s of invisibility is added
  to what the target has left, plus two different effects out of thirteen at
  level I-V for 10 s, without particles.
- The rune flies straight at its launch speed for up to 5 s, leaves an enchant,
  portal and witch particle trail, passes through its thrower, and hits like a
  snowball (knockback without damage on a mob, 3 damage on a blaze) before its
  effect.
- The donor rune replaced the snowball's tick: each tick it moves 3 blocks with
  block collision and only then tests for a hit over the next 3. It therefore
  passes through anything in its first 3 blocks (a player closer than about
  3.6 blocks is never hit), and a wall inside them stops it, where it lingers
  as a particle cloud until its 5 s are over; further out it breaks on a wall.
  This is what the donor did, not what its author seems to have meant; kept
  under the observable-behaviour standard (R764, S14-1).
- The donor rune reached every client as a vanilla snowball (it used the
  snowball entity type; the hiding mixin never matched it), so it is drawn as
  a snowball and ends in the snowball burst (R764, S14-2).
- While the Stand is out its user has Jump Boost II and Speed II, and a vampire
  user's sun burn turns into sun resistance.
- Whoever attacks an Achtung Baby user has a 30% chance of Weakness II and
  Slowness II for 5 s (a player only if they have a Stand); mobs cannot target
  a player in Perfect Invisibility.
- `rotp_ab:void` is the Stand's summon and unsummon sound (summon plays the
  same file as `rotp_ab:void_summon`).

### Port additions

Not in the donor, kept: Item Invisibility's void sound, arm swing and
off-hand fallback with a "no item" message; Blood Splash's particles (the
donor action did nothing); crouching with Perfect Invisibility gives the aimed
target 12 s of invisibility (the donor toggled the user's own).
