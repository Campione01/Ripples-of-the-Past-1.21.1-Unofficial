# Independent Modification Record

## Editable Stand-stat data files - 2026-08-24

- restored the operator-only `/jojoconfig` command from the 1.16.5 feature
  line for generating all or one Stand's editable numeric data;
- emitted current-loader `jojostandpowers/<stand>/stats.json` member files
  instead of reintroducing the removed parallel configuration loader;
- preserved administrator edits by default and required an explicit `force`
  subcommand before replacing existing templates;
- generated a current-format world data pack and bilingual editing guide, with
  normal `/reload` application through the existing server/client sync path.

## GPL distribution packaging - 2026-08-24

- embedded the GPLv3 text, upstream attribution, third-party notices, asset
  provenance, and modification record in release and source JARs;
- updated the repository publication boundary for explicitly unofficial binary
  releases that link to the exact public Corresponding Source commit;
- retained the distinct unofficial artifact and in-game display names so the
  release cannot be mistaken for a StandoByte-supported build.

## Menu and stone mask rendering reliability - 2026-07-30

- made the JoJo menu key fall back to the live player power attachments when
  the client power cache has not synchronized yet;
- made Stand information and skill pages use the same resolved live Stand
  power as the menu tab, avoiding a second stale-cache lookup;
- kept the controls page available as a stable menu entry even when the player
  has no active power, and added targeted diagnostics for failed menu opens;
- moved both stone mask variants to one custom armor-model pass, removing the
  competing vanilla leather helmet and extra renderer-layer paths;
- preserved the normal, activated, Aja, and activated Aja textures through the
  NeoForge per-stack armor texture hook;
- registered and verified the dedicated face-only armor model for both core
  stone masks, and suppressed armor trim and glint passes that do not belong
  to stone-mask rendering;
- made generated Stand hand attachment points use the already-resolved arm
  bend descendants, so addon models do not have to place bend bones directly
  below their arm bones;
- made entity-action ticking safe when an action callback clears or replaces
  itself during the same tick, preserving the replacement instead of
  dereferencing or clearing stale state;
- bounded variable-length network payloads and nested state codecs without
  changing normal payload layouts, including Stand, Hamon, vampirism, Pillar
  Man, Golden Experience, block-restoration, and configuration data;
- made Polaroid image uploads one-shot, size-limited sessions bound to a
  server-authorized uploader and recipient, with deterministic timeout,
  logout, and server-stop cleanup;
- rejected unsolicited photo-cache population while preserving authorized
  multiplayer captures made on behalf of another player;
- validated client-authoritative movement, controlled-mob commands, debug
  commands, external-container slots, Stand skins, Walkman controls, and
  variable-length interaction data on the server;
- added executable contract tests for the menu, armor, payload bounds,
  ownership checks, legacy photo transfers, and valid multiplayer inputs.

## Addon API and shared Stand combat input - 2026-07-27

This update prepares the core for independently built 1.21.1 addons and aligns
their common combat controls with the current game design:

- added a versioned addon feature-negotiation API and server-authoritative
  Stand insert, extract, and replace operations;
- made addon ability translations and icons follow the owning power namespace
  while preserving the built-in `jojo_ripples` resource paths;
- added defensive Stand-instance ownership and Stand-disc serialization copies;
- disabled incompatible base attacks and guarding while a Stand is holding a
  target, including locked grab follow-up variations;
- unified grab and charged heavy behind one rebindable input, defaulting to
  Shift + right mouse: short press grabs where eligible and long press charges
  a heavy attack where available;
- added smoke tests for the public API, Stand transitions, disc persistence,
  contextual grab behavior, and shared input topology.
- added the data-driven `jojo_ripples:crazy_d_cannot_restore` block tag so
  addons can exclude their temporary blocks from Crazy Diamond restoration
  without patching the restoration pipeline.
- added server-side time-stop `PreStart`, `Added`, and `Removed` lifecycle
  events, including cancellable duration/range/visual mutation before costs or
  world state are committed and explicit removal reasons for addon cleanup.
- added a client-only managed addon PostChain API with lazy loading, current
  main-target rebinding, resource/resize/world-exit cleanup, render-state
  restoration, and read-only Iris/Super Resolution compatibility reporting.

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
- included the inherited runtime audio resources and preserved their historical
  source credits;
- excluded the optional user-content development templates from the Git
  snapshot;
- independently rewrote the previously Patchouli-derived translucent block
  preview path to avoid carrying incompatible CC BY-NC-SA implementation code.
