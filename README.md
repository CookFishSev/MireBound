# Mirebound

Mirebound is a work-in-progress NeoForge mod for Minecraft 1.21.1. It adds
dangerous sinking terrain, material-specific physics, environmental stains,
and tools for exploring and escaping unstable ground.

## Features

- Multiple sinking materials with distinct movement and sinking behavior.
- Configurable natural generation across supported dimensions and biomes.
- Mud coverage on players, armor, dropped items, and nearby surfaces.
- Footprints, wall stains, splashes, bubbles, and other material effects.
- In-game tools for probing depth, tuning sinking blocks, and washing mud away.
- Optional integration with Sable physical structures.
- Client and world-generation settings with multilingual interface support.

Mirebound is still under active development. Features, configuration, and save
compatibility may change before a stable release.

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.233` or a compatible `21.1.x` release
- Java `21`
- Curios `9.5+` (optional)

## Building

Run the following command from the repository root:

```text
gradle "-Dorg.gradle.problems.report=false" build --no-configuration-cache --stacktrace
```

The built mod JAR is written to `build/libs/`.

## Installing

Place the built JAR in the `mods` directory of a NeoForge 1.21.1 instance.
Install Mirebound on both the server and every connecting client when playing
multiplayer. Back up important worlds before testing development builds.

## Credits

Thanks to the developers of
[Made In Abyss](https://github.com/MIA-Development-Team/Made-In-Abyss),
[Aeronautics](https://github.com/Creators-of-Aeronautics/Simulated-Project),
[Sable](https://github.com/ryanhcode/sable),
[Litematica](https://github.com/maruohon/litematica),
[Quicksand-Rehydrated](https://github.com/Theyoungster/Quicksand-Rehydrated),
and [MFQM](https://github.com/Iwaku-Real/MFQM-decompiled) for the projects and
ideas that informed parts of Mirebound's design and implementation.

## License

See [LICENSE](LICENSE).
