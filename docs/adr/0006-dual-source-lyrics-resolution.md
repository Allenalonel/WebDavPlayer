# Dual-Source Lyrics Resolution Strategy

## Context

WebDAV 音乐收藏中，歌词的存在形式主要有两种：一种是与音频文件同目录且同名的 `.lrc` 文件（常见于 NAS 及自建音乐库），另一种是直接嵌入在音频文件内部的歌词元数据标签（如 ID3v2 USLT 帧或 FLAC Vorbis 歌词块）。

## Decision

采用“双源歌词解析（Dual-Source）”机制：
1. 优先探测当前 `Remote Directory` 下是否存在同名的 `.lrc` 文本文件；
2. 若不存在，则回退提取当前 `Audio Track` 内部内嵌的歌词标签数据。

## Consequences

- 覆盖了绝大多数 WebDAV 用户的音频歌词组织习惯，兼容性最佳。
- 在探测同目录 `.lrc` 时仅需一次轻量的 WebDAV HEAD/GET 请求，不会对网络造成显著负荷。
