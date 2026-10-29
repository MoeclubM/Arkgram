# ArkGram

基于 [Telegram Android](https://github.com/DrKLO/Telegram) 的非官方客户端，保留原版聊天功能，提供额外的账号、翻译、AI 和界面设置。当前上游版本为 Telegram **13.1.1 (7237)**。

[Google Play](https://play.google.com/store/apps/details?id=moe.telecom.arkgram) · [下载最新 Release](https://github.com/MoeclubM/Arkgram/releases/latest) · [隐私政策](https://www.telecom.moe/arkgram/privacy/) · [问题反馈](https://github.com/MoeclubM/Arkgram/issues)

## 下载与版本

| 版本 | Android 包名 | 用途 |
| --- | --- | --- |
| Store | `moe.telecom.arkgram` | 对应 Google Play 渠道，Release 提供 APK 和 AAB；Play 页面是否可安装以商店发布状态及所在地区为准 |
| Web | `moe.telecom.arkgram.web` | 对应上游 standalone 下载渠道，可与 Store 版同时安装；Web 指官网下载版本，仍是 Android 应用 |
| Beta | `moe.telecom.arkgram.beta` | 持续集成生成的测试版本，可与 Store 版同时安装 |

直接安装请下载 `.apk`，`.aab` 用于应用商店分发。正式 Release 的 Store 和 Web 版本包含 ARMv7、ARM64、x86、x86_64，最低要求 Android 7.0。Beta 构建可在 [Actions](https://github.com/MoeclubM/Arkgram/actions/workflows/beta.yml) 中下载。

## 增强功能

- 最多 32 个账号，沿用原版账号切换方式。
- 消息和聊天翻译，可选择 Telegram、其他翻译服务或自定义大模型。
- 自定义 AI 提供商与模型，支持 OpenAI Chat Completions、Responses 和 Claude Messages 接口，并可用于原版 AI 入口及聊天总结。
- 广告过滤、Markdown 显示设置、下载速度与媒体质量设置。
- 独立的增强设置页面、设置导入导出，以及文件管理入口。
- 米色主题、小船标识和可调整的底栏。

聊天文件保存至系统公共下载目录下的 `Download/ArkGram`。AI 请求使用用户配置的服务，相关内容会发送至所选提供商；使用前请阅读该服务的隐私政策。

## 源码与构建

```bash
git clone --recursive --shallow-submodules https://github.com/MoeclubM/Arkgram.git
cd Arkgram
```

已克隆的仓库可以使用 `git submodule update --init --recursive --depth=1` 补齐子模块。

构建环境为 Java 17、Android SDK 36、Build Tools 36.0.0、NDK 27.2.12479018 和 CMake 3.22.1。Gradle 使用仓库自带的 wrapper。

自行发布衍生客户端前，需要：

- 从 [my.telegram.org](https://my.telegram.org/apps) 申请自己的 Telegram API ID 和 API Hash，并修改 `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java`。
- 在 `gradle.properties` 设置自己的应用包名和版本；使用自己的名称与标识，明确说明是非官方客户端。
- 在 Firebase 中为实际包名配置应用，将自己的 `google-services.json` 放入相应模块。
- 将自己的签名文件放入 `TMessagesProj/config/release.keystore`，并通过本机 `local.properties` 或 CI 私密变量提供 `RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`、`RELEASE_STORE_PASSWORD`。不要提交私密凭据。

Store 构建对应 `:TMessagesProj_App:assembleBundleAfatRelease` 和 `:TMessagesProj_App:bundleBundleAfatRelease`；Web 构建对应 `:TMessagesProj_AppStandalone:assembleAfatStandalone` 和 `:TMessagesProj_AppStandalone:bundleAfatStandalone`。正式分发包通过 [Release 工作流](https://github.com/MoeclubM/Arkgram/actions/workflows/release.yml) 构建并签名；推送版本标签会自动发布 GitHub Release。

## 隐私与联系

支持邮箱：[moecaa@telecom.moe](mailto:moecaa@telecom.moe)。数据处理及账号删除说明见[隐私政策](https://www.telecom.moe/arkgram/privacy/)。请勿在公开问题反馈中填写验证码、密码或 API 密钥。

ArkGram 与 Telegram 官方无隶属关系。源码遵循 [GNU GPL v2](LICENSE)；第三方组件保留其各自许可。Telegram [API 文档](https://core.telegram.org/api)及 [MTProto 文档](https://core.telegram.org/mtproto)可用于了解协议。
