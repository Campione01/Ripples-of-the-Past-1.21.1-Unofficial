# Tinkers of the Past (`tjojo`): source and license

## Public release

- Module: `tinkers-of-the-past`, mod id `tjojo`, version `1.16-1.0-port.2`.
- Original 1.16.5 add-on: `Tinkers of the Past-1.16-1.0.jar` (Tinkers of the Past, mod id `tjojo`), by Zeml; declared license: LGPL3; SHA-256 `907F4953F755F225278C06492715D9D40334D9925D20501DBFD52A98ADC63199`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-09-16); it is a modified version of the original add-on, not the original.
- License of this module: LGPL-3.0-only (see `LICENSE` and `LICENSE-GPL-3.0.txt`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## Tinkers of the Past migration source and license

The canonical input is `Tinkers of the Past-1.16-1.0.jar`, authored by
Zeml and distributed as LGPL3 in its `mods.toml`.

This module is a clean NeoForge 1.21.1 rewrite based on behavior and resource
inventory evidence from that canonical archive. Canonical resources retained
under `META-INF/migration/canonical-resources` remain under their original
license and ownership. No Mantle or TConstruct binary or source is bundled.

The 160 legacy resources retained for evidence are packaged byte-for-byte
below `assets/tjojo/legacy_archive` and `data/tjojo/legacy_archive`. These
paths remain archival and are never registered or referenced. Of those
resources, 81 TConstruct-owned entries also have repaired active counterparts:
four textures are exact copies, while 77 JSON files use current resource
directories, common tags, conditions, fluid units, and material schemas. Four
additional canonical Forge tag rows were repaired to the 1.21.1 `c` tag
convention. The 85 source-to-target mappings and both identities are recorded
in `META-INF/migration/manifests/tconstruct-data-restoration.json`.

The canonical Spanish and Simplified Chinese locale files each contain 24
entries. Twenty-one donor values map to the exact current `tjojo` translation
keys derived from `en_us.json` and the registry inventory, so active
`es_es.json` and `zh_cn.json` counterparts are client-loadable. The comment,
and two obsolete flowing-fluid keys in each donor remain only in the
exact-byte archive. The two block-item tooltip values are active again through
a module-local 1.21.1 equivalent of Mantle's donor `BlockTooltipItem`.
The mapping, active hashes, and
archive identities are recorded in
`META-INF/migration/manifests/locale-restoration.json`.

The remaining archive-only resources include 66 specifically classified inert
templates and three preserved evidence rows. All eight donor recipe-book
advancements have repaired active 1.21.1 counterparts with current resource
directories, item predicates, item IDs, and recipe IDs. The exact donor bytes
remain in the archival tree. `template-reachability.json` records the canonical
registration, provider-body, namespace, and exact-hash basis for each template
classification. Preserved donor content is not treated as deletable.

The canonical donor registered `tjojo:stando_arrow` and
`tjojo:meteor_virus`, and both active material trait files still reference
those exact IDs. Their core-facing hit behavior is implemented once in
`rotp.addons.tinkers_of_the_past.compat.TConstructModifierBridge`, without TConstruct imports.
Registration, callback dispatch, tool persistence, and one-use consumption
remain blocked on a lockable public NeoForge 1.21.1 TConstruct/Mantle API.
The same external boundary applies to the donor-registered
`RecipeCacheInvalidator::onReloadListenerReload` and
`BlockSideHitListener.init()` bootstrap calls; both are explicitly retained in
the dependency and future-adapter contracts.
The 2026-07-29 cache and upstream audit resolved zero compatible artifacts:
the latest official releases are TConstruct `1.20.1-3.11.2.166` and Mantle
`1.20.1-1.11.104`, while their official branches stop at `1.20.1` and `1.20`.
`dependency-boundary.json` records the queried coordinates and exact blocker.
The 1.20.1 source is used only as a historical data-schema reference, never as
a compiled Java API. No fabricated interface, reflection, mixin, generic
attack event, or dependency binary substitutes for that missing prerequisite.

The rewritten Java source in this module is distributed under
LGPL-3.0-only.
