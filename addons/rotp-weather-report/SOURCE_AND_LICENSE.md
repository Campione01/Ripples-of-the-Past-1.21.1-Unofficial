# RotP Weather Report (`rotp_wr`): source and license

## Public release

- Module: `rotp-weather-report`, mod id `rotp_wr`, version `1.4.1-port.32`.
- Original 1.16.5 add-on: `RotP-天气预报-1.4.1.jar` (RotP Weather Report, mod id `rotp_wr`), by HK47bot, StandoByte; declared license: GNU GPL v3.0; SHA-256 `88F5A79BDDB3562E3C5528BD3167AA777B55A64059AF182C4C25D8F17EDFF3BE`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-26 to 2026-10-28); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a bounded NeoForge 1.21.1 port of **RotP Weather Report 1.4.1**.

- Canonical source artifact: `RotP-天气预报-1.4.1.jar`
- Canonical SHA-256: `88F5A79BDDB3562E3C5528BD3167AA777B55A64059AF182C4C25D8F17EDFF3BE`
- Canonical decompile: `045_RotP-天气预报-1.4.1`
- Original authors: HK47bot, StandoByte
- License: GNU General Public License v3.0

The static closure inventory contains 61 class files, 88 non-class resources,
50 legacy registrations, two C2S packet types, and zero mixins. The three
available decompile trees contain the same 50 Java files plus `summary.txt`
and are byte-identical. `migration/manifests/legacy-closure.json` records the
hash and disposition of every JAR class and resource, all registrations, both
packets, the behavior matrix, runtime-state matrix, effect-owner matrix, and
the explicit blocker matrix.

The current Stand retains the complete humanoid punch, barrage, heavy,
charged-heavy, guard, grab, grab-throw, grab-punch, grab-barrage, and
grab-heavy control chains in both control schemes. Reachable legacy behavior
also includes the aerodynamic-friction finisher, authenticated vanilla weather selection,
both cloud-shield forms, warm/cold wind, the weather-scaled lightning strike,
the owner-bound extending icicle, core bleeding integration, persistent blood
puddles and spikes, bounded pufferfish rain, the cloud-shield user voice, and
the two rain droplets emitted around the Stand each rainy client tick.

Cloud protection is an uncurable target-owned effect. Its visuals use
target-owned vanilla particles plus the 1.16 cloud suit, attached to both
player renderers through `EntityRenderersEvent.AddLayers` and drawn only while
the effect is active (no global renderer replacement or static entity map). Wind physics and cold effects are server authoritative. Lightning uses
a vanilla LightningBolt without a forced player cause. The icicle is a
normally tracked Stand-owned body-part entity that ends with its Stand. The
pufferfish storm is saveable and independent of its caster (owner decision of
2026-10-07): death, logout, dimension change, spectator mode, Stand recall and
Stand loss neither end nor pause it, and fish in flight are not removed because
the caster is gone. It ends 2400 game ticks after the cast, when the rain stops,
or when the same Stand casts again; time spent unloaded counts against it.
Weather remains owned by `ServerLevel`,
effects by the target entity, and blood blocks by normal chunk block state.

The blood-puddle-to-blood-ice-spikes chain uses the existing core Bleeding
effect and ordinary chunk persistence. The donor-registered compatibility
effect `rotp_wr:bleeding` is also active with its original color, cadence,
armor-bypassing damage, and staged-puddle production. The four spike-model
parent references remain repaired to their matching namespaced puddle models.

The canonical 256x256 Stand and glow PNGs are retained byte for byte and
without scaling. The converted model retains its authored 128x128 UV canvas,
42 bones, 62 cubes, and all 14 animation clips.
`migration/manifests/art-evidence.json` pins deterministic four-view,
UV/material, and fixed-action evidence. The earlier runtime-capture status is
preserved in the port record.
First-person, third-person, Iris and Sodium results are recorded with the
tested artifact and rendering profile.

The donor-attached `weather_report_ost` list is restored through the current
core `OstSoundList`. All seven registry IDs and the Stand attachment are
active. They intentionally remain silent because the locked source JAR has no
matching `sounds.json` entries or OGG files; no media payload is fabricated.

Two bounded compatibility omissions remain outside baseline closure. The Better
Weather donor branch casts `ServerWorld` to
`corgitaco.betterweather.helpers.BetterWeatherWorldData`, obtains
`BWWeatherEventContext`, then invokes `setWeatherForced(true)` and
`weatherForcer(...)`. The original project is archived and stops at Forge
1.16.5; its last full artifact is
`curse.maven:better-weather-400714:3420517` with SHA-256
`1FBED912464F07DD3A43540AC9710DC0E7EF44E65B33749110F1C88F0A74F166`.
The closest known fork stops at Forge 1.20.1, not NeoForge 1.21.1. No compatible
artifact or supported mutation API was found in the installed instance, Gradle
cache, local source workspaces, or official upstream. The exact audit and
minimum unlock contract are in
`migration/manifests/better-weather-dependency.json`. No reflection bridge or
vanilla-weather substitute is fabricated. The exact donor Better Weather atlas
is restored at `assets/rotp_wr/textures/gui/better_weather_change_gui.png` and
is used directly by the authenticated clear/rain/thunder selection screen; the
external provider mutation branch remains blocked. The exact arbitrary-player
cloud model layer requires
the prohibited global renderer hook and is replaced by the active target-owned
particle visual. Heavy Weather is not present anywhere in this source artifact
and is a separate owner/topology; it is not inherited or fabricated by this
module.

Four canonical payloads are retained byte-for-byte under
`assets/rotp_wr/legacy_archive/source/`: three unreferenced OGG files and the
donor GUI atlas. The GUI atlas is also restored byte-for-byte to its active
donor path and referenced by `WeatherSelectionScreen`. The exact bleeding icon
and both retract voice variants were restored to active resource paths. The
three inert audio rows stay outside `sounds.json`, registries, models, menus,
and production code. The
locked contract and provenance, disposition, retention, and closure manifests
are packaged in both runtime and sources artifacts.
