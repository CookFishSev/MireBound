# Development Updates

## 2026-09-13 - MireBound-Beta1.2.3-MC1.21.1-NeoForge

- Fixed recessed corners on wall stains with some shader packs.
- Fixed overlapping wall flow layers and inconsistent fade timing.
- Fixed mixed materials being lost when stains wrap onto adjacent faces.
- Added irregular wall-stain edges and opacity variation.
- Fixed redundant stain calculations and uploads of unchanged textures.

- 修复了部分光影下墙面污染四角凹陷的问题。
- 修复了墙面流痕重复叠加和消退时间不一致的问题。
- 修复了相邻面污染丢失混合材质的问题。
- 添加了墙面污染边缘和浓淡的不规则变化。
- 修复了污染贴图重复计算和无变化时仍上传的问题。

Minecraft 1.21.1 / NeoForge. Version: `1.2.3-beta`. Author: CookFishSev.

## 2026-09-12 - MireBound-Beta1.2.2-MC1.21.1-NeoForge

- Added a Shift-expanded wand tooltip with fading text, gentle floating, and subtle gray flicker.
- Fixed assimilation coverage on extra skin UV pixels while preserving transparency and ignoring custom stain exclusions.
- Adjusted frozen model poses to retain rendered part sizes and visibility.

- 新增权杖 Shift 展开提示，文字逐字浮现、缓慢浮动并带有灰色暗闪。
- 修复特殊皮肤 UV 像素的同化覆盖，保留透明区域，并忽略自定义禁止沾染标记。
- 调整冻结姿势，保留部件实际渲染时的尺寸和可见状态。

Minecraft 1.21.1 / NeoForge. Version: `1.2.2-beta`. Author: CookFishSev.

## 2026-09-12 - MireBound-Beta1.2.1-MC1.21.1-NeoForge

- Improved backpack staining, washing, pollution indicators, and stain transfer to walls.
- Fixed missed equipment surfaces and reduced coverage rendering overhead.
- Fixed false foot stains and surface compression at Sable block edges.
- Fixed F11 settings panel positioning when resizing the window.

- 改善背包沾染、水洗、污染度显示和向墙壁转移污染。
- 修复装备部分表面漏染，减少污染渲染开销。
- 修复 Sable 方块交界处脚底误染和挤压误触发。
- 修复缩放窗口后 F11 设置面板的位置。

Minecraft 1.21.1 / NeoForge. Version: `1.2.1-beta`. Author: CookFishSev.
Clients and servers must both update to this version. 客户端与服务端均需更新至此版本。

## 2026-09-12 - MireBound-Beta1.2.0-MC1.21.1-NeoForge

- Added free-end rope connections, including closed loops and rescue ropes.
- Adjusted drag damping and preserved rope motion when released.
- Fixed reset buttons lighting up when switching unchanged settings pages.

- 新增绳索自由端连接，支持首尾闭环和救援绳互接。
- 调整拖动阻尼，松手后保留绳索运动状态。
- 修复未修改设置时切换分类导致重置按钮误亮的问题。

Minecraft 1.21.1 / NeoForge. Version: `1.2.0-beta`. Author: CookFishSev.
Clients and servers must both update to this version. 客户端与服务端均需更新至此版本。

## 2026-09-12 - MireBound-Beta1.1.0-MC1.21.1-NeoForge

A development release with a new skin editor and improvements from the recent code review.

- Fixed rescue lasso binding on upright posts; horizontal beams and obstruction checks are retained.

- Added a skin coverage editor with linked 3D and UV painting, HD pixel selection, part visibility, undo/redo, and pose presets, including Fresh Moves 3.1 eyes. You edit only your own skin; applying shares your coverage rules with other players on the server.
- Added an experimental equipment surface coverage mode for shared-UV models, transparent cutouts, and Sophisticated Backpacks. The classic coverage system remains available and is the default.
- Made large wand selections scan in bounded steps and reuse profile summaries to reduce repeated allocation and comparisons.
- Isolated rope input ordering per player. Rope and tentacle simulation now pauses when required terrain chunks are unavailable.
- Improved custom mud appearance persistence, coverage texture resizing for HD skins, and placement of world-generation settings alongside other mods.
- Removed 25 obsolete recipes and loot tables, restored the bundled rope-code license notice, and updated all nine supported languages.

Minecraft 1.21.1 / NeoForge. The loader version is `1.1.0-beta`; the release file is named `MireBound-Beta1.1.0-MC1.21.1-NeoForge.jar`. Network protocol is `174`, so clients and servers need the same build.

## 中文

这次主要加了皮肤沾染编辑器，也把前面代码审查发现的问题整理了一轮，作为开发版本发布。

- 修复救援套索无法绑定竖直柱子的问题，保留横梁绑定和障碍物检查。

- 新增皮肤沾染编辑器：3D 与 UV 联动涂画、高清像素选择、部位隐藏、撤销重做和动作预设，包含 Fresh Moves 3.1 眼部预设。只能编辑自己，应用后同服玩家也能看到本人设置的效果。
- 新增实验性的装备独立表面污染，处理共享 UV、透明孔洞，并接入精妙背包。原来的污染系统继续保留，默认使用经典方案。
- 大型权杖选区改为分步扫描，复用参数汇总，减少重复分配和比较。
- 绳索输入序号按玩家隔离；绳索、触手需要的地形区块未加载时暂停模拟。
- 改善自定义流沙污染材质的保存与恢复、高清皮肤切换后的纹理尺寸，以及创建世界设置入口与其他模组的布局兼容。
- 清理 25 个失效配方和战利品表，补回绳索参考代码的许可文件，并更新现有九种语言。

适用于 Minecraft 1.21.1 / NeoForge。加载器内部版本为 `1.1.0-beta`，发布文件名为 `MireBound-Beta1.1.0-MC1.21.1-NeoForge.jar`。网络协议为 `174`，客户端和服务端需要使用同一构建。
