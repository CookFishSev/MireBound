# Mirebound：沉陷之境

Mirebound 是一个面向 Minecraft 1.21.1 NeoForge 的模组，围绕流沙与其
他沉陷介质，提供材质差异化物理、环境污染，以及探索和脱困工具。

项目仍在持续开发中。不同开发版本之间可能调整功能、配置格式和存档兼容性。

## 内容特色

- 多种具有不同物理、视觉和玩法表现的沉陷介质。
- 支持按群系和维度配置自然生成。
- 支持玩家皮肤、盔甲、掉落物和环境表面的持久污染效果。
- 提供脚印、墙面污染、水花、气泡等环境表现。
- 提供探泥杖、权杖、水枪和救援绳索等工具。
- 提供客户端设置和世界生成设置，并支持多语言界面。
- 可选的 Sable 物理结构兼容。

## 环境要求

- Minecraft `1.21.1`
- NeoForge `21.1.233` 或兼容的 `21.1.x` 版本
- Java `21`
- Curios `9.5+` 为可选依赖

多人游戏时，服务端和所有加入的客户端都需要安装 Mirebound。测试开发版前，
请先备份重要存档。

## 构建

在仓库根目录执行：

```text
gradle "-Dorg.gradle.problems.report=false" build --no-configuration-cache --stacktrace
```

构建得到的模组 JAR 位于 `build/libs/`。

## 反馈与贡献

欢迎在 [Issues](https://github.com/CookFishSev/MireBound/issues) 提交 Bug
或改进建议。请尽量附上 Minecraft 和 NeoForge 版本、Mirebound 版本、清晰的
复现步骤，以及相关的 `latest.log` 或崩溃报告。

较大的代码改动建议先通过 Issue 讨论，并在适当情况下附带测试。请保持改动
范围清晰，不要提交本机配置、构建产物或私人开发文件。

## 致谢

Mirebound 的部分设计和实现参考了以下项目：

- [Made In Abyss](https://github.com/MIA-Development-Team/Made-In-Abyss)
- [Aeronautics / Simulated Project](https://github.com/Creators-of-Aeronautics/Simulated-Project)
- [Sable](https://github.com/ryanhcode/sable)
- [Litematica](https://github.com/maruohon/litematica)
- [Quicksand-Rehydrated](https://github.com/Theyoungster/Quicksand-Rehydrated)
- [MFQM Decompiled Reference](https://github.com/Iwaku-Real/MFQM-decompiled)

具体的项目关系和许可证说明请参阅
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

Mirebound 使用 [MIT License](LICENSE) 发布。
