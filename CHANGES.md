# Independent Modification Record

## Initial public source snapshot - 2026-07-26

This commit establishes the independently maintained public source history.
It is based on the official Ripples of the Past 1.16.5 project and official
1.21.1 port, with a substantial set of local changes. The official 1.21.1
comparison baseline used during publication review was commit
`1a68125557221d1461ab1ba01119815ef0ba206d` dated 2026-03-29.

The local changes include, at a high level:

- NeoForge 1.21.1 gameplay and compatibility work;
- Stand ability, input, UI, animation, and rendering changes;
- Hamon, vampirism, and Pillar Man system alignment and extensions;
- renderer compatibility work, including shader-mod and transparency paths;
- bug fixes, validation scripts, and migration support for related content.

This list is intentionally non-exhaustive. The source itself is authoritative.
The original local directory had no usable Git object database, so a reliable
historical per-commit changelog could not be reconstructed without inventing
provenance. All upstream authorship remains recorded in [NOTICE.md](NOTICE.md).

Publication preparation performed on 2026-07-26:

- restored the complete GNU GPL version 3 license text;
- added explicit upstream, contributor, and third-party attribution;
- removed upstream-specific Maven, Discord, and binary-artifact CI publishing;
- removed a machine-specific JDK path from project properties;
- changed repository-facing display and artifact identifiers to say
  "Unofficial";
- excluded audio with unverified redistribution rights and an embedded
  `All Rights Reserved` sample JAR from the Git snapshot;
- independently rewrote the previously Patchouli-derived translucent block
  preview path to avoid carrying incompatible CC BY-NC-SA implementation code.
