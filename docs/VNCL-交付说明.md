# VNCL 交付说明（M1–M2 + 打包）

> 更新：2026-11-08 · 源码：`D:\MC\VNCL` · 可执行文件：`D:\MC\VNCL\dist\VNCL.exe`

## 1. 可运行产物

| 文件 | 说明 |
|---|---|
| `dist\VNCL.exe` | 单文件启动器（HMCL 启动包装器 + 内嵌 jar），双击运行 |
| `dist\LICENSE.txt` | GPLv3 + HMCL 附加条款全文（分发必须附带） |
| `dist\README.txt` | 运行要求与许可提示 |
| `HMCL\build\libs\VNCL-<ver>.exe / .jar` | 构建产物（可重建） |

运行要求：Windows 10/11 + Java 17+（推荐 21）。数据目录 `%APPDATA%\.vncl`。

## 2. 已实现（相对上游的改动）

- **品牌**：VNCL 名称、图标（32–512 px）、窗口标题、用户目录 `.vncl`、关于页署名与「基于 HMCL」链接
- **极简主页**：居中实例卡（实例名 / Minecraft 版本 / 大启动按钮 / 切换箭头），无公告与更新气泡
- **侧栏精简**：账户 / 实例 / 下载 / 设置 / 反馈；移除重复实例入口、Terracotta 入口、上游群组
- **VNCL 外观**（设置 → 通用）：
  - 圆角 0–24 px、界面密度 85%–120%、字号缩放 90%–115%
  - 预设：默认 / 紧凑 / 宽松 / 恢复默认
  - 设置搜索框：按标题、描述、关键词过滤设置分区
- **离线解耦**：更新检查无更新源时不联网；不再请求 HMCL 官方更新/群组/联机服务

## 3. 验证记录

- 构建：`gradlew :HMCL:makeExecutables` 通过，产物 ~11.9 MB
- 运行：`dist\VNCL.exe` 双击后出现 `VNCL Launcher 0.1.SNAPSHOT` 窗口（Java 21）
- 界面：主页实例卡、设置页 VNCL 外观与预设、关于页（VNCL + 基于 HMCL + GPL 说明）均截图核对
- 配置：`%APPDATA%\.vncl\config\vncl-ui.json` 正确写入圆角/密度/字号

## 4. 已知限制

1. **Java 运行时**：启动包装器（上游 `HMCLauncher`）会把运行时放在 `%APPDATA%\.hmcl\java`；若要完全品牌化，需要自建包装器或改用 jpackage/jlink 打包。
2. **微软正版登录**：需要自备 Azure 应用 Client ID（构建时注入 `MICROSOFT_AUTH_ID`），当前版本未启用。
3. **仓库地址**：`Metadata` 中仍是占位 `github.com/VNCL-Launcher/VNCL`，更新源/反馈入口指向该地址。
4. **测试范围**：仅在 Windows 11 实机验证；macOS/Linux 未测。
5. **外部服务**：联机（Terracotta）、官方公告、社区群组已移除入口，代码仍在上游工程中。

## 5. 重建命令

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
cd D:\MC\VNCL
./gradlew.bat :HMCL:makeExecutables --no-daemon
Copy-Item HMCL\build\libs\VNCL-0.1.SNAPSHOT.exe dist\VNCL.exe -Force
```

## 6. 许可合规

- 保留全部 SPDX 版权头与 `LICENSE`（GPL-3.0-or-later WITH AdditionRef-HMCL）
- 按附加条款改名（VNCL）以区别于原版
- 关于页与 README 均标注「基于 HMCL 的第三方修改版」并提供上游链接
- 仓库公开，分发时须附源码

## 7. v0.2 更新（侧栏 / 设置入口 / 启动动画）

- **侧栏收缩**：侧栏底部箭头按钮可在完整导航与 60px 图标栏之间切换，状态保存在 `%APPDATA%\.vncl\config\vncl-ui.json` 的 `sidebarCollapsed`。
- **启动动画**：设置 → 通用 → 「启动动画」启用后，每次启动都会在**整个窗口**（含标题栏）播放所选视频；**按任意键或点击**跳过，视频结束/超时自动淡出。
- 视频目录：**启动器所在目录的 `videos\`**（`dist\videos\`），支持 `mp4 / m4v / mov / wmv`；推荐 H.264 + AAC 的 MP4（部分编码会报 `ERROR_MEDIA_INVALID`，例如旧的 `mov_bbb.mp4`）。
- 「提交反馈」从主页侧栏移除，仍可在 设置 → 反馈 进入。
- **设置入口**：移入窗口标题栏右上角的齿轮按钮，默认打开「通用」页；侧栏不再有设置项。
- 首次使用启动动画时，启动器会联网下载 `javafx-media`（约 10MB，Java 21 / Java 25 两套运行时各一次）。
- 附带示例视频 `dist\videos\sample-5s.mp4`，可删除或替换。

## 8. v0.2.1 修复与改名

- 启动器全称改为 **Vanilla-Neko Craft Launcher**（标题栏、关于页、崩溃报告等文本同步；`VNCL` 仍作为简称使用）。
- 启动动画改为在 `primaryStage.show()` **之后**启动，避免窗口还没画出来视频就已经播完。
- 跳过方式：**按任意键**或**在窗口内点击鼠标左键**（点击画面任意位置即可）。
- 增加 300ms 按键保护期，避免启动瞬间的按键/连发直接跳过；超时保护从 20 秒放宽到 5 分钟，避免长视频被提前切断。
- 动画启动、跳过原因（key / end / timeout / error）与失败原因写入日志，便于排查。
- 移除了在 JavaFX 25（打包版 exe 使用的运行时）上无法解码的示例视频，避免「不播放」的误判；推荐使用 H.264 + AAC 的 MP4。

## 9. v0.2.2 外观设置修复

- 修复：调整「VNCL 外观」滑块时，运行时替换样式表会让 JFXSwitch 控件重新测量、渲染成黑色色块，并伴随背景闪烁。
- 现在外观 token 只在启动时应用一次，设置页提示「外观修改将在重启启动器后生效」；滑块仍会立即显示数值并持久化。
- 这样避免了拖动/松手时的整页重排与控件变形。

## 10. Logo / 图标

- 启动器 logo 与 icon 已替换为用户提供的图片：窗口图标（`icon*.png`）、标题栏图标（`icon-title*.png`）、关于页图标、macOS 图标与 exe 图标。
- 小尺寸（16–64px）使用面部紧凑裁切，保证任务栏/标题栏可辨认；大尺寸保留完整构图；四角做透明圆角。
- 生成脚本：`scripts/generate-icons.py <图片> [资源目录]`，输出 `assets/img/icon*.png` 与 `image/vncl.ico`。
- exe 图标：`scripts/set-exe-icon.ps1 -ExePath dist\VNCL.exe -IcoPath image\vncl.ico -JarPath HMCL\build\libs\VNCL-0.1.SNAPSHOT.jar`。
  启动器 exe 是 HMCLauncher 桩 + 追加的 jar；直接 `UpdateResource` 会丢掉追加的 jar，脚本会先提取桩、改图标、再与 jar 拼接。
- 一键构建：`scripts/build-dist.ps1`（Gradle 构建 → 复制到 dist → 打图标）。
