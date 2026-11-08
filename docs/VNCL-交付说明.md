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
