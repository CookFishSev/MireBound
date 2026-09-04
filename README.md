# Mirebound: Sinking Depths

<p>
  <img src="src/main/resources/icon.png" alt="Mirebound icon" width="128">
</p>

**Mirebound: Sinking Depths** is a gameplay expansion for Minecraft centered
on quicksand and other sinking materials. It currently targets Minecraft
1.21.1 with NeoForge.

The project continues the design direction of the 1.7.10-era *More Fun
Quicksand Mod*, while rebuilding its physics, gameplay balance, and visual
systems for a modern Minecraft version.

The project is under active development. Features, configuration formats, and
save compatibility may change between development releases.

## Design Goals

- Deliver stable and clear visual effects while keeping performance in mind.
- Make the physics immersive without turning the experience into a chore.
- Provide enough configuration options for different play styles.
- Maintain broad compatibility with common mods and custom content.

## Features

- 27 sinking-material types built around the idea that almost any block can
  become dangerous terrain, while preserving as much of its original identity
  as possible.
- Persistent contamination effects for player skins, armor, dropped items,
  and nearby surfaces, with support for custom content and resource packs.
- Configurable sinking physics with depth, movement resistance, struggle, and
  material-specific behavior.
- Configurable natural generation across biomes and dimensions.
- Environmental effects including footprints, wall contamination, splashes,
  bubbles, and other surface details.
- Exploration and escape tools including the mud probe, tuning wand, water
  gun, and rescue rope.
- Client and world-generation settings with multilingual interface support.
- Optional compatibility with Sable physical structures.

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.233` or a compatible `21.1.x` release
- Java `21`
- Curios `9.5+` is optional

Install Mirebound on the server and on every client that joins a multiplayer
world. Back up important worlds before testing development builds.

## Building

Run the following command from the repository root:

```text
gradle "-Dorg.gradle.problems.report=false" build --no-configuration-cache --stacktrace
```

The built mod JAR is written to `build/libs/`.

## Feedback And Contributions

Bug reports and focused improvement proposals are welcome on the
[issue tracker](https://github.com/CookFishSev/MireBound/issues). Please
include the Minecraft and NeoForge versions, the Mirebound version, clear
reproduction steps, and the relevant `latest.log` or crash report.

For code contributions, discuss larger changes in an issue first and include
tests where practical. Keep changes focused and do not include local
configuration, generated build output, or private development files.

## Roadmap

The immediate focus is stability and refinement of the core experience. Future
work may expand the project with companion content, new dimensions, tools, and
environmental events.

## Credits

Mirebound was informed by the work and ideas in the following projects:

- [Made In Abyss](https://github.com/MIA-Development-Team/Made-In-Abyss)
- [Aeronautics / Simulated Project](https://github.com/Creators-of-Aeronautics/Simulated-Project)
- [Sable](https://github.com/ryanhcode/sable)
- [Litematica](https://github.com/maruohon/litematica)
- [Quicksand-Rehydrated](https://github.com/Theyoungster/Quicksand-Rehydrated)
- [MFQM Decompiled Reference](https://github.com/Iwaku-Real/MFQM-decompiled)

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for project-specific
relationship and license notes.

For Chinese documentation, see [README.zh-CN.md](README.zh-CN.md).

## License

Mirebound is released under the [MIT License](LICENSE).
