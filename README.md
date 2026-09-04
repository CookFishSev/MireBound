# Mirebound: Sinking Depths

<p>
  <img src="src/main/resources/icon.png" alt="Mirebound icon" width="128">
</p>

**Mirebound: Sinking Depths** is a gameplay expansion mod centered on
quicksand. It currently supports Minecraft 1.21.11 with NeoForge.

This mod continues the design concept of the 1.7.10-era `More Fun Quicksand
Mod`, rebuilding its gameplay balance and physics systems while introducing a
small number of new items.

---

## Design Philosophy

- Deliver the best possible visual quality while maintaining good performance;
- Keep the physics immersive without compromising gameplay balance;
- Provide a high degree of configurability for different preferences;
- Pursue broad compatibility with different mods.

---

## Features

- **27 types of quicksand**: following the idea that almost anything can become
  quicksand, with as much of the original block identity retained as possible;
- **Detailed contamination effects**: player skins and armor can be affected
  by quicksand, with broad support for custom mods and resource packs;
- **Physics simulation**: highly configurable quicksand physics for sinking and
  struggling;
- **Rich event systems**: multiple configurable quicksand events add variety
  to the gameplay;
- **New struggle mechanics**: more detailed feedback and interaction after a
  player becomes trapped in quicksand;
- **Rendering optimization**: compatibility with most custom renderers while
  keeping quicksand surface effects distinctive and unobtrusive.

---

## Future Plans

The core mod is currently focused on stability, and no major new content is
planned in the short term.

In the future, I plan to build companion mods on top of this project, which
may introduce new dimensions, items, and special events.

---

## Building

Build requirements:

- Java `21`
- Gradle `8.x` or a compatible version

Run the following command from the repository root:

```text
gradle "-Dorg.gradle.problems.report=false" build --no-configuration-cache --stacktrace
```

The built mod JAR is written to `build/libs/`.

---

## Feedback And Contributions

Feedback and support from players and developers are welcome!

- **Report a bug**: please visit [GitHub Issues](https://github.com/CookFishSev/MireBound/issues). When submitting an issue, please include:
  - The mod and game version, such as `v1.0.0`;
  - The complete error log (`latest.log` or a crash report);
  - Clear steps to reproduce the problem.
- **Other feedback**: suggestions and questions are also welcome in [Issues](https://github.com/CookFishSev/MireBound/issues).

Thank you for helping make Mirebound better!

---

## Special Thanks

The development of this mod was informed by the following open-source
projects:

- **[Memento In Abyss / Made In Abyss](https://github.com/MIA-Development-Team/Made-In-Abyss)** - rope physics implementation ideas;
- **[Aeronautics / Simulated Project](https://github.com/Creators-of-Aeronautics/Simulated-Project)** - reference material for physical-world compatibility;
- **[Sable](https://github.com/ryanhcode/sable)** - reference material for physical-structure compatibility;
- **[Litematica](https://github.com/maruohon/litematica)** - selection rendering and related logic;
- **[Quicksand-Rehydrated](https://github.com/Theyoungster/Quicksand-Rehydrated)** - gameplay inspiration for a modern quicksand mod;
- **[MFQM Decompiled Reference](https://github.com/Iwaku-Real/MFQM-decompiled)** - early behavior and presentation reference.

### Development Note

ChatGPT and Claude were used as programming assistants during development.
I have spent several months carefully polishing and testing every feature in
the actual game to provide players with the best possible experience.

> For detailed third-party relationship and license information, see
> [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).
