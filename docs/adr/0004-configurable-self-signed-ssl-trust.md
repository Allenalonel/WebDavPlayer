# Configurable Self-Signed SSL Trust

## Context

许多私有 WebDAV 服务（如部署在局域网内或家庭 NAS 上的 AList、Nextcloud、群晖等）使用自签名 SSL 证书，或通过自定义 IP:Port 提供 HTTPS 服务。Android 默认安全策略会拦截非受信任 CA 颁发的证书，导致连接直接失败。

## Decision

在 WebDAV Server 连接配置中，为每个服务器提供独立的“信任自签名证书 / 忽略 SSL 校验”开关。默认关闭以确保安全性，允许用户根据实际内网环境手动开启。

## Consequences

- 显著改善局域网与自建 NAS 用户的开箱即用连接体验，无需繁琐地在 Android 系统中安装根证书。
- 开启该选项时，OkHttp 客户端将使用专用的宽松 TrustManager 与 HostnameVerifier，仅对该特定 Server 生效，不影响应用全局网络安全性。
