# VNCL 内置游玩时长统计 — 设计计划

> 目标：让玩家知道「自己玩了多久 Minecraft」，并提供历史统计、本次游玩计时与轻量可视化。
> 原则：只统计通过 VNCL 启动的游戏；数据纯本地；低开销、可导出、可清除。

---

## 1. 统计口径

- **范围**：仅统计由 VNCL 启动的 Minecraft 进程（外部启动器 / 直接开游戏不计）。
- **单局会话**：从游戏进程启动（`ProcessListener.setProcess`）到进程退出（`onExit`）。
  - 推荐默认含 JVM 启动/加载时间（实现简单、不会漏记）；
  - 可选设置「从游戏窗口出现后开始计时」更精确（需要监听窗口检测回调）。
- **过滤**：单局 < 10 秒不计入总时长（但保留会话记录并标记 `short`），避免崩溃/误启动污染。
- **多开**：每个进程一个会话，总时长 = 各会话之和；同时显示「正在游玩 N 个实例」。
- **睡眠/时间跳变**：会话内用 `System.nanoTime` 累计，心跳间隔用墙钟兜底并夹取，避免休眠/改系统时间导致异常数值。
- **时区**：存储 UTC epoch，界面按本地时区展示。

---

## 2. 数据采集与容错

### 接入点

| 时机 | 位置 | 动作 |
|---|---|---|
| 游戏启动 | `LauncherHelper.HMCLProcessListener#setProcess` | 创建 active 会话（实例 id、名称、版本、开始时间）并立即落盘 |
| 运行中 | 定时器（30s） | 更新 `lastHeartbeat`，刷新本次计时 |
| 游戏退出 | `HMCLProcessListener#onExit(exitCode, exitType)` | 写入结束时间、时长、退出类型，移出 active |

### 启动器先退出 / 断电

- 开始会话时写入 `active` 记录；运行中每 30s 更新 `lastHeartbeat`。
- 下次启动发现未结束的 active 会话：
  - 有 `lastHeartbeat` → 结束时间取 `min(lastHeartbeat, now)`，标记 `recovered=true`（误差 ≤ 30s）；
  - 无心跳 → 丢弃并记录日志（避免把闲置时间计入）。
- 关闭启动器（`stopApplication` / shutdown hook）时 flush 一次并正常结束本进程内所有会话。

### 多启动器实例

- 数据文件加 `FileChannel.lock`；读-改-写全程持锁，避免两个启动器互相覆盖。
- 会话结束按 sessionId 幂等合并。

---

## 3. 存储格式

路径：`<用户数据目录>/playtime.json`（`Metadata.HMCL_USER_HOME`，便携模式跟随 `.vncl`）。

```json
{
  "version": 1,
  "instances": {
    "<instance-id>": {
      "name": "1.20.1 Fabric",
      "version": "1.20.1",
      "totalMs": 123456789,
      "lastPlayedAt": 1762600000000,
      "daily": { "2026-11-09": 7200000, "2026-11-08": 3600000 }
    }
  },
  "sessions": [
    {
      "id": "uuid",
      "instance": "<instance-id>",
      "name": "1.20.1 Fabric",
      "startedAt": 1762600000000,
      "endedAt": 1762603600000,
      "durationMs": 3600000,
      "exitType": "NORMAL",
      "recovered": false,
      "short": false
    }
  ],
  "active": [
    { "id": "uuid", "instance": "...", "startedAt": 1762600000000, "lastHeartbeat": 1762600030000 }
  ]
}
```

- 原子写：写临时文件 → `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`；保留一份 `.bak`。
- 写入时机：会话开始、每 30s、会话结束、设置变更、退出应用；其余时间不写盘。
- 体积控制：`sessions` 保留最近 2000 条；`daily` 保留 730 天；超出裁剪。

---

## 4. 界面设计

保持现有极简风格，不新增常驻大面板。

### 4.1 主页

- 右下角一个半透明小胶囊（不遮挡背景图）：
  - 空闲：`今日 1h 12m · 总计 128h`
  - 游戏中：`本次 0:42:13 · 总计 128h`（每秒刷新）
  - 点击进入统计页。
- 侧栏实例项副标题（可选）：
  - 空闲：`已玩 128 小时`
  - 游戏中：`游戏中 · 0:42`

### 4.2 实例详情页（`GameInstancePage`）

- 「游玩时长」行：总时长、最近一次时间、本次（若正在玩）。
- 按钮「查看游玩统计」。

### 4.3 统计页（新增 `PlaytimePage`）

- 顶部 4 个卡片：总计 / 本周 / 今日 / 最近游玩
- 近 7 天柱状图、近 30 天热力图（无数据日期留空）
- 实例排行 TOP 5（按总时长，带进度条）
- 最近会话列表（时间、实例、时长、退出类型；崩溃/恢复标记）
- 里程碑：1 / 10 / 50 / 100 / 500 / 1000 小时徽章，达成时 toast 提示
- 入口：主页胶囊、实例页按钮（建议**不**在侧栏新增入口，保持精简）

### 4.4 设置（设置 → 通用 → 游玩统计）

- 开关：启用统计（默认开）
- 计时起点：进程启动 / 窗口出现（可选，默认进程启动）
- 单局最短计入：默认 10 秒
- 数据：打开数据目录 / 导出 JSON / 导出 CSV / 清空统计（二次确认）

---

## 5. 代码落点

| 文件 | 改动 |
|---|---|
| 新增 `vncl/playtime/VnclPlaytimeManager.java` | 会话开始/结束/心跳、持久化、锁、观察者 |
| 新增 `vncl/playtime/PlaytimeData.java` | 数据结构 + Gson 序列化/迁移 |
| `game/LauncherHelper.java` | `HMCLProcessListener.setProcess` / `onExit` 调用 manager |
| `ui/main/MainPage.java` | 主页右下角统计胶囊 + 1s 刷新 |
| `ui/instances/GameInstancePage.java` | 实例时长行 |
| 新增 `ui/main/PlaytimePage.java` | 统计页 |
| `ui/main/SettingsPage.java` | 统计设置分区 |
| `ui/main/RootPage.java`（可选） | 实例副标题显示时长 |
| `resources/assets/lang/I18N*.properties` | 新增文案（中简/中繁/英） |
| `resources/assets/css/root.css` | 胶囊、图表、热力图样式 |

实现要点：

- manager 单线程串行化（`synchronized` 或单线程 executor），文件 IO 放后台线程；UI 只读 JavaFX 属性快照。
- `onExit` 可能在非 FX 线程 → 更新属性时 `Platform.runLater`。
- 实时刷新用 `Timeline`（1s），仅当页面可见或游戏运行中启用，避免后台轮询。
- 关闭启动器时 flush（`Launcher.stopApplication` / shutdown hook）。
- 不修改游戏文件，不读取世界数据。

---

## 6. 风险与对策

| 风险 | 对策 |
|---|---|
| 启动器先退出导致少计 | 30s 心跳 + `recovered` 标记 |
| 外部启动器启动的游戏不计 | 首次使用提示「仅统计通过 VNCL 启动的游戏」 |
| 多开同时写文件 | 文件锁 + 幂等合并 |
| 系统睡眠/改时间导致超长 | 单调时钟累计 + 心跳增量夹取 |
| 崩溃/强杀 | `onExit` 仍会触发；异常退出照常记录并标记 exitType |
| 数据文件损坏 | 原子写 + `.bak` + 解析失败回退空数据并告警 |

---

## 7. 里程碑

| 阶段 | 内容 | 预估 |
|---|---|---|
| P1 | 采集 + 持久化 + 本次/总计显示（主页胶囊） | 1–1.5 天 |
| P2 | 统计页（7 日/30 日、排行、会话列表） | 1.5–2 天 |
| P3 | 设置项、导出/清空、里程碑、休息提醒 | 1 天 |
| 测试 | 崩溃/多开/启动器先退/睡眠/清零 | 0.5–1 天 |

**MVP 建议**：P1 + 统计页的总计/本周/实例排行/最近会话；先不做按世界统计和在线排行。

---

## 8. 待确认

1. 计时起点：进程启动（含加载）还是游戏窗口出现（更精确）？
2. 主页展示：右下角胶囊 / 侧栏底部 / 实例项副标题，选哪个？
3. 是否需要「连续游玩 X 小时提醒休息」和每日目标？
4. 统计页入口：主页胶囊 + 实例页，还是侧栏新增「统计」？
