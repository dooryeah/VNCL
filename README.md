# VNCL Launcher

VNCL 是一个基于 [Hello Minecraft! Launcher (HMCL)](https://github.com/HMCL-dev/HMCL) 的第三方修改版，
主打「默认极简、设置可搜索」：主页只保留实例卡与启动按钮，界面外观（圆角 / 密度 / 字号）可以在设置里实时调整。

> **这是第三方二次创作，不是 HMCL 官方版本。** 本项目不是由 HMCL 团队发布或背书的。

## 当前功能

- Minecraft 版本管理：原版 / Fabric / Quilt / Forge / NeoForge，版本隔离，整合包导入导出
- 下载：游戏、加载器、资源、Mod（多下载源、并发、校验、断点续传）
- 账户：离线、微软正版（需自行提供 Client ID）、authlib-injector 第三方皮肤站
- Java 管理：自动检测、版本匹配、下载
- Mod / 资源包 / 数据包 / 截图管理
- **VNCL 外观**：设置 → 通用 → 「VNCL 外观」，提供
  - 圆角（0–24 px）
  - 界面密度（85%–120%）
  - 字号缩放（90%–115%）
  - 预设：默认 / 紧凑 / 宽松 / 恢复默认
  - 顶部「搜索设置…」框可按标题、关键词过滤设置分区
- 主题与背景沿用 HMCL 的主题包体系（主题包、背景图、壁纸取色、透明标题栏等）

## 运行要求

- Windows 10/11（其他平台的构建未测试）
- Java 17 或更高版本（推荐 21）
  - 若系统未安装，发行版自带的启动器会尝试查找/使用已配置的 Java 运行时

## 构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
./gradlew.bat :HMCL:makeExecutables --no-daemon
```

产物位于 `HMCL/build/libs/`：

- `VNCL-<version>.exe` —— 单文件启动器（Windows，双击运行）
- `VNCL-<version>.jar` —— 需要自行用 `java -jar` 启动

> 构建需要访问 Maven Central；若 Gradle 发行版下载缓慢，可参考 `gradle/wrapper/gradle-wrapper.properties`
> 中使用的镜像配置（本项目默认指向腾讯云镜像，校验和不变）。

## 与上游的差异

详见 `docs/` 与提交历史，主要改动：

- 品牌替换：名称、图标、窗口标题、用户目录（`%APPDATA%\.vncl`）、更新与反馈入口
- 移除上游社区服务入口（Terracotta 联机、QQ/Discord 群组、构建通道公告）
- 新增极简主页（实例卡 + 大启动按钮）
- 新增 VNCL 外观 token（圆角 / 密度 / 字号 / 预设）与设置搜索

## 许可

本项目基于 HMCL，按 **GNU General Public License v3.0 or later**（含 HMCL 附加条款）分发：

- 完整许可文本见 [`LICENSE`](LICENSE)
- 附加条款要求：分发修改版时必须改名或改版本号以区别于原版；不得移除版权声明
- 上游版权：`Copyright (C) 2013-2026 The HMCL Authors`
- VNCL 修改部分：`Copyright (C) 2026 The VNCL Contributors`

本项目源代码公开，任何分发都必须附带对应源代码。
