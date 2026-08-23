# StandAuraFx integration notice

This build incorporates a modified source adaptation of StandAuraFx by
KINnaoinza.

- Upstream: https://github.com/KINnao087/StandAuraFx
- Revision: 6f36008b37bc7165a8c1fd594b246923557dc417
- License received: GNU GPL version 3
- Modified for ROTP 1.21.1 integration: 2026-08-23

The integration replaces upstream raw OpenGL/framebuffer ownership with the
ROTP exact-entity mask compositor, persists the upstream configuration values
in the existing client settings file, and exposes them in the Stand display
settings panel. The complete GPLv3 text is distributed with the project as
`LICENSE`; detailed provenance is in `THIRD_PARTY_NOTICES.md`.

The upstream repository-level `LICENSE` is GPLv3. An older `All Rights
Reserved` value remains in its Forge metadata; the fixed source revision above
was received under the repository-level GPLv3 grant.
