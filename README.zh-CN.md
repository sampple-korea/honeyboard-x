[![English](https://img.shields.io/badge/English-555?style=flat-square)](README.md) [![한국어](https://img.shields.io/badge/%ED%95%9C%EA%B5%AD%EC%96%B4-555?style=flat-square)](README.ko.md) [![日本語](https://img.shields.io/badge/%E6%97%A5%E6%9C%AC%E8%AA%9E-555?style=flat-square)](README.ja.md) [![简体中文](https://img.shields.io/badge/%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-2F80ED?style=flat-square)](README.zh-CN.md)

# HoneyBoard X

为三星键盘添加 Rambler 语音输入和 Codex 翻译的 LSPosed 模块。

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white) ![LSPosed](https://img.shields.io/badge/LSPosed-libxposed%20API%20102-6f42c1) ![License: MIT](https://img.shields.io/badge/License-MIT-blue)

## 功能

- **Rambler 语音输入：**在三星键盘工具栏添加语音按钮，直接使用 Gboard Rambler 语音识别服务。提供极光风格标题栏、实时识别、多语言识别和语音编辑指令；识别完成后一次性插入文本。支持静音自动结束、长按暂停/继续，以及可选的触觉反馈和声音。
- **Codex 翻译：**将三星键盘翻译面板的请求交由 ChatGPT/Codex 处理。可选择模型和推理强度、设置翻译指令，并查看账户模型与用量。5 小时或每周用量达到 0% 时会自动切换至 `gpt-reserve`；请求失败时可回退至三星内置翻译。
- **统一设置应用：**采用 One UI 风格的启动器应用，支持韩语、英语、中文和日语。

## 要求

- 安装了三星键盘的三星设备。本模块面向 One UI 8 / Android 16～17 上的 `com.samsung.android.honeyboard` **5.9.40.75**。键盘更新可能改变私有实现并导致兼容性问题。
- Android 12 或更高版本（模块最低 SDK 为 31）。
- 支持 libxposed API 102 的 LSPosed。
- 网络连接。语音输入需要为三星键盘授予麦克风权限；翻译需要登录 ChatGPT/Codex。

## 安装

1. 如果正在使用旧版独立 Samsung Rambler 或 Codex 翻译模块，请先卸载。应用私有设置和登录令牌无法迁移；请记录偏好设置并准备重新登录。
2. 如果项目 **Releases** 中提供了已签名的 HoneyBoard X APK，请下载并安装；否则请按下文说明自行从源码构建。
3. 在 LSPosed 中启用 **HoneyBoard X**，并将作用范围设为三星键盘（`com.samsung.android.honeyboard`）。
4. 强行停止三星键盘，然后重新打开以应用 Hook。
5. 从启动器打开 HoneyBoard X 并配置语音输入。使用翻译时，请启用该功能并登录 ChatGPT/Codex。

## Google API 密钥（语音输入必需）

HoneyBoard X **不附带** Gboard 用于 Rambler 的 Google API 密钥。请自行从 Gboard APK 中获取 `X-Goog-Api-Key` 值（以 `AIza` 开头的 39 个字符），然后任选其一：

- **在应用中：** HoneyBoard X → 语音输入 → **Google API 密钥**，粘贴密钥
- **构建时：** `GOOGLE_API_KEY=AIza... module/build.sh`，或在 `module/local.keys`（已 gitignore）中写入 `google_api_key=AIza...`

发布版 APK 未内置密钥，需要在应用中输入。

## 设置

应用包含语音输入、翻译和信息界面。语音设置包括主要/附加识别语言、静音结束时间、极光效果、触觉反馈和声音。翻译设置包括启用开关、模型、推理强度、自定义指令、三星翻译回退，以及账户、模型和用量信息。

轻触语音按钮可结束识别并插入结果；长按可暂停/继续。语音输入需要三星键盘的麦克风权限。

## 从源码构建

需要 JDK 17 或更高版本、Android 构建工具（`aapt`、`zipalign`、`apksigner`、`zip`），以及[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)中列出的 SDK 文件。

```bash
./module/build.sh
```

签名后的 APK 输出到 `module/build/HoneyBoardX.apk`。环境配置、测试命令、设置迁移和已知限制请参阅开发文档。

## 隐私

- 语音音频和光标前的上下文（最多 2,000 个 UTF-16 字符）会发送至 Google Rambler 语音识别服务，用于处理语音编辑指令。
- 请求翻译的文本会发送至 ChatGPT/Codex 服务器。
- 登录令牌使用 Android Keystore 加密。语音输入需要三星键盘的麦克风权限。

## 免责声明

HoneyBoard X 是独立的非官方项目，与 Samsung、Google 或 OpenAI 均无关联，也未获得其认可。项目依赖私有 API 和服务行为，这些内容可能随时更改或被屏蔽。使用风险和责任由用户自行承担。

## 致谢

- [PixelBoard](https://github.com/Akshayykadam/PixelBoard) — Rambler 实现参考
- [libxposed](https://github.com/libxposed) — LSPosed 模块 API

## 许可证

[MIT](LICENSE) © 2026 sampple-korea。内置的 libxposed 库采用 Apache-2.0（`module/libs/LICENSE-libxposed.txt`）。

## 作者：[sampple-korea](https://github.com/sampple-korea)
