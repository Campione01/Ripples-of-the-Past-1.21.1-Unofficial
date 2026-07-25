# Third-Party Code Notices

This project incorporates or adapts code from the components below. Fixed
upstream revisions are recorded so later license changes do not obscure the
terms under which the relevant code was received.

## Mocha

- Project: Unnamed Team Mocha
- Revision: `eac679c71a1c4211ccf5ec2ff9a31b25c52e7509` (v3.0.1)
- Source: <https://github.com/unnamed/mocha/tree/eac679c71a1c4211ccf5ec2ff9a31b25c52e7509>
- License: MIT
- Copyright: 2021-2025 Unnamed Team
- Local use: Molang engine integration and a modified engine implementation
  that disables Javassist compilation and bytecode execution paths while
  retaining an API type dependency

The complete MIT text is in `licenses/MIT-MOCHA.txt`.

## React Native

- Project: React Native
- Revision: `c20070f10458d48d6ac1eaac49e681e932bfb9fd`
  (v0.59.10)
- Source:
  <https://github.com/facebook/react-native/blob/c20070f10458d48d6ac1eaac49e681e932bfb9fd/Libraries/Animated/src/Easing.js>
- License: MIT
- Copyright: Facebook, Inc. and its affiliates
- Local use: easing functions adapted for Gecko-based entity animations

The complete MIT text is in `licenses/MIT-REACT-NATIVE.txt`.

## Better Combat

- Project: Better Combat by Zsolt Molnar
- Revision: `f3cd35d0b26a1a029fbfb577999391f6e530bfef`
- Source: <https://github.com/ZsoltMolnarrr/BetterCombat/tree/f3cd35d0b26a1a029fbfb577999391f6e530bfef>
- License at that revision: GNU GPL version 3
- Local use: oriented bounding-box implementation, adapted for this project

The fixed historical revision is intentional. A later Better Combat revision
uses different licensing; this project relies only on the GPL-licensed
revision identified above. The complete GPLv3 text is in `LICENSE`.

## Lithium

- Project: Lithium
- Revision: `0fe3cfd526300d11f72f2a00dcb0dc09d847d500`
- Source: <https://github.com/CaffeineMC/lithium/tree/0fe3cfd526300d11f72f2a00dcb0dc09d847d500>
- License: GNU Lesser General Public License version 3
- Relevant authors recorded upstream: JellySquid and 2No2Name
- Local use: adapted explosion block-raycast logic

The complete LGPLv3 text is in `licenses/LGPL-3.0.txt`.

## Blockbench

- Project: Blockbench by JannisX11 and contributors
- Revision: `368efc7c8275d11fac355efa90720ebcd850f3b8`
- Source: <https://github.com/JannisX11/blockbench/tree/368efc7c8275d11fac355efa90720ebcd850f3b8>
- License: GNU GPL version 3 or later
- Local use: mesh intersection/vertex helper logic

For this combined work the GPL version 3 option is used; its text is in
`LICENSE`.

## three.js

- Project: three.js
- Revision: `8540d9f9a6818db6879d8a92abe162ea7efa3475`
- Source: <https://github.com/mrdoob/three.js/tree/8540d9f9a6818db6879d8a92abe162ea7efa3475>
- License: MIT
- Copyright: 2010-2025 three.js authors
- Local use: line/segment mathematics adapted for mesh helpers

The complete MIT text is in `licenses/MIT-THREEJS.txt`.

## PneumaticCraft: Repressurized

- Project: PneumaticCraft: Repressurized by Team Pneumatic/desht
- Revision: `9f722043d49d248222e0117a7eea6afef75af069`
- Source: <https://github.com/TeamPneumatic/pnc-repressurized/tree/9f722043d49d248222e0117a7eea6afef75af069>
- License: GNU GPL version 3 or later
- Local use: particle render-type behavior adapted for Hamon aura particles

For this combined work the GPL version 3 option is used; its text is in
`LICENSE`.

## Independently rewritten prior-art path

The Crazy Diamond translucent block-preview renderer was rewritten on
2026-07-26 using Minecraft/NeoForge rendering APIs. The current implementation
does not include Patchouli code. Patchouli is therefore not a component of the
published source and its CC BY-NC-SA license is not applied to this repository.

These notices supplement, and do not replace, copyright and license notices in
individual source files.
