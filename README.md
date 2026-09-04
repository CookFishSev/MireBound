# Mirebound: Sinking Depths

![Mirebound icon](src/main/resources/icon.png)

Mirebound is a NeoForge mod for Minecraft 1.21.1 centered on sinking terrain,
material-specific physics, environmental contamination, and practical tools
for exploration and escape.

The project is under active development. Features, configuration formats, and
save compatibility may change between development releases.

## Features

- Multiple sinking materials with distinct physics, visuals, and gameplay.
- Configurable natural generation across supported biomes and dimensions.
- Persistent mud coverage for player skins, armor, dropped items, and surfaces.
- Footprints, wall stains, splashes, bubbles, and other material effects.
- A depth probe, tuning wand, water gun, and rope-based rescue tools.
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

The built JAR is written to `build/libs/`.

## Feedback And Contributions

Bug reports and focused improvement proposals are welcome on the
[issue tracker](https://github.com/CookFishSev/MireBound/issues). Please
include the Minecraft and NeoForge versions, the Mirebound version, clear
reproduction steps, and the relevant `latest.log` or crash report.

For code contributions, discuss larger changes in an issue first and include
tests where practical. Keep changes focused and do not include local
configuration, generated build output, or private development files.

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
