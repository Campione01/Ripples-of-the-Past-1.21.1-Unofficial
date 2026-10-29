# RotP White Album (`rotp_zwa`): source and license

## Public release

- Module: `rotp-white-album`, mod id `rotp_zwa`, version `1.1.8-port.16`.
- Original 1.16.5 add-on: `RotP-白色相簿-1.1.8.jar` (RotP 白色相簿, mod id `rotp_zwa`), by Makuta Zeml, Sounds by: Lambirno, StandoByte; declared license: GNU GPL v3.0; SHA-256 `E9A0ACE7787B451736655F9B3D998A3407BEFBDE4E6AC7DB660A602632F07FC2`.
- This module was ported to NeoForge 1.21.1 and modified by Campione01 (port work dated 2026-07-27 to 2026-10-24); it is a modified version of the original add-on, not the original.
- License of this module: GPL-3.0-only (see `LICENSE`).
- The port record below is the one kept with the port; local file-system paths in it were reduced to file names, and links into the private port workspace to their titles.

## RotP White Album source and license

This NeoForge 1.21.1 module is a full reachable static port of the legacy
White Album addon.

### Locked authority

- JAR:
  `RotP-白色相簿-1.1.8.jar`
- Size: `2924071` bytes
- SHA-256:
  `E9A0ACE7787B451736655F9B3D998A3407BEFBDE4E6AC7DB660A602632F07FC2`
- Legacy mod id/version: `rotp_zwa` / `1.1.8`
- License declared by the legacy metadata: GNU GPL v3.0
- Author: Makuta Zeml
- Credits: Lambirno for sounds and StandoByte
- Canonical decompile:
  `059_RotP-白色相簿-1.1.8`
- Decompiler: CFR `0.152`
- Normalized decompile SHA-256:
  `18ACA44CD091DC2B6B56EB51087A860E575C05DB91F2ADC6B9D6442C2ACA581F`

The canonical JAR contains 35 classfiles and 85 files under
`assets/rotp_zwa` or `data/rotp_zwa`. Every entry has an explicit disposition
in `META-INF/migration/full-closure.json`.

### Port topology

White Album is a wearable suit. The legacy visible owner is the player model;
the legacy Stand entity is forcibly invisible and owns actions and transient
state. The port therefore keeps a tiny, invisible, non-colliding controller
with a `NoopEntityRenderer` and renders the suit and Ice Shield through
owner-scoped default/slim player layers.

The four visible suit/shield atlases are lossless nearest-neighbor 4x
materializations of the canonical donor's 64x64 resources. Every donor texel
is an exact uniform 4x4 block, with no bicubic blending or invented detail.
Their 0.2 suit and 0.6 shield inflation, logical 64x64 player-model UV layout,
selected-skin remapping, lifecycle alpha, and material path are recorded by
`phase2-art.json` and `visual-chain-ledger.json`. Rendering uses
`StandVisualContext`, `AlphaMultiBufferSource`, and the core Stand translucent
material. It does not install global shader, framebuffer, Iris, Super
Resolution, or player-renderer replacement hooks.

### Reachable closure

The public-API port includes core punch/guard, held Freeze, Frost Walker,
Unfreeze, Gently Weeps, the custom melting ice layer, passive/guard freeze
auras, armor floor, ice movement, sunlight (ultraviolet) immunity, projectile
reflection, synchronized toggle lifecycle, canonical sounds, particle
registration, and default/slim first- and third-person suit/shield rendering.

There is no barrage, heavy punch, charged heavy, grab, grab throw, manual
control, or Stand leap. Those controls are ineligible because the canonical
manifestation is wearable/non-grabbable armor and the legacy moveset has no
RMB heavy action.

Of the 85 canonical resources, 73 remain byte-for-byte copies, eight files are
adapted for valid 1.21.1 identifiers, aliases, or syntax, and the four visible
textures are losslessly materialized at 256x256. Modern ability-resource aliases are
additional files. The backed `wa_heavy_punch` sound is retained without
creating a heavy action. The legacy `void`, `wa_ora_long`, grapple, throw, and
OST registrations have no backed reachable stream and are audit-only.

### Restored render ownership

The donor `ClientHandler` hid exactly six outer player-skin parts in
`RenderPlayerEvent.Pre` and had no matching restoration handler. The current
addon registers one owner-keyed base-model visibility policy. It uses the
donor `canSeeStands` and active White Album controller gate, hides only the
hat, jacket, sleeves, and pants during the third-person base draw, and relies
on the core frame scope to restore the exact prior state before armor or addon
layers. First-person arms are not affected.

The registered `MixinPPlayerRenderer` behavior is restored without a mixin.
When optional Epic Fight 21.17.3.1 is loaded, the addon registers suit and Ice
Shield custom patched layers through Epic Fight's public
`MODIFY_PATCHED_ENTITY` hook. The layers preserve the controller owner gate,
guard-state selection, default/slim textures, lifecycle alpha, and core Stand
material.

No static donor behavior blocker remains. Live fixed-view and compatibility
matrix execution is still pending and is not claimed by the static closure.

White Album's canonical classification was also checked against the JoJo Wiki
authority digest at `https://jojowiki.com/White_Album`.

The NeoForge integration and new implementation are distributed under
`GPL-3.0-only`, compatible with the legacy module's declared license.
