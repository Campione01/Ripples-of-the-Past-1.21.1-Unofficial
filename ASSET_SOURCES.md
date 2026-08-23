# Asset Provenance and Publication Scope

## Retained assets

The retained models, animations, textures, sprites, structures, language files,
data files, sound effects, and voice assets were inherited from the two
upstream Ripples of the Past repositories or were developed in this independent
working tree.

Known upstream asset contributors are:

- Yujin
- WhiteMind
- August_dr
- KWiNTA
- iIIiBlackiIIi
- Motvik

Detailed contribution roles are preserved in [NOTICE.md](NOTICE.md).
Campione01 does not claim original authorship of inherited upstream assets.

## Stand Aura FX shader source

The `stand_aura_composite` shader is a Minecraft 1.21.1/GLSL 150 adaptation
of the GPLv3 StandAuraFx shader by KINnaoinza, fixed at revision
`6f36008b37bc7165a8c1fd594b246923557dc417`:
<https://github.com/KINnao087/StandAuraFx/tree/6f36008b37bc7165a8c1fd594b246923557dc417>.
The adaptation retains the upstream SDF/noise/color algorithm and moves
framebuffer ownership into ROTP's existing exact-entity mask compositor.

## Deliberately excluded assets

The Git snapshot excludes:

| Path | Reason |
| --- | --- |
| `src/main/resources/User content templates/` | Optional Blockbench and clothes-pack development examples that are not required at runtime. |

The exclusions are implemented in `.gitignore`; the local development copies
are not deleted.

## Historical sound sources

The original 1.16.5 metadata records these sound-effect sources:

- Spybreak001: <https://youtube.com/user/Spybreak001>
- dendy: <https://www.youtube.com/channel/UCfFQSPN-Zxx8jEALchUPpPw>
- Folzy SFX Sound Design
- Supah X:
  <https://www.youtube.com/channel/UCMmaTmRcF9UYb_Kf-wGT1eA/videos>
- JoJo SFX Green Screen & Blue Screen:
  <https://www.youtube.com/channel/UCkxl6FSocj8mHEIJRw5KjYg>
- PassioneSound: <https://www.youtube.com/@PassioneSound>
- JoJo's Sound Design Discord server

The original metadata identifies voice lines from *Eyes of Heaven* and
*All Star Battle*, sourced through <https://sounds-resource.com/>.
