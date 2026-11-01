# Feature Map: WebDAV Protocol Hardening, Path Encoding Compliance, and Artwork Request Resilience

## Summary
实现 WebDAV 协议韧性加固与封面请求优化，彻底根除三大协议与网络性能瓶颈：
- **Ticket 01**: RFC 3986 规范路径百分号解码与双向加号（`+`）对称编解码，消除 `1+1.mp3` 或 `AC+DC/` 等含加号文件 404 播放与浏览失败。
- **Ticket 02**: 基于内存目录清单（Directory Manifest）的外置封面候选预过滤，将无内嵌封面专辑浏览时的 HTTP 404 探测风暴从几十次降为 0 次（若无封面）或 1 次（命中有效封面）。
- **Ticket 03**: RFC 7616 / RFC 2617 HTTP Digest 认证支持与 IPv4/IPv6 双栈 OkHttp Fast Fallback，支持群晖等 Digest-only NAS 并消除 10-15s 连接延迟。
- **Ticket 04**: 全量端到端流水线回归与发布包二进制体积预算（APK <= 7.5 MB, classes.dex <= 4.0 MB, arm64-v8a 架构）质量门禁。

---

## Decisions
- **RFC 3986 Path Compliance**: 严禁使用表单标准的 `URLDecoder.decode` 处理 URL 路径；实现专用路径百分号解码器，保留字面量 `+`，仅将 `%XX` 转换为 UTF-8 字符；路径解析中空格编码为 `%20`，字面量 `+` 编码为 `%2B`，确保 100% 字符对称保真。
- **In-Memory Candidate Pre-Filtering**: 目录浏览与音轨元数据富化接缝下发已知文件清单 `directoryFiles: Collection<RemoteFile>?`，在发起任何网络 Range 请求探测候选封面（`$basename.jpg` 等）前先在内存进行忽略大小写（`equalsIgnoreCase`）比对；未包含的文件一律跳过，零网络请求。
- **Digest Authenticator & Safe Retry**: 实现符合 OkHttp `Authenticator` 的 `WebDavDigestAuthenticator`，支持 `MD5` 摘要算法及 `qop="auth"`，维护单调递增 `nc` 与安全随机 `cnonce`；同一次 Challenge 仅重试 1 次，凭据错误立即退出，杜绝死循环；两套 OkHttp Client 均开启 `fastFallback(true)`。

---

## Ticket Topology & Dependency Graph

```text
[01-rfc3986-path-decoding] ──┐
[02-manifest-artwork-filter] ─┼──► [04-end-to-end-pipeline-and-packaging-budget]
[03-digest-auth-fast-fallback]┘
```

---

## Tickets Breakdown
1. `01-rfc3986-path-decoding-and-plus-sign-resilience.md`: RFC 3986 路径解码与加号对称保真 (Status: resolved, Blocked by: None)
2. `02-manifest-aware-artwork-probe-filtering.md`: 内存清单感知的外置封面探测过滤 (Status: resolved, Blocked by: None)
3. `03-digest-authentication-and-fast-fallback.md`: HTTP Digest 认证与 Happy Eyeballs 竞速 (Status: resolved, Blocked by: None)
4. `04-end-to-end-pipeline-and-packaging-budget-gate.md`: 全量回归与二进制体积预算门禁 (Status: resolved, Blocked by: 01, 02, 03)

---

## Decisions-so-far
- **Ticket 01 Resolved**: 实现了 RFC 3986 路径解码算法 `WebDavXmlParser.decodeRfc3986Path`，保留字面量 `+` 并安全处理残缺 `%` 序列；`WebDavServer.resolveFileUrl` 严格保证空格编码为 `%20`、加号编码为 `%2B` 并保留目录斜杠，彻底根除包含加号文件浏览与播放 404 错误。
- **Ticket 02 Resolved**: 元数据解析流水线引入可选的 `directoryFiles` 内存清单，在发起 HTTP 请求前通过忽略大小写的查找表预过滤候选封面文件；未出现的文件直接在内存中剔除并设置哨兵，将外部封面探测网络请求从几十次压降至 0 次或 1 次。
- **Ticket 03 Resolved**: 实现标准的 RFC 7616 / RFC 2617 `WebDavDigestAuthenticator`，支持 `MD5` 与 `qop="auth"`，携带安全随机 `cnonce` 与单调递增 `nc`，并在重试失败后立即中止退出；通过 `FastFallbackDns` 在常规与流媒体连接池实现 IPv4-first 双栈快速回退；动态记录 Digest 服务端主机防止向 Digest 服务端重复发送明文凭据。
- **Ticket 04 Resolved**: 全量 1012 个单元测试与端到端测试 100% 通过（0 失败）；`./gradlew verifyReleasePackaging` 验证通过（APK: 6.68 MB <= 7.5 MB, classes.dex: 3.98 MB <= 4.0 MB, arm64-v8a 单一架构且无 32 位遗留库）。
- **Code Review Remediations Resolved**: 消除单调用卫星文件 `FastFallbackDns.kt`，将其并入 `OkHttpWebDavClient.kt` 消除碎片化并保证深模块内聚；移除投机性拓展属性 `OkHttpClient.fastFallback`，以真实接口为测试表面；纯化 `WebDavDigestAuthenticator` 剔除冗余 Basic 逻辑；清理业务方法中冗余的手动 Auth 构建代码。
