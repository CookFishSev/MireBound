# Development Updates

## 2026-09-12 - MireBound-Beta1.1.0-MC1.21.1-NeoForge

A development release with a new skin editor and improvements from the recent code review.

- Added a skin coverage editor with linked 3D and UV painting, HD pixel selection, part visibility, undo/redo, and reusable presets, including Fresh Moves 3.1 eyes. You edit only your own skin; applying shares your coverage rules with other players on the server.
- Added an experimental equipment surface coverage mode for shared-UV models, transparent cutouts, and Sophisticated Backpacks. The classic coverage system remains available and is the default.
- Made large wand selections scan in bounded steps and reuse profile summaries to reduce repeated allocation and comparisons.
- Isolated rope input ordering per player. Rope and tentacle simulation now pauses when required terrain chunks are unavailable.
- Improved custom mud appearance persistence, coverage texture resizing for HD skins, and placement of world-generation settings alongside other mods.
- Removed 25 obsolete recipes and loot tables, restored the bundled rope-code license notice, and updated all nine supported languages.

Minecraft 1.21.1 / NeoForge. Internal mod version is `MireBound-Beta1.1.0-MC1.21.1-NeoForge`; network protocol is `174`, so clients and servers need the same build.

## 中文

这次主要加了皮肤沾染编辑器，也把前面代码审查发现的问题整理了一轮，作为开发版本发布。

- 新增皮肤沾染编辑器：3D 与 UV 联动涂画、高清像素选择、部位隐藏、撤销重做和预设，包含 Fresh Moves 3.1 眼部预设。只能编辑自己，应用后同服玩家也能看到本人设置的效果。
- 新增实验性的装备独立表面污染，处理共享 UV、透明孔洞，并接入精妙背包。原来的污染系统继续保留，默认使用经典方案。
- 大型权杖选区改为分步扫描，复用参数汇总，减少重复分配和比较。
- 绳索输入序号按玩家隔离；绳索、触手需要的地形区块未加载时暂停模拟。
- 改善自定义流沙污染材质的保存与恢复、高清皮肤切换后的纹理尺寸，以及创建世界设置入口与其他模组的布局兼容。
- 清理 25 个失效配方和战利品表，补回绳索参考代码的许可文件，并更新现有九种语言。

适用于 Minecraft 1.21.1 / NeoForge。内部模组版本为 `MireBound-Beta1.1.0-MC1.21.1-NeoForge`，网络协议为 `174`，客户端和服务端需要使用同一构建。
