# RotP Made in Heaven (`rotp_mih`): source and license

## Public release

- Module: `rotp-made-in-heaven`, mod id `rotp_mih`, version `1.0.0-port.17`.
- Original 1.16.5 add-on: `RotP-天堂制造-1.2.5-1.jar` (RotP 天堂制造, mod id `rotp_mih`), by Weever; declared license: GNU GPL v3.0; SHA-256 `B4FCD7C0CAF8680225CB21D3717DEFA6CD7895C5B539BF596BF62891A14F4A4D`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a NeoForge 1.21.1 port of the GPL-3.0-only artifact
`RotP-天堂制造-1.2.5-1.jar`.

- Canonical artifact SHA-256:
  `B4FCD7C0CAF8680225CB21D3717DEFA6CD7895C5B539BF596BF62891A14F4A4D`
- Canonical artifact size: `1058896` bytes
- Canonical decompile mirrors:
  `041_RotP-天堂制造-1.2.5-1`,
  `043_RotP-天堂制造-1.2.5-1`, and
  `044_RotP-天堂制造-1.2.5-1`
- Original author: `Weever`
- Port namespace:
  `rotp.addons.madeinheaven`

The three decompile mirrors are byte-for-byte identical for every relative
file. Binary inspection of all 44 legacy class files found no reference to
the Hourglass package or API. The only Hourglass edge was the mandatory
dependency line in the legacy `META-INF/mods.toml`; this port removes that
stale metadata edge and does not copy or reimplement unrelated Hourglass
code.

Time acceleration follows the legacy `GameplayEventHandler` and `TimeUtil`
at an unchanged server tick rate. Every other tick the Overworld day time
gains 20 x phase extra ticks; the phase rises every 150 ticks up to phase 31,
after which the universe reset runs. Projectiles entering the Overworld are
sped up by the legacy multiplier table, the owner's sprint and swim speed,
step height, afterimages and stamina drain follow `boostOnAcceleration` and
`accelerateTime`, and other users' time stops end early as in
`onLivingUpdate`. With Hourglass installed, the legacy server resent the day
time every tick; this port sends that update itself while the acceleration
runs. Two differences are kept on purpose: the acceleration survives a Stand
retraction (owner feedback B11), and it ends when the owner loses the Made in
Heaven power.

The active core dependency is defined by the workspace build configuration.
The earlier dependency coordinate and artifact hashes are preserved in the
port record.

Legacy sound and texture assets remain covered by the source addon's
GPL-3.0-only license. No Hourglass binary or source is redistributed.
The donor `enableNewTSIcons` client option remains available. Its default is
declared by `MadeInHeavenClientConfig.ENABLE_NEW_TIME_SYSTEM_ICONS` in
MadeInHeavenClientConfig.java.
All four new/old acceleration/clear clock icons are copied
byte-for-byte into the current ability resource namespace.
