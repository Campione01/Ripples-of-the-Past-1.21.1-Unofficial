# Asset Provenance and Publication Scope

## Retained assets

The retained models, animations, textures, sprites, structures, language files,
and data files were inherited from the two upstream Ripples of the Past
repositories or were developed in this independent working tree.

Known upstream asset contributors are:

- Yujin
- WhiteMind
- August_dr
- KWiNTA
- iIIiBlackiIIi
- Motvik

Detailed contribution roles are preserved in [NOTICE.md](NOTICE.md).
Campione01 does not claim original authorship of inherited upstream assets.

Files without a separate notice are distributed under the repository license
only to the extent that their contributors had authority to license them.
Names, likenesses, characters, trademarks, and other underlying third-party
intellectual property remain the property of their respective owners.

## Known fan-project intellectual-property boundary

The retained visual resources include depictions of JoJo characters, clothing,
names, and other franchise elements. The repository notices identify source
provenance and avoid claiming endorsement or original ownership, but they do
not grant rights in those underlying franchise elements. Publishing those
resources therefore retains the ordinary intellectual-property risk of an
unofficial fan project. Reducing that risk further would require removing or
replacing the affected visual resources, not merely adding another disclaimer.

## Deliberately excluded assets

The Git snapshot excludes:

| Path | Reason |
| --- | --- |
| `src/main/resources/assets/jojo_ripples/sounds/` | The upstream metadata identifies Internet sound-effect sources and voice lines extracted from commercial JoJo games, but does not provide a clear redistribution license. |
| `src/main/resources/User content templates/` | The embedded `clothes_example.jar` declares `All Rights Reserved` and contains no sufficient redistribution grant or author notice. |

The exclusions are implemented in `.gitignore`; the local development copies
are not deleted. Anyone building this public snapshot must supply only assets
they are legally permitted to use.

## Historical source references

The original 1.16.5 metadata named sound-effect channels and commercial-game
voice-line sources. Those credits document provenance but do not themselves
grant redistribution rights, so the corresponding audio is not included here.

If a contributor can provide a verifiable license or original-source grant for
an excluded asset, it can be reviewed and added later with an explicit entry in
this file.
