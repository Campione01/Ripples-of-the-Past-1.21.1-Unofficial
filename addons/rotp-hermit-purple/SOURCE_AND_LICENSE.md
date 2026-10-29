# RotP Hermit Purple (`rotp_zhp`): source and license

## Public release

- Module: `rotp-hermit-purple`, mod id `rotp_zhp`, version `1.3.5d-port.31`.
- Original 1.16.5 add-on: `RotP-紫色隐者-1.3.5d.jar` (RotP 紫色隐者, mod id `rotp_zhp`), by Makuta Zeml, StandoByte, StandSkins and Icons by: MlgMC; declared license: GNU GPL v3.0; SHA-256 `BEC55C7EB3A3C2654837740BC56D2CC97D25D4AAE39F20FBC58A8DF0044C3AA6`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is the NeoForge 1.21.1 migration of the Hermit Purple logical
unit from **RotP Hermit Purple 1.3.5d**.

- Canonical artifact:
  `RotP-紫色隐者-1.3.5d.jar`
- Canonical size: `3,648,911` bytes
- Canonical SHA-256:
  `BEC55C7EB3A3C2654837740BC56D2CC97D25D4AAE39F20FBC58A8DF0044C3AA6`
- Canonical decompile: `061_RotP-紫色隐者-1.3.5d`
- Identical mirrors: `062_RotP-紫色隐者-1.3.5d` and
  `063_RotP-紫色隐者-1.3.5d`
- Original author: Makuta Zeml
- Original framework: StandoByte
- Stand skins and icons: MlgMC'Funkadelic'Player
- Declared license: GNU General Public License v3.0

The canonical decompile contains 87 Java files. All are classified exactly
once: 38 ported, 36 replaced by current public APIs, one canonical
player-animation adapter proven unregistered and unreachable, and 12
Emperor-only files excluded. The physical source artifact includes The
Emperor, but Emperor is an independent migration unit and is not duplicated
here.

The canonical JAR contains 314 namespaced asset/data files. The formal
ledger assigns every file one disposition: 119 byte-identical, 14 adapted,
147 translated, 15 Emperor exclusions, eight replaced target UI files, four
current-framework replacements, and seven dead/unreachable files. The
current module contains 317 resources: 246 PNG, 28 OGG, and 42 JSON files.
Twenty-three Hermit-owned files are retained byte-for-byte under legal
owner-scoped `legacy_archive` paths. Four are exact conversion sources for
the active combined player-animation file; 18 are inactive evidence; and one
is byte evidence for the active donor-exact
`data/rotp_zhp/stand_combos/hermit_combo.json`. The combo's nine unique
action ids all resolve against the current 17-ability registry. The one Catch
the Rainbow glow texture remains owned by that separate migration unit.

The migrated unit registers one Stand type, all 17 Hermit abilities, all
five Hermit entities, 16 useful sound events, and the default plus 12
imported skins. It preserves vine attacks, the Hermit-specific vine grab,
grapples, Hamon follow-ups, thorns, barriers, breathing, divination, target
selection, spirit photography, leap gates, Hamon-based granting, skin
colors, texture branches, and voice branches.

Hermit Purple uses the H4 visual topology. The invisible live controller
owns visibility; player layers, first-person arms, lash/grab/grappling
vines, and barriers all derive `StandVisualContext`, effective alpha,
`AlphaMultiBufferSource`, and the core Stand material from that controller.
No player/item renderer mutation, Iris/SR hook, global shader hook, or
framebuffer hook is present.

Owner decision of 2026-10-07 (registry D-rotp-hermit-purple-05): every visible vine except the barrier keeps the port's animated wrist anchor (`HermitVineHandOrigin.forPlayer`, used by `HermitVineRenderer.visualOrigin`) instead of the donor's origin near the eyes, while the server-side origin of the grapple follows the donor (eyes plus the donor offset).

Owner decision of 2026-10-15 (supersedes the drawing part of the entry above): the vine geometry is drawn as in 1.16 HPVineModel with the core RepeatingModel, for all four vine renderers including the barrier. The vine is drawn from its tip back toward the wrist, each segment centred on its point and half a pixel off the line: it always reaches a quarter block past the tip or contact point, and since every segment is full length its wrist end falls anywhere from a quarter block short of the wrist to a quarter block behind it. Only the origin point, the wrist, stays the port's.

Owner decision of 2026-10-28 (amends D-rotp-hermit-purple-05): in the user's own first-person view, where the wrist belongs to a body that is not drawn, every vine except the barrier starts at the donor's origin instead (eyes plus the donor offset: grapple 0.2 below, command grab 0.25 below, lash 0.3 to the side and 0.2 below, turned by the body yaw; `HermitVineGeometry.startsAtWrist`). Third person and every other viewer keep the wrist. Render only: gameplay origins and hit rays are unchanged.

All three donor compatibility paths are restored:

1. `integrated_stand_targeting`: the removed `INTEGRATED_STAND` effect and
   its broad temporary effect are not reintroduced. An owner-keyed public
   Stand damage authorizer preserves only the donor `hermitHamon`, power,
   resolve, summoned-controller, and hand-state gates.
2. `kosmx_player_animation_adapters`: a public addon player-animation
   registry and namespace-neutral loader now drive converted `grab`,
   `grapple`, `doxx_left`, and `vine` poses from synchronized Hermit actions.
   The sole donor adapter without a registration or call site remains inert.
3. `extra_hamon_grant_cancellation_hook`: the automated Hamon grant has the
   stable source `rotp_zhp:hamon_to_hermit` and consults the public
   `AutomatedStandGrantVetoes` registry immediately after server-player
   identification, before cadence, config, or tried-state checks. This
   restores Extra Hamon Stands' donor cancellation boundary without a mixin.

The authoritative closure evidence is
`META-INF/migration/full-closure.json` in the built JAR. It locks the source
and pinned-core hashes, all Java/resource dispositions, registrations,
controls, H4 owners, and the complete 13-skin model/material/texture/voice
matrix. `META-INF/migration/manual-runtime-matrix.json` explicitly records
that live validation remains pending.

The exact build contract is:

```powershell
.\gradlew.bat :addons:rotp-hermit-purple:clean :addons:rotp-hermit-purple:test :addons:rotp-hermit-purple:check :addons:rotp-hermit-purple:build :addons:rotp-hermit-purple:sourcesJar --offline --no-daemon --no-build-cache --no-configuration-cache --console=plain
```

No deployment or upload is part of this module closure.
