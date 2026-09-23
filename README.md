# ChatHelp 智谱助手 · Android

基于 [jev-chat/jev-chat-jarvis](https://github.com/jev-chat/jev-chat-jarvis) 改造的非官方 Android 聊天副驾，感谢 Finderchangchang 与 jev-chat contributors。默认使用国内智谱 BigModel 的免费 `glm-4-flash-250414` 模型完成聊天判断、候选回复和排序；保留免费的本机中文 OCR。不代表原项目、智谱或聊天平台官方背书。

在微信、QQ 等聊天应用中读取当前屏幕，展示七项判断与三条候选回复。点选后只填入输入框，发送由用户手动完成。首版重点验证微信和 QQ，其他上游适配器继续保留。

## 安装和配置

1. 从 [本分支 Release](https://github.com/qq244901796/chathelp-android/releases/tag/v1.4.2-glm) 下载 `chathelp-android-1.4.2-glm-release.apk`。应用名为「ChatHelp 智谱助手」，包名 `com.jev.probe.glm.release`，适用于 Android 11+ 的 ARM64 手机，可覆盖升级本分支旧发布版，与原版和调试版共存。
2. 在 [智谱控制台](https://bigmodel.cn/) 创建自己的通用 API Key。
3. 打开「设置」，新安装已经选好智谱；旧配置可点击「一键应用智谱免费预设」。在判断接口中填写智谱密钥，回复密钥留空即可。
4. 点击「测试完整流程」，使用内置虚构对话检查判断、回复、排序，然后点击页面底部「保存」。
5. 按主页向导开启无障碍、悬浮窗，并根据手机系统允许后台运行。在聊天页面查看建议，自行确认填入的内容后发送。

| 项目 | 默认值 |
| --- | --- |
| 判断协议 | BigModel 标准聊天接口 |
| API Base URL | `https://open.bigmodel.cn/api/paas/v4` |
| 请求路径 | `/chat/completions` |
| 判断、回复模型 | `glm-4-flash-250414` |
| 中文 OCR | 随 APK 打包的 ML Kit，在本机运行 |
| 云端图片理解 | 默认关闭，不参与聊天采集 |
| 聊天历史记录 | 默认关闭，仅在用户开启后存本机 |

免费指上述模型当前的通用 API 推理价格，不代表无限并发或未来价格保证。参见 [智谱官方定价](https://docs.bigmodel.cn/cn/guide/start/pricing) 和 [速率限制](https://docs.bigmodel.cn/cn/api/rate-limit)。程序不会自动改用收费模型。其他供应商预设属于高级配置，费用由对应平台决定。

## 工作方式

### 没有聊天账号也能测试

同时安装 Release 中的 `chathelp-test-chat-1.0.0-release.apk`，打开「ChatHelp 测试聊天」。它提供两组虚构会话、模拟双方消息、普通文字和 OCR 测试模式，可以验证分析与回复填入。必须配合 ChatHelp 1.4.2-glm 或更新版。详细步骤见 [测试聊天说明](test-chat/README.md)。

无障碍读取文字 → 读不到时本机 OCR → 智谱七项判断 → 生成三条回复 → 智谱排序 → 悬浮窗展示 → 复制或填入。

原版 Jev 的判断使用专用 `state/questions` 协议。本分支新增 BigModel 适配，将既有判断标准转成提示词并校验 JSON 回答；原有 Jev 供应商配置仍可使用。

同一进程的智谱请求串行，最多尝试三次可重试的网络/限流错误。密钥错误不重试，格式异常不会补成固定回复。切换应用、联系人或聊天内容后，过期结果不再显示。评分、把握度和排序比例只是模型估计。

## OCR 与数据

- 保留 `com.google.mlkit:text-recognition-chinese:16.0.1` bundled 中文识别模型，不需要 OCR API Key，识别模型不依赖首次联网下载或 Google Play 服务。
- 「读不到文字时使用本机 OCR」开关可以关闭；关闭后，无障碍不暴露正文的界面可能无法读取。
- 通用「截屏识别一次」沿用上游行为：不区分说话方，界面会标注；按已知气泡矩形识别时保留说话方。
- 本机 OCR 不向模型接口上传截图。识别出的聊天文字会在分析时发送到配置的模型接口。
- ML Kit 可能向 Google 发送性能和使用量统计，以及请求设备兼容信息；“离线可识别”不代表 SDK 完全不联网。详见 [隐私说明](PRIVACY.md) 及 [ML Kit 官方条款](https://developers.google.com/ml-kit/terms)。
- 云端图片理解是单独的可选接口，目前仅保留手动连通测试；关闭时不会发出视觉请求。
- 密钥保存在应用私有设置中，不写入源码或日志。密钥只在相同接口域名之间继承，切换预设不会把智谱密钥发给其他服务商。
- 联系人、知识库和用户主动开启的历史记录继续只存本机。没有自建中转服务器。

## 开发与验证

需要 JDK 17、Android SDK 35，手机 Android 11+、ARM64。配置 `local.properties` 中的 SDK 路径，或设置 `ANDROID_HOME`。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :test-chat:installRelease
.\gradlew.bat :app:connectedDebugAndroidTest
```

JVM 测试使用本地 HTTP 服务和虚构数据，覆盖协议、解析、重试、串行与过期结果。设备测试覆盖真实 Android 配置迁移、禁用视觉，以及合成中文图片的 ML Kit 识别。真实智谱响应需要用户在应用中配置有效密钥后测试。

详细说明见 [智谱改造说明](docs/BIGMODEL.md)。上游 `docs/v1.3-*` 与 `tools/jev/` 是原 Jev 实现的历史资料，不能用于验证 GLM 输出质量。

构建可分发版本（设置 JDK 17 后执行）：

```powershell
python scripts/prepare_release_signing.py
.\gradlew.bat :app:assembleRelease
```

签名脚本只在首次运行时生成发布证书，重复执行会保留已有证书。请单独备份被 Git 忽略的 `.signing/`，后续更新必须复用相同签名。构建原始产物为 `app/build/outputs/apk/release/app-release.apk`；发布包不包含 API Key。签名配置和设备测试命令见 [发布构建说明](docs/BIGMODEL.md#发布构建)。

## 来源与许可

保留上游 [LICENSE](LICENSE)、[NOTICE](NOTICE) 与 Git 历史，自有代码沿用 MIT；SDK 各受其原许可/服务条款约束，不能把整个 APK 宣称为 MIT。新增品牌图标区分修改版，当前分支移除原版旧 APK 和宣传入口。

详见 [来源](ABOUT.md)、[许可范围](LICENSING.md)、[第三方完整通知](THIRD_PARTY_NOTICES.txt)、[隐私说明](PRIVACY.md)、[测试范围](TESTING.md) 和 [变更记录](CHANGELOG.md)。上述关于与许可信息也可在应用内离线阅读。

Windows 修改版：[chathelp-windows](https://github.com/qq244901796/chathelp-windows)。Windows 保留 GPL 界面组件，组合程序按 GPLv3 分发，许可范围与安卓版不同。
