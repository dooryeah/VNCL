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
