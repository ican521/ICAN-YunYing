---
name: fongmi-player-tweak
description: 微调ICAN云影播放页/全屏播放器UI与交互并对齐fongmi实现。用户提出播放页、全屏控制层、进度条、动作栏、弹窗、手势、锁屏、投屏图标等播放器相关改动时使用；不适用于首页、收藏、历史、搜索、设置等非播放器页面。
---

# FongMi 播放器微调工作流

用户对播放页（竖屏小窗 / 平板横屏 / 全屏）提出视觉或交互微调时按本流程执行，保证改动可追溯、可构建、可装机验证。

## 0. 需求确认（用户硬规则）

- 先复述需求，用户确认后再改代码；涉及放置位置等歧义点时先提问。
- UI 必须严格参考 fongmi 源码实现，禁止凭主观判断自创样式。

## 1. 改动范围（严格限定）

- 只允许改动播放相关文件，典型为：
  - `app/src/main/java/com/ican/tvplay/player/PlayerScreen.kt`
  - `app/src/main/java/com/ican/tvplay/ui/components/AppIcons.kt`
  - `player` 包内播放器内核文件（仅当功能确需）
- 禁止读取/扫描项目其余无关文件，首页、收藏、历史、搜索、设置、站源弹窗原有代码保持不变。
- 播放器内核沿用 FongMi View 播放器，不重写为 Compose；Compose 外层只做 UI 承载。

## 2. 参考 fongmi 的方法

- fongmi 源码本地路径：`D:\TV-fongmi`，直接读本地，不联网查询。
- 常用参照位置：
  - 手机布局：`app/src/mobile/res/layout/`（如 `view_widget_vod.xml`、`activity_detail.xml`）
  - TV 布局：`app/src/leanback/res/layout/`
  - 播放控件逻辑：media3 ui 库 `media-fongmi/.../libraries/ui/.../PlayerControlView.java`
  - 倍速档位：`app/src/main/java/com/fongmi/android/tv/setting/SpeedSetting.java`（PRESETS）
  - 图标：`app/src/main/res/drawable/ic_control_*.xml`，矢量 path data 可直接照搬到 `AppIcons.kt` 的 `vector(...)`。
- 照搬时连交互时序一起对齐（如动作栏点击不收起控制层只重置 5 秒计时、横拖快进快退浮层、长按 2 倍速）。

## 3. 本项目播放器既有实现约定（改动时必须保持）

- 控制层自动隐藏计时收口在顶层一个 `LaunchedEffect`（keys 含 `controlsTick`、`seekDragging`）。新增"某交互期间禁止自动隐藏"需求时，在该执行点加门禁，不要在事件源处分散补丁；阶段型交互在 start/end 复位状态，松手后靠 key 变化重新计 5 秒。
- 时间文字：用 `StableTimeText`（透明最长格式占位锁宽 + `FontFamily.Monospace`），因为设备 OEM 字体会忽略 `tnum`。不要回退成普通 `Text`。
- 进度条用自绘 `LineSlider`：拖动中只更新 UI，松手才 seek；`onDragFractionChange(null/非null)` 是拖动会话信号。
- 选项弹窗统一用 `OptionDialog`（已限高 65% 屏高、内部可滚动）；新增档位改 options 列表即可，竖屏和全屏两处弹窗必须同步。
- 图标两态（如实心/空心心形）必须外轮廓完全重合，避免视觉尺寸不一致。
- 视频区手势层需避让已被控件消费的触摸事件，防止按钮点击与单击/双击/拖动手势冲突。
- 小窗 ↔ 全屏切换保持 PlayerView 实例不销毁，只改容器尺寸。
- 间距/边距/圆角优先用 Theme 全局常量，禁止硬编码新魔法数字。

## 4. 构建 → 提交 → 装机（固定顺序）

1. 编译：在 `d:\new tvbox` 执行 `.\gradlew.bat assembleDebug --console=plain -q`（PowerShell 5，命令前不要加 `&&`）。
2. 编译失败必须修复后重编，禁止带错提交；不要构建 release。
3. 编译通过才本地提交：
   - `git -C "d:\new tvbox" add <具体改动文件>`（不要 `git add -A`）
   - commit message 前缀用 `feat: / ui: / fix: / perf: / refactor:` + 中文描述
   - **只本地 commit，禁止 push**
4. 覆盖安装到手机：
   `& "C:\Users\15791\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "d:\new tvbox\app\build\outputs\apk\debug\app-debug.apk"`
   设备 ID 以 `adb devices` 实际结果为准。
5. 向用户汇报：改了哪些文件、每个需求点如何解决、commit 短哈希、安装结果；用 computer:// 链接给出 apk，用 file:/// 链接引用改动源码。

## 5. 注意事项

- 小步可逆：一次只改一个维度，避免布局整体重构互相覆盖。
- 不创建文档/README；不主动新增文件，优先编辑现有文件。
- 播放页历史遗留需求（片头片尾、画中画、保活服务等）以项目记忆中的 Hard Constraints 为准，不得顺手回退。
