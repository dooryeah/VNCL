# VNCL 改动计划 v0.2

> 目标：侧栏可收展、主页去反馈入口、设置改为右上角齿轮、新增可配置的启动器启动动画（窗口内播放）
> 基线：`D:\MC\VNCL`（当前 `2523bf0`）

---

## 0. 摘要与工期

| # | 改动 | 预估 | 主要触点 |
|---|---|---|---|
| 1 | 左侧栏收缩 / 展开 | 1–1.5 天 | `DecoratorAnimatedPage`、`AdvancedListBox`、`RootPage`、`root.css` |
| 2 | 移除主页「提交反馈」 | 0.1 天 | `RootPage` |
| 3 | 设置入口改为右上角齿轮 | 0.5 天 | `MainWindowPane.createWindowButtons`、`LauncherSettingsPage`、`RootPage` |
| 4 | 启动动画（窗口内、可配置） | 2–3 天 | `buildSrc/JavaFXUtils`、`openjfx-dependencies.json`、`VnclUiConfig`、新增 `VnclStartupAnimation`、`MainWindowPane` |

合计约 **4–5 个工作日**。

---

## 1. 侧栏收缩 / 展开

### 交互

- 默认展开（现状宽度，约 200px）；收起后为 56–64px 的图标栏。
- 切换入口：侧栏底部一个吸附按钮（chevron 图标），点击切换；展开时 chevron 朝左，收起时朝右。
- 过渡：宽度 200ms 缓动，同时淡入/淡出文字。
- 收起态：
  - 分类标题（“账户 / 游戏 / 通用”）隐藏；
  - 条目标题与副标题隐藏，图标居中；
  - 悬停显示 Tooltip（账户名 / 实例名 / 下载 / 启动）；
  - 侧栏「启动」按钮变成圆形/方形图标按钮（Play 图标）。
- 状态持久化：写入 `VnclUiConfig`（新增 `sidebarCollapsed`，默认 `false`）。

### 技术实现

| 文件 | 改动 |
|---|---|
| `ui/decorator/DecoratorAnimatedPage.java` | `left` 当前被 `FXUtils.setLimitWidth(left, 200)` 限宽；新增 `setSidebarCollapsed(boolean)`：用 `Timeline` 动画 `left.prefWidth`（200 ↔ 64），并给根节点加样式类 `vncl-sidebar-collapsed` |
| `ui/construct/AdvancedListBox.java` | 新增 `setCollapsed(boolean)`：给 `container` 加样式类；折叠时隐藏 `ClassTitle`（分类标题节点） |
| `ui/construct/AdvancedListItem(‑Skin)` | 折叠态下隐藏 `.title` / `.subtitle`、图标居中；Tooltip 取标题文本（CSS 无法直接做，需在 skin 里监听样式类或提供 API） |
| `ui/main/RootPage.java` | 底部增加切换按钮（`setSidebarBottom` 已有 API）；侧栏启动按钮在折叠态切换为图标 + Tooltip |
| `assets/css/root.css` | `.vncl-sidebar-collapsed` 下的隐藏/居中规则、过渡时长对齐动效设置 |

### 边界

- 窗口最小宽度下仍可用（折叠后内容区变宽，无裁切）。
- 折叠时右键菜单、弹窗定位仍依附于对应条目。
- 键盘导航（Tab）在折叠态仍可达。
- 动画遵循「减少动效」设置（动效关闭时直接切换）。

---

## 2. 主页移除「提交反馈」

- 删除 `RootPage` 中的 `.addNavigationDrawerItem(i18n("contact.feedback"), SVG.CHAT, ...)`。
- 反馈页本身保留：设置 → 反馈 tab（`LauncherSettingsPage.feedbackTab`）仍可进入。
- 结果：主页侧栏 = 账户 / 游戏（实例 + 启动）/ 下载。

---

## 3. 设置入口移到右上角齿轮

### 交互

- 在窗口标题栏右侧按钮组（任务 / 帮助 / 最小化 / 关闭）中，新增**设置齿轮**，位于任务按钮之后、帮助之前。
- 点击后进入设置页并默认选中「通用」tab（启动器设置），而不是现在的「全局游戏设置」。
- 设置页内再次点击齿轮：若已在设置页则不重复入栈（仅切 tab）。
- 侧栏移除「设置」条目，保留下载。

### 技术实现

| 文件 | 改动 |
|---|---|
| `ui/decorator/MainWindowPane.java` | `createWindowButtons()` 中新增 `settingsButton`（`SVG.SETTINGS`，样式类 `jfx-decorator-button`，`accessibleText` 用 `i18n("settings")`） |
| `ui/main/LauncherSettingsPage.java` | 新增 `showLauncherSettings()`：`tabController.selectTab(settingsTab, false)` |
| `Controllers` / 导航 | 齿轮 action：`Controllers.getSettingsPage().showLauncherSettings(); Controllers.navigate(Controllers.getSettingsPage())`；已在设置页时只切 tab |
| `ui/main/RootPage.java` | 移除设置条目 |

### 边界

- 设置页、帮助页等子页面标题栏也有按钮组 → 齿轮全局可见，行为一致。
- 齿轮字体/图标颜色跟随标题栏主题（`Themes.titleFillProperty()`）。

---

## 4. 启动器启动动画（窗口内）

### 交互与设置项

设置 → 通用 → 新增「启动动画」分区（与 VNCL 外观并列）：

| 设置项 | 说明 |
|---|---|
| 启用启动动画 | 开关，默认关闭 |
| 动画文件 | 下拉：扫描 `<用户目录>/videos` 下的 `.mp4/.m4v/.mov/.wmv` |
| 音量 | 0–100%，或静音开关 |
| 允许点击跳过 | 默认开；跳过时立即淡出 |
| 按钮 | 打开文件夹 / 刷新列表 / 预览 |

播放行为：

- 主窗口 `show` 后，在**窗口内容区**叠加全尺寸覆盖层播放视频，`MediaView` 随窗口尺寸缩放：默认等比（黑边），可选「填充裁剪」；
- 播放期间覆盖标题栏以下全部内容（是否盖住标题栏见待确认项）；
- 结束 / 跳过 / 超时（默认 8–10s）后 200ms 淡出，露出主界面；
- 视频缺失 / 解码失败 / 未启用 → 直接跳过（写日志，不弹窗）。

### 技术实现

**① JavaFX 媒体模块**

- HMCL 的 JavaFX 是运行时按需下载的（`assets/openjfx-dependencies.json` + `SelfDependencyPatcher`），当前模块 `base/graphics/controls`。
- 修改 `buildSrc/.../javafx/JavaFXUtils.java` 的 `MODULES`，加入 `"media"`，重新执行 `generateOpenJFXDependencies`（会从 Maven Central 拉取各平台 `javafx-media` 的 sha1，写入 JSON）。
- 运行时 `SelfDependencyPatcher` 下载 `javafx-media-<ver>-<classifier>.jar`（Windows x64 约 8–10 MB，首次启动下载）。
- 之后即可使用 `javafx.scene.media.Media` / `MediaPlayer` / `MediaView`。

**② 配置与存储**

- `vncl/VnclUiConfig.java` 新增字段：`startupAnimationEnabled`、`startupAnimationFile`、`startupAnimationVolume`、`startupAnimationSkip`、`startupAnimationFill`（等比/裁剪）。
- 视频目录：`Metadata.HMCL_USER_HOME/videos`（`%APPDATA%\.vncl\videos`），自动创建。

**③ 播放覆盖层**

- 新增 `vncl/VnclStartupAnimation.java`：
  - `installIfEnabled(Decorator/MainWindowPane)`：读配置、校验文件、创建 `Media`；
  - 覆盖层 `StackPane`（style class `vncl-startup-overlay`，黑底），内含 `MediaView`、右下角「点击跳过」提示、可能的静音小按钮；
  - `MediaView.fitWidth/fitHeight` 绑定覆盖层尺寸；`preserveRatio` 按设置切换；
  - `mediaPlayer.setOnEndOfMedia` / `setOnError` / `PauseTransition` 超时 → `finish()` 淡出并 `dispose()`；
  - 点击 / ESC → `finish()`（仅当允许跳过）；
  - 预览复用同一实例，但只覆盖窗口、结束即关。
- 挂载点：`MainWindowPane` 的 `getChildren()` 现有 `[backgroundNode, frame]`，在 `Decorator` 显示后调用 `VnclStartupAnimation.showIfEnabled(this)` 追加为第三层。
- 时机：`Launcher` 中 `stage.show()` 之后（`Platform.runLater`）触发，避免首帧黑屏；若播放器初始化失败则静默跳过。

### 风险与对策

| 风险 | 对策 |
|---|---|
| 首次启动需下载 javafx-media（体积/网络） | 发布包可预置依赖（离线包），失败时静默跳过动画 |
| 解码兼容性（H.265/AV1 不支持） | 文件类型限制在 mp4/H.264；失败降级 |
| 播放时窗口缩放/最小化 | 绑定尺寸、监听窗口状态，最小化时暂停 |
| 启动被动画拖慢 | 覆盖层不阻塞初始化；硬超时 |
| 多显示器 DPI | 覆盖层跟随窗口场景坐标，无需额外换算 |

---

## 5. 验证计划

1. 侧栏收起/展开：动画、Tooltip、状态持久化、重启后保持；
2. 折叠态启动按钮可正常启动/安装；
3. 主页无「提交反馈」，设置页仍能进入反馈；
4. 任意页面点击右上角齿轮 → 设置（通用 tab）；重复点击不叠加页面；
5. 启动动画：设置选中 mp4 → 重启 → 窗口内播放、随窗口缩放、点击/ESC 跳过、播完淡出；
6. 禁用/文件缺失/损坏视频 → 正常启动无报错弹窗；
7. 构建 + `dist\VNCL.exe` 运行验证。

---

## 6. 待确认

1. **视频目录**：`%APPDATA%\.vncl\videos`（推荐，跟随数据目录）还是启动器目录下 `videos\`（便携）？
2. **播放范围**：动画覆盖整个窗口（含标题栏）还是只覆盖内容区（标题栏保留）？
3. **播放时机**：每次启动都播，还是仅在「首次启动 / 版本更新后」播一次？
4. 动画结束前是否允许用户先进入界面（例如视频继续播、界面已可操作）？建议：不允许，保持仪式感，但可跳过。
