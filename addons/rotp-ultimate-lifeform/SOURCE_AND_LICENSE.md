# RotP Ultimate Lifeform (`rotp_ul`): source and license

## Public release

- Module: `rotp-ultimate-lifeform`, mod id `rotp_ul`, version `1.0.0-port.26`.
- Original 1.16.5 add-on: `RotP-UltimateLifeform-1.0.jar` (RotP Ultimate Lifeform, mod id `rotp_ul`), by HK47bot, StandoByte, hkelemei; declared license: GNU GPL v3.0; SHA-256 `671F002096136DA9A342CEC1DC3031D2E8A6F47CCDECAA028075F7625D5B3C17`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-29); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Source and license

This module is a clean NeoForge 1.21.1 migration of
`RotP-UltimateLifeform-1.0.jar`.

- Legacy authors: HK47bot, StandoByte, hkelemei
- Migration package and registry namespace: `com.hk47bit.rotp_ul` / `rotp_ul`
- Legacy binary SHA-256:
  `671f002096136da9a342cec1dc3031d2e8a6f47ccdecaa028075f7625d5b3c17`
- Legacy license: GNU General Public License v3.0
- ROTP core: consumed as an external required mod and pinned by the parent
  workspace lock
- Caelus API: compile-only, required at runtime, and not bundled

The `migration/baseline-legacy` tree is an evidence-only byte extraction of
the canonical legacy JAR. Runtime resources under `src/main/resources` retain
their legacy authored bytes unless a migration matrix records a required
format or namespace repair.

### Legacy source archive and active adaptation

Eight canonical resources are byte-exactly retained under
`assets/rotp_ul/legacy_archive/`: two unregistered particle descriptors, four
player-animation source files, and the old mixin descriptor and refmap. The
four animation sources are converted into
`assets/rotp_ul/animations/ulf_wall_climb.animation.json` and registered by
`UltimateLifeformPlayerAnimations` through the core
`AddonPlayerAnimations` API. The archived source paths themselves are not
runtime inputs. The two particles and two metadata files remain inactive.

The old `hamon_spark_purple.json` is intentionally preserved as a zero-byte
archive entry. Its former 70-byte migration repair is separately retained at
`migration/repaired-inert/hamon_spark_purple.json` as evidence only; it is not
packaged at the original particle path. Run
`migration/restore-legacy-resources.ps1` to deterministically restore and
verify the B8 source archive from the canonical JAR. Exact paths, byte counts,
adaptation ownership, and hashes are recorded in
`migration/manifests/legacy-resource-retention.json`.
