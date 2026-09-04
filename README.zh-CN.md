# Mirebound: Sinking Depths

**Mirebound: Sinking Depths** 是一个以流沙为核心的玩法扩展模组，目前仅支持 Minecraft 1.21.1 NeoForge 版本。

本模组延续了 1.7.10 时期 `More Fun Quicksand Mod` 的设计理念，对玩法平衡性与物理系统进行了重构，并引入少量新道具。

---

## 设计理念

- 在保证良好性能的前提下，呈现最优质的视觉效果；
- 力求贴近真实物理，同时不破坏游戏平衡；
- 提供高度可配置性，满足不同玩家的偏好；
- 追求与各类模组的最大兼容性。

---

## 内容特色

- **27 种流沙类型**：贯彻“万物皆可流沙”的理念，支持几乎所有方块的流沙化，并尽可能保留其原有特性；
- **精细的污染效果**：皮肤和盔甲会受流沙影响，且最大限度兼容自定义模组和材质包；
- **拟真物理系统**：可高度自定义的流沙物理参数，带来真实的下沉与挣扎体验；
- **丰富的事件系统**：提供多种可配置的流沙特殊事件，增加游戏变数；
- **全新的挣扎机制**：玩家陷入流沙后的操作反馈更加丰富；
- **渲染优化**：兼容大部分自定义渲染器，并呈现有趣而不突兀的流沙表面效果。

---

## 后续规划

目前模组主体将专注于核心体验的稳定，短期内不再新增内容。  
未来，我将以本模组为基石，着力开发附属模组，可能会带来全新的维度、道具和特殊事件，敬请期待。

---

## 构建

构建要求：

- Java `21`
- Gradle `8.x` 或兼容版本

在仓库根目录执行：

```text
gradle "-Dorg.gradle.problems.report=false" build --no-configuration-cache --stacktrace
```

构建得到的模组 JAR 位于 `build/libs/`。

---

## 反馈与贡献

欢迎所有玩家和开发者为本模组提供反馈与支持！

- **报告 Bug**：请前往 [GitHub Issues](https://github.com/CookFishSev/MireBound/issues) 提交问题。提交时请务必附上：
  - 模组版本（如 `0.1.0`）；
  - 完整的错误日志（`latest.log` 或崩溃报告）；
  - 清晰的重现步骤。
- **其他反馈**：如果你有任何建议或疑问，也可以在 [Issues](https://github.com/CookFishSev/MireBound/issues) 中提出，我会尽快回复。

感谢你的参与，让 Mirebound 变得更好！

---

## 特别致谢

本模组的开发离不开以下开源项目的启发与帮助，特此致谢：

- **[Memento In Abyss / Made In Abyss]** – 提供了物理绳索的实现思路；
- **[Aeronautics / Simulated Project]** – 为适配物理世界提供了巨大帮助；
- **[Sable](https://github.com/ryanhcode/sable)** – 用于物理结构兼容；
- **[Litematica](https://github.com/maruohon/litematica)** – 参考了其框选内容的渲染方式与逻辑，极大优化了性能；
- **[Quicksand-Rehydrated]** – 提供了玩法灵感，并激励我在高版本实现流沙模组；
- **[MFQM Decompiled Reference]** – 为本模组的起步提供了宝贵的指引。

### 开发备注

本模组在开发过程中借助了 ChatGPT 与 Claude 进行辅助编程，但所有功能均经过本人数月的细致打磨与实机调试，以确保玩家获得最佳体验。

> 更详细的第三方依赖及许可证信息，请参阅 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)
