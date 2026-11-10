# VNCL 远程联机 P0 记录

## 目标

Java 版 Minecraft 远程联机：房主在单机世界「对局域网开放」，VNCL 通过阿里云 ECS 中转，让同样使用 VNCL 的朋友加入。

## 方案

采用 **frp STCP + visitor**，而不是给每个房间分配公网端口：

```
房主 VNCL ── frpc (stcp proxy) ──TLS──> ECS frps:7000
访客 VNCL ── frpc (stcp visitor) ──TLS──> ECS frps:7000
访客 MC ──> 127.0.0.1:<local visitor port> ──> 房主 <LAN port>
```

只需要在 ECS 开放 **7000/tcp**，不需要端口段，也不需要 Cloudflare Spectrum。

## ECS 现状（2026-11-10）

- 地域：华东 1（杭州）
- 规格：2 vCPU / 2 GiB / 40 GiB ESSD
- 系统：Ubuntu 20.04.6 LTS（注意：已 EOL，建议后续升级或启用 ESM）
- 现有服务（未改动）：
  - Nginx 1.18：80/443，站点 `nekoinn.top`，Cloudflare Origin 证书（通配 `*.nekoinn.top`）
  - Docker：RustDesk `21115–21119`，NapCat `3000/3001/6099`
  - UFW：80/443 仅允许 Cloudflare 网段；22 限流
- 已部署：
  - `/opt/vncl-relay/frps`（frp 0.61.1，从源码编译）
  - `/opt/vncl-relay/frps.toml`（权限 600）
  - `/etc/systemd/system/vncl-frps.service`，已 enable + active
  - UFW 放行 `7000/tcp`
  - 阿里云安全组放行 `7000/tcp`（用户手动添加）
- frps dashboard：`127.0.0.1:7500`，不对公网开放

## P0 测试结果

### 1. ECS 本机回环测试（通过）

- TCP echo `127.0.0.1:25565`
- 房主 frpc stcp proxy
- 访客 frpc stcp visitor `127.0.0.1:25566`
- 结果：`recv: b'vncl-p0-test'`

### 2. 公网端到端测试（通过）

- Windows 本机运行 TCP echo + 房主 frpc + 访客 frpc
- frpc 通过公网 `47.98.250.11:7000` 登录 ECS frps
- 访客连接 `127.0.0.1:25566`，经 ECS 中转回显
- 结果：`recv: vncl-external-p0`
- 日志：`login to server success` / `start proxy success` / `visitor added`

说明 TLS、token、STCP、visitor、ECS 中转链路均正常。

## 尚未验证

- 真实 Minecraft 局域网世界（需要房主在世界中「对局域网开放」后抓端口）
- 房间 API、VNCL UI、一键进服
- 多房间并发、限流、清理

## 下一步

### P1

1. `relay.nekoinn.top` DNS 生效（当前仍 NXDOMAIN）
2. Nginx 增加 `relay.nekoinn.top` server block，`/vncl/` 反代 room-api
3. Go room-api：
   - 创建房间：`code + proxyName + secret`
   - 查询房间：给访客返回 `proxyName + secret`
   - 心跳/TTL/删除
   - 限流：房间数、每房人数、单 IP 频率
4. VNCL 联机页：
   - 房主：检测 LAN 端口 → 创建房间 → 启动 frpc → 展示房间码
   - 访客：输入房间码 → 启动 visitor → 本地端口就绪 → quick play
5. 真实 MC 联机验收

### 安全注意

- 当前 frpc 配置含全局 frps token，随客户端分发意味着用户可提取并创建任意 stcp proxy；朋友小范围可用，公开分发前应改为 frps HTTP plugin / 每房间 token。
- frps 7000 暴露公网，需要连接数/频率限制与监控。
- MC 流量到 frps 后为明文（frp TLS 只保护隧道），ECS 上理论上可见。
- Ubuntu 20.04 EOL，建议后续快照后升级。

## 相关模板

- `deploy/relay/frps.toml.example`
- `deploy/relay/frpc-host.toml.example`
- `deploy/relay/frpc-visitor.toml.example`
- `deploy/relay/vncl-frps.service.example`

## P1 后端进展（2026-11-10）

- `relay.nekoinn.top` DNS 已生效（Cloudflare 代理）
- Nginx 新增 `relay.nekoinn.top` server block，复用 `*.nekoinn.top` Origin 证书
- Go room-api 已部署为 `vncl-room-api.service`（127.0.0.1:8080，内存存储，10 房间上限，12h TTL）
- API 路径：`https://relay.nekoinn.top/vncl/api/rooms`
  - `POST /vncl/api/rooms` 创建（返回 code/proxyName/secret/hostToken）
  - `GET /vncl/api/rooms/{code}` 访客取配置
  - `POST /vncl/api/rooms/{code}/heartbeat` 续期
  - `DELETE /vncl/api/rooms/{code}` 删除（Bearer hostToken）
- 已验证：经 Cloudflare 的 health、create、join、heartbeat、delete 全流程通过
- 注意：room-api 使用内存存储，服务重启会清空房间；房间 TTL 12h，服务端每 IP 创建/加入限流
- 待决：frps token 如何下发给 VNCL（内嵌构建 vs 房间 API 返回）；公开分发前建议改为 frps HTTP plugin/每房间 token

## P1 客户端（2026-11-10）

- 侧栏新增「远程联机」独立页（`VnclRelayPage`）
- 房主：
  - 输入/自动填入局域网端口
  - 创建房间 → room API → 生成 host 配置并启动随包 frpc
  - 显示房间码、可复制；关闭房间时删除房间并停止 frpc
- 访客：
  - 输入房间码 → room API → 生成 visitor 配置（`serverUser` 指向房主用户）并启动 frpc
  - 本地监听优先 25566，被占用时使用临时端口
  - 显示 `127.0.0.1:<port>`，可复制
- 局域网端口由游戏日志 `Started serving on <port>` 自动识别（`LauncherHelper`），识别失败可手动填
- frpc Windows 二进制随 jar 打包（`assets/vncl/frpc.exe`，Apache-2.0，已附 `frp-LICENSE.txt`）
- frps 改为 HTTP plugin 鉴权：
  - 房间创建返回 host/guest 独立 token
  - frps `[[httpPlugins]]` 调用 room API `/plugin`
  - 访客只能加入，不能创建 proxy；Nginx 显式 404 `/vncl/plugin`
- 已知限制：
  - 仍需手动「对局域网开放」，未实现自动开 LAN
  - 加入后只显示本地地址，尚未做 MC quick play 自动进服
  - 尚未用真实 Minecraft 世界做最终联机验收
  - room API 创建接口无账号鉴权，按 IP 限流，适合朋友小范围使用

### 真实 Minecraft 验收（2026-11-10）

- 用户创建房间 `X8T9ZB`，房间 API 返回有效配置
- 本机 visitor frpc 登录 ECS 成功，`visitor added`
- 通过 `127.0.0.1:25566` 发送 Minecraft status 请求，成功返回：
  - version: `26.3`
  - players: `1 / 8`
- 说明真实 Minecraft 局域网世界已能经 ECS + per-room token 中转访问；测试 visitor 已停止，房间保留给用户
