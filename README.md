# Ripples of the Past - Unofficial 1.21.1 Modified Port

> 本仓库是由 Campione01 独立维护的非官方修改版源码仓库，不是 StandoByte 的官方发行版。

This is an independent, unofficial source repository for a modified NeoForge
1.21.1 port of **Ripples of the Past**. It contains substantial code and assets
copied or adapted from the upstream projects and preserves their authorship.

## Upstream projects

- Original Forge 1.16.5 project:
  [StandoByte/Ripples-of-the-Past](https://github.com/StandoByte/Ripples-of-the-Past)
- Official NeoForge 1.21.1 port:
  [StandoByte/Ripples-of-the-Past-1-21-1](https://github.com/StandoByte/Ripples-of-the-Past-1-21-1)

StandoByte is the original author. This repository is maintained by
Campione01 and is not an official fork, release, continuation, or endorsement
by StandoByte. The historical `com.github.standobyte` Java package names and
the `jojo_ripples` mod id are retained solely for source and save/mod
compatibility; they do not imply upstream ownership or approval of this
repository.

## Publication scope

This repository publishes source and project resources, including the inherited
sound and voice assets used by the mod. Independently labeled release JARs may
also be distributed through third-party hosting such as CurseForge. Every binary
release must identify the exact public source commit used to build it and link
back to this repository as its Corresponding Source under GPLv3.

The following local resources are deliberately excluded from the source
snapshot:

- the optional `User content templates/` development examples;
- local run data, logs, IDE state, Codex work records, generated build output,
  and internal validation tooling.

See [ASSET_SOURCES.md](ASSET_SOURCES.md) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the retained material and
its provenance.

## Building

Requirements:

- JDK 21
- an Internet connection for the first Gradle dependency resolution

On Windows:

```powershell
.\gradlew.bat clean build
```

On Linux or macOS:

```bash
./gradlew clean build
```

The build remains a local, maintainer-controlled operation; this repository does
not automatically upload the resulting JAR. The playable artifact is the
non-sources ROTP JAR under `build/libs/`. Only an artifact whose exact source
commit is public and whose bundled legal notices have been verified may be
distributed.
Do not install `build/moddev/artifacts/neoforge-*.jar`, a `-sources.jar`, or a
same-named JAR left over from an older build.

Each built runtime JAR records the exact source commit in
`META-INF/MANIFEST.MF` as `ROTP-Git-Commit`. Minecraft also writes a
`ROTP build identity` line to `logs/latest.log` during mod initialization.
Use that commit identity when reporting or comparing builds; the historical
`0.2.2.2` mod version and JAR filename alone do not distinguish newer source
revisions from older builds.

## Editable Stand stats

Server operators can run `/jojoconfig stand_stats` to generate editable
Stand-stat member files in the current world's `datapacks/jojoconfig` data
pack. Supplying a Stand ID generates only that Stand. After editing the JSON,
run vanilla `/reload`; the existing data-driven Stand loader applies and
synchronizes the values. Existing files are preserved unless the explicit
`force` subcommand is used. `/jojoconfig folder_link` prints the generated path.

## License and attribution

The program is distributed under
[GNU GPL version 3 only](LICENSE), except for separately identified third-party
components and assets. Copyright notices and license terms for those components
remain in force.

- Original project authors and contributors: [NOTICE.md](NOTICE.md)
- Third-party code: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- Resource provenance and exclusions: [ASSET_SOURCES.md](ASSET_SOURCES.md)
- Modifications in this independent repository: [CHANGES.md](CHANGES.md)

This repository is provided as an independently maintained source-code project.
